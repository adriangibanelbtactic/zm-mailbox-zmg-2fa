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

package com.zimbra.cs.account.soap;

import java.util.Map;

import com.zimbra.common.account.Key;
import com.zimbra.common.account.Key.CalendarResourceBy;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.cs.account.CalendarResource;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.Element.XMLElement;
import com.zimbra.soap.admin.type.Attr;
import com.zimbra.soap.admin.type.CalendarResourceInfo;

class SoapCalendarResource extends CalendarResource implements SoapEntry {

    SoapCalendarResource(String name, String id, Map<String, Object> attrs, Provisioning prov) {
        super(name, id, attrs, null, prov);
    }

    SoapCalendarResource(CalendarResourceInfo calResource, Provisioning prov)
    throws ServiceException {
        super(calResource.getName(), calResource.getId(),
                Attr.collectionToMap(calResource.getAttrList()), null, prov);
    }
    
    SoapCalendarResource(Element e, Provisioning prov)
    throws ServiceException {
        super(e.getAttribute(AdminConstants.A_NAME),
                e.getAttribute(AdminConstants.A_ID),
                SoapProvisioning.getAttrs(e), null, prov);
    }
    
    public void modifyAttrs(SoapProvisioning prov, Map<String, ? extends Object> attrs, boolean checkImmutable) throws ServiceException {
        XMLElement req = new XMLElement(AdminConstants.MODIFY_CALENDAR_RESOURCE_REQUEST);
        req.addElement(AdminConstants.E_ID).setText(getId());
        SoapProvisioning.addAttrElements(req, attrs);
        setAttrs(SoapProvisioning.getAttrs(prov.invoke(req).getElement(AdminConstants.E_CALENDAR_RESOURCE)));
    }

    public void reload(SoapProvisioning prov) throws ServiceException {
        XMLElement req = new XMLElement(AdminConstants.GET_CALENDAR_RESOURCE_REQUEST);
        Element a = req.addElement(AdminConstants.E_CALENDAR_RESOURCE);
        a.setText(getId());
        a.addAttribute(AdminConstants.A_BY, Key.CalendarResourceBy.id.name());
        setAttrs(SoapProvisioning.getAttrs(prov.invoke(req).getElement(AdminConstants.E_CALENDAR_RESOURCE)));
    }
}
