package com.bsg.ai.services.util;

import lombok.SneakyThrows;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Currently unused.
 */
public class ResourceHasher {

    public static final String SHA256 = "SHA-256";
    public static final String MD5 = "MD5";

    /**
     * Generate a hash with the given algorithm.
     */
    @SneakyThrows
    public static String hash(Resource resource, String algorithm) {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream is = resource.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest.digest()) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    @SneakyThrows
    public static String hash(String resource, String algorithm) {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        digest.update(resource.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest.digest()) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
