/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2012, 2013, 2014 Zimbra, Inc.
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

/*
 * Created on Apr 18, 2004
 */
package com.zimbra.cs.mime;

import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimePart;

import com.google.common.annotations.VisibleForTesting;
import com.zimbra.common.mime.ContentType;
import com.zimbra.common.mime.MimeConstants;

public class MPartInfo {
    MimePart mPart;
    MPartInfo mParent;
    List<MPartInfo> mChildren;
    String mPartName;
    String mContentType;
    String mDisposition;
    String mFilename;
    int mPartNum;
    int mSize;
    boolean mIsFilterableAttachment;
    boolean mIsToplevelAttachment;

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MPartInfo: {");
        sb.append("partName: ").append(mPartName).append(", ");
        sb.append("contentType: ").append(mContentType).append(", ");
        sb.append("size: ").append(mSize).append(", ");
        sb.append("disposition: ").append(mDisposition).append(", ");
        sb.append("filename: ").append(mFilename).append(", ");
        sb.append("partNum: ").append(mPartNum).append(", ");
        sb.append("isFilterableAttachment: ").append(mIsFilterableAttachment);
        sb.append("isToplevelAttachment: ").append(mIsToplevelAttachment);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Returns true if we consider this to be an attachment for the sake of "filtering" by attachments.
     * i.e., if someone searches for messages with attachment types of "text/plain", we probably wouldn't want
     * every multipart/mixed message showing up, since 99% of them will have a first body part of text/plain.
     *
     * @param part
     * @return
     */
    public boolean isFilterableAttachment() {
        return mIsFilterableAttachment;
    }

    public boolean isTopLevelAttachment() {
        return mIsToplevelAttachment;
    }

    public MimePart getMimePart() {
        return mPart;
    }

    public MPartInfo getParent() {
        return mParent;
    }

    public boolean hasChildren() {
        return mChildren != null && !mChildren.isEmpty();
    }

    public List<MPartInfo> getChildren() {
        return mChildren;
    }

    public String getPartName() {
        return mPartName;
    }

    public int getPartNum() {
        return mPartNum;
    }

    public int getSize() {
        return mSize;
    }

    public String getContentType() {
        return mContentType;
    }

    @VisibleForTesting
    String getFullContentType() {
        try {
            return mPart.getContentType();
        } catch (MessagingException e) {
            return mContentType;
        }
    }

    public boolean isMultipart() {
        return mContentType.startsWith(MimeConstants.CT_MULTIPART_PREFIX);
    }

    public boolean isMessage() {
        return mContentType.equals(MimeConstants.CT_MESSAGE_RFC822);
    }

    public String getContentTypeParameter(String name) {
        try {
            return new ContentType(mPart.getContentType()).getParameter(name);
        } catch (MessagingException e) {
            return null;
        }
    }

    public String getContentID() {
        try {
            return mPart.getContentID();
        } catch (MessagingException me) {
            return null;
        }
    }

    public String getDisposition() {
        return mDisposition;
    }

    public String getFilename() {
        return mFilename;
    }
}