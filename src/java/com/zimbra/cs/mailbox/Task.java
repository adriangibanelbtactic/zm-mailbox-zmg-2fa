/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2006, 2007, 2009, 2010, 2013, 2014 Zimbra, Inc.
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

import javax.mail.internet.MimeMessage;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.calendar.Invite;
import com.zimbra.cs.redolog.op.CreateCalendarItemPlayer;
import com.zimbra.cs.redolog.op.CreateCalendarItemRecorder;

public class Task extends CalendarItem {
    public Task(Mailbox mbox, UnderlyingData data) throws ServiceException {
        this(mbox, data, false);
    }
    
    public Task(Mailbox mbox, UnderlyingData data, boolean skipCache) throws ServiceException {
        super(mbox, data, skipCache);
        if (mData.type != Type.TASK.toByte()) {
            throw new IllegalArgumentException();
        }
    }

    @Override
    protected String processPartStat(Invite invite, MimeMessage mmInv, boolean forCreate, String defaultPartStat)
            throws ServiceException {
        Mailbox mbox = getMailbox();
        OperationContext octxt = mbox.getOperationContext();
        CreateCalendarItemPlayer player =
            octxt != null ? (CreateCalendarItemPlayer) octxt.getPlayer() : null;

        String partStat = defaultPartStat;
        if (player != null) {
            String p = player.getCalendarItemPartStat();
            if (p != null) partStat = p;
        }

        CreateCalendarItemRecorder recorder =
            (CreateCalendarItemRecorder) mbox.getRedoRecorder();
        recorder.setCalendarItemPartStat(partStat);

        Account account = getMailbox().getAccount();
        invite.updateMyPartStat(account, partStat);
        if (forCreate) {
            Invite defaultInvite = getDefaultInviteOrNull();
            if (defaultInvite != null && !defaultInvite.equals(invite) &&
                !partStat.equals(defaultInvite.getPartStat())) {
                defaultInvite.updateMyPartStat(account, partStat);
                saveMetadata();
            }
        }
        return partStat;
    }
}
