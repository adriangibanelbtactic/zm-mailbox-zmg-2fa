/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2010, 2011, 2012, 2013, 2014 Zimbra, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.imap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.zimbra.common.localconfig.DebugConfig;
import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.SoapProtocol;
import com.zimbra.common.util.Constants;
import com.zimbra.common.util.Pair;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.imap.ImapHandler.ImapExtension;
import com.zimbra.cs.index.SearchParams;
import com.zimbra.cs.index.SortBy;
import com.zimbra.cs.index.ZimbraHit;
import com.zimbra.cs.index.ZimbraQueryResults;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.mailbox.SearchFolder;
import com.zimbra.cs.mailbox.util.TagUtil;
import com.zimbra.cs.memcached.MemcachedConnector;
import com.zimbra.cs.session.Session;
import com.zimbra.cs.util.EhcacheManager;
import com.zimbra.cs.util.Zimbra;

final class ImapSessionManager {
    static final long SERIALIZER_INTERVAL_MSEC =
            DebugConfig.imapSessionSerializerFrequency * Constants.MILLIS_PER_SECOND;
    static final long SESSION_INACTIVITY_SERIALIZATION_TIME =
            DebugConfig.imapSessionInactivitySerializationTime * Constants.MILLIS_PER_SECOND;
    static final int TOTAL_SESSION_FOOTPRINT_LIMIT = DebugConfig.imapTotalNonserializedSessionFootprintLimit;
    static final int MAX_NONINTERACTIVE_SESSIONS = DebugConfig.imapNoninteractiveSessionLimit;
    static final boolean CONSISTENCY_CHECK = DebugConfig.imapCacheConsistencyCheck;

    private static final boolean TERMINATE_ON_CLOSE = DebugConfig.imapTerminateSessionOnClose;
    private static final boolean SERIALIZE_ON_CLOSE = DebugConfig.imapSerializeSessionOnClose;

    /**
     * ConcurrentLinkedHashMap is used because it has good concurrency attributes, offers fast access by key and
     * maintains information to easily iterate over the keys by order of most recent access.
     * Note that the values are not currently used at all.
     */
    private final ConcurrentLinkedHashMap<ImapSession, Object> sessions =
            new ConcurrentLinkedHashMap.Builder<ImapSession, Object>()
            .initialCapacity(128)
            .maximumWeightedCapacity(Long.MAX_VALUE) // we manually manage evictions
            .build();
    private final Cache activeSessionCache; // not LRU'ed
    private final Cache inactiveSessionCache; // LRU'ed

    private static final ImapSessionManager SINGLETON = new ImapSessionManager();

    private static final ExecutorService CLOSER = Executors.newSingleThreadExecutor(
                    new ThreadFactoryBuilder().setNameFormat("ImapInvalidSessionCloser").setDaemon(true).build());

    private ImapSessionManager() {
        if (SERIALIZER_INTERVAL_MSEC > 0) {
            Zimbra.sTimer.schedule(new SessionSerializerTask(), SERIALIZER_INTERVAL_MSEC, SERIALIZER_INTERVAL_MSEC);
            ZimbraLog.imap.debug("initializing IMAP session serializer task");
        }
        if (LC.imap_use_ehcache.booleanValue()) {
            activeSessionCache = new EhcacheImapCache(EhcacheManager.IMAP_ACTIVE_SESSION_CACHE, true);
            Preconditions.checkState(activeSessionCache != null);
        } else {
            activeSessionCache = new DiskImapCache();
        }
        //inactive preference order memcache, ehcache, diskcache
        inactiveSessionCache = MemcachedConnector.isConnected() ?
                new MemcachedImapCache() : (LC.imap_use_ehcache.booleanValue() ?
                new EhcacheImapCache(EhcacheManager.IMAP_INACTIVE_SESSION_CACHE, false) :
                activeSessionCache);
        Preconditions.checkState(inactiveSessionCache != null);
    }

    static ImapSessionManager getInstance() {
        return SINGLETON;
    }

    /**
     * Record that this session has been used.  The underlying ConcurrentLinkedHashMap uses this information to
     * ensure that the iterator associated with ascendingKeySet() / descendingKeySet() will have the correct order.
     *  i.e. iterator returns the keys whose order of iteration is the ascending order in which its entries are
     *       considered eligible for retention, from the least-likely to be retained to the most-likely or vice versa.
     */
    void recordAccess(ImapSession session) {
        sessions.get(session);
    }

    void uncacheSession(ImapSession session) {
        sessions.remove(session);
    }

    /**
     * <ol>
     *  <li>deserialize/reserialize sessions with notification overflow
     *  <li>serialize enough sessions to get under the max memory footprint
     *  <li>prune noninteractive sessions beyond a specified count
     *  <li>maybe checkpoint a few "dirty" sessions if we're not doing anything else?
     * </ol>
     */
    final class SessionSerializerTask extends TimerTask {

        @Override
        public void run() {
            ZimbraLog.imap.debug("running IMAP session serializer task");

            try {
                long cutoff = SESSION_INACTIVITY_SERIALIZATION_TIME > 0 ?
                        System.currentTimeMillis() - SESSION_INACTIVITY_SERIALIZATION_TIME : Long.MIN_VALUE;

                List<ImapSession> overflow = new ArrayList<ImapSession>();
                List<ImapSession> pageable = new ArrayList<ImapSession>();
                List<ImapSession> droppable = new ArrayList<ImapSession>();

                // first, figure out the set of sessions that'll need to be brought into memory and reserialized
                int maxOverflow = 0;
                Iterator<ImapSession> unorderedIterator = sessions.keySet().iterator();
                while (unorderedIterator.hasNext()) {
                    ImapSession session = unorderedIterator.next();
                    if (session.requiresReload()) {
                        overflow.add(session);
                        // note that these will add to the memory footprint temporarily, so need the largest size...
                        maxOverflow = Math.max(maxOverflow, session.getEstimatedSize());
                    }
                }
                int footprint = Math.min(maxOverflow, TOTAL_SESSION_FOOTPRINT_LIMIT - 1000);
                int noninteractive = 0;

                // next, get the set of in-memory sessions that need to get serialized out or dropped.

                // As we are more likely to decide to drop or page out sessions we process later, we want to start
                // with the most recently used ones as they are more likely to be useful again.
                Iterator<ImapSession> mostRecentToLeastRecentIterator = sessions.descendingKeySet().iterator();
                while (mostRecentToLeastRecentIterator.hasNext()) {
                    ImapSession session = mostRecentToLeastRecentIterator.next();
                    int size = session.getEstimatedSize();
                    // want to serialize enough sessions to get below the memory threshold
                    // also going to serialize anything that's been idle for a while
                    if (!session.isInteractive() && ++noninteractive > MAX_NONINTERACTIVE_SESSIONS) {
                        droppable.add(session);
                    } else if (!session.isSerialized() && session.getLastAccessTime() < cutoff) {
                        pageable.add(session);
                    } else if (footprint + size > TOTAL_SESSION_FOOTPRINT_LIMIT) {
                        pageable.add(session);
                    } else {
                        footprint += size;
                    }
                }

                for (ImapSession session : pageable) {
                    try {
                        ZimbraLog.imap.debug("Paging out session due to staleness or total memory footprint: %s (sid %s)",
                                session.getPath(), session.getSessionId());
                        session.unload(true);
                    } catch (Exception e) {
                        ZimbraLog.imap.warn("error serializing session; clearing", e);
                        // XXX: make sure this doesn't result in a loop
                        quietRemoveSession(session);
                    }
                }

                for (ImapSession session : overflow) {
                    try {
                        ZimbraLog.imap.debug("Loading/unloading paged session due to queued notification overflow: %s (sid %s)",
                                session.getPath(), session.getSessionId());
                        if (session.reload() instanceof ImapFolder) {
                            session.unload(true);
                        } else {
                            ZimbraLog.imap.debug("unable to reload session during paged overflow replay; probably evicted from cache");
                            quietRemoveSession(session);
                        }
                    } catch (ImapSessionClosedException ignore) {
                    } catch (Exception e) {
                        ZimbraLog.imap.warn("error deserializing overflowed session; clearing", e);
                        // XXX: make sure this doesn't result in a loop
                        quietRemoveSession(session);
                    }
                }

                for (ImapSession session : droppable) {
                    ZimbraLog.imap.debug("Removing session due to having too many noninteractive sessions: %s (sid %s)",
                            session.getPath(), session.getSessionId());
                    // only noninteractive sessions get added to droppable list, so this next conditional should never be true
                    quietRemoveSession(session);
                }
            } catch (Throwable t) {  //don't let exceptions kill the timer
                ZimbraLog.imap.warn("Error during IMAP session serializer task", t);
            }
        }

        private void quietRemoveSession(final ImapSession session) {
            // XXX: make sure this doesn't result in a loop
            try {
                if (session.isInteractive()) {
                    CLOSER.submit(new Runnable() {
                        @Override
                        public void run() {
                            session.cleanup();
                        }
                    });
                }
                session.detach();
            } catch (Exception e) {
                ZimbraLog.imap.warn("skipping error while trying to remove session", e);
            }
        }
    }

    static class InitialFolderValues {
        final int uidnext, modseq;
        int firstUnread = -1;

        InitialFolderValues(Folder folder) {
            uidnext = folder.getImapUIDNEXT();
            modseq = folder.getImapMODSEQ();
        }
    }

    Pair<ImapSession, InitialFolderValues> openFolder(ImapPath path, byte params, ImapHandler handler) throws ServiceException {
        ZimbraLog.imap.debug("opening folder: %s", path);

        if (!path.isSelectable()) {
            throw ServiceException.PERM_DENIED("cannot select folder: " + path);
        }
        if ((params & ImapFolder.SELECT_CONDSTORE) != 0) {
            handler.activateExtension(ImapExtension.CONDSTORE);
        }

        Folder folder = (Folder) path.getFolder();
        int folderId = folder.getId();
        Mailbox mbox = folder.getMailbox();
        // don't have a session when the folder is loaded...
        OperationContext octxt = handler.getCredentials().getContext();

        mbox.beginTrackingImap();

        List<ImapMessage> i4list = null;
        // *always* recalculate the contents of search folders
        if (folder instanceof SearchFolder) {
            i4list = loadVirtualFolder(octxt, (SearchFolder) folder);
        }

        mbox.lock.lock();
        try {
            // need mInitialRecent to be set *before* loading the folder so we can determine what's \Recent
            folder = mbox.getFolderById(octxt, folderId);
            int recentCutoff = folder.getImapRECENTCutoff();

            if (i4list == null) {
                List<Session> listeners = mbox.getListeners(Session.Type.IMAP);
                // first option is to duplicate an existing registered session
                //   (could try to just activate an inactive session, but this logic is simpler for now)
                i4list = duplicateExistingSession(folderId, listeners);
                // no matching session means we next check for serialized folder data
                if (i4list == null) {
                    i4list = duplicateSerializedFolder(folder);
                } else if (CONSISTENCY_CHECK) {
                    Collections.sort(i4list);
                    //sort only if using list from duplicated session which may be out of order
                    //if loaded from serialized folder order _should_ already be OK since no changes have occurred
                }
                // do the consistency check, if requested
                if (CONSISTENCY_CHECK) {
                    i4list = consistencyCheck(i4list, mbox, octxt, folder);
                }
                // no matching serialized session means we have to go to the DB to get the messages
                if (i4list == null) {
                    i4list = mbox.openImapFolder(octxt, folderId);
                }
            }

            Collections.sort(i4list);
            // check messages for imapUid <= 0 and assign new IMAP IDs if necessary
            renumberMessages(octxt, mbox, i4list);

            ImapFolder i4folder = new ImapFolder(path, params, handler);

            // don't rely on the <code>Folder</code> object being updated in place
            folder = mbox.getFolderById(octxt, folderId);
            // can't set these until *after* loading the folder because UID renumbering affects them
            InitialFolderValues initial = new InitialFolderValues(folder);

            for (ImapMessage i4msg : i4list) {
                i4folder.cache(i4msg, i4msg.imapUid > recentCutoff);
                if (initial.firstUnread == -1 && (i4msg.flags & Flag.BITMASK_UNREAD) != 0) {
                    initial.firstUnread = i4msg.sequence;
                }
            }
            i4folder.setInitialSize();
            ZimbraLog.imap.debug("added %s", i4list);

            ImapSession session = null;
            try {
                session = new ImapSession(i4folder, handler);
                session.register();
                sessions.put(session, session /* cannot be null for ConcurrentLinkedHashMap */);
                return new Pair<ImapSession, InitialFolderValues>(session, initial);
            } catch (ServiceException e) {
                if (session != null) {
                    session.unregister();
                }
                throw e;
            }
        } finally {
            mbox.lock.release();
        }
    }

    /** Fetches the messages contained within a search folder.  When a search
     *  folder is IMAP-visible, it appears in folder listings, is SELECTable
     *  READ-ONLY, and appears to have all matching messages as its contents.
     *  If it is not visible, it will be completely hidden from all IMAP
     *  commands.
     * @param octxt   Encapsulation of the authenticated user.
     * @param search  The search folder being exposed. */
    private static List<ImapMessage> loadVirtualFolder(OperationContext octxt, SearchFolder search) throws ServiceException {
        List<ImapMessage> i4list = new ArrayList<ImapMessage>();

        Set<MailItem.Type> types = ImapFolder.getTypeConstraint(search);
        if (types.isEmpty()) {
            return i4list;
        }

        SearchParams params = new SearchParams();
        params.setQueryString(search.getQuery());
        params.setIncludeTagDeleted(true);
        params.setTypes(types);
        params.setSortBy(SortBy.DATE_ASC);
        params.setChunkSize(1000);
        params.setFetchMode(SearchParams.Fetch.IMAP);

        Mailbox mbox = search.getMailbox();
        try {
            ZimbraQueryResults zqr = mbox.index.search(SoapProtocol.Soap12, octxt, params);
            try {
                for (ZimbraHit hit = zqr.getNext(); hit != null; hit = zqr.getNext()) {
                    i4list.add(hit.getImapMessage());
                }
            } finally {
                zqr.close();
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceException.FAILURE("failure opening search folder", e);
        }
        return i4list;
    }

    private static List<ImapMessage> duplicateExistingSession(int folderId, List<Session> sessionList) {
        for (Session session : sessionList) {
            ImapSession i4listener = (ImapSession) session;
            if (i4listener.getFolderId() == folderId) {
                //   FIXME: may want to prefer loaded folders over paged-out folders
                synchronized (i4listener) {
                    ImapFolder i4selected;
                    try {
                        i4selected = i4listener.getImapFolder();
                    } catch (ImapSessionClosedException e) {
                        return null;
                    }
                    if (i4selected == null) { // cache miss
                        return null;
                    }
                    // found a matching session, so just copy its contents!
                    ZimbraLog.imap.info("copying message data from existing session: %s", i4listener.getPath());

                    final List<ImapMessage> i4list = new ArrayList<ImapMessage>(i4selected.getSize());
                    i4selected.traverse(new Function<ImapMessage, Void>() {
                        @Override
                        public Void apply(ImapMessage i4msg) {
                            if (!i4msg.isExpunged()) {
                                i4list.add(new ImapMessage(i4msg));
                            }
                            return null;
                        }
                    });

                    // if we're duplicating an inactive session, nuke that other session
                    // XXX: watch out for deadlock between this and the SessionCache
                    if (!i4listener.isInteractive()) {
                        i4listener.unregister();
                    }
                    return i4list;
                }
            }
        }
        return null;
    }

    private List<ImapMessage> duplicateSerializedFolder(Folder folder) {
        ImapFolder i4folder = getCache(folder);
        if (i4folder == null) { // cache miss
            return null;
        }
        ZimbraLog.imap.info("copying message data from serialized session: %s", folder.getPath());

        final List<ImapMessage> i4list = new ArrayList<ImapMessage>(i4folder.getSize());
        i4folder.traverse(new Function<ImapMessage, Void>() {
            @Override
            public Void apply(ImapMessage i4msg) {
                if (!i4msg.isExpunged()) {
                    i4list.add(i4msg.reset());
                }
                return null;
            }
        });
        return i4list;
    }

    private List<ImapMessage> consistencyCheck(List<ImapMessage> i4list, Mailbox mbox, OperationContext octxt, Folder folder) {
        if (i4list == null) {
            return i4list;
        }

        String fid = mbox.getAccountId() + ":" + folder.getId();
        try {
            List<ImapMessage> actual = mbox.openImapFolder(octxt, folder.getId());
            Collections.sort(actual);
            if (i4list.size() != actual.size()) {
                ZimbraLog.imap.error("IMAP session cache consistency check failed: inconsistent list lengths " +
                        "folder=%s,cache=%d,db=%d,diff={cache:%s,db:%s},dupes=%s", fid, i4list.size(), actual.size(),
                        diff(i4list, actual), diff(actual, i4list), dupeCheck(i4list));
                clearCache(folder);
                return actual;
            }

            for (Iterator<ImapMessage> it1 = i4list.iterator(), it2 = actual.iterator(); it1.hasNext() || it2.hasNext(); ) {
                ImapMessage msg1 = it1.next(), msg2 = it2.next();
                if (msg1.msgId != msg2.msgId || msg1.imapUid != msg2.imapUid) {
                    ZimbraLog.imap.error("IMAP session cache consistency check failed: id mismatch " +
                            "folder=%s,cache=%d/%d,db=%d/%d,diff={cache:%s,db:%s}",
                            fid, msg1.msgId, msg1.imapUid, msg2.msgId, msg2.imapUid,
                            diff(i4list, actual), diff(actual, i4list));
                    clearCache(folder);
                    return actual;
                } else if (msg1.flags != msg2.flags || msg1.sflags != msg2.sflags || !TagUtil.tagsMatch(msg1.tags, msg2.tags)) {
                    ZimbraLog.imap.error("IMAP session cache consistency check failed: flag/tag/sflag mismatch " +
                            "folder=%s,cache=%X/[%s]/%X,db=%X/[%s]/%X,diff={cache:%s,db:%s}", fid,
                            msg1.flags, TagUtil.encodeTags(msg1.tags), msg1.sflags,
                            msg2.flags, TagUtil.encodeTags(msg2.tags), msg2.sflags,
                            diff(i4list, actual), diff(actual, i4list));
                    clearCache(folder);
                    return actual;
                }
            }
            return i4list;
        } catch (ServiceException e) {
            ZimbraLog.imap.info("  ** error caught during IMAP session cache consistency check; falling back to reload", e);
            clearCache(folder);
            return null;
        }
    }

    private Set<ImapMessage> diff(List<ImapMessage> list1, List<ImapMessage> list2) {
        Set<ImapMessage> diff = Sets.newHashSet(list1);
        diff.removeAll(list2);
        return diff;
    }

    private List<ImapMessage> dupeCheck(List<ImapMessage> list) {
        List<ImapMessage> dupes = new ArrayList<ImapMessage>();
        for (int i = 0; i < list.size(); i++) {
            ImapMessage current = list.get(i);
            if (dupes.contains(current) || list.lastIndexOf(current) != i) {
                dupes.add(current);
            }
        }
        return dupes;
    }

    private static void renumberMessages(OperationContext octxt, Mailbox mbox, List<ImapMessage> i4sorted)
    throws ServiceException {
        List<ImapMessage> unnumbered = new ArrayList<ImapMessage>();
        List<Integer> renumber = new ArrayList<Integer>();
        while (!i4sorted.isEmpty() && i4sorted.get(0).imapUid <= 0) {
            ImapMessage i4msg = i4sorted.remove(0);
            unnumbered.add(i4msg);  renumber.add(i4msg.msgId);
        }
        if (!renumber.isEmpty()) {
            List<Integer> newIds = mbox.resetImapUid(octxt, renumber);
            for (int i = 0; i < newIds.size(); i++) {
                unnumbered.get(i).imapUid = newIds.get(i);
            }
            i4sorted.addAll(unnumbered);
        }
    }

    void closeFolder(ImapSession session, boolean isUnregistering) {
        // XXX: does this require synchronization?

        // detach session from handler and jettison session state from folder
        if (session.isInteractive()) {
            session.inactivate();
        }

        // no fancy stuff for search folders since they're always recalculated on load
        if (session.isVirtual()) {
            session.detach();
            return;
        }

        // checkpoint the folder data if desired
        if (SERIALIZE_ON_CLOSE) {
            try {
                // could use session.serialize() if we want to leave it in memory...
                ZimbraLog.imap.debug("Paging session during close: %s", session.getPath());
                session.unload(false);
            } catch (Exception e) {
                ZimbraLog.imap.warn("Skipping error while trying to serialize during close (%s)", session.getPath(), e);
            }
        }

        if (isUnregistering) {
            return;
        }

        // recognize if we're not configured to allow sessions to hang around after end of SELECT
        if (TERMINATE_ON_CLOSE) {
            session.detach();
            return;
        }

        // if there are still other listeners on this folder, this session is unnecessary
        Mailbox mbox = session.getMailbox();
        if (mbox != null) {
            mbox.lock.lock();
            try {
                for (Session listener : mbox.getListeners(Session.Type.IMAP)) {
                    ImapSession i4listener = (ImapSession) listener;
                    if (i4listener != session && i4listener.getFolderId() == session.getFolderId()) {
                        session.detach();
                        recordAccess(i4listener);
                        return;
                    }
                }
            } finally {
                mbox.lock.release();
            }
        }
    }

    /**
     * Try to retrieve from inactive session cache, then fall back to active session cache.
     */
    private ImapFolder getCache(Folder folder) {
        ImapFolder i4folder = inactiveSessionCache.get(cacheKey(folder, false));
        if (i4folder != null) {
            return i4folder;
        }
        return activeSessionCache.get(cacheKey(folder, true));
    }

    /**
     * Remove cached values from both active session cache and inactive session cache.
     */
    private void clearCache(Folder folder) {
        activeSessionCache.remove(cacheKey(folder, true));
        inactiveSessionCache.remove(cacheKey(folder, false));
    }

    /**
     * Generates a cache key for the {@link ImapSession}.
     *
     * @param session IMAP session
     * @param active true to use active session cache, otherwise inactive session cache.
     * @return cache key
     */
    String cacheKey(ImapSession session, boolean active) throws ServiceException {
        Mailbox mbox = session.getMailbox();
        if (mbox == null) {
            mbox = MailboxManager.getInstance().getMailboxByAccountId(session.getTargetAccountId());
        }

        String cachekey = cacheKey(mbox.getFolderById(null, session.getFolderId()), active);
        // if there are unnotified expunges, *don't* use the default cache key
        //   ('+' is a good separator because it alpha-sorts before the '.' of the filename extension)
        return session.hasExpunges() ? cachekey + "+" + session.getQualifiedSessionId() : cachekey;
    }

    private String cacheKey(Folder folder, boolean active) {
        Mailbox mbox = folder.getMailbox();
        int modseq = folder instanceof SearchFolder ? mbox.getLastChangeID() : folder.getImapMODSEQ();
        int uvv = folder instanceof SearchFolder ? mbox.getLastChangeID() : ImapFolder.getUIDValidity(folder);
        if (active) { // use '_' as separator
            return String.format("%s_%d_%d_%d", mbox.getAccountId(), folder.getId(), modseq, uvv);
        } else { // use ':' as a separator
            return String.format("%s:%d:%d:%d", mbox.getAccountId(), folder.getId(), modseq, uvv);
        }
    }

    void serialize(String key, ImapFolder folder) {
        if (!isActiveKey(key)) {
            inactiveSessionCache.put(key, folder);
        } else {
            activeSessionCache.put(key, folder);
        }
    }

    ImapFolder deserialize(String key) {
        if (!isActiveKey(key)) {
            return inactiveSessionCache.get(key);
        } else {
            ImapFolder folder = activeSessionCache.get(key);
            return folder;
        }
    }

    void updateAccessTime(String key) {
        if (!isActiveKey(key)) {
            inactiveSessionCache.updateAccessTime(key);
        } else {
            activeSessionCache.updateAccessTime(key);
        }
    }

    void safeRemoveCache(String key) {
        //remove only from inactive
        if (!isActiveKey(key)) {
            inactiveSessionCache.remove(key);
        } else {
            //removal from active is unsafe; not great to have inconsistent state but it is nicer than bombing totally
            ZimbraLog.imap.warn("Inconsistent active cache entry %s cannot be removed. Client state may not be accurate", key);
        }
    }

    public static boolean isActiveKey(String key) {
        return key.contains("_");
    }

    static interface Cache {
        /** Stores the folder into cache, or does nothing if failed to do so. */
        void put(String key, ImapFolder folder);

        /** Retrieves the folder from cache, or returns null if it's not cached or an error occurred. */
        ImapFolder get(String key);

        /** Removes the folder from the cache. */
        void remove(String key);

        /** Update the last access time without necessarily loading the underlying object **/
        void updateAccessTime(String key);
    }

}
