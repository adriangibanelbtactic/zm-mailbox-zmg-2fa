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

package com.zimbra.cs.account.callback;

import java.util.Map;

import com.zimbra.common.account.Key.AccountBy;
import com.zimbra.common.account.Key.DomainBy;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.EmailUtil;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;

public class AllowFromAddress extends AttributeCallback {

    /**
     * zimbraAllowFromAddress may not contain the address of an internal account or a distribution list
     */
    @Override
    public void preModify(CallbackContext context, String attrName, Object value,
            Map attrsToModify, Entry entry) 
    throws ServiceException {

        MultiValueMod mod = multiValueMod(attrsToModify, Provisioning.A_zimbraAllowFromAddress);
        if (mod != null && (mod.adding() || mod.replacing())) {
            for (String addr : mod.valuesSet()) {
                checkAddress(addr);
            }
        }
    }

    private void checkAddress(String addr) throws ServiceException {
        Provisioning prov = Provisioning.getInstance();
        String domain = EmailUtil.getValidDomainPart(addr);
        if (domain != null) {  // addresses in non-local domains are allowed
            Domain internalDomain = prov.getDomain(DomainBy.name, domain, true);
            if (internalDomain != null) {
                if (prov.isDistributionList(addr)) {
                    throw ServiceException.INVALID_REQUEST(Provisioning.A_zimbraAllowFromAddress +
                            " may not contain a distribution list: " + addr, null);
                }
                Account acct = prov.get(AccountBy.name, addr);
                if (acct != null) {
                    throw ServiceException.INVALID_REQUEST(Provisioning.A_zimbraAllowFromAddress +
                            " may not contain an internal account: " + addr, null);
                }
            }
        }
    }
    
    @Override
    public void postModify(CallbackContext context, String attrName, Entry entry) {
    }
}
