/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2006, 2007, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.service.admin;

import java.util.List;
import java.util.Map;

import org.apache.lucene.search.Query;

import com.zimbra.common.account.Key;
import com.zimbra.common.account.Key.ServerBy;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.rmgmt.RemoteMailQueue;
import com.zimbra.cs.rmgmt.RemoteMailQueue.QueueAction;
import com.zimbra.cs.rmgmt.RemoteMailQueue.QueueAttr;
import com.zimbra.soap.ZimbraSoapContext;

public class MailQueueAction extends AdminDocumentHandler {

    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();

        Element serverElem = request.getElement(AdminConstants.E_SERVER);
        String serverName = serverElem.getAttribute(AdminConstants.A_NAME);

        Server server = prov.get(Key.ServerBy.name, serverName);
        if (server == null) {
            throw ServiceException.INVALID_REQUEST("server with name " + serverName + " could not be found", null);
        }

        checkRight(zsc, context, server, Admin.R_manageMailQueue);

        Element queueElem = serverElem.getElement(AdminConstants.E_QUEUE);
        String queueName = queueElem.getAttribute(AdminConstants.A_NAME);

        RemoteMailQueue rmq = RemoteMailQueue.getRemoteMailQueue(server, queueName, false);

        Element actionElem = queueElem.getElement(AdminConstants.E_ACTION);
        String op = actionElem.getAttribute(AdminConstants.A_OP);
        QueueAction action = QueueAction.valueOf(op);
        if (action == null) {
            throw ServiceException.INVALID_REQUEST("bad " + AdminConstants.A_OP + ":" + op, null);
        }
        String by = actionElem.getAttribute(AdminConstants.A_BY);
        String[] ids;
        if (by.equals(AdminConstants.BY_ID)) {
            ids = actionElem.getText().split(",");
        } else if (by.equals(AdminConstants.BY_QUERY)) {
            Element queryElem = actionElem.getElement(AdminConstants.E_QUERY);
            Query query = GetMailQueue.buildLuceneQuery(queryElem);
            RemoteMailQueue.SearchResult sr = rmq.search(query, 0, Integer.MAX_VALUE);
            ids = new String[sr.qitems.size()];
            int i = 0;
            for (Map<QueueAttr,String> qitem : sr.qitems) {
                ids[i++] = qitem.get(QueueAttr.id);
            }
        } else {
            throw ServiceException.INVALID_REQUEST("bad " + AdminConstants.A_BY + ": " + by, null);
        }

        rmq.action(server, action, ids);

        Element response = zsc.createElement(AdminConstants.MAIL_QUEUE_ACTION_RESPONSE);
        return response;
    }

    @Override
    public void docRights(List<AdminRight> relatedRights, List<String> notes) {
        relatedRights.add(Admin.R_manageMailQueue);
    }
}
