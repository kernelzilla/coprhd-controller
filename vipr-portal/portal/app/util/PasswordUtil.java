/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import java.security.SecureRandom;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.google.common.util.concurrent.ExecutionError;

import play.Logger;
import play.libs.Crypto;
import plugin.StorageOsPlugin;

public class PasswordUtil {
    /** Identifier for SHA-512 password. */
    private static final String CRYPT_SHA_512 = "$6$";
    /** The length of the SHA-512 hash generated by Crypt. */
    private static final int SHA_512_HASH_LENGTH = 106;
    /** Prefix for encrypted values. */
    private static String ENC_PREFIX = "${enc:";
    /** Suffix for encrypted values. */
    private static String ENC_SUFFIX = "}";

    /**
     * Encrypts a password for a local user.
     * 
     * @param password
     *        the clear text password.
     * @return the encrypted password.
     */
    public static String generateHash(String password) {
        String salt = generateSalt();
        return Crypt.crypt(password, CRYPT_SHA_512 + salt);
    }

    /**
     * Determines if the string is a hash generated by the {@link #generateHash(String)} method.
     * 
     * @param str
     *        the string to test.
     * @return true if the string is a hash.
     */
    public static boolean isHash(String str) {
        return StringUtils.startsWith(str, CRYPT_SHA_512) && (StringUtils.length(str) == SHA_512_HASH_LENGTH);
    }

    public static boolean isValid(String value) {
        if (StringUtils.isNotBlank(value)) {
            // Check to ensure if there is an encrypted value it can be decrypted
            String decrypted = decryptedValue(value);
            return StringUtils.isNotBlank(decrypted);
        }
        return true;
    }

    public static boolean isNotValid(String value) {
        return !isValid(value);
    }
    /**
     * Validates a password using the ViPR api call. 
     * 
     * @param plaintext password to validate
     * @return If validation passes, returns an empty string.  If validation fails, returns the validation error message.  
     */
    public static String validatePassword(String value) {
    	ViPRSystemClient client = BourneUtil.getSysClient();
    	try {
    		String decrypted = decryptedValue(value);
    		client.password().validate(decrypted);
    	}
    	catch (ServiceErrorException e) {
    		if (e.getHttpCode() == 400 && e.getServiceError() != null) {
    			return e.getServiceError().getDetailedMessage();
    		}
    	}	
    	catch (Exception e) {
    		Logger.error(e, "Error executing api call to validate password");
    		return MessagesUtils.get("setup.password.notValid");
    	}
    	return StringUtils.EMPTY;
    }

    /**
     * Validates a password using the ViPR api call during self update. 
     * 
     * @param plaintext password to validate
     * @return If validation passes, returns an empty string.  If validation fails, returns the validation error message.  
     */
    public static String validatePasswordforUpdate(String oldPassword,String newPassword) {
    	
    	ViPRSystemClient client = BourneUtil.getSysClient();
    	try {
    		oldPassword = decryptedValue(oldPassword);
    		newPassword = decryptedValue(newPassword);
    		client.password().validateUpdate(oldPassword,newPassword);
    	}
    	catch (ServiceErrorException e) {
    		if (e.getHttpCode() == 400 && e.getServiceError() != null) {
    			return e.getServiceError().getDetailedMessage();
    		}
    	}	
    	catch (Exception e) {
    		Logger.error(e, "Error executing api call to validate password");
    		return MessagesUtils.get("setup.password.notValid");
    	}
    	return StringUtils.EMPTY;
    }

    public static String encryptedValue(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        else if (isEncrypted(value)) {
            return value;
        }
        else {
            return ENC_PREFIX + encrypt(value) + ENC_SUFFIX;
        }
    }

    public static String decryptedValue(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        else if (isEncrypted(value)) {
            String data = StringUtils.substring(value, ENC_PREFIX.length(), value.length() - ENC_SUFFIX.length());
            return decrypt(data);
        }
        else {
            return value;
        }
    }

    private static boolean isEncrypted(String value) {
        return StringUtils.startsWith(value, ENC_PREFIX) && StringUtils.endsWith(value, ENC_SUFFIX);
    }

    public static String encrypt(String value) {
        if (StringUtils.isNotBlank(value)) {
            EncryptionProvider encryption = StorageOsPlugin.getInstance().getEncryptionProvider();
            if (encryption != null) {
                try {
                    return Base64.encodeBase64String(encryption.encrypt(value));
                }
                catch (RuntimeException e) {
                    Logger.error("Faild to encrypt value", e);
                    return null;
                }
            }
        }
        return value;
    }

    public static String decrypt(String value) {
        if (StringUtils.isNotBlank(value)) {
            EncryptionProvider encryption = StorageOsPlugin.getInstance().getEncryptionProvider();
            if (encryption != null) {
                try {
                    return encryption.decrypt(Base64.decodeBase64(value));
                }
                catch (RuntimeException e) {
                    Logger.error("Failed to decrypt value: %s", e.getMessage());
                    return null;
                }
            }
        }
        return value;
    }

    /**
     * Generate a random salt
     */
    private static String generateSalt() {
        // number of Base64 characters for salt is dependent on the number of salt bytes
        final int SALT_LENGTH = 16;

        // valid chars as part of salt acceptable by commons-codec
        final String SALT_BASE_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ./";
        // create the salt of random bytes
        SecureRandom random = new SecureRandom();

        StringBuilder salt = new StringBuilder(SALT_LENGTH);
        for (int i = 0; i < SALT_LENGTH; i++) {
            salt.append(SALT_BASE_CHARS.charAt(random.nextInt(SALT_BASE_CHARS.length())));
        }
        return salt.toString();
    }

}