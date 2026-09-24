package com.asg.fabricerp.security;

/**
 * The one rule enforced on a new password: length. Ported from asfl-erp's
 * {@code CredentialServiceImpl}.
 *
 * <p>Composition rules — a digit, a symbol, mixed case — push people toward {@code Password1!}
 * and are worse than length. Anything beyond this belongs in a breached-password check, which
 * needs a service this system does not have.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    /** BCrypt ignores everything past 72 bytes; accepting more would imply it counts. */
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() { }

    public static void requireAcceptable(String raw) {
        if (raw == null || raw.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                "A password must be at least " + MIN_LENGTH + " characters. Length beats "
                    + "composition rules, which mostly produce predictable substitutions.");
        }
        if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException(
                "A password may be at most " + MAX_BYTES + " bytes; anything longer is silently truncated.");
        }
    }
}
