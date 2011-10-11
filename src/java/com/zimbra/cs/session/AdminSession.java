/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2005, 2006, 2007, 2009, 2010 Zimbra, Inc.
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.3 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */
/*
 * Created on Aug 31, 2005
 */
package com.zimbra.cs.session;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.Constants;
import com.zimbra.cs.account.Domain;
import com.zimbra.cs.account.EntrySearchFilter;
import com.zimbra.cs.account.NamedEntry;
import com.zimbra.cs.account.Provisioning.SearchDirectoryObjectType;
import com.zimbra.cs.account.Provisioning.SearchObjectsOptions;
import com.zimbra.cs.account.Provisioning.SearchObjectsOptions.SortOpt;
import com.zimbra.cs.account.ldap.LdapEntrySearchFilter;
import com.zimbra.cs.ldap.ZLdapFilterFactory.FilterId;

import java.util.HashMap;
import java.util.List;

/** @author dkarp */
public class AdminSession extends Session {

    private static final long ADMIN_SESSION_TIMEOUT_MSEC = 10 * Constants.MILLIS_PER_MINUTE;
  
    private AccountSearchParams mSearchParams;
    private HashMap<String,Object> mData = new HashMap<String,Object>();

    public AdminSession(String accountId) {
        super(accountId, Session.Type.ADMIN);
    }

    @Override
    protected boolean isMailboxListener() {
        return false;
    }

    @Override
    protected boolean isRegisteredInCache() {
        return true;
    }

    @Override
    protected long getSessionIdleLifetime() {
        return ADMIN_SESSION_TIMEOUT_MSEC;
    }
    
    public Object getData(String key) { return mData.get(key); }
    public void setData(String key, Object data) { mData.put(key, data); }
    public void clearData(String key) { mData.remove(key); }

    @Override public void notifyPendingChanges(PendingModifications pns, int changeId, Session source) { }

    @Override protected void cleanup() { }

    public List<NamedEntry> searchDirectory(SearchObjectsOptions searchOpts,
            int offset, NamedEntry.CheckRight rightChecker) 
    throws ServiceException {
        
        AccountSearchParams params = new AccountSearchParams(searchOpts, rightChecker);
        
        boolean needToSearch =  (offset == 0) || (mSearchParams == null) || !mSearchParams.equals(params);
        //ZimbraLog.account.info("this="+this+" mSearchParams="+mSearchParams+" equal="+!params.equals(mSearchParams));
        if (needToSearch) {
            //ZimbraLog.account.info("doing new search: "+query+ " offset="+offset);
            params.doSearch();
            mSearchParams = params;
        } else {
            //ZimbraLog.account.info("cached search: "+query+ " offset="+offset);
        }
        return mSearchParams.getResult();
    }

    public List searchCalendarResources(Domain d, EntrySearchFilter filter, String[] attrs, 
            String sortBy, boolean sortAscending, int offset, NamedEntry.CheckRight rightChecker)
    throws ServiceException {
        String query = LdapEntrySearchFilter.toLdapCalendarResourcesFilter(filter);
        
        SearchObjectsOptions options = new SearchObjectsOptions();
        options.setDomain(d);
        options.setTypes(SearchDirectoryObjectType.resources);
        options.setFilterString(FilterId.ADMIN_SEARCH, query);
        options.setReturnAttrs(attrs);
        options.setSortOpt(sortAscending ? SortOpt.SORT_ASCENDING : SortOpt.SORT_DESCENDING);
        options.setSortAttr(sortBy);
        options.setConvertIDNToAscii(true);
        
        return searchDirectory(options, offset, rightChecker);
    }

}
