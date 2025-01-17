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
package com.zimbra.cs.mailclient.imap;

import com.zimbra.cs.mailclient.ParseException;
import com.zimbra.cs.mailclient.util.Ascii;

import java.io.IOException;

/**
 * IMAP mailbox LIST response:
 *
 * mailbox-list    = "(" [mbx-list-flags] ")" SP
 *                 (DQUOTE QUOTED-CHAR DQUOTE / nil) SP mailbox
 *
 * mbx-list-flags  = *(mbx-list-oflag SP) mbx-list-sflag
 *                 *(SP mbx-list-oflag) /
 *                 mbx-list-oflag *(SP mbx-list-oflag)
 *
 * mbx-list-oflag  = "\Noinferiors" / flag-extension
 *                   ; Other flags; multiple possible per LIST response
 *
 * mbx-list-sflag  = "\Noselect" / "\Marked" / "\Unmarked"
 *                   ; Selectability flags; only one per LIST response
 */
public final class ListData {
    private String mailbox;
    private char delimiter;
    private Flags flags;

    public static ListData read(ImapInputStream is) throws IOException {
        ListData mb = new ListData();
        mb.readMailboxList(is);
        return mb;
    }

    private ListData() {}

    public ListData(String mailbox, char delimiter) {
        this.mailbox = mailbox;
        this.delimiter = delimiter;
        flags = new Flags();
    }
    
    private void readMailboxList(ImapInputStream is) throws IOException {
        is.skipSpaces();
        // bug 42163: LIST response from Cisco IMAP server omits flags if empty
        if (is.peek() == '(') {
            flags = readFlags(is);
            is.skipChar(' ');
        } else {
            flags = new Flags();
        }
        String delim = is.readNString();
        if (delim != null) {
            if (delim.length() != 1) {
                throw new ParseException(
                    "Invalid delimiter specification in LIST data: " + delim);
            }
            delimiter = delim.charAt(0);
        }
        is.skipChar(' ');
        is.skipSpaces();
        String s = is.peekChar() == '"' ? readQuoted(is) : is.readAString();
        //bug 52019
        //if we did a list-extended there should be a space here; not ( or )
        if (is.peek() == '(' || is.peek() == ')') {
            s += is.readText("\r\n");
        }
        mailbox = MailboxName.decode(s).toString();
    }

    /*
     * Bug 37101: Workaround for Exchange IMAP which can return mailbox names
     * containing unescaped double quotes.
     */
    private static String readQuoted(ImapInputStream is) throws IOException {
        is.skipChar('"');
        StringBuilder sb = new StringBuilder();
        while (is.peekChar() != '\r') {
            char c = is.readChar();
            if (c == '\\') {
                c = is.readChar();
            }
            sb.append(c);
        }
        String s = sb.toString();
        if (!s.endsWith("\"")) {
            throw new ParseException(
                "Unexpected end of line while reading QUOTED string");
        }
        return s.substring(0, s.length() - 1);
    }
    
    private static Flags readFlags(ImapInputStream is) throws IOException {
        Flags flags = Flags.read(is);
        int count = 0;
        if (flags.isNoselect()) count++;
        if (flags.isMarked()) count++;
        if (flags.isUnmarked()) count++;
        if (count > 1) {
            throw new ParseException(
                "Invalid LIST flags - only one of \\Noselect, \\Marked, or" +
                 " \\Unmarked expected: " + flags);
        }
        return flags;
    }

    public Flags getFlags() { return flags; }
    public String getMailbox() { return mailbox; }
    public char getDelimiter() { return delimiter; }


    public String toString() {
        return String.format("{mailbox=%s, delimiter=%s, flags=%s}",
                             mailbox, Ascii.pp((byte) delimiter), flags);
    }
}
