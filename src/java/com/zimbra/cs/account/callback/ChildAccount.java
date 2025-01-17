/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2008, 2009, 2010, 2011, 2012, 2013, 2014 Zimbra, Inc.
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.common.account.Key.AccountBy;
 
public class ChildAccount extends AttributeCallback {
    
    @Override
    public void preModify(CallbackContext context, String attrName, Object value,
            Map attrsToModify, Entry entry) 
    throws ServiceException {

        /*
         * This callback is for both zimbraPrefChildVisibleAccount and zimbraChildAccount, and it handles
         * both in one shot.  If we've been called just return.
         */ 
        if (context.isDoneAndSetIfNot(ChildAccount.class)) {
            return;
        }
        
        // the +/- has been striped off from attrName but we need that info, it is in attrsToModify
        
        MultiValueMod visibleChildrenMod = multiValueMod(attrsToModify, Provisioning.A_zimbraPrefChildVisibleAccount);
        MultiValueMod allChildrenMod = multiValueMod(attrsToModify, Provisioning.A_zimbraChildAccount);

        Set<String> visibleChildren = newValuesToBe(visibleChildrenMod, entry, Provisioning.A_zimbraPrefChildVisibleAccount);
        Set<String> allChildren = newValuesToBe(allChildrenMod, entry, Provisioning.A_zimbraChildAccount);

        //if child account has already been deleted, let it go
        if (allChildren != null && !allChildren.contains(value)) {
            return;
        }

        if (allChildrenMod != null && allChildrenMod.deleting()) {
            attrsToModify.put(Provisioning.A_zimbraPrefChildVisibleAccount, "");
        } else {
            Set<String> vidsToRemove = new HashSet<String>();
            for (String vid : visibleChildren) {
                if (!allChildren.contains(vid)) {
                    /*
                     * if the request is removing children but not updating the visible children, 
                     * we remove the visible children that are no longer a child.
                     * otherwise, throw exception if the mod results into a situation where a 
                     * visible child is not one of the children.
                     */ 
                    if (allChildrenMod!=null && allChildrenMod.removing() && visibleChildrenMod==null)
                        vidsToRemove.add(vid);
                    else
                        throw ServiceException.INVALID_REQUEST("visible child id " + vid + " is not one of " + Provisioning.A_zimbraChildAccount, null);
                }
            }

            if (vidsToRemove.size() > 0)
                attrsToModify.put("-" + Provisioning.A_zimbraPrefChildVisibleAccount, vidsToRemove.toArray(new String[vidsToRemove.size()]));
        }

        // check circular relationship
        if (entry instanceof Account) {
            Provisioning prov = Provisioning.getInstance();
            Account parentAcct = (Account)entry;
            String parentId = parentAcct.getId();
            for (String childId : allChildren) {
                Account childAcct = prov.get(AccountBy.id, childId);
                if (childAcct == null)
                    throw AccountServiceException.NO_SUCH_ACCOUNT(childId);

                String[] children = childAcct.getChildAccount();
                for (String child : children) {
                    if (child.equals(parentId))
                        throw ServiceException.INVALID_REQUEST(
                                "child account " + childId + "(" + childAcct.getName() + ")"  +
                                " is parent of the parent account " + parentId + "(" + parentAcct.getName() + ")", 
                                null);
                }
            }
        }
    }

    @Override
    public void postModify(CallbackContext context, String attrName, Entry entry) {
    }
}

