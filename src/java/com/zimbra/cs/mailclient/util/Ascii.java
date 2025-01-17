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
package com.zimbra.cs.mailclient.util;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Various utility methods for handling ASCII characters and strings.
 */
public final class Ascii {
    /**
     * Returns human readable representation of the specified byte.
     *
     * @param b the byte to be printed
     * @return the human readable string representation
     */
    public static String pp(byte b) {
        switch (b) {
        case '\0': return "\\0";
        case '\b': return "\\b";
        case '\r': return "\\r";
        case '\n': return "\\n";
        case '\t': return "\\t";
        case '\f': return "\\f";
        }
        return b >= 0x20 && b <= 0x7e ?
            String.valueOf((char) b) :
            "\\0x" + toHexChar((b >> 4) & 0xf) + toHexChar(b & 0xf);
    }

    public static char toHexChar(int digit) {
        switch (digit) {
        case 0: case 1: case 2: case 3: case 4:
        case 5: case 6: case 7: case 8: case 9:
            return (char)('0' + digit);
        case 10: case 11: case 12: case 13: case 14: case 15:
            return (char)('a' + digit - 10);
        default:
            throw new IllegalArgumentException(String.valueOf(digit));
        }
    }

    public static String toString(byte[] b) {
        char[] c = new char[b.length];
        for (int i = 0; i < b.length; i++) {
            c[i] = (char) (b[i] & 0xff);
        }
        return new String(c);
    }

    public static String toString(ByteBuffer bb) {
        bb = bb.slice();
        char[] c = new char[bb.remaining()];
        for (int i = 0; bb.hasRemaining(); i++) {
            c[i] = (char) (bb.get() & 0xff);
        }
        return new String(c);
    }

    public static byte[] getBytes(String s) {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            b[i] = (byte) s.charAt(i);
        }
        return b;
    }

    public static void write(OutputStream os, String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            os.write(s.charAt(i));
        }
    }
}
