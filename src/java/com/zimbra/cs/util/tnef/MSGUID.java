/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2010, 2013, 2014 Zimbra, Inc.
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

package com.zimbra.cs.util.tnef;

import net.freeutils.tnef.GUID;

/**
 * The <code>MSGUID</code> class is a wrapper round the jtnef GUID class
 * intended to address the difference between the string representation
 * of a GUID used in Microsoft literature like MS-OXPROPS and the
 * canonized GUID representation used in jtnef 1.6
 * Information gleaned from :
 * http://en.wikipedia.org/wiki/Globally_Unique_Identifier
 * 
 * GUIDs are 16 byte numbers split up as follows :
 *     1st 4 bytes  - native 32-bit
 *     2nd 2 bytes  - native 16-bit
 *     3rd 2 bytes  - native 16-bit
 *     4th 8 bytes  - big-endian 64-bit
 *
 * The native byte order used in TNEF is little-endian order.
 * The string representation uses hex digits, separating each component
 * with '-' with an additional '-' after the first 2 bytes in the 4th
 * component.
 * The canonized jtnef String representation reflects the underlying byte sequence directly.
 * The MS-OXPROPS form String representation reflects the underlying little-endian data and
 * typically encloses the whole string in curly brackets.
 * e.g. for PSETID_Meeting :
 *     MS-OXPROPS representation {6ED8DA90-450B-101B-98DA-00AA003F1305}
 *     jtnef representation       90dad86e-0b45-1b10-98da-00aa003f1305
 * 
 * @author Gren Elliot
 *
 */
public class MSGUID {

    public final static MSGUID PSETID_Meeting     = new MSGUID("{6ED8DA90-450B-101B-98DA-00AA003F1305}");
    public final static MSGUID PSETID_Appointment = new MSGUID("{00062002-0000-0000-C000-000000000046}");
    public final static MSGUID PSETID_Task        = new MSGUID("{00062003-0000-0000-C000-000000000046}");
    public final static MSGUID PSETID_Address     = new MSGUID("{00062004-0000-0000-C000-000000000046}");
    public final static MSGUID PSETID_Common      = new MSGUID("{00062008-0000-0000-C000-000000000046}");
    public final static MSGUID PS_PUBLIC_STRINGS  = new MSGUID("{00020329-0000-0000-C000-000000000046}");

	private GUID jtnefGuid;
	/**
	 * @param guid in MS-OXPROPS form, NOT jtnef 1.6 canonized form.
	 */
	public MSGUID(String guid) {
		// re-use GUID checking code before re-ordering.
		if (guid.length() == 38) {
			// Assume is form encapsulated by '{' and '}'
			setJtnefGuid(new GUID(guid.substring(1,37)));
		} else {
			setJtnefGuid(new GUID(guid));
		}
		byte[] msVisualOrder = getJtnefGuid().toByteArray();
		byte[] jtnefOrder = new byte[16];
		jtnefOrder[0] = msVisualOrder[3];
		jtnefOrder[1] = msVisualOrder[2];
		jtnefOrder[2] = msVisualOrder[1];
		jtnefOrder[3] = msVisualOrder[0];
		
		jtnefOrder[4] = msVisualOrder[5];
		jtnefOrder[5] = msVisualOrder[4];
		
		jtnefOrder[6] = msVisualOrder[7];
		jtnefOrder[7] = msVisualOrder[6];
		for (int i = 8; i < msVisualOrder.length; i++) {
			jtnefOrder[i] = msVisualOrder[i];
		}
		setJtnefGuid(new GUID(jtnefOrder));
	}

	/**
	 * @param guid
	 */
	public MSGUID(byte[] guid) {
		setJtnefGuid(new GUID(guid));
	}

	/**
	 * @param guid
	 */
	public MSGUID(GUID guid) {
		setJtnefGuid(guid);
	}

	public GUID getJtnefGuid() {
		return jtnefGuid;
	}


    /**
     * Returns an MS-OXPROPS style string representation of this object.
     *
     * @return a string representation of this object
     */
	public String toString() {
		StringBuffer s = new StringBuffer(39);
		byte[] guidArray = getJtnefGuid().toByteArray();
		s.append('{');
		appendHex(s, guidArray[3]);
		appendHex(s, guidArray[2]);
		appendHex(s, guidArray[1]);
		appendHex(s, guidArray[0]);
		s.append('-');
		appendHex(s, guidArray[5]);
		appendHex(s, guidArray[4]);
		s.append('-');
		appendHex(s, guidArray[7]);
		appendHex(s, guidArray[6]);
		s.append('-');
		for (int i = 8; i < 10; i++) {
			appendHex(s,guidArray[i]);
		}
		s.append('-');
		for (int i = 10; i < guidArray.length; i++) {
			appendHex(s,guidArray[i]);
		}
		s.append('}');
		return s.toString();
    }

	private void appendHex(StringBuffer s, byte myBight) {
		String b = Integer.toHexString(myBight & 0xFF);
		if (b.length() == 1) {
			s.append('0');
		}
		s.append(b);
	}
	
	private void setJtnefGuid(GUID jtnefGuid) {
		this.jtnefGuid = jtnefGuid;
	}

}
