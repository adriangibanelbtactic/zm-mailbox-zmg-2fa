/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2008, 2009, 2010, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.dav.client;

import org.dom4j.Element;
import org.dom4j.QName;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.dav.DavElements;

public class DavObject {
	
	public DavObject(Element objElem) {
		mProps = new HashMap<Integer,Element>();
		if (!objElem.getQName().equals(DavElements.E_RESPONSE))
			return;
		Element href = objElem.element(DavElements.E_HREF);
		if (href != null)
			mHref = href.getText();
		for (Object obj : objElem.elements(DavElements.E_PROPSTAT)) {
			Element propStat = (Element)obj;
			Integer status = null;
			Element statusElem = propStat.element(DavElements.E_STATUS);
			if (statusElem != null)
				status = parseStatusCode(statusElem.getText());
			mProps.put(status, propStat.element(DavElements.E_PROP));
		}
	}
	
	public String getHref() {
		return mHref;
	}
	
	private int parseStatusCode(String str) {
		int status = 400;
		String[] tokens = str.split(" ");
		try {
			if (tokens.length > 2)
				status = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			ZimbraLog.dav.warn("can't parse status code: "+str, e);
		}
		return status;
	}

	public Element getProperty(QName prop) {
		Element p = mProps.get(200);
		if (p == null)
			return null;
		return p.element(prop);
	}
	
	public String getPropertyText(QName prop) {
		Element e = getProperty(prop);
		if (e != null)
			return e.getText();
		return null;
	}
	
	public long getPropertyLong(QName prop) {
		String val = getPropertyText(prop);
		if (val != null) {
			try {
				return Long.parseLong(val);
			} catch (NumberFormatException e) {
				
			}
		}
		return -1;
	}
	
	public Collection<Element> getProperties(int code) {
		Element prop = mProps.get(code);
		if (prop == null)
			return Collections.emptyList();
		@SuppressWarnings("unchecked")
		Collection<Element> ret = prop.elements();
		return ret;
	}
	
	public boolean isResourceType(QName prop) {
		Element rtype = getProperty(DavElements.E_RESOURCETYPE);
		if (rtype == null)
			return false;
		return rtype.element(prop) != null;
	}
	
	public boolean isFolder() {
		return isResourceType(DavElements.E_COLLECTION);
	}
	
	public boolean isCalendarFolder() {
		return isResourceType(DavElements.E_CALENDAR);
	}
	
	public String getDisplayName() {
		return getPropertyText(DavElements.E_DISPLAYNAME);
	}

	public String getEtag() {
		return getPropertyText(DavElements.E_GETETAG);
	}
	
	public long getContentLength() {
		return getPropertyLong(DavElements.E_GETCONTENTLENGTH);
	}
	
	public String getContentLanguage() {
		return getPropertyText(DavElements.E_GETCONTENTLANGUAGE);
	}
	
	public String getContentType() {
		return getPropertyText(DavElements.E_GETCONTENTTYPE);
	}
	
	/* iso-8601 date YYYY-MM-DD'T'HH:MM:SS+ZZ */
	public String getCreationDate() {
		return getPropertyText(DavElements.E_GETCREATIONDATE);
	}
	
	/* rfc 2608 HTTP-Date */
	public String getLastModifiedDate() {
		return getPropertyText(DavElements.E_GETLASTMODIFIED);
	}
	
	private String mHref;
	private HashMap<Integer,Element> mProps;
}
