/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2005, 2006, 2007, 2008, 2009, 2010, 2013, 2014 Zimbra, Inc.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.zimbra.common.auth.ZAuthToken;
import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.util.MapUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.accesscontrol.AdminRight;
import com.zimbra.cs.account.accesscontrol.Rights.Admin;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.service.FileUploadServlet;
import com.zimbra.cs.service.FileUploadServlet.Upload;
import com.zimbra.cs.util.WebClientServiceUtil;
import com.zimbra.cs.zimlet.ZimletFile;
import com.zimbra.cs.zimlet.ZimletUtil;
import com.zimbra.cs.zimlet.ZimletUtil.DeployListener;
import com.zimbra.cs.zimlet.ZimletUtil.ZimletSoapUtil;
import com.zimbra.soap.ZimbraSoapContext;

public class DeployZimlet extends AdminDocumentHandler {

	public static final String sPENDING = "pending";
	public static final String sSUCCEEDED = "succeeded";
	public static final String sFAILED = "failed";

	private Map<String, Progress> mProgressMap;

	private static class Progress implements DeployListener {
	    private static class Status {
	        String value;
	        Exception error;
	    }
		private Map<String,Status> mStatus;

		public Progress(boolean allServers) throws ServiceException {
			mStatus = new HashMap<String,Status>();
			Provisioning prov = Provisioning.getInstance();
			if (!allServers) {
				changeStatus(prov.getLocalServer().getName(), sPENDING);
				return;
			}
			for (Server s : prov.getAllServers()) {
			    changeStatus(s.getName(), sPENDING);
            }
		}
		@Override
        public void markFinished(Server s) {
			changeStatus(s.getName(), sSUCCEEDED);
		}
		@Override
        public void markFailed(Server s, Exception e) {
			changeStatus(s.getName(), sFAILED);
			mStatus.get(s.getName()).error = e;
		}
		public void changeStatus(String name, String status) {
		    Status s = mStatus.get(name);
		    if (s == null) {
                s = new Status();
                mStatus.put(name, s);
		    }
		    s.value = status;
		}
		public void writeResponse(Element resp) {
			for (Map.Entry<String, Status> entry : mStatus.entrySet()) {
				Element progress = resp.addElement(AdminConstants.E_PROGRESS);
				progress.addAttribute(AdminConstants.A_SERVER, entry.getKey());
				progress.addAttribute(AdminConstants.A_STATUS, entry.getValue().value);
				Exception e = entry.getValue().error;
				if (e != null) {
	                progress.addAttribute(AdminConstants.A_ERROR, e.getMessage());
				}
			}
		}
	}

	private static class DeployThread implements Runnable {
	    final Server server;
		Upload upload;
		Progress progress;
		ZAuthToken auth;
		boolean flushCache;
		boolean isLocal = true;

		public DeployThread(Server server, Upload up, Progress pr, ZAuthToken au, boolean flush) {
		    this.server = server;
			upload = up;
			progress = pr;
			if (au != null) {
			    auth = au;
			    isLocal = false;
			}
			flushCache = flush;
		}

		@Override
        public void run() {
			try {
                ZimletFile zf = new ZimletFile(upload.getName(), upload.getInputStream());
                if (isLocal) {
                    ZimletUtil.deployZimletLocally(zf, progress);
                } else {
                    byte[] data = zf.toByteArray();
                    ZimletSoapUtil soapUtil = new ZimletSoapUtil(auth);
                    soapUtil.deployZimletRemotely(server, zf.getName(), data, progress, flushCache);
				}
			} catch (Exception e) {
                if (server != null) {
                    ZimbraLog.zimlet.warn("failed to deploy zimlet on node %s", server.getName(), e);
                } else {
                    ZimbraLog.zimlet.warn("failed to get local server", e);
                }
                if (server != null && progress != null) {
					progress.markFailed(server, e);
                }
			}
		}
	}

	public DeployZimlet() {
		// keep past 20 zimlet deployment progresses
		mProgressMap = MapUtil.newLruMap(20);
	}

	private void deploy(ZimbraSoapContext lc, Server server, Upload upload, String aid, ZAuthToken auth,
	        boolean flushCache, boolean synchronous, CountDownLatch latch) throws ServiceException {
        Progress pr = new Progress((auth != null));
        mProgressMap.put(aid, pr);
        Runnable action = new DeployThread(server, upload, pr, auth, flushCache);
        Thread t = new Thread(action);
        t.start();
        if (!synchronous) {
            try {
                t.join(TimeUnit.MILLISECONDS.convert(LC.zimlet_deploy_timeout.intValue(), TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                ZimbraLog.zimlet.warn("error while deploying Zimlet", e);
            }
        }
        if (latch != null) {
            latch.countDown();
        }
	}

	@Override
	public Element handle(Element request, Map<String, Object> context) throws ServiceException {

	    ZimbraSoapContext zsc = getZimbraSoapContext(context);
		String action = request.getAttribute(AdminConstants.A_ACTION).toLowerCase();
		Element content = request.getElement(MailConstants.E_CONTENT);
		String aid = content.getAttribute(MailConstants.A_ATTACHMENT_ID, null);
		boolean flushCache = request.getAttributeBool(AdminConstants.A_FLUSH, false);
        boolean synchronous = request.getAttributeBool(AdminConstants.A_SYNCHRONOUS, false);
		if (action.equals(AdminConstants.A_STATUS)) {
			// just print the status
		} else if (action.equals(AdminConstants.A_DEPLOYALL)) {
		    List<Server> servers = Provisioning.getInstance().getAllServers();
	        Upload up = FileUploadServlet.fetchUpload(zsc.getAuthtokenAccountId(), aid, zsc.getAuthToken());
	        if (up == null) {
	            throw MailServiceException.NO_SUCH_UPLOAD(aid);
	        }
	        CountDownLatch latch = new CountDownLatch(servers.size());
	        ZimbraLog.zimlet.debug("countdown latch init: %d", latch.getCount());
            for (Server server : servers) {
                try {
                    checkRight(zsc, context, server, Admin.R_deployZimlet);
                    ZimbraLog.zimlet.debug("countdown latch: %d", latch.getCount());
                    if (server.isLocalServer()) {
                        deploy(zsc, server, up, aid, null, false, synchronous, latch);
                    } else {
                        ZimbraLog.zimlet.info("deploy on remote node %s", server.getName());
                        deploy(zsc, server, up, aid, zsc.getRawAuthToken(), flushCache, synchronous, latch);
                    }
                    if (flushCache) {
                        if (ZimbraLog.misc.isDebugEnabled()) {
                            ZimbraLog.misc.debug("DeployZimlet: flushing zimlet cache");
                        }
                        checkRight(zsc, context, Provisioning.getInstance().getLocalServer(), Admin.R_flushCache);
                        if (server.hasMailClientService()) {
                            FlushCache.flushAllZimlets(context);
                        } else {
                            WebClientServiceUtil.sendFlushZimletRequestToUiNode(server);
                        }
                    }
                } catch (ServiceException e) {
                    latch.countDown();
                    ZimbraLog.zimlet.warn("deploy zimlet failed for node %s, coutdown latch %d",
                            server.getName(), latch.getCount(), e);
                }
            }
            try {
                latch.await(LC.zimlet_deploy_timeout.intValue() * servers.size(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                ZimbraLog.zimlet.warn("CountDownLatch failed %d", latch.getCount(), e);
            }
            FileUploadServlet.deleteUpload(up);
		} else if (action.equals(AdminConstants.A_DEPLOYLOCAL)) {
		    Server localServer = Provisioning.getInstance().getLocalServer();
		    checkRight(zsc, context, localServer, Admin.R_deployZimlet);
            Upload up = FileUploadServlet.fetchUpload(zsc.getAuthtokenAccountId(), aid, zsc.getAuthToken());
            if (up == null) {
                throw MailServiceException.NO_SUCH_UPLOAD(aid);
            }
            try {
                deploy(zsc, localServer, up, aid, null, false, synchronous, null);
            } finally {
                FileUploadServlet.deleteUpload(up);
            }
            if (flushCache) {
				if (ZimbraLog.misc.isDebugEnabled()) {
					ZimbraLog.misc.debug("DeployZimlet: flushing zimlet cache");
				}
				checkRight(zsc, context, localServer, Admin.R_flushCache);
				if (localServer.hasMailClientService()) {
				    FlushCache.flushAllZimlets(context);
				} else {
				    WebClientServiceUtil.sendFlushZimletRequestToUiNode(localServer);
				}
			}
		} else {
			throw ServiceException.INVALID_REQUEST("invalid action "+action, null);
		}
		Element response = zsc.createElement(AdminConstants.DEPLOY_ZIMLET_RESPONSE);
		Progress progress = mProgressMap.get(aid);
		if (progress != null) {
            progress.writeResponse(response);
        }
		return response;
	}

	@Override
	public void docRights(List<AdminRight> relatedRights, List<String> notes) {
	    relatedRights.add(Admin.R_deployZimlet);

	    notes.add("If deploying on all servers, need the " + Admin.R_deployZimlet.getName() +
	            " right on all servers or on global grant.  If deploying on local server, need " +
	            "the " + Admin.R_deployZimlet.getName() + " on the local server.");
    }
}
