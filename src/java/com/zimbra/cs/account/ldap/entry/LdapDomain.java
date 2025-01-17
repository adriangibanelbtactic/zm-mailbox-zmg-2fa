/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.account.ldap.entry;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.ldap.LdapProv;
import com.zimbra.cs.gal.ZimbraGalSearchBase.PredefinedSearchBase;
import com.zimbra.cs.ldap.LdapConstants;
import com.zimbra.cs.ldap.LdapException;
import com.zimbra.cs.ldap.ZAttributes;
import com.zimbra.cs.ldap.ZLdapFilter;
import com.zimbra.cs.ldap.ZLdapFilterFactory;

/**
 * 
 * @author pshao
 *
 */
public class LdapDomain extends Domain implements LdapEntry {

    private String mDn;
    
    public LdapDomain(String dn, ZAttributes attrs, Map<String, Object> defaults, Provisioning prov) 
    throws LdapException {
        super(attrs.getAttrString(Provisioning.A_zimbraDomainName), 
                attrs.getAttrString(Provisioning.A_zimbraId), 
                attrs.getAttrs(), defaults, prov);
        mDn = dn;
    }

    public String getDN() {
        return mDn;
    }
    
    @Override
    public String getGalSearchBase(String searchBaseRaw) throws ServiceException {
        LdapProv ldapProv = (LdapProv)getProvisioning();
        
        if (searchBaseRaw.equalsIgnoreCase(PredefinedSearchBase.DOMAIN.name())) {
            // dynamic groups are under the cn=groups tree,
            // accounts and Dls are under the people tree
            // We can no longer just search under the people tree because that 
            // will leave dynamic groups out.   We don't want to do two(once under the 
            // people tree, once under the groups tree) LDAP searches either because 
            // that will hurt perf.  
            // As of bug 66001, we now use the dnSubtreeMatch filter 
            // (extension supported by OpenLDAP) to exclude entries in sub domains.
            // See getDnSubtreeMatchFilter().
            return getDN();
            // return ldapProv.getDIT().domainDNToAccountSearchDN(getDN());
        } else if (searchBaseRaw.equalsIgnoreCase(PredefinedSearchBase.SUBDOMAINS.name())) {
            return getDN();
        } else if (searchBaseRaw.equalsIgnoreCase(PredefinedSearchBase.ROOT.name())) {
            return LdapConstants.DN_ROOT_DSE;
        }
        
        // broken by p4 changed 150971, fixed now
        return searchBaseRaw;
    }
    
    public ZLdapFilter getDnSubtreeMatchFilter() throws ServiceException {
        LdapProv ldapProv = (LdapProv)getProvisioning();
        
        return ZLdapFilterFactory.getInstance().dnSubtreeMatch(
                ldapProv.getDIT().domainDNToAccountSearchDN(getDN()),
                ldapProv.getDIT().domainDNToDynamicGroupsBaseDN(getDN()));
    }
}
