/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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

import com.zimbra.common.account.Key.CalendarResourceBy;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.CalendarResource;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.soap.JaxbUtil;
import com.zimbra.soap.ZimbraSoapContext;
import com.zimbra.soap.admin.message.ModifyCalendarResourceRequest;

/**
 * @author jhahm
 */
public class ModifyCalendarResource extends AdminDocumentHandler {

    private static final String[] TARGET_RESOURCE_PATH = new String[] { AdminConstants.E_ID };
    @Override
    protected String[] getProxiedResourcePath()  { return TARGET_RESOURCE_PATH; }

    /**
     * must be careful and only allow modifies to
     * calendar resources/attrs domain admin has access to
     */
    @Override
    public boolean domainAuthSufficient(Map context) {
        return true;
    }

    /**
     * @return true - which means accept responsibility for measures to prevent account harvesting by delegate admins
     */
    @Override
    public boolean defendsAgainstDelegateAdminAccountHarvesting() {
        return true;
    }

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();
        ModifyCalendarResourceRequest req = JaxbUtil.elementToJaxb(request);
        String id = req.getId();
        if (null == id) {
            throw ServiceException.INVALID_REQUEST("missing required attribute: " + AdminConstants.E_ID, null);
        }
        Map<String, Object> attrs = req.getAttrsAsOldMultimap();

        CalendarResource resource = prov.get(CalendarResourceBy.id, id);
        defendAgainstCalResourceHarvesting(resource, CalendarResourceBy.id, id, zsc, attrs);

        // pass in true to checkImmutable
        prov.modifyAttrs(resource, attrs, true);

        ZimbraLog.security.info(ZimbraLog.encodeAttrs(
                new String[] {"cmd", "ModifyCalendarResource", "name", resource.getName()}, attrs));

        Element response = zsc.createElement(AdminConstants.MODIFY_CALENDAR_RESOURCE_RESPONSE);
        ToXML.encodeCalendarResource(response, resource, true);
        return response;
    }

    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        notes.add(String.format(AdminRightCheckPoint.Notes.MODIFY_ENTRY,
                Admin.R_modifyCalendarResource.getName(), "calendar resource"));
    }
}
