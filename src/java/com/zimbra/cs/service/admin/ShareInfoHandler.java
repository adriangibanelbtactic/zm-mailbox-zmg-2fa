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
package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.DistributionList;
import com.zimbra.cs.account.NamedEntry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.common.account.Key;
import com.zimbra.common.account.Key.AccountBy;
import com.zimbra.common.account.Key.DistributionListBy;
import com.zimbra.soap.ZimbraSoapContext;

public abstract class ShareInfoHandler extends AdminDocumentHandler {

    /**
     * must be careful and only return accounts a domain admin can see
     */
    public boolean domainAuthSufficient(Map context) {
        return true;
    }
    
    /*
     * DL only for now
     */
    protected NamedEntry getPublishableTargetEntry(ZimbraSoapContext zsc, Element request, Provisioning prov) throws ServiceException {
        Element eDl = request.getElement(AdminConstants.E_DL);
        
        NamedEntry entry = null;
        
        String key = eDl.getAttribute(AdminConstants.A_BY);
        String value = eDl.getText();
    
        DistributionList dl = prov.get(Key.DistributionListBy.fromString(key), value);
            
        if (dl == null)
            throw AccountServiceException.NO_SUCH_DISTRIBUTION_LIST(value);
            
        entry = dl;
       
        return entry;
    }
    
    protected Account getOwner(ZimbraSoapContext zsc, Element eShare, Provisioning prov, boolean required) throws ServiceException {
        Element eOwner = null;
        if (required)
            eOwner = eShare.getElement(AdminConstants.E_OWNER);
        else
            eOwner = eShare.getOptionalElement(AdminConstants.E_OWNER);
        
        if (eOwner == null)
            return null;
        
        String key = eOwner.getAttribute(AdminConstants.A_BY);
        String value = eOwner.getText();

        Account account = prov.get(AccountBy.fromString(key), value, zsc.getAuthToken());

        if (account == null)
            throw AccountServiceException.NO_SUCH_ACCOUNT(value);
        
        return account;
    }
    

}
