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

package com.zimbra.cs.mailclient.smtp;

import com.zimbra.cs.mailclient.CommandFailedException;

@SuppressWarnings("serial")
final class InvalidRecipientException extends CommandFailedException {

    private String recipient;

    InvalidRecipientException(String recipient, String serverError) {
        super(SmtpConnection.RCPT, "Invalid recipient " + recipient + ": " + serverError);
        this.recipient = recipient;
    }

    String getRecipient() {
        return recipient;
    }

}
