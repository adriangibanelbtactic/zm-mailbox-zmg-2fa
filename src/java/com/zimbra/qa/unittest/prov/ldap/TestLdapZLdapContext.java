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
package com.zimbra.qa.unittest.prov.ldap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import org.junit.*;
import static org.junit.Assert.*;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.ldap.IAttributes;
import com.zimbra.cs.ldap.LdapClient;
import com.zimbra.cs.ldap.LdapConstants;
import com.zimbra.cs.ldap.LdapUsage;
import com.zimbra.cs.ldap.SearchLdapOptions;
import com.zimbra.cs.ldap.ZLdapContext;
import com.zimbra.cs.ldap.ZLdapFilter;
import com.zimbra.cs.ldap.ZLdapFilterFactory;
import com.zimbra.cs.ldap.ZSearchControls;
import com.zimbra.cs.ldap.ZSearchResultEntry;
import com.zimbra.cs.ldap.ZSearchResultEnumeration;
import com.zimbra.cs.ldap.ZSearchScope;
import com.zimbra.cs.ldap.LdapException.LdapSizeLimitExceededException;

public class TestLdapZLdapContext extends LdapTest {
    private static LdapProvTestUtil provUtil;
    private static Provisioning prov;
    private static Domain domain;
    
    @BeforeClass
    public static void init() throws Exception {
        provUtil = new LdapProvTestUtil();
        prov = provUtil.getProv();
        domain = provUtil.createDomain(baseDomainName());
    }
    
    @AfterClass
    public static void cleanup() throws Exception {
        Cleanup.deleteAll(baseDomainName());
    }
    
    @Test
    public void searchPaged() throws Exception {
        int SIZE_LIMIT = 5;
        
        String base = LdapConstants.DN_ROOT_DSE;
        ZLdapFilter filter = ZLdapFilterFactory.getInstance().anyEntry();
        String returnAttrs[] = new String[]{"objectClass"};
        
        final List<String> result = new ArrayList<String>();
        
        SearchLdapOptions.SearchLdapVisitor visitor = new SearchLdapOptions.SearchLdapVisitor() {
            @Override
            public void visit(String dn, Map<String, Object> attrs, IAttributes ldapAttrs) {
                result.add(dn);
            }
        };
        
        SearchLdapOptions searchOptions = new SearchLdapOptions(
                base, filter, returnAttrs, SIZE_LIMIT, null, 
                ZSearchScope.SEARCH_SCOPE_SUBTREE, visitor);
        
        boolean caughtException = false;
        
        ZLdapContext zlc = null;
        try {
            zlc = LdapClient.getContext(LdapUsage.UNITTEST);
            zlc.searchPaged(searchOptions);
        } catch (LdapSizeLimitExceededException e) {
            caughtException = true;
        } finally {
            LdapClient.closeContext(zlc);
        }
        
        assertTrue(caughtException);
        assertEquals(SIZE_LIMIT, result.size());
    }
    
    @Test
    public void searchDir() throws Exception {
        int SIZE_LIMIT = 5;
        
        String base = LdapConstants.DN_ROOT_DSE;
        ZLdapFilter filter = ZLdapFilterFactory.getInstance().anyEntry();
        String returnAttrs[] = new String[]{"objectClass"};
        
        ZSearchControls searchControls = ZSearchControls.createSearchControls(
                ZSearchScope.SEARCH_SCOPE_SUBTREE, 
                SIZE_LIMIT, returnAttrs);
        
        int numFound = 0;
        boolean caughtException = false;
        
        ZLdapContext zlc = null;
        try {
            zlc = LdapClient.getContext(LdapUsage.UNITTEST);
            
            ZSearchResultEnumeration ne = zlc.searchDir(base, filter, searchControls);
            while (ne.hasMore()) {
                ZSearchResultEntry sr = ne.next();
                numFound++;
            }
            ne.close();
            
        } catch (LdapSizeLimitExceededException e) {
            caughtException = true;
        } finally {
            LdapClient.closeContext(zlc);
        }
        
        assertTrue(caughtException);
        
        /*
        // unboundid does not return entries if LdapSizeLimitExceededException
        // is thrown,  See commons on ZLdapContext.searchDir().
        if (testConfig != TestLdap.TestConfig.UBID) {
            assertEquals(SIZE_LIMIT, numFound);
        }
        */
    }
}
