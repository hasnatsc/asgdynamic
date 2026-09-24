package com.asg.fabricerp.security;

/**
 * The five separately-grantable verbs, ported from asfl-erp's admin module's
 * {@code AdminFlags.Verb}. {@code CREATE} and {@code AMEND} are deliberately distinct: many
 * staff may raise a document but must not change one after submission, and a combined "maker"
 * flag (asgdynamic's old model) cannot express that.
 */
public enum Verb {
    VIEW,
    CREATE,
    AMEND,
    DELETE,
    APPROVE
}
