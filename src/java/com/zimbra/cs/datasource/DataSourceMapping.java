/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.datasource;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.DataSource;
import com.zimbra.cs.db.DbDataSource;
import com.zimbra.cs.db.DbDataSource.DataSourceItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Metadata;

public class DataSourceMapping {
    protected DataSource ds;
    protected DataSourceItem dsi;
    
    public DataSourceMapping(DataSource ds, DataSourceItem dsi) throws ServiceException {
        this.ds = ds;
        this.dsi = dsi;
        parseMetaData();
    }
    
    public DataSourceMapping(DataSource ds, int itemId) throws ServiceException {
        this.ds = ds;
        dsi = DataSourceDbMapping.getInstance().getMapping(ds, itemId);
        if (dsi.remoteId == null)
            throw MailServiceException.NO_SUCH_ITEM(itemId);
        parseMetaData();
    }
    
    public DataSourceMapping(DataSource ds, String remoteId) throws ServiceException {
        this.ds = ds;
        dsi = DataSourceDbMapping.getInstance().getReverseMapping(ds, remoteId);
        if (dsi.itemId == 0)
            throw MailServiceException.NO_SUCH_ITEM(remoteId);
        parseMetaData();
    }
    
    public DataSourceMapping(DataSource ds, int folderId, int itemId, String
        remoteId) throws ServiceException {
        this.ds = ds;
        dsi = new DataSourceItem(folderId, itemId, remoteId, new Metadata());
    }
    
    public DataSourceMapping(DataSource ds, int folderId, int itemId, String
        remoteId, int localFlags) throws ServiceException {
        this.ds = ds;
        dsi = new DataSourceItem(folderId, itemId, remoteId, new Metadata(),
            localFlags);
    }

    public DataSource getDataSource() { return ds; }
    
    public DataSourceItem getDataSourceItem() { return dsi; }
    
    public int getFolderId() { return dsi.folderId; }

    public int getItemId() { return dsi.itemId; }
    
    public int getItemFlags() throws ServiceException {
        if (dsi.itemFlags == -1) {
            com.zimbra.cs.mailbox.Message localMsg =
                DataSourceManager.getInstance().getMailbox(ds).getMessageById(null, dsi.itemId);
            dsi.itemFlags = localMsg.getFlagBitmask();
        }
        return dsi.itemFlags;
    }
    
    public String getRemoteId() { return dsi.remoteId; }
    
    public void setFolderId(int folderId) { dsi.folderId = folderId; }
    
    public void setItemId(int itemId) { dsi.itemId = itemId; }
    
    public void setItemFlags(int itemFlags) { dsi.itemFlags = itemFlags; }
    
    public void setRemoteId(String remoteId) { dsi.remoteId = remoteId; }
    
    public void add() throws ServiceException {
        DataSourceDbMapping.getInstance().addMapping(ds, dsi);
    }
    
    public void delete() throws ServiceException {
        DataSourceDbMapping.getInstance().deleteMapping(ds, dsi.itemId);
    }
    
    public void set() throws ServiceException {
        try {
            DataSourceDbMapping.getInstance().addMapping(ds, dsi);
        } catch (Exception e) {
            delete();
            DataSourceDbMapping.getInstance().addMapping(ds, dsi);
        }
    }
    
    public void update() throws ServiceException {
        DataSourceDbMapping.getInstance().updateMapping(ds, dsi);
    }
    
    protected void parseMetaData() throws ServiceException {}
}
