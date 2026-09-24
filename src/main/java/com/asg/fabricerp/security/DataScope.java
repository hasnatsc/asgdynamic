package com.asg.fabricerp.security;

import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.common.ScopeDimension;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One grant of row visibility on one dimension — asfl-erp's {@code DataScope} (ADM-3).
 *
 * <p>Answers "which bookings may you see?", which a {@link RoleScreenGrant} deliberately does
 * not: a merchandiser and the MD may hold identical permissions on the Booking screen and
 * still, correctly, see different rows.
 *
 * <p>Effective-dated and never deleted or edited in place. A revocation closes the row from a
 * date, so "what could this person see last March" keeps the answer that was true then.
 */
@Entity
@Table(name = "sec_fabric_data_scopes")
public class DataScope extends AuditableEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private ScopeDimension dimension;

    /** An id in whichever table {@link #dimension} names. */
    @Column(name = "scope_value_id", nullable = false, updatable = false)
    private Long scopeValueId;

    @Column(name = "granted_from", nullable = false, updatable = false)
    private LocalDate grantedFrom;

    @Column(name = "revoked_from")
    private LocalDate revokedFrom;

    @Column(name = "revoked_reason", length = 255)
    private String revokedReason;

    @Column(length = 255)
    private String remarks;

    protected DataScope() { }

    public DataScope(Long userId, ScopeDimension dimension, Long scopeValueId, LocalDate grantedFrom,
                     String remarks) {
        this.userId = userId;
        this.dimension = dimension;
        this.scopeValueId = scopeValueId;
        this.grantedFrom = grantedFrom;
        this.remarks = remarks;
    }

    /** Held on a date: started on or before it, and not yet revoked by it. */
    public boolean isHeldOn(LocalDate on) {
        return !grantedFrom.isAfter(on) && (revokedFrom == null || on.isBefore(revokedFrom));
    }

    /** Still open — not revoked, even from a future date. */
    public boolean isOpen() {
        return revokedFrom == null;
    }

    public void revokeFrom(LocalDate from, String reason) {
        if (revokedFrom != null) {
            throw new IllegalStateException("Scope grant " + getId() + " is already revoked");
        }
        if (from.isBefore(grantedFrom)) {
            throw new IllegalArgumentException(
                "A grant cannot be revoked from before it started (" + grantedFrom + ")");
        }
        this.revokedFrom = from;
        this.revokedReason = reason;
    }

    public Long getUserId()            { return userId; }
    public ScopeDimension getDimension() { return dimension; }
    public Long getScopeValueId()      { return scopeValueId; }
    public LocalDate getGrantedFrom()  { return grantedFrom; }
    public LocalDate getRevokedFrom()  { return revokedFrom; }
    public String getRevokedReason()   { return revokedReason; }
    public String getRemarks()         { return remarks; }
}
