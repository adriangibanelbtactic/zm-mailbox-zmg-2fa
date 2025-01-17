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
package com.zimbra.qa.unittest;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.mailbox.DeliveryOptions;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.cs.store.file.BlobDeduper;
import com.zimbra.cs.store.file.FileBlobStore;
import com.zimbra.cs.util.Zimbra;
import com.zimbra.cs.volume.Volume;
import com.zimbra.cs.volume.VolumeManager;
import com.zimbra.znative.IO;

public final class TestBlobDeduper extends TestCase {

    private static String USER_NAME = "user1";
    private static String TEST_NAME = "TestBlobDeduper";
    private Mailbox mbox;
    private Account account;

    public TestBlobDeduper(String testName) {
        super(testName);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        account = TestUtil.getAccount(USER_NAME);
        mbox = MailboxManager.getInstance().getMailboxByAccount(account);
        // Clean up data, in case a previous test didn't exit cleanly
        cleanUp();
    }


    public void testBlobDeduper() throws Exception {
        if (!(StoreManager.getInstance() instanceof FileBlobStore)) {
            ZimbraLog.test.info("Skipping deduper test for non-FileBlobStore");
            return;
        }
        DeliveryOptions opt = new DeliveryOptions();
        opt.setFolderId(Mailbox.ID_FOLDER_INBOX);
        String[] paths = new String[5];
        Volume vol = VolumeManager.getInstance().getCurrentMessageVolume();
        for (int i = 0; i < 5; i++) {
            Message msg = mbox.addMessage(null, new ParsedMessage(("From: test@zimbra.com\r\nTo: to1@zimbra.com\r\nSubject: "+ TEST_NAME).getBytes(), false), opt, null);
            paths[i] = msg.getBlob().getLocalBlob().getFile().getPath();
        }
        // Make sure inodes are different for paths
        for (int i = 0; i < 4; i++) {
            Assert.assertFalse(IO.fileInfo(paths[i]).getInodeNum() == IO.fileInfo(paths[i+1]).getInodeNum());
        }
        // wait for a seconds, so that timestamp gets changed.
        Thread.sleep(1000);
        BlobDeduper deduper = BlobDeduper.getInstance();
        List<Short> volumeIds = new ArrayList<Short>();
        volumeIds.add(vol.getId());
        deduper.process(volumeIds);
        while (deduper.isRunning()) { // wait until deduper finishes.
            Thread.sleep(1000);
        }
        // Make sure inodes are same for paths
        for (int i = 0; i < 4; i++) {
            Assert.assertTrue(IO.fileInfo(paths[i]).getInodeNum() == IO.fileInfo(paths[i+1]).getInodeNum());
        }
    }

    @Override public void tearDown()
    throws Exception {
        try {
            cleanUp();
        } catch (Throwable t) {
            // Catch exceptions during cleanup, so that the original test error
            // isn't lost
            if (t instanceof OutOfMemoryError) {
                Zimbra.halt("TestBlobDeduper ran out of memory", t);
            }
            ZimbraLog.test.error("", t);
        }
    }

    /**
     * Moves all items back to the primary volume and deletes the temporary
     * volume created for the test.
     */
    private void cleanUp() throws Exception {
        TestUtil.deleteTestData(USER_NAME, TEST_NAME);
    }
}

