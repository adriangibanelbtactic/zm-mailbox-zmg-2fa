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
package com.zimbra.qa.unittest.prov.ldap;

import com.zimbra.cs.account.ldap.LdapProv;
import com.zimbra.qa.unittest.prov.ProvTestUtil;

class LdapProvTestUtil extends ProvTestUtil {

    LdapProvTestUtil() throws Exception {
        super(LdapProv.getInst());
    }
    
    LdapProv getProv() {
        return (LdapProv) prov;
    }
}

