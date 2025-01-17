/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2009, 2010, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mime.Mime;

public class AttachmentDataSource implements DataSource {

    private Contact mContact;
    private String mPartName;

    public AttachmentDataSource(Contact contact, String partName) {
        if (contact == null) {
            throw new NullPointerException("contact cannot be null");
        }
        if (partName == null) {
            throw new NullPointerException("partName cannot be null");
        }
        mContact = contact;
        mPartName = partName;
    }
    
    public String getContentType() {
        String contentType = null;
        MimePart mp = null;
        try {
            mp = getMimePart();
            if (mp != null) {
                contentType = mp.getContentType();
            }
        } catch (Exception e) {
            ZimbraLog.mailbox.error("Unable to determine content type for contact %d.", mContact.getId(), e);
        }
        return contentType;
    }
    
    private MimePart getMimePart()
    throws MessagingException, ServiceException {
        MimeMessage msg = mContact.getMimeMessage(false);
        MimePart mp = null;
        try {
            mp = Mime.getMimePart(msg, mPartName);
        } catch (IOException e) {
            throw ServiceException.FAILURE("Unable to look up part " + mPartName + " for contact " + mContact.getId(), null);
        }
        
        if (mp == null) {
            ZimbraLog.mailbox.warn("Unable to find part %s for contact %d.", mPartName, mContact.getId());
        }
        return mp;
    }

    public InputStream getInputStream() throws IOException {
        try {
            return getMimePart().getInputStream();
        } catch (Exception e) {
            ZimbraLog.mailbox.error("Unable to get stream to part %s for contact %d.", mPartName, mContact.getId());
            throw new IOException(e.toString());
        }
    }

    public String getName() {
        MimePart mp = null;
        String name = null;
        try {
            mp = getMimePart();
            if (mp != null) {
                name = mp.getFileName();
            }
        } catch (Exception e) {
            ZimbraLog.mailbox.error("Unable to determine the filename for contact %d, part %s.", mContact.getId(), mPartName, e);
        }
        return name;
    }

    public OutputStream getOutputStream() {
        throw new UnsupportedOperationException();
    }
}
