package com.vibe.domain.auth;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Objects;

/**
 * Robust password hashing and verification using BCrypt.
 */
public final class PasswordHasher {

    private final int logRounds;

    public PasswordHasher(int logRounds) {
        if (logRounds < 4 || logRounds > 31) {
            throw new IllegalArgumentException("logRounds must be between 4 and 31");
        }
        this.logRounds = logRounds;
    }

    public static PasswordHasher standard() {
        return new PasswordHasher(10);
    }

    public static String hashPassword(String plaintext) {
        return standard().hash(plaintext);
    }

    public static boolean verifyPassword(String plaintext, String storedHash) {
        return standard().verify(plaintext, storedHash);
    }

    public static PasswordHasher fastForTests() {
        return new PasswordHasher(4);
    }

    /**
     * Hashes a raw plaintext password using a freshly generated salt.
     */
    public String hash(String plaintextPassword) {
        Objects.requireNonNull(plaintextPassword, "plaintextPassword must not be null");
        String salt = BCrypt.gensalt(logRounds);
        return BCrypt.hashpw(plaintextPassword, salt);
    }

    /**
     * Verifies that a plaintext password matches a stored BCrypt hash.
     */
    public boolean verify(String plaintextPassword, String storedHash) {
        if (plaintextPassword == null || storedHash == null || !storedHash.startsWith("$2")) {
            return false;
        }
        try {
            return BCrypt.checkpw(plaintextPassword, storedHash);
        } catch (Exception e) {
            return false;
        }
    }
}
