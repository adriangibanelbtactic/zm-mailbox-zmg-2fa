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
package com.zimbra.cs.mailclient.pop3;

import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailclient.MailConfig;
import com.zimbra.cs.mailclient.util.Config;

import java.util.Properties;
import java.io.File;
import java.io.IOException;

/**
 * Represents POP3 mail client configuration.
 */
public class Pop3Config extends MailConfig {
    /** POP3 configuration protocol name */
    public static final String PROTOCOL = "pop3";

    /** Default port for POP3 plain text connection */
    public static final int DEFAULT_PORT = 110;

    /** Default port for POP3 SSL connection */
    public static final int DEFAULT_SSL_PORT = 995;

    /**
     * Loads POP3 configuration properties from the specified file.
     *
     * @param file the configuration properties file
     * @return the <tt>Pop3Config</tt> for the properties
     * @throws IOException if an I/O error occurred
     */
    public static Pop3Config load(File file) throws IOException {
        Properties props = Config.loadProperties(file);
        Pop3Config config = new Pop3Config();
        config.applyProperties(props);
        return config;
    }

    /**
     * Creates a new {@link Pop3Config}.
     */
    public Pop3Config() {
        super(ZimbraLog.pop_client);
    }

    /**
     * Creates a new {@link Pop3Config} for the specified server host.
     *
     * @param host the server host name
     */
    public Pop3Config(String host) {
        super(ZimbraLog.pop_client, host);
        setLogger(ZimbraLog.pop_client);
    }

    /**
     * Returns the POP3 protocol name (value of {@link #PROTOCOL}).
     *
     * @return the POP3 protocol name
     */
    @Override
    public String getProtocol() {
        return PROTOCOL;
    }

    /**
     * Returns the POP3 server port number. If not set, the default is
     * {@link #DEFAULT_PORT} for a plain text connection and
     * {@link #DEFAULT_SSL_PORT} for an SSL connection.
     *
     * @return the POP3 server port number
     */
    @Override
    public int getPort() {
        int port = super.getPort();
        if (port != -1) return port;
        return getSecurity() == Security.SSL ? DEFAULT_SSL_PORT : DEFAULT_PORT;
    }
}
