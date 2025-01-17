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
package com.zimbra.cs.service.formatter;

import java.io.IOException;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.service.UserServletContext;
import com.zimbra.cs.service.UserServletException;
import com.zimbra.cs.service.formatter.FormatterFactory.FormatType;

public class TarFormatter extends ArchiveFormatter {
    @Override
    public String[] getDefaultMimeTypes() {
        return new String[] { "application/x-tar" };
    }

    @Override
    public FormatType getType() {
        return FormatType.TAR;
     }

    @Override
    protected ArchiveInputStream getInputStream(UserServletContext context,
        String charset) throws IOException, ServiceException, UserServletException {

        return new TarArchiveInputStream(context.getRequestInputStream(-1),
            charset);
    }

    @Override
    protected ArchiveOutputStream getOutputStream(UserServletContext context, String
        charset) throws IOException {
        return new TarArchiveOutputStream(context.resp.getOutputStream(), charset);
    }
}
