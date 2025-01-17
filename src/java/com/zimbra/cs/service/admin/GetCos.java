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

/*
 * Created on Jun 17, 2004
 */
package com.zimbra.cs.service.admin;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.zimbra.common.account.Key;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AccountConstants;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.AccessManager.AttrRightChecker;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.AttributeClass;
import com.zimbra.cs.account.AttributeManager;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Cos;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class GetCos extends AdminDocumentHandler {

    @Override
    public boolean domainAuthSufficient(Map context) {
        return true;
    }

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {

        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();

        Set<String> reqAttrs = getReqAttrs(request, AttributeClass.cos);

        Element d = request.getElement(AdminConstants.E_COS);
        String key = d.getAttribute(AdminConstants.A_BY);
        String value = d.getText();

        Cos cos = prov.get(Key.CosBy.fromString(key), value);

        if (cos == null)
            throw AccountServiceException.NO_SUCH_COS(value);

        AdminAccessControl aac = checkCosRight(zsc, cos, AdminRight.PR_ALWAYS_ALLOW);

        Element response = zsc.createElement(AdminConstants.GET_COS_RESPONSE);
        encodeCos(response, cos, reqAttrs, aac.getAttrRightChecker(cos));

        return response;
    }

    public static void encodeCos(Element e, Cos c) throws ServiceException {
        encodeCos(e, c, null, null);
    }

    public static void encodeCos(Element e, Cos c, Set<String> reqAttrs, AttrRightChecker attrRightChecker) throws ServiceException {
        Config config = Provisioning.getInstance().getConfig();
        Element cos = e.addNonUniqueElement(AdminConstants.E_COS);
        cos.addAttribute(AdminConstants.A_NAME, c.getName());
        cos.addAttribute(AdminConstants.E_ID, c.getId());

        if (c.isDefaultCos())
            cos.addAttribute(AdminConstants.A_IS_DEFAULT_COS, true);

        Map attrs = c.getUnicodeAttrs();
        AttributeManager attrMgr = AttributeManager.getInstance();
        for (Iterator mit=attrs.entrySet().iterator(); mit.hasNext(); ) {
            Map.Entry entry = (Entry) mit.next();
            String name = (String) entry.getKey();
            Object value = entry.getValue();

            if (reqAttrs != null && !reqAttrs.contains(name))
                continue;

            boolean allowed = attrRightChecker == null ? true : attrRightChecker.allowAttr(name);

            boolean isCosAttr = !attrMgr.isAccountInherited(name);
            if (value instanceof String[]) {
                String sv[] = (String[]) value;
                for (int i = 0; i < sv.length; i++) {
                    encodeCosAttr(cos, name, sv[i], isCosAttr, allowed);
                }
            } else if (value instanceof String) {
                value = com.zimbra.cs.service.account.ToXML.fixupZimbraPrefTimeZoneId(name, (String)value);
                encodeCosAttr(cos, name, (String)value, isCosAttr, allowed);
            }
        }
    }

    private static void encodeCosAttr(Element parent, String key, String value, boolean isCosAttr, boolean allowed) {

        Element e = parent.addNonUniqueElement(AdminConstants.E_A);
        e.addAttribute(AdminConstants.A_N, key);

        if (allowed) {
            e.setText(Provisioning.sanitizedAttrValue(key, value).toString());
        } else {
            e.addAttribute(AccountConstants.A_PERM_DENIED, true);
        }

        if (isCosAttr) {
            e.addAttribute(AdminConstants.A_C, true);
        }
    }

    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        relatedRights.add(Admin.R_getCos);
        notes.add(String.format(AdminRightCheckPoint.Notes.GET_ENTRY, Admin.R_getCos.getName()));
    }
}
