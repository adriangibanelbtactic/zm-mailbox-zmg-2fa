/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2004, 2005, 2006, 2007, 2009, 2010, 2013, 2014 Zimbra, Inc.
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
 * Created on Sep 7, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.zimbra.cs.lmtpserver;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.zimbra.common.util.ZimbraLog;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;


public class LmtpEnvelope {
	
	private List<LmtpAddress> mRecipients; 
	private List<LmtpAddress> mLocalRecipients;
	private List<LmtpAddress> mRemoteRecipients;
    private Multimap<String, LmtpAddress> mRemoteServerToRecipientsMap;
    private LmtpAddress mSender;
    private int mSize;
    private LmtpBodyType mBodyType;
    
    public LmtpEnvelope() {
    	mRecipients = new LinkedList<LmtpAddress>();
    	mLocalRecipients = new LinkedList<LmtpAddress>();
    	mRemoteRecipients = new LinkedList<LmtpAddress>();
    	mRemoteServerToRecipientsMap = ArrayListMultimap.create();
    }
    
    public boolean hasSender() {
    	return mSender != null;
    }
    
    public boolean hasRecipients() {
    	return mRecipients.size() > 0;
    }
    
    public void setSender(LmtpAddress sender) {
    	mSender = sender;
    }
    
    public void addLocalRecipient(LmtpAddress recipient) {
    	mRecipients.add(recipient);
    	mLocalRecipients.add(recipient);
    }

    public void addRemoteRecipient(LmtpAddress recipient) {
        if (recipient.getRemoteServer() == null) {
            ZimbraLog.lmtp.error("Server for remote recipient %s has not been set", recipient);
            return;
        }
    	mRecipients.add(recipient);
    	mRemoteRecipients.add(recipient);
        mRemoteServerToRecipientsMap.put(recipient.getRemoteServer(), recipient);
    }

    public List<LmtpAddress> getRecipients() {
    	return mRecipients;
    }
    
    public List<LmtpAddress> getLocalRecipients() {
    	return mLocalRecipients;
    }

    public List<LmtpAddress> getRemoteRecipients() {
    	return mRemoteRecipients;
    }

    public Multimap<String, LmtpAddress> getRemoteServerToRecipientsMap() {
    	return mRemoteServerToRecipientsMap;
    }

    public LmtpAddress getSender() {
    	return mSender;
    }
	
    public LmtpBodyType getBodyType() {
		return mBodyType;
	}
	
    public void setBodyType(LmtpBodyType bodyType) {
		mBodyType = bodyType;
	}
	
    public int getSize() {
		return mSize;
	}
	
    public void setSize(int size) {
		mSize = size;
	}
}