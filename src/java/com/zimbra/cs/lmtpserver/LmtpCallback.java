/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2009, 2010, 2012, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.lmtpserver;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mime.ParsedMessage;

public interface LmtpCallback {

    /**
     * Called after the message is delivered to the given account.
     */
    public void afterDelivery(Account account, Mailbox mbox, String envelopeSender, String recipientEmail, Message newMessage);

    /**
     * Called when mail forwarding is set up for the account but delivery to mailbox is disabled.
     */
    public void forwardWithoutDelivery(Account account, Mailbox mbox, String envelopeSender, String recipientEmail, ParsedMessage pm);
}
