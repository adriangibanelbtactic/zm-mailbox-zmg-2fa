/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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
import java.util.Map;

import com.google.common.collect.Maps;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ArrayUtil;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.mailbox.Tag;

public class ImapFlagCache implements Iterable<ImapFlagCache.ImapFlag>, java.io.Serializable {
    private static final long serialVersionUID = -8938341239505513246L;

    public static final class ImapFlag implements java.io.Serializable {
        private static final long serialVersionUID = 5445749167572465447L;

        final String  mName;
        final String  mImapName;
        final int     mId;
        final long    mBitmask;
        final boolean mPositive;
        final boolean mPermanent;
        final boolean mListed;
        final int     mModseq;

        static final boolean VISIBLE = true, HIDDEN = false;

        ImapFlag(Tag ltag) {
            this(ltag.getName(), ltag, true);
        }

        ImapFlag(String name, Tag ltag, boolean positive) {
            mId   = ltag.getId();    mBitmask   = ltag instanceof Flag ? ((Flag) ltag).toBitmask() : 0;
            mName = ltag.getName();  mImapName  = normalize(name, mId);
            mPositive = positive;    mPermanent = true;
            mListed = VISIBLE;       mModseq    = ltag.getSavedSequence();
        }

        ImapFlag(String name, short bitmask, boolean listed) {
            mId   = 0;         mBitmask   = bitmask;
            mName = name;      mImapName  = name;
            mPositive = true;  mPermanent = false;
            mListed = listed;  mModseq    = -1;
        }

        private String normalize(String name, int id) {
            StringBuilder sb = new StringBuilder(name.length());
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                // strip all non-{@link ImapRequest#ATOM_CHARS} except for a leading '\'
                if (c > 0x20 && c < 0x7f && c != '(' && c != ')' && c != '{' && c != '%' && c != '*' && c != '"' && c != ']' && (i == 0 || c != '\\')) {
                    sb.append(c);
                }
            }
            // if we stripped chars, make sure to disambiguate the resulting keyword names
            if (sb.length() != name.length()) {
                sb.append(":FLAG").append(id - 64);
            }
            return sb.toString();
        }

        boolean matches(ImapMessage i4msg) {
            if (mId > 0) {
                String[] tags = i4msg.tags;
                if (!ArrayUtil.isEmpty(tags)) {
                    for (String tag : tags) {
                        if (mName.equals(tag)) {
                            return true;
                        }
                    }
                }
                return false;
            } else {
                long mask = mId == 0 ? i4msg.sflags : i4msg.flags;
                return (mask & mBitmask) != 0;
            }
        }

        @Override
        public String toString() {
            return mImapName;
        }
    }


    private final Map<String, ImapFlag> mImapNames;
    private final Map<String, ImapFlag> mNames;

    public ImapFlagCache() {
        mImapNames = Maps.newLinkedHashMap();
        mNames = Maps.newHashMap();
    }

    ImapFlagCache(Mailbox mbox, OperationContext octxt) throws ServiceException {
        this();
        try {
            for (Tag ltag : mbox.getTagList(octxt)) {
                if (!(ltag instanceof Flag)) {
                    cache(new ImapFlag(ltag));
                }
            }
        } catch (ServiceException e) {
            if (!e.getCode().equals(ServiceException.PERM_DENIED)) {
                throw e;
            }
        }
    }

    static ImapFlagCache getSystemFlags(Mailbox mbox) throws ServiceException {
        ImapFlagCache i4cache = new ImapFlagCache();

        i4cache.cache(new ImapFlag("\\Answered", mbox.getFlagById(Flag.ID_REPLIED),   true));
        i4cache.cache(new ImapFlag("\\Deleted",  mbox.getFlagById(Flag.ID_DELETED),   true));
        i4cache.cache(new ImapFlag("\\Draft",    mbox.getFlagById(Flag.ID_DRAFT),     true));
        i4cache.cache(new ImapFlag("\\Flagged",  mbox.getFlagById(Flag.ID_FLAGGED),   true));
        i4cache.cache(new ImapFlag("\\Seen",     mbox.getFlagById(Flag.ID_UNREAD),    false));
        i4cache.cache(new ImapFlag("$Forwarded", mbox.getFlagById(Flag.ID_FORWARDED), true));
        i4cache.cache(new ImapFlag("$MDNSent",   mbox.getFlagById(Flag.ID_NOTIFIED),  true));
        i4cache.cache(new ImapFlag("Forwarded",  mbox.getFlagById(Flag.ID_FORWARDED), true));

        i4cache.cache(new ImapFlag("\\Recent",     ImapMessage.FLAG_RECENT,       ImapFlag.HIDDEN));
        i4cache.cache(new ImapFlag("$Junk",        ImapMessage.FLAG_SPAM,         ImapFlag.VISIBLE));
        i4cache.cache(new ImapFlag("$NotJunk",     ImapMessage.FLAG_NONSPAM,      ImapFlag.VISIBLE));
        i4cache.cache(new ImapFlag("Junk",         ImapMessage.FLAG_SPAM,         ImapFlag.VISIBLE));
        i4cache.cache(new ImapFlag("JunkRecorded", ImapMessage.FLAG_JUNKRECORDED, ImapFlag.VISIBLE));
        i4cache.cache(new ImapFlag("NonJunk",      ImapMessage.FLAG_NONSPAM,      ImapFlag.VISIBLE));
        i4cache.cache(new ImapFlag("NotJunk",      ImapMessage.FLAG_NONSPAM,      ImapFlag.VISIBLE));

        return i4cache;
    }


    ImapFlag getByImapName(String name) {
        return mImapNames.get(name.toUpperCase());
    }

    ImapFlag getByZimbraName(String name) {
        return mNames.get(name.toUpperCase());
    }

    List<String> listNames(boolean permanentOnly) {
        if (mImapNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, ImapFlag> entry : mImapNames.entrySet()) {
            ImapFlag i4flag = entry.getValue();
            if (i4flag.mListed && (!permanentOnly || i4flag.mPermanent)) {
                names.add(i4flag.mImapName);
            }
        }
        return names;
    }

    public ImapFlag cache(ImapFlag i4flag) {
        mImapNames.put(i4flag.mImapName.toUpperCase(), i4flag);
        mNames.put(i4flag.mName.toUpperCase(), i4flag);
        return i4flag;
    }

    ImapFlag uncache(int tagId) {
        for (ImapFlag i4flag : this) {
            if (i4flag.mId == tagId) {
                mImapNames.remove(i4flag.mImapName.toUpperCase());
                mNames.remove(i4flag.mName.toUpperCase());
                return i4flag;
            }
        }
        return null;
    }

    void clear() {
        mImapNames.clear();
        mNames.clear();
    }

    @Override
    public Iterator<ImapFlag> iterator() {
        return mImapNames.values().iterator();
    }
}
