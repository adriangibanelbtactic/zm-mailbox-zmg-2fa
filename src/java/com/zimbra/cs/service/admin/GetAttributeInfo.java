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
package com.zimbra.cs.service.admin;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.AttributeClass;
import com.zimbra.cs.account.AttributeInfo;
import com.zimbra.cs.account.AttributeManager;
import com.zimbra.cs.account.FileGenUtil;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.soap.ZimbraSoapContext;

public class GetAttributeInfo extends AdminDocumentHandler {

    @Override
    public Element handle(Element request, Map<String, Object> context)
    throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        
        String[] attrs = null;
        String attrsRequested = request.getAttribute(AdminConstants.A_ATTRS, null);
        if (attrsRequested != null) {
            attrs = attrsRequested.split(",");
        }
        
        String[] entryTypes = null;
        String entryTypesRequested = request.getAttribute(AdminConstants.A_ENTRY_TYPES, null);
        if (entryTypesRequested != null) {
            entryTypes = entryTypesRequested.split(",");
        }
        
        if (attrs != null && entryTypes != null) {
            throw ServiceException.INVALID_REQUEST("only one of " + 
                    AdminConstants.A_ATTRS + " or " + AdminConstants.A_ENTRY_TYPES + 
                    " can be specified", null);
        }
        
        AttributeManager attrMgr = AttributeManager.getInstance();
        
        Element response = zsc.createElement(AdminConstants.GET_ATTRIBUTE_INFO_RESPONSE);
        
        if (attrs != null) {
            for (String attr : attrs) {
                encodeAttr(response, attrMgr, attr.trim());
            }
        } else if (entryTypes != null) {
            for (String entry : entryTypes) {
                AttributeClass attrClass = AttributeClass.fromString(entry.trim());
                TreeSet<String> attrsOnEntry = new TreeSet<String>(attrMgr.getAllAttrsInClass(attrClass));
                for (String attr : attrsOnEntry) {
                    encodeAttr(response, attrMgr, attr);
                }
            }
        } else {
            // AttributeManager.getAllAttrs() only contains attrs with AttributeInfo,
            // not extension attrs
            // attrs = new TreeSet<String>(am.getAllAttrs());
            //
            // attr sets for each AttributeClass contain attrs in the extensions, use them
            TreeSet<String> allAttrs = new TreeSet<String>();
            for (AttributeClass ac : AttributeClass.values()) {
                allAttrs.addAll(attrMgr.getAllAttrsInClass(ac));
            }
            
            for (String attr : allAttrs) {
                encodeAttr(response, attrMgr, attr);
            }
        }
       
        return response;
    }
    
    private void encodeAttr(Element response, AttributeManager attrMgr, String attr) {
        AttributeInfo attrInfo = attrMgr.getAttributeInfo(attr);
        
        if (attrInfo == null) {
            ZimbraLog.account.info("no attribte info for " + attr);
            return;
        }
        
        String desc = attrInfo.getDescription();
        String deSpacedDesc = FileGenUtil.wrapComments((desc==null?"":desc), Integer.MAX_VALUE, "");
        
        Element eAttr = response.addElement(AdminConstants.E_A);
        eAttr.addAttribute(AdminConstants.A_N, attr);
        eAttr.addAttribute(AdminConstants.A_DESC, deSpacedDesc);
    }
    
    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        notes.add(AdminRightCheckPoint.Notes.ALLOW_ALL_ADMINS);
    }

}
