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
package com.zimbra.cs.account.ldap.upgrade;

import java.util.HashMap;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Provisioning;

public class BUG_14531 extends UpgradeOp {

    @Override
    void doUpgrade() throws ServiceException {
        Config config = prov.getConfig();
        
        String value = 
            "zimbraSync:(&(|(displayName=*)(cn=*)(sn=*)(gn=*)(mail=*)(zimbraMailDeliveryAddress=*)(zimbraMailAlias=*))(|(objectclass=zimbraAccount)(objectclass=zimbraDistributionList))(!(zimbraHideInGal=TRUE))(!(zimbraIsSystemResource=TRUE)))";
        
        Map<String, Object> attr = new HashMap<String, Object>();
        attr.put("+" + Provisioning.A_zimbraGalLdapFilterDef, value);
        
        printer.println("Adding zimbraSync filter to global config " + Provisioning.A_zimbraGalLdapFilterDef);
        prov.modifyAttrs(config, attr);
    }
}
