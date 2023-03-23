package com.zimbra.cs.service.util;

import com.google.common.base.Strings;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Provisioning;
import org.apache.commons.codec.binary.Hex;

import javax.mail.MessagingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class SecretKey {

    public static final int KEY_SIZE_BYTES = 32;
    public static final String MSGVRFY_HEADER_PREFIX = "hash=SHA256;guid=";
    public static final String MSGVRFY_ALGORITHM_NAME = "SHA-256";

    /**
     * returns the randomly generated String
     * @return randomly generated String
     * @throws ServiceException if an error occurred
     */
    public static String generateRandomString() throws ServiceException {
        try {
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            byte[] key = new byte[KEY_SIZE_BYTES];
            random.nextBytes(key);
            return new String(Hex.encodeHex(key));
        } catch (NoSuchAlgorithmException e) {
            throw ServiceException.FAILURE("unable to initialize SecureRandom for mail recall", e);
        }
    }

    /**
     * provide the Hash for Message-Verification header field.
     * @param id
     * @param date
     * @param from
     * @throws ServiceException
     */
    public static String getMessageVerificationHeaderValue(String id, String date, String from) throws MessagingException, ServiceException {
        String secretKey = Provisioning.getInstance().getConfig().getSecretKeyForMailRecall();
        if(Strings.isNullOrEmpty(secretKey)) {
            secretKey = SecretKey.generateRandomString();
            Provisioning.getInstance().getConfig().setSecretKeyForMailRecall(secretKey);
        }
        String guid = (id + date + from + secretKey);
        String guidHash = getHashForMessageVerification(guid);
        String hash = MSGVRFY_HEADER_PREFIX + guidHash;
        return hash;
    }

    /**
     * Create a digest of the given input with the given algorithm.
     * @param input
     * @throws ServiceException
     */
    private static String getHashForMessageVerification(String input) throws ServiceException {
        try {
            MessageDigest md = MessageDigest.getInstance(MSGVRFY_ALGORITHM_NAME);
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            BigInteger number = new BigInteger(1, messageDigest);
            StringBuilder hexString = new StringBuilder(number.toString(16));

            while (hexString.length() < 64) {
                hexString.insert(0, '0');
            }

            String basicBase64format
                    = Base64.getEncoder()
                    .encodeToString(hexString.toString().getBytes());
            return basicBase64format;
        } catch (NoSuchAlgorithmException e) {
            throw ServiceException.FAILURE("Unable to encrypt", e);
        }
    }
}
