/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.redolog.op;

import java.io.IOException;

import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.MailboxOperation;
import com.zimbra.cs.redolog.RedoLogInput;
import com.zimbra.cs.redolog.RedoLogOutput;

public class EnableSharedReminder extends RedoableOp {

    private int mountpointId;
    private boolean enabled;

    public EnableSharedReminder() {
        super(MailboxOperation.EnableSharedReminder);
        mountpointId = UNKNOWN_ID;
        enabled = false;
    }

    public EnableSharedReminder(int mailboxId, int mountpointId, boolean enabled) {
        this();
        setMailboxId(mailboxId);
        this.mountpointId = mountpointId;
        this.enabled = enabled;
    }

    @Override protected String getPrintableData() {
        StringBuilder sb = new StringBuilder("mountpoint=").append(mountpointId);
        sb.append(", reminderEnabled=").append(enabled);
        return sb.toString();
    }

    @Override protected void serializeData(RedoLogOutput out) throws IOException {
        out.writeInt(mountpointId);
        out.writeBoolean(enabled);
    }

    @Override protected void deserializeData(RedoLogInput in) throws IOException {
        mountpointId = in.readInt();
        enabled = in.readBoolean();
    }

    
    @Override public void redo() throws Exception {
        Mailbox mbox = MailboxManager.getInstance().getMailboxById(getMailboxId());
        mbox.enableSharedReminder(getOperationContext(), mountpointId, enabled);
    }
}
