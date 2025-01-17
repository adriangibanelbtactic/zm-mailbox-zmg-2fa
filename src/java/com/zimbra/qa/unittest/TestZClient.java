/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2010, 2011, 2013, 2014 Zimbra, Inc.
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

import java.util.List;

import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException.AuthFailedServiceException;
import com.zimbra.common.account.Key.AccountBy;
import com.zimbra.client.ZFeatures;
import com.zimbra.client.ZGetInfoResult;
import com.zimbra.client.ZMailbox;
import com.zimbra.client.ZPrefs;
import com.zimbra.client.ZSignature;
import com.zimbra.client.ZMailbox.Options;

import junit.framework.TestCase;

public class TestZClient
extends TestCase {
    
    private static final String USER_NAME = "user1";

    public void setUp()
    throws Exception {
        cleanUp();
    }
    
    /**
     * Confirms that the prefs accessor works (bug 51384).
     */
    public void testPrefs()
    throws Exception {
        Account account = TestUtil.getAccount(USER_NAME);
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        ZPrefs prefs = mbox.getPrefs();
        assertEquals(account.getPrefLocale(), prefs.getLocale());
    }
    
    /**
     * Confirms that the features accessor doesn't throw NPE (bug 51384).
     */
    public void testFeatures()
    throws Exception {
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        ZFeatures features = mbox.getFeatures();
        features.getPop3Enabled();
    }

    public void testChangePassword()
    throws Exception {
        Account account = TestUtil.getAccount(USER_NAME);
        Options options = new Options();
        options.setAccount(account.getName());
        options.setAccountBy(AccountBy.name);
        options.setPassword(TestUtil.DEFAULT_PASSWORD);
        options.setNewPassword("test456");
        options.setUri(TestUtil.getSoapUrl());
        ZMailbox.changePassword(options);
        
        try {
            TestUtil.getZMailbox(USER_NAME);
        } catch (SoapFaultException e) {
            assertEquals(AuthFailedServiceException.AUTH_FAILED, e.getCode());
        }
    }
    
    /**
     * Confirms that the {@code List} of signatures returned by {@link ZMailbox#getSignatures}
     * is modifiable (see bug 51842). 
     */
    public void testModifySignatures()
    throws Exception {
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        List<ZSignature> signatures = mbox.getSignatures();
        try {
            signatures.set(signatures.size(), null);
        } catch (IndexOutOfBoundsException e) {
            // Not UnsupportedOperationException, so we're good.
        }
        
        ZGetInfoResult info = mbox.getAccountInfo(true);
        signatures = info.getSignatures();
        try {
            signatures.set(signatures.size(), null);
        } catch (IndexOutOfBoundsException e) {
            // Not UnsupportedOperationException, so we're good.
        }
    }
    
    public void tearDown()
    throws Exception {
        cleanUp();
    }
    
    private void cleanUp()
    throws Exception {
        Account account = TestUtil.getAccount(USER_NAME);
        account.setPassword(TestUtil.DEFAULT_PASSWORD);
    }
    
    public static void main(String[] args)
    throws Exception {
        TestUtil.cliSetup();
        TestUtil.runTest(TestZClient.class);
    }
}
