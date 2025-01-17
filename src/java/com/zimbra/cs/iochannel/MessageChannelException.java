/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2012, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.iochannel;

import com.zimbra.common.iochannel.IOChannelException;

public class MessageChannelException extends IOChannelException {

    private static final long serialVersionUID = -3595831838657110474L;

    public MessageChannelException(String msg) {
        super(Code.Error, msg);
    }

    public static MessageChannelException NoSuchMessage(String message) {
        return new MessageChannelException("message doesn't exist " + message);
    }

    public static MessageChannelException CannotCreate(String error) {
        return new MessageChannelException(error);
    }
}
