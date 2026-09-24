package com.asg.fabricerp.global.documents;

import java.util.EnumSet;
import java.util.Set;

/**
 * Document lifecycle, enforced server-side.
 *
 * <p>asgdynamic drove this from the browser: {@code soChangeStatus()},
 * {@code poChangeStatus()}, {@code grmChangeStatus()} and friends POSTed a target status,
 * and nothing on the server said which transitions were legal. The table below is now the
 * only authority.
 *
 * <p>{@code PARTIAL} and {@code PROCESSING} are kept because the live SpindleERP data uses
 * them for real: requisitions sit PARTIAL while being progressively fulfilled, and import
 * PIs sit PROCESSING between issue and receipt.
 */
public enum BusinessDocumentStatus {

    DRAFT,
    /** Submitted by the maker, awaiting check/approval. */
    SUBMITTED,
    APPROVED,
    /** Downstream consumption has started but is incomplete (e.g. partially received SPR). */
    PARTIAL,
    /** In flight with a counterparty (e.g. an issued import PI). */
    PROCESSING,
    COMPLETED,
    CLOSED,
    CANCELLED,
    REJECTED;

    private static final Set<BusinessDocumentStatus> TERMINAL = EnumSet.noneOf(BusinessDocumentStatus.class);

    public Set<BusinessDocumentStatus> allowedNext() {
        return switch (this) {
            case DRAFT      -> EnumSet.of(SUBMITTED, CANCELLED);
            case SUBMITTED  -> EnumSet.of(APPROVED, REJECTED, CANCELLED);
            case REJECTED   -> EnumSet.of(DRAFT, CANCELLED);
            case APPROVED   -> EnumSet.of(PARTIAL, PROCESSING, COMPLETED, CANCELLED);
            case PARTIAL    -> EnumSet.of(PARTIAL, COMPLETED, CANCELLED);
            case PROCESSING -> EnumSet.of(PARTIAL, COMPLETED, CANCELLED);
            case COMPLETED  -> EnumSet.of(CLOSED);
            case CLOSED, CANCELLED -> TERMINAL;
        };
    }

    public boolean canTransitionTo(BusinessDocumentStatus target) {
        return allowedNext().contains(target);
    }

    /** Only a draft or a rejected document may be edited in place. */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }

    /** Once here the document has legal/financial standing; changes go through a revision. */
    public boolean isCommitted() {
        return this == APPROVED || this == PARTIAL || this == PROCESSING
            || this == COMPLETED || this == CLOSED;
    }
}
