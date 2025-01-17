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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.zimbra.common.account.ZAttrProvisioning.MailMode;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.AttributeCallback;
import com.zimbra.cs.account.Config;
import com.zimbra.cs.account.Entry;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;

public class LocalBind extends AttributeCallback {

    @Override
    public void preModify(CallbackContext context, String attrName, Object attrValue,
            Map attrsToModify, Entry entry)
    throws ServiceException {
    }
    
    @Override
    public void postModify(CallbackContext context, String attrName, Entry entry) {
        // Update zimbraAdminLocalBind if zimbraAdminBindAddress is changed.
        if (entry instanceof Server) {
            Server server = (Server) entry;
            if (attrName.equals(Provisioning.A_zimbraAdminBindAddress)) {
                String address = server.getAttr(Provisioning.A_zimbraAdminBindAddress, true);
                try{
                    if ((address == null) || (address.isEmpty())) {
                        server.setAdminLocalBind(false);
                    } else {
                        InetAddress inetAddress;
                        try {
                            inetAddress = InetAddress.getByName(address);
                        } catch (UnknownHostException e) {
                            server.setAdminLocalBind(false);
                            return;
                        }
                        if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress())
                            server.setAdminLocalBind(false);
                        else
                            server.setAdminLocalBind(true);
                    }
                } catch (ServiceException se) {
                    ZimbraLog.misc.warn("Unable to update zimbraAdminLocalBind " + se);
                }
                return;
            }
        }
        
        // Update zimbraMailLocalBind if zimbraMailMode or zimbraMailBindAddress or zimbraMailSSLBindAddress is changed.
        // zimbraMailMode is also in globalConfig. Make sure to update the zimbraMailLocalBind of all the
        // servers if this globalconfig is changed.
        
        List<Server> serverList = new ArrayList<Server>();
        if (entry instanceof Config) {
            try {
                serverList = Provisioning.getInstance().getAllServers();
            } catch (ServiceException se) {
                ZimbraLog.misc.warn("Unable to get server list " + se);
            }
        } else if (entry instanceof Server) {
            serverList.add((Server) entry);
        } 
            
        if (attrName.equals(Provisioning.A_zimbraMailMode) ||
                attrName.equals(Provisioning.A_zimbraMailBindAddress) ||
                attrName.equals(Provisioning.A_zimbraMailSSLBindAddress)) {
            for (Server server : serverList) {
                try {
                    MailMode mailMode = server.getMailMode();
                    if (mailMode == null)
                        mailMode = Provisioning.getInstance().getConfig().getMailMode();
                    if (mailMode == null || !mailMode.isHttps()) {
                        // http is enabled. Check if bindaddress conflicts with localhost.
                        String address = server.getAttr(Provisioning.A_zimbraMailBindAddress, true);
                        if ((address == null) || (address.isEmpty())) {
                            server.setMailLocalBind(false);
                        } else {
                            InetAddress inetAddress;
                            try {
                                inetAddress = InetAddress.getByName(address);
                            } catch (UnknownHostException e) {
                                server.setMailLocalBind(false);
                                continue;
                            }
                            if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress())
                                server.setMailLocalBind(false);
                            else
                                server.setMailLocalBind(true);
                        }
                    } else {
                        // mailmode set to https. Enable http for localhost binding.
                        server.setMailLocalBind(true);
                    }
                } catch (ServiceException e) {
                    ZimbraLog.misc.warn("Unable to set zimbraMailLocalBind " + e);
                    continue;
                }
            }
        }
    }
}
