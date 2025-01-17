/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2014 Zimbra, Inc.
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
import com.zimbra.common.util.DateUtil;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.Entry;

public class PositiveTimeInterval extends AttributeCallback {

    @SuppressWarnings("rawtypes")
    @Override
    public void preModify(CallbackContext context, String attrName,
            Object attrValue, Map attrsToModify, Entry entry)
            throws ServiceException {
        SingleValueMod mod = singleValueMod(attrName, attrValue);
        if (mod.setting()) {
            long interval = DateUtil.getTimeInterval(mod.value(), 0);
            if (interval <= 0) {
                throw ServiceException.INVALID_REQUEST("cannot set " + attrName + " less than or equal to 0", null);
            }
        }
    }

    @Override
    public void postModify(CallbackContext context, String attrName, Entry entry) {
    }
}
