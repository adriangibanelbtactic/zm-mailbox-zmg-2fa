/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.account.callback;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.DistributionList;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.ldap.LdapProv;

public class AdminGroup extends AttributeCallback {

    @Override
    public void preModify(CallbackContext context, String attrName, Object attrValue,
            Map attrsToModify, Entry entry)
    throws ServiceException {
    }
    
    @Override
    public void postModify(CallbackContext context, String attrName, Entry entry) {
        
        if (!(entry instanceof DistributionList))
            return;
             
        Provisioning prov = Provisioning.getInstance();
        if (!(prov instanceof LdapProv))
            return;
        
        DistributionList group = (DistributionList)entry;
        ((LdapProv) prov).removeFromCache(group);
    }
}
