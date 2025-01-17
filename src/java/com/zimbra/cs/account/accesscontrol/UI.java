/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2012, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.account.accesscontrol;

import com.zimbra.common.service.ServiceException;

public class UI implements Comparable<UI> {
    
    private String name;
    private String desc;
    
    public UI(String name) {
        this.name = name;
    }
    
    /*
     * for sorting for RightManager.genAdminDocs()
     */
    @Override
    public int compareTo(UI other) {
        return name.compareTo(other.name);
    }
    
    String getName() {
        return name;
    }
    
    void setDesc(String desc) {
        this.desc = new String(desc);
    }
    
    String getDesc() {
        return desc;
    }
    
    void validate() throws ServiceException {
        if (desc == null) {
            throw ServiceException.PARSE_ERROR("missing desc", null);
        }
    }


    
}
