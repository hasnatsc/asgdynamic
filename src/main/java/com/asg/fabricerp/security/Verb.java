package com.asg.fabricerp.security;

/**
 * The separately-grantable verbs, ported from asfl-erp's admin module's
 * {@code AdminFlags.Verb}. {@code CREATE} and {@code AMEND} are deliberately distinct: many
 * staff may raise a document but must not change one after submission, and a combined "maker"
 * flag (asgdynamic's old model) cannot express that.
 *
 * <p>{@code CHECK} is the middle signature of the Commercial family's maker → checker → approver
 * triad (the legacy {@code ROLE_PI_CHECKER}/{@code ROLE_LC_CHECKER}/{@code ROLE_CI_CHECKER}), and
 * only means something on a screen whose {@link Screen#supports(Verb)} says so.
 */
public enum Verb {
    VIEW,
    CREATE,
    AMEND,
    DELETE,
    CHECK,
    APPROVE
}
