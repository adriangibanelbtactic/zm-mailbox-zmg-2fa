/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2008, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.util.yauth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.zimbra.common.httpclient.HttpClientUtil;
import com.zimbra.common.localconfig.LC;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.common.util.Constants;

/**
 * Implementation of Yahoo "Raw Auth" aka "Token Login v2"
 * See http://twiki.corp.yahoo.com/view/Membership/OpenTokenLogin
 */
public class RawAuth implements Auth {
    private final String appId;
    private String cookie;
    private String wssId;
    private long expiration;

    private static final Logger LOG = Logger.getLogger(RawAuth.class);

    private static final boolean DEBUG = false;

    static {
        if (DEBUG) {
            LOG.setLevel(Level.DEBUG);
            System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
            System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
            System.setProperty("org.apache.commons.logging.simplelog.log.httpclient.wire.header", "debug");
            System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.commons.httpclient", "debug");
        }
    }

    private static final String GET_AUTH_TOKEN = "get_auth_token";
    private static final String GET_AUTH = "get_auth";

    // Auth request parameters
    private static final String LOGIN = "login";
    private static final String PASSWD = "passwd";
    private static final String APPID = "appid";
    private static final String TOKEN = "token";

    // Auth response fields
    private static final String AUTH_TOKEN = "AuthToken";
    private static final String COOKIE = "Cookie";
    private static final String WSSID = "WSSID";
    private static final String EXPIRATION = "Expiration";
    private static final String ERROR = "Error";
    private static final String ERROR_DESCRIPTION = "ErrorDescription";
    private static final String CAPTCHA_URL = "CaptchaUrl";
    private static final String CAPTCHA_DATA = "CaptchaData";

    // Maximum number of milliseconds between current time and expiration
    // time before cookie is considered no longer valid.
    private static final long EXPIRATION_LIMIT = 60 * 1000; // 1 minute

    public static String getToken(String appId, String user, String pass)
        throws AuthenticationException, IOException {
        debug("Sending getToken request: appId = %s, user = %s", appId, user);
        Response res = doGet(GET_AUTH_TOKEN,
            new NameValuePair(APPID, appId),
            new NameValuePair(LOGIN, user),
            new NameValuePair(PASSWD, pass));
        String token = res.getRequiredField(AUTH_TOKEN);
        debug("Got getToken response: token = %s", token);
        return token;
    }

    public static RawAuth authenticate(String appId, String token)
        throws AuthenticationException, IOException {
        debug("Sending authenticate request: appId = %s, token = %s", appId, token);
        RawAuth auth = new RawAuth(appId);
        auth.authenticate(token);
        debug("Got authenticate response: %s", auth);
        return auth;
    }

    private RawAuth(String appId) {
        this.appId = appId;
    }

    @Override
    public String getAppId() {
        return appId;
    }

    @Override
    public String getCookie() {
        return cookie;
    }

    @Override
    public String getWSSID() {
        return wssId;
    }

    @Override
    public boolean isExpired() {
        return System.currentTimeMillis() + EXPIRATION_LIMIT > expiration;
    }

    private void authenticate(String token)
        throws AuthenticationException, IOException {
        Response res = doGet(GET_AUTH, new NameValuePair(APPID, appId),
                                       new NameValuePair(TOKEN, token));
        cookie = res.getRequiredField(COOKIE);
        wssId = res.getRequiredField(WSSID);
        String s = res.getRequiredField(EXPIRATION);
        try {
            expiration = System.currentTimeMillis() + Long.parseLong(s) * Constants.MILLIS_PER_SECOND;
        } catch (NumberFormatException e) {
            throw new IOException(
                "Invalid integer value for field '" + EXPIRATION + "': " + s);
        }
    }

    private static Response doGet(String action, NameValuePair... params)
        throws AuthenticationException, IOException {
        String uri = LC.yauth_baseuri.value() + '/' + action;
        GetMethod method = new GetMethod(uri);
        method.setQueryString(params);
        int rc = HttpClientUtil.executeMethod(method);
        Response res = new Response(method);
        String error = res.getField(ERROR);
        // Request can sometimes fail even with a 200 status code, so always
        // check for "Error" attribute in response.
        if (rc == 200 && error == null) {
            return res;
        }
        if (rc == 999) {
            // Yahoo service temporarily unavailable (error code text not included)
            throw new AuthenticationException(
                ErrorCode.TEMP_ERROR, "Unable to process request at this time");
        }
        ErrorCode code = error != null ?
            ErrorCode.get(error) : ErrorCode.GENERIC_ERROR;
        String description = res.getField(ERROR_DESCRIPTION);
        if (description == null) {
            description = code.getDescription();
        }
        AuthenticationException e = new AuthenticationException(code, description);
        e.setCaptchaUrl(res.getField(CAPTCHA_URL));
        e.setCaptchaData(res.getField(CAPTCHA_DATA));
        throw e;
    }

    private static class Response {
        final Map<String, String> attributes;

        Response(GetMethod method) throws IOException {
            debug("Response status: %s", method.getStatusLine());
            attributes = new HashMap<String, String>();
            InputStream is = null;
            try {
                is = method.getResponseBodyAsStream();
	            BufferedReader br = new BufferedReader(
	                new InputStreamReader(is, method.getResponseCharSet()));
	            String line;
	            while ((line = br.readLine()) != null) {
	                debug("Response line: %s", line);
	                int i = line.indexOf('=');
	                if (i != -1) {
	                    String name = line.substring(0, i);
	                    String value = line.substring(i + 1);
	                    attributes.put(name.toLowerCase(), value);
	                }
	            }
            } finally {
                ByteUtil.closeStream(is);
            }
        }

        String getRequiredField(String name) throws IOException {
            String value = getField(name);
            if (value == null) {
                throw new IOException("Response missing required '" + name + "' field");
            }
            return value;
        }

        String getField(String name) {
            String s = attributes.get(name.toLowerCase());
            if (s != null) {
                s = s.trim();
                if (s.length() > 0) {
                    return s;
                }
            }
            return null;
        }
    }

    @Override
    public String toString() {
        if (DEBUG) {
            return String.format("{appid=%s,cookie=%s,wssId=%s,expiration=%d}",
                                 appId, cookie, wssId, expiration);
        } else {
            return super.toString();
        }
    }

    private static void debug(String fmt, Object... args) {
        LOG.debug(String.format(fmt, args));
    }
}
