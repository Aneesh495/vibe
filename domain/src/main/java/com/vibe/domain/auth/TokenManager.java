package com.vibe.domain.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Generates and validates cryptographically signed session tokens using HMAC-SHA256.
 */
public final class TokenManager {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secretKey;

    public TokenManager(byte[] secretKey) {
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey must not be null");
        if (secretKey.length < 32) {
            throw new IllegalArgumentException("Secret key must be at least 32 bytes");
        }
    }

    public static TokenManager createRandom() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new TokenManager(key);
    }

    /**
     * Issues a signed token bound to userId, deviceId, and expiration time.
     */
    public String generateToken(String userId, String deviceId, long ttlMs) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        long expiresAt = System.currentTimeMillis() + ttlMs;

        String payload = userId + ":" + deviceId + ":" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(encodedPayload);

        return encodedPayload + "." + signature;
    }

    /**
     * Validates token format, cryptographic signature, and expiry.
     */
    public TokenValidationResult validateToken(String token) {
        if (token == null || !token.contains(".")) {
            return TokenValidationResult.invalid("Malformed token format");
        }

        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            return TokenValidationResult.invalid("Invalid token structure");
        }

        String encodedPayload = parts[0];
        String expectedSignature = parts[1];

        String actualSignature = sign(encodedPayload);
        if (!constantTimeEquals(expectedSignature, actualSignature)) {
            return TokenValidationResult.invalid("Signature mismatch");
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] segments = payload.split(":", 3);
            if (segments.length != 3) {
                return TokenValidationResult.invalid("Invalid payload segments");
            }

            String userId = segments[0];
            String deviceId = segments[1];
            long expiresAt = Long.parseLong(segments[2]);

            if (System.currentTimeMillis() > expiresAt) {
                return TokenValidationResult.expired(userId, deviceId, expiresAt);
            }

            return TokenValidationResult.valid(userId, deviceId, expiresAt);
        } catch (Exception e) {
            return TokenValidationResult.invalid("Failed to decode token payload: " + e.getMessage());
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    public record TokenValidationResult(
            boolean isValid,
            boolean isExpired,
            String userId,
            String deviceId,
            long expiresAt,
            String error
    ) {
        public static TokenValidationResult valid(String userId, String deviceId, long expiresAt) {
            return new TokenValidationResult(true, false, userId, deviceId, expiresAt, null);
        }

        public static TokenValidationResult expired(String userId, String deviceId, long expiresAt) {
            return new TokenValidationResult(false, true, userId, deviceId, expiresAt, "Token has expired");
        }

        public static TokenValidationResult invalid(String error) {
            return new TokenValidationResult(false, false, null, null, 0L, error);
        }
    }
}
