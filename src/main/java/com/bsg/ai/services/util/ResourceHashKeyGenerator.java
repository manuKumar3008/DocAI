package com.bsg.ai.services.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Currently unused.
 */
@Component("resourceHashKeyGenerator")
@Slf4j
public class ResourceHashKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        Resource resource = (Resource) Arrays.stream(params).findFirst().orElseThrow();

        // Create a temp file and copy the resource bytes into it
        String originalName = resource.getFilename();
        String prefix = "res-";
        String suffix = ".tmp";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot > -1 && dot < originalName.length() - 1) {
                suffix = originalName.substring(dot);
            }
        }

        Path tempPath = null;
        try {
            tempPath = Files.createTempFile(prefix, suffix);
            try (InputStream is = resource.getInputStream()) {
                Files.copy(is, tempPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            File tempFile = tempPath.toFile();
            FileSystemResource fsr = new FileSystemResource(tempFile);

            String hash = ResourceHasher.hash(fsr, ResourceHasher.SHA256);
            log.debug("Generated hash from temp file: {} - {}", tempFile.getAbsolutePath(), hash);
            return hash;
        } catch (Exception ex) {
            log.warn("Failed to hash resource via temp file. Falling back to description-based key. cause={}", ex.toString());
            // Fallback (stable but less precise): use a surrogate derived from description/filename
            String surrogate = String.join("|",
                    "desc=" + (resource.getDescription() == null ? "" : resource.getDescription()),
                    "name=" + (resource.getFilename() == null ? "" : resource.getFilename())
            );
            // Hash the surrogate string to produce a compact key
            return Hashing.sha256Hex(surrogate);
        } finally {
            // Clean up temp file
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (Exception ignore) {
                    // As a last resort, mark for deletion on exit
                    try {
                        tempPath.toFile().deleteOnExit();
                    } catch (Exception ignored) {
                        // no-op
                    }
                }
            }
        }
    }

    // Minimal local helper to avoid extra dependencies; replace with your own if desired
    private static final class Hashing {
        static String sha256Hex(String input) {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(bytes.length * 2);
                for (byte b : bytes) sb.append(String.format("%02x", b));
                return sb.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
