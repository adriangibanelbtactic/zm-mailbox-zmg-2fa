/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2009, 2010, 2011, 2012, 2013, 2014 Zimbra, Inc.
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;

import com.zimbra.client.ZDocument;
import com.zimbra.client.ZItem;
import com.zimbra.client.ZMailbox;
import com.zimbra.client.ZSearchParams;
import com.zimbra.common.account.Key;
import com.zimbra.common.httpclient.HttpClientUtil;
import com.zimbra.common.util.ZimbraHttpConnectionManager;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.mailbox.ACL;
import com.zimbra.cs.mailbox.Document;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;

public class TestDocument extends TestCase {

    private static final String NAME_PREFIX = TestDocument.class.getSimpleName();
    private static final String USER_NAME = "user1";

    @Override public void setUp()
    throws Exception {
        cleanUp();
    }

    /**
     * Tests documents created with the {@code Note} flag set.
     */
    public void testNote()
    throws Exception {
        // Create a document and a note.
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        String folderId = Integer.toString(Mailbox.ID_FOLDER_BRIEFCASE);
        ZDocument doc = TestUtil.createDocument(mbox, folderId, NAME_PREFIX + "-doc.txt", "text/plain", "doc".getBytes());
        ZDocument note = TestUtil.createDocument(mbox, folderId, NAME_PREFIX + "-note.txt", "text/plain", "note".getBytes(), true);
        String flags = Character.toString(ZItem.Flag.note.getFlagChar());
        mbox.updateItem(note.getId(), null, null, flags, null);

        // Confirm that the Note flag is set when getting the documents.
        doc = mbox.getDocument(doc.getId());
        assertEquals(null, doc.getFlags());
        assertEquals(flags, note.getFlags());

        // Test searching for notes.
        List<String> ids = TestUtil.search(mbox, "in:briefcase tag:\\note", ZSearchParams.TYPE_DOCUMENT);
        assertEquals(1, ids.size());
        assertEquals(note.getId(), ids.get(0));

    }

    /**
     * Tests moving of documents created with the {@code Note} flag set.
     */
    public void testMoveNote()
    throws Exception {
        String USER2_NAME = "user2";
        String filename = NAME_PREFIX + "-testMoveNote.txt";
        Account acct = Provisioning.getInstance().get(Key.AccountBy.name, TestUtil.getAddress(USER_NAME));
        Account acct2 = Provisioning.getInstance().get(Key.AccountBy.name, TestUtil.getAddress(USER2_NAME));

        // Create a note.
        ZMailbox zmbx = TestUtil.getZMailbox(USER_NAME);
        String folderId = Integer.toString(Mailbox.ID_FOLDER_BRIEFCASE);
        ZDocument note = TestUtil.createDocument(zmbx, folderId, filename, "text/plain", "note".getBytes(), true);
        String flags = Character.toString(ZItem.Flag.note.getFlagChar());
        // Confirm that note flag is set.
        assertEquals(flags, note.getFlags());

        Mailbox remoteMbox = MailboxManager.getInstance().getMailboxByAccount(acct2);
        Mailbox mbox1 = MailboxManager.getInstance().getMailboxByAccount(acct);

        // Clean up test data for user2.
        TestUtil.deleteTestData(USER2_NAME, NAME_PREFIX);

        // reset the access if there are any present.
        remoteMbox.revokeAccess(null, Mailbox.ID_FOLDER_BRIEFCASE, acct.getId());
        mbox1.revokeAccess(null, Mailbox.ID_FOLDER_BRIEFCASE, acct2.getId());

        // Give write permissions on user2's Briefcase for user1 and user1's Briefcase for user2.
        remoteMbox.grantAccess(null, Mailbox.ID_FOLDER_BRIEFCASE, acct.getId(), ACL.GRANTEE_USER,(short) (ACL.RIGHT_READ | ACL.RIGHT_WRITE | ACL.RIGHT_INSERT), null);
        mbox1.grantAccess(null, Mailbox.ID_FOLDER_BRIEFCASE, acct2.getId(), ACL.GRANTEE_USER,(short) (ACL.RIGHT_READ | ACL.RIGHT_WRITE | ACL.RIGHT_INSERT), null);

        // move the note to user2
        zmbx.moveItem(note.getId(), acct2.getId() + ":" + Mailbox.ID_FOLDER_BRIEFCASE, null);
        ZMailbox remoteZmbx = TestUtil.getZMailbox(USER2_NAME);
        String idStr = TestUtil.search(remoteZmbx, "in:briefcase " + filename , ZSearchParams.TYPE_DOCUMENT).get(0);
        Document doc = remoteMbox.getDocumentById(null, Integer.parseInt(idStr));

        // make sure moved document has note flag.
        assertEquals(note.getFlags(), doc.getFlagString());

        // move the note back to user1
        remoteZmbx.moveItem(String.valueOf(doc.getId()), acct.getId() + ":" + Mailbox.ID_FOLDER_BRIEFCASE, null);

        // reset the access
        remoteMbox.revokeAccess(null, Mailbox.ID_FOLDER_BRIEFCASE, acct.getId());
        mbox1.revokeAccess(null, Mailbox.ID_FOLDER_BRIEFCASE, acct2.getId());
        idStr = TestUtil.search(zmbx, "in:briefcase " + filename , ZSearchParams.TYPE_DOCUMENT).get(0);

        doc = mbox1.getDocumentById(null, Integer.parseInt(idStr));

        // make sure moved document has note flag.
        assertEquals(note.getFlags(), doc.getFlagString());
    }

    /**
     * Tests deletion of blob when document revision is deleted.
     */
    public void testPurgeRevision()
    throws Exception {
        // Create document
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Folder.FolderOptions fopt = new Folder.FolderOptions().setDefaultView(MailItem.Type.DOCUMENT);
        Folder folder = mbox.createFolder(null, "/" + NAME_PREFIX + " testPurgeRevisions", fopt);
        Document doc = mbox.createDocument(null, folder.getId(), "test1.txt", "text/plain", NAME_PREFIX, "testPurgeRevisions", new ByteArrayInputStream("One".getBytes()));
        int id = doc.getId();
        File file1 = doc.getBlob().getLocalBlob().getFile();
        assertTrue(file1.exists());
        // Create revisions
        doc = mbox.addDocumentRevision(null, id, NAME_PREFIX, "test1.txt", "testPurgeRevisions", new ByteArrayInputStream("Two".getBytes()));
        int version = doc.getVersion();
        File file2 = doc.getBlob().getLocalBlob().getFile();
        assertTrue(file2.exists());
        mbox.addDocumentRevision(null, id, NAME_PREFIX, "test1.txt", "testPurgeRevisions", new ByteArrayInputStream("Three".getBytes()));
        // remove the first revision
        mbox.purgeRevision(null, id, version, false);
        assertTrue(file1.exists());
        assertFalse(file2.exists());
    }

    /**
     * Tests deletion of blobs when document revisions are deleted.
     */
    public void testPurgeRevisions()
    throws Exception {
        // Create document
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        Folder.FolderOptions fopt = new Folder.FolderOptions().setDefaultView(MailItem.Type.DOCUMENT);
        Folder folder = mbox.createFolder(null, "/" + NAME_PREFIX + " testPurgeRevisions", fopt);
        Document doc = mbox.createDocument(null, folder.getId(), "test2.txt", "text/plain", NAME_PREFIX, "testPurgeRevisions", new ByteArrayInputStream("One".getBytes()));
        int id = doc.getId();
        File file1 = doc.getBlob().getLocalBlob().getFile();
        assertTrue(file1.exists());
        // Create revisions
        doc = mbox.addDocumentRevision(null, id, NAME_PREFIX, "test2.txt", "testPurgeRevisions", new ByteArrayInputStream("Two".getBytes()));
        int version = doc.getVersion();
        File file2 = doc.getBlob().getLocalBlob().getFile();
        assertTrue(file2.exists());
        mbox.addDocumentRevision(null, id, NAME_PREFIX, "test2.txt", "testPurgeRevisions", new ByteArrayInputStream("Three".getBytes()));
        // remove the first two revisions
        mbox.purgeRevision(null, id, version, true);
        assertFalse(file1.exists());
        assertFalse(file2.exists());
    }

    /**
     * Tests the content-type based on file extension.
     */
    public void testContentType()
    throws Exception {
        // Create two documents.
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        String folderId = Integer.toString(Mailbox.ID_FOLDER_BRIEFCASE);
        ZDocument doc1 = TestUtil.createDocument(mbox, folderId, NAME_PREFIX + "-docOne.doc", "application/octet-stream", "doc1".getBytes());
        ZDocument doc2 = TestUtil.createDocument(mbox, folderId, NAME_PREFIX + "-docTwo.xls", "application/ms-tnef", "doc2".getBytes());

        // Confirm that the content-type changed based on file extension
        assertEquals("application/msword", doc1.getContentType());
        assertEquals("application/vnd.ms-excel", doc2.getContentType());
    }

    /**
     * Test REST access to publicly shared folder
     * @throws Exception
     */
    public void testPublicShare()
    throws Exception {
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        String folderName = NAME_PREFIX + "testPublicSharing";
        Folder.FolderOptions fopt = new Folder.FolderOptions().setDefaultView(MailItem.Type.DOCUMENT);
        Folder folder = mbox.createFolder(null, "/" + folderName, fopt);
        Document doc = mbox.createDocument(null, folder.getId(), "test2.txt", "text/plain", NAME_PREFIX, "testPublicSharing", new ByteArrayInputStream("A Test String".getBytes()));
        mbox.grantAccess(null, folder.getId(), null, ACL.GRANTEE_PUBLIC, ACL.stringToRights("rw"), null);

        String URL = TestUtil.getBaseUrl()+"/home/" + mbox.getAccount().getName()+"/"+folderName;
        HttpClient eve = ZimbraHttpConnectionManager.getInternalHttpConnMgr().newHttpClient();
        GetMethod get = new GetMethod(URL);
        int statusCode = HttpClientUtil.executeMethod(eve, get);
        assertEquals("This request should succeed. Getting status code " + statusCode, HttpStatus.SC_OK,statusCode);
        String respStr = get.getResponseBodyAsString();
        assertFalse("Should not contain AUTH_EXPIRED", respStr.contains("AUTH_EXPIRED"));
        assertTrue("Should contain shared content ", respStr.contains("test2.txt"));
    }
    @Override public void tearDown()
    throws Exception {
        cleanUp();
    }

    private void cleanUp()
    throws Exception {
        TestUtil.deleteTestData(USER_NAME, NAME_PREFIX);
    }

    public static void main(String[] args)
    throws Exception {
        TestUtil.cliSetup();
        TestUtil.runTest(TestDocument.class);
    }
}
