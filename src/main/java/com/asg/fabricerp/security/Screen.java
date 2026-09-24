package com.asg.fabricerp.security;

/**
 * Every screen a role grant can name — the asgdynamic equivalent of asfl-erp's admin module's
 * {@code screenCode}, code-defined rather than DB-driven (there is no menu table here; see
 * {@code Role}'s javadoc). One value per document-type controller plus the two setup/admin
 * screens that aren't a {@link com.asg.fabricerp.global.documents.DocumentType}.
 */
public enum Screen {
    BOOKING,
    BPO,
    RPI,
    WWO,
    PWO,
    GR,
    DO,
    FD,
    FABRIC_SETUP,
    SECURITY_ADMIN
}
