/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2009, 2010, 2011, 2012, 2013, 2014 Zimbra, Inc.
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MailDateFormat;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.zimbra.client.ZFolder;
import com.zimbra.client.ZMailbox;
import com.zimbra.common.mime.shim.JavaMailInternetAddress;
import com.zimbra.common.mime.shim.JavaMailInternetHeaders;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.DeliveryOptions;
import com.zimbra.cs.mailbox.Flag;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.Tag;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.store.Blob;
import com.zimbra.cs.store.BlobBuilder;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.cs.util.AccountUtil;

/**
 * Encapsulates append message data for an APPEND request.
 */
final class AppendMessage {
    final ImapHandler handler;
    final String tag;

    private Date date;
    private boolean catenate;
    private List<Part> parts;
    private Blob content;
    private List<String> flagNames;
    private List<String> persistentFlagNames; //flagNames is nulled out in checkFlags; this holds the original constructed list of names
    private int flags = Flag.BITMASK_UNREAD;
    private final Set<String> tags = Sets.newHashSetWithExpectedSize(3);
    private short sflags;

    Blob getContent() throws IOException, ImapException, ServiceException {
        if (content == null) {
            content = catenate ? doCatenate() : parts.get(0).literal.getBlob();
        }
        return content;
    }

    Date getDate() {
        return date;
    }

    List<String> getPersistentFlagNames() {
        return persistentFlagNames;
    }

    List<Part> getParts() {
        return parts;
    }

    static AppendMessage parse(ImapHandler handler, String tag, ImapRequest req)
            throws ImapParseException, IOException {
        AppendMessage append = new AppendMessage(handler, tag);
        append.parse(req);
        return append;
    }

    private AppendMessage(ImapHandler handler, String tag) {
        this.handler = handler;
        this.tag = tag;
    }

    private void parse(ImapRequest req) throws ImapParseException, IOException {
        if (req.peekChar() == '(') {
            persistentFlagNames = flagNames = req.readFlags();
            req.skipSpace();
        }
        if (req.peekChar() == '"') {
            date = req.readDate(true, true);
            req.skipSpace();
        }
        if ((req.peekChar() == 'c' || req.peekChar() == 'C') && handler.extensionEnabled("CATENATE")) {
            req.skipAtom("CATENATE");
            req.skipSpace();
            req.skipChar('(');
            catenate = true;
            parts = new ArrayList<Part>(5);
            while (req.peekChar() != ')') {
                if (!parts.isEmpty()) {
                    req.skipSpace();
                }
                String type = req.readATOM();
                req.skipSpace();
                if (type.equals("TEXT")) {
                    parts.add(new Part(req.readLiteral()));
                } else if (type.equals("URL")) {
                    parts.add(new Part(new ImapURL(tag, handler, req.readAstring())));
                } else {
                    throw new ImapParseException(tag, "unknown CATENATE cat-part: " + type);
                }
            }
            req.skipChar(')');
        } else {
            parts = Arrays.asList(new Part(req.readLiteral8()));
        }
    }

    @VisibleForTesting
    public AppendMessage(List<String> flagNames, Date date, List<Part> parts) {
        this.flagNames = persistentFlagNames = flagNames;
        this.date = date;
        this.parts = parts;
        this.tag = null;
        this.handler = null;
    }

    void checkFlags(Mailbox mbox, ImapFlagCache flagSet, ImapFlagCache tagSet, List<Tag> newTags)
            throws ServiceException {
        if (flagNames == null) {
            return;
        }
        for (String name : flagNames) {
            ImapFlagCache.ImapFlag i4flag = flagSet.getByImapName(name);
            if (i4flag != null && !i4flag.mListed) {
                i4flag = null;
            } else if (i4flag == null && !name.startsWith("\\")) {
                i4flag = tagSet.getByImapName(name);
            }

            if (i4flag == null) {
                //add to tags, this is passed to DeliveryOptions later
                tags.add(name);
            }

            if (i4flag != null) {
                if (!i4flag.mPermanent) {
                    sflags |= i4flag.mBitmask;
                } else if (i4flag.mId > 0) {
                    tags.add(i4flag.mName);
                } else if (i4flag.mPositive) {
                    flags |= i4flag.mBitmask;
                } else {
                    flags &= ~i4flag.mBitmask;
                }
            }
        }
        flagNames = null;
    }

    int storeContent(Object mboxObj, Object folderObj)
            throws ImapSessionClosedException, IOException, ServiceException {
        try {
            checkDate(content);
            if (mboxObj instanceof Mailbox) {
                return store((Mailbox) mboxObj, (Folder) folderObj);
            } else {
                return store((ZMailbox) mboxObj, (ZFolder) folderObj);
            }
        } finally {
            cleanup();
        }
    }

    private int store(Mailbox mbox, Folder folder) throws ImapSessionClosedException, ServiceException, IOException {
        boolean idxAttach = mbox.attachmentsIndexingEnabled();
        Long receivedDate = date != null ? date.getTime() : null;
        ParsedMessage pm = new ParsedMessage(content, receivedDate, idxAttach);
        try {
            if (!pm.getSender().isEmpty()) {
                InternetAddress ia = new JavaMailInternetAddress(pm.getSender());
                if (AccountUtil.addressMatchesAccountOrSendAs(mbox.getAccount(), ia.getAddress())) {
                    flags |= Flag.BITMASK_FROM_ME;
                }
            }
        } catch (Exception e) { }

        DeliveryOptions dopt = new DeliveryOptions().setFolderId(folder).setNoICal(true).setFlags(flags).setTags(tags);
        Message msg = mbox.addMessage(handler.getContext(), pm, dopt, null);
        if (msg != null && sflags != 0 && handler.getState() == ImapHandler.State.SELECTED) {
            ImapFolder selectedFolder = handler.getSelectedFolder();
            // remember, selected folder may be on another host (i.e. mProxy != null)
            //   (note that this leaves session flags unset on remote appended messages)
            if (selectedFolder != null) {
                ImapMessage i4msg = selectedFolder.getById(msg.getId());
                if (i4msg != null) {
                    i4msg.setSessionFlags(sflags, selectedFolder);
                }
            }
        }
        return msg == null ? -1 : msg.getId();
    }

    private int store(ZMailbox mbox, ZFolder folder) throws IOException, ServiceException {
        InputStream is = content.getInputStream();
        String id = mbox.addMessage(folder.getId(), Flag.toString(flags), null, date.getTime(), is, content.getRawSize(), true);
        return new ItemId(id, getCredentials().getAccountId()).getId();
    }

    void checkContent() throws IOException, ImapException, ServiceException {
        getContent();
        long size = content.getRawSize();
        long maxMsgSize = handler.getConfig().getMaxMessageSize();
        if ((maxMsgSize != 0 /* 0 means unlimited */) && (size > handler.getConfig().getMaxMessageSize())) {
            cleanup();
            if (catenate) {
                throw new ImapParseException(tag, "TOOBIG", "maximum message size exceeded", false);
            } else {
                throw new ImapParseException(tag, "maximum message size exceeded", true);
            }
        } else if (size <= 0) {
            throw new ImapParseException(tag, "zero-length message", false);
        }
    }

    private Blob doCatenate() throws IOException, ImapException, ServiceException {
        // translate CATENATE (...) directives into Blob
        BlobBuilder bb = StoreManager.getInstance().getBlobBuilder();
        try {
            for (Part part : parts) {
                copyBytes(part.getInputStream(), bb);
                part.cleanup();
            }
            return bb.finish();
        } finally {
            for (Part part : parts) {
                part.cleanup();
            }
            parts = null;
        }
    }

    void cleanup() {
        if (content != null) {
            StoreManager.getInstance().quietDelete(content);
            content = null;
        }
    }

    private void copyBytes(InputStream is, BlobBuilder bb) throws IOException {
        try {
            bb.append(is);
        } finally {
            ByteUtil.closeStream(is);
        }
    }

    private void checkDate(Blob blob) throws IOException, ServiceException {
        // if we're using Thunderbird, try to set INTERNALDATE to the message's Date: header
        if (date == null && getCredentials().isHackEnabled(ImapCredentials.EnabledHack.THUNDERBIRD)) {
            date = getSentDate(blob);
        }

        // server uses UNIX time, so range-check specified date (is there a better place for this?)
        // FIXME: Why is this different from INTERNALDATE range check?
        if (date != null && date.getTime() > Integer.MAX_VALUE * 1000L) {
            ZimbraLog.imap.info("APPEND failed: date out of range");
            throw ServiceException.FAILURE("APPEND failed (date out of range)", null);
        }
    }

    private static Date getSentDate(Blob content) throws IOException {
        InputStream is = new BufferedInputStream(content.getInputStream());
        try {
            // inefficient, but must be done before creating the ParsedMessage
            return getSentDate(new JavaMailInternetHeaders(is));
        } catch (MessagingException e) {
            return null;
        } finally {
            ByteUtil.closeStream(is);
        }
    }

    static Date getSentDate(InternetHeaders ih) {
        String s = ih.getHeader("Date", null);
        if (s != null) {
            try {
                return new MailDateFormat().parse(s);
            } catch (ParseException pex) {
                return null;
            }
        }
        return null;
    }

    private ImapCredentials getCredentials() {
        return handler.getCredentials();
    }

    /** APPEND message part, either literal data or IMAP URL. */
    final class Part {
        Literal literal;
        ImapURL url;

        Part(Literal literal) {
            this.literal = literal;
        }

        Part(ImapURL url) {
            this.url = url;
        }

        InputStream getInputStream() throws IOException, ImapException {
            if (literal != null) {
                return literal.getInputStream();
            } else {
                return url.getContentAsStream(handler, handler.getCredentials(), tag).getSecond();
            }
        }

        void cleanup() {
            if (literal != null) {
                literal.cleanup();
            }
        }

        Literal getLiteral() {
            return literal;
        }

        ImapURL getUrl() {
            return url;
        }
    }
}
