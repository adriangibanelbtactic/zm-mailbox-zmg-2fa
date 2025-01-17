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
package com.zimbra.cs.account.callback;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;

public class UCProvider extends AttributeCallback {
    @Override
    public void preModify(CallbackContext context, String attrName,
            Object attrValue, Map attrsToModify, Entry entry)
    throws ServiceException {
        SingleValueMod mod = singleValueMod(attrsToModify, attrName);
        if (mod.unsetting()) {
            return;
        }

        String newValue = mod.value();
        String allowedValue = Provisioning.getInstance().getConfig().getUCProviderEnabled();

        if (allowedValue == null) {
            throw ServiceException.INVALID_REQUEST("no " + Provisioning.A_zimbraUCProviderEnabled +
                    " is configured on global config",  null);
        }

        if (!allowedValue.equals(newValue)) {
            throw ServiceException.INVALID_REQUEST("UC provider " + newValue + " is not allowed " +
                    " by " + Provisioning.A_zimbraUCProviderEnabled, null);
        }

    }

    @Override
    public void postModify(CallbackContext context, String attrName, Entry entry) {
    }

}
