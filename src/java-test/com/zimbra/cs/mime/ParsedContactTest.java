/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011, 2013, 2014 Zimbra, Inc.
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
package com.zimbra.cs.mime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Strings;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.MailServiceException;

/**
 * Unit test for {@link ParsedContact}.
 *
 * @author ysasaki
 */
public final class ParsedContactTest {

    @Test
    public void tooBigField() throws Exception {
        try {
            new ParsedContact(Collections.singletonMap(Strings.repeat("k", 101), "v"));
            Assert.fail();
        } catch (ServiceException e) {
            Assert.assertEquals(ServiceException.INVALID_REQUEST, e.getCode());
        }

        try {
            new ParsedContact(Collections.singletonMap("k", Strings.repeat("v", 10000001)));
            Assert.fail();
        } catch (MailServiceException e) {
            Assert.assertEquals(MailServiceException.CONTACT_TOO_BIG, e.getCode());
        }

        Map<String, String> fields = new HashMap<String, String>();
        for (int i = 0; i < 1001; i++) {
           fields.put("k" + i, "v" + i);
        }
        try {
            new ParsedContact(fields);
            Assert.fail();
        } catch (ServiceException e) {
            Assert.assertEquals(ServiceException.INVALID_REQUEST, e.getCode());
        }

    }

}
