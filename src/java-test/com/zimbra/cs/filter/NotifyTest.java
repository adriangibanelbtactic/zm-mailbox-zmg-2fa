/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2014 Zimbra, Inc.
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

package com.zimbra.cs.filter;

import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.Maps;
import com.zimbra.common.account.Key;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.mailbox.DeliveryContext;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.MailboxTestUtil;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.service.mail.SendMsgTest.DirectInsertionMailboxManager;
import com.zimbra.cs.service.util.ItemId;

/**
 * @author zimbra
 *
 */
public class NotifyTest {

    @BeforeClass
    public static void init() throws Exception {
        MailboxTestUtil.initServer();
        MailboxTestUtil.clearData();
        Provisioning prov = Provisioning.getInstance();

        Map<String, Object> attrs = Maps.newHashMap();
        prov.createDomain("zimbra.com", attrs);

        attrs = Maps.newHashMap();
        attrs.put(Provisioning.A_zimbraId, UUID.randomUUID().toString());
        prov.createAccount("test@zimbra.com", "secret", attrs);

        attrs = Maps.newHashMap();
        attrs.put(Provisioning.A_zimbraId, UUID.randomUUID().toString());
        prov.createAccount("test2@zimbra.com", "secret", attrs);

        // this MailboxManager does everything except actually send mail
        MailboxManager.setInstance(new DirectInsertionMailboxManager());
    }

    @Before
    public void setUp() throws Exception {
        MailboxTestUtil.clearData();
    }

    @Test
    public void filterValidToField() {
        try {

            Account acct1 = Provisioning.getInstance().get(Key.AccountBy.name,
                    "test@zimbra.com");
            Account acct2 = Provisioning.getInstance().get(Key.AccountBy.name,
                    "test2@zimbra.com");

            Mailbox mbox1 = MailboxManager.getInstance().getMailboxByAccount(
                    acct1);
            Mailbox mbox2 = MailboxManager.getInstance().getMailboxByAccount(
                    acct2);
            RuleManager.clearCachedRules(acct1);
            String filterScript = "if anyof (true) { notify \"test2@zimbra.com\" \"\" \"Hello World\""
                    + "[\"*\"];" + "    keep;" + "}";
            acct1.setMailSieveScript(filterScript);
            List<ItemId> ids = RuleManager.applyRulesToIncomingMessage(
                    new OperationContext(mbox1), mbox1, new ParsedMessage(
                            "To: test@zimbra.com".getBytes(), false), 0, acct1
                            .getName(), new DeliveryContext(),
                    Mailbox.ID_FOLDER_INBOX, true);
            Assert.assertEquals(1, ids.size());
            Integer item = mbox2.getItemIds(null, Mailbox.ID_FOLDER_INBOX)
                    .getIds(MailItem.Type.MESSAGE).get(0);
            Message notifyMsg = mbox2.getMessageById(null, item);
            Assert.assertEquals("Hello World", notifyMsg.getFragment());
            Assert.assertEquals("text/plain; charset=us-ascii", notifyMsg
                    .getMimeMessage().getContentType());
        } catch (Exception e) {
            fail("No exception should be thrown");
        }

    }

}
