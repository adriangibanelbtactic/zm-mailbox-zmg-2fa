/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2005, 2006, 2007, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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

/**
 * 
 */
package com.zimbra.cs.mailbox.calendar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.activation.DataSource;
import javax.mail.internet.ContentType;

import com.zimbra.common.calendar.ZCalendar;
import com.zimbra.common.calendar.ZCalendar.ICalTok;
import com.zimbra.common.mime.MimeConstants;


/**
 * @author tim
 * 
 * Very simple class which wraps an iCal4j Calendar object in a javamail DataSource 
 *
 */
public class CalendarDataSource implements DataSource
{
    private ZCalendar.ZVCalendar mICal;
    private String mMethod;
    private String mAttachName; // NULL if we want a text/calendar part, or set if we want an attached file
    private byte[] mBuf = null;

    public CalendarDataSource(ZCalendar.ZVCalendar iCal, String attachmentName) {
        mICal = iCal;
        mAttachName = attachmentName;
        if (mAttachName == null || mAttachName.equals("")) {
            mAttachName = "meeting.ics";
        }
        
//        Method method = (Method)(iCal.getProperties().getProperty(Property.METHOD));
//        mMethod = method.getValue();
        mMethod = iCal.getPropVal(ICalTok.METHOD, ICalTok.PUBLISH.toString());
    }

    public String getContentType() {
        ContentType ct = new ContentType();
        ct.setParameter(MimeConstants.P_CHARSET, MimeConstants.P_CHARSET_UTF8);
        
        ct.setPrimaryType("text");
        ct.setSubType("calendar");
        ct.setParameter("method", mMethod);
        if (!mAttachName.toLowerCase().endsWith(".ics")) {
            mAttachName = mAttachName+".ics";
        }
        ct.setParameter("name", mAttachName);
        
        return ct.toString();
    }

    /**
     * Returns the InputStream for this blob. Note that this method 
     * needs a database connection and will obtain/release one
     * automatically if needed, or use the one passed to it from
     * the constructor.
     * @throws IOException
     */
    public InputStream getInputStream() throws IOException {
        synchronized(this) {
            if (mBuf == null) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                OutputStreamWriter wout =
                    new OutputStreamWriter(buf, MimeConstants.P_CHARSET_UTF8);
                mICal.toICalendar(wout);
                wout.flush();
                mBuf = buf.toByteArray();
            }
        }
        ByteArrayInputStream in = new ByteArrayInputStream(mBuf);
        return in;
    }

    /* (non-Javadoc)
     * @see javax.activation.DataSource#getName()
     */
    public String getName() {
        //Bug: 58971
        //JavaMailMimeMessage.setDataSource() adds the Content-Disposition header w/disposition type attachment if a value is returned.
        //This causes external mail systems (exchange or, gmail) renders the text/calendar part as an attachment.
        //Hence, just return a null value.
        return null;
    }

    /* (non-Javadoc)
     * @see javax.activation.DataSource#getOutputStream()
     */
    public OutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }
    
}