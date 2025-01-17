/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2005, 2006, 2007, 2009, 2010, 2013, 2014 Zimbra, Inc.
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

/*
 * Created on Jan 10, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.zimbra.cs.account;

import com.zimbra.common.service.ServiceException;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * @author schemers
 */
public class PreAuthKey {
    
    public static final int KEY_SIZE_BYTES = 32;

    public static String generateRandomPreAuthKey() throws ServiceException {
        try {
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            byte[] key = new byte[KEY_SIZE_BYTES];
            random.nextBytes(key);
            return new String(Hex.encodeHex(key));
        } catch (NoSuchAlgorithmException e) {
            throw ServiceException.FAILURE("unable to initialize SecureRandom", e);
        }
    }
    
    public static  String computePreAuth(Map<String,String> params, String key) {
        TreeSet<String> names = new TreeSet<String>(params.keySet());
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (sb.length() > 0) sb.append('|');
            sb.append(params.get(name));
        }
        return getHmac(sb.toString(), key.getBytes());
    }

    private static String getHmac(String data, byte[] key) {
        try {
            ByteKey bk = new ByteKey(key);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(bk);
            return new String(Hex.encodeHex(mac.doFinal(data.getBytes())));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("fatal error", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException("fatal error", e);
        }
    }
    
    static class ByteKey implements SecretKey {
        private byte[] mKey;
        
        ByteKey(byte[] key) {
            mKey = key.clone();
        }
        
        public byte[] getEncoded() {
            return mKey;
        }

        public String getAlgorithm() {
            return "HmacSHA1";
        }

        public String getFormat() {
            return "RAW";
        }       

    }
    
    public static void main(String args[]) throws ServiceException {
        long now = System.currentTimeMillis();
        HashMap<String,String> params = new HashMap<String,String>();
        params.put("account", "user1");
        params.put("by", "name");
        params.put("timestamp", "1176399950434");
        params.put("expires", "0");
        String key = "9d8ad87fd726ba7d5fecf3d705621024b31cedb142310ec965f9263568fa0f27";
        System.out.printf("key=%s preAuth=%s\n", key, computePreAuth(params, key));
    }
}
