/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2008, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.session.IWaitSet;
import com.zimbra.cs.session.WaitSetMgr;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * This API is used to dump the internal state of a wait set.  This API is intended
 * for debugging use only.
 */
public class QueryWaitSet extends AdminDocumentHandler {

    @Override
    public Element handle(Element request, Map<String, Object> context)
    throws ServiceException {
        
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        WaitSetMgr.checkRightForAllAccounts(zsc); // must be a global admin
        
        Element response = zsc.createElement(AdminConstants.QUERY_WAIT_SET_RESPONSE);
        
        String waitSetId = request.getAttribute(MailConstants.A_WAITSET_ID, null);
        
        List<IWaitSet> sets;
        
        if (waitSetId != null) {
            sets = new ArrayList<IWaitSet>(1);
            IWaitSet ws = WaitSetMgr.lookup(waitSetId);
            if (ws == null) {
                throw AdminServiceException.NO_SUCH_WAITSET(waitSetId);
            }
            sets.add(ws);
        } else {
            sets = WaitSetMgr.getAll();
        }

        for (IWaitSet set : sets) {
            Element waitSetElt = response.addElement(AdminConstants.E_WAITSET);
            set.handleQuery(waitSetElt);
        }
        return response;
    }
    
    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        notes.add(AdminRightCheckPoint.Notes.SYSTEM_ADMINS_ONLY);
    }
}
