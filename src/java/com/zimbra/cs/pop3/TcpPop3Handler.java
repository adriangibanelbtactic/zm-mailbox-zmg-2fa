/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2007, 2008, 2009, 2010, 2011, 2013, 2014 Zimbra, Inc.
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

package com.zimbra.cs.pop3;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import com.zimbra.common.io.TcpServerInputStream;
import com.zimbra.common.util.NetUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.server.ProtocolHandler;

final class TcpPop3Handler extends ProtocolHandler {
    private TcpServerInputStream input;
    private String remoteAddress;
    private final HandlerDelegate delegate;

    TcpPop3Handler(TcpPop3Server server) {
        super(server);
        delegate = new HandlerDelegate(server.getConfig());
    }

    @Override
    protected boolean setupConnection(Socket connection) throws IOException {
        remoteAddress = connection.getInetAddress().getHostAddress();
        input = new TcpServerInputStream(connection.getInputStream());
        delegate.output = new BufferedOutputStream(connection.getOutputStream());
        if (delegate.startConnection(connection.getInetAddress())) {
            return true;
        } else {
            dropConnection();
            return false;
        }
    }

    @Override
    protected boolean processCommand() throws IOException {
        setIdle(false);
        if (delegate.processCommand(input.readLine())) {
            return true;
        } else {
            dropConnection();
            return false;
        }
    }

    @Override
    protected void dropConnection() {
        ZimbraLog.addIpToContext(remoteAddress);
        try {
            if (input != null) {
                input.close();
                input = null;
            }
            if (delegate.output != null) {
                delegate.output.close();
                delegate.output = null;
            }
        } catch (IOException e) {
            if (ZimbraLog.pop.isDebugEnabled()) {
                ZimbraLog.pop.debug("I/O error while closing connection", e);
            } else {
                ZimbraLog.pop.debug("I/O error while closing connection: " + e);
            }
        } finally {
            ZimbraLog.clearContext();
        }
    }

    @Override
    protected boolean authenticate() throws IOException {
        // we auth with the USER/PASS commands
        return true;
    }

    @Override
    protected void notifyIdleConnection() {
        // according to RFC 1939 we aren't supposed to snd a response on idle timeout
        ZimbraLog.pop.debug("idle connection");
    }

    private class HandlerDelegate extends Pop3Handler {

        HandlerDelegate(Pop3Config config) {
            super(config);
        }

        @Override
        protected void startTLS() throws IOException {
            sendOK("Begin TLS negotiation");
            SSLSocketFactory fac = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sock = (SSLSocket) fac.createSocket(connection,
                    connection.getInetAddress().getHostName(), connection.getPort(), true);
            NetUtil.setSSLProtocols(sock, config.getMailboxdSslProtocols());
            NetUtil.setSSLEnabledCipherSuites(sock, config.getSslExcludedCiphers(), config.getSslIncludedCiphers());
            sock.setUseClientMode(false);
            startHandshake(sock);
            ZimbraLog.pop.debug("suite: %s", sock.getSession().getCipherSuite());
            input = new TcpServerInputStream(sock.getInputStream());
            output = new BufferedOutputStream(sock.getOutputStream());
        }

        @Override
        protected void completeAuthentication() throws IOException {
            setLoggingContext();
            authenticator.sendSuccess();
            if (authenticator.isEncryptionEnabled()) {
                // Switch to encrypted streams
                input = new TcpServerInputStream(authenticator.unwrap(connection.getInputStream()));
                output = authenticator.wrap(connection.getOutputStream());
            }
        }

        @Override
        InetSocketAddress getLocalAddress() {
            return (InetSocketAddress) connection.getLocalSocketAddress();
        }

        @Override
        void sendLine(String line, boolean flush) throws IOException {
            ZimbraLog.pop.trace("S: %s", line);
            output.write(line.getBytes());
            output.write(LINE_SEPARATOR);
            if (flush) {
                output.flush();
            }
        }

    }

}
