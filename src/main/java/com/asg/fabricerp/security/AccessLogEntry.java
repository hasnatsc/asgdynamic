package com.asg.fabricerp.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One refused or security-relevant attempt — asfl-erp's ADM-11 access log.
 *
 * <p>Not an {@link com.asg.fabricerp.common.AuditableEntity}: a refused attempt changed no row,
 * so there is no row for an audit column to sit on, and this table is append-only — nothing
 * here is ever updated, so a version and an updated-by would only be noise.
 */
@Entity
@Table(name = "sec_access_log")
public class AccessLogEntry {

    public enum Event {
        /** Wrong password, or a username that does not exist. */
        LOGIN_FAILED,
        /** The failure that crossed the lockout threshold. */
        ACCOUNT_LOCKED,
        /** A correct or incorrect password against a locked or disabled account. */
        LOGIN_REFUSED_INACTIVE,
        LOGIN_SUCCEEDED,
        /** An authenticated request refused by {@code @PreAuthorize} — ADM-1, ADM-2. */
        ACCESS_DENIED,
        /** A live session ended because the account was locked, deleted or unscoped since. */
        SESSION_ENDED,
        PASSWORD_CHANGED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    /** Text, not a foreign key: an unknown username is what a failed login often is. */
    @Column(length = 80, updatable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Event event;

    /** The method or URL refused, for {@link Event#ACCESS_DENIED}. */
    @Column(length = 255, updatable = false)
    private String target;

    @Column(length = 500, updatable = false)
    private String detail;

    @Column(name = "source_address", length = 64, updatable = false)
    private String sourceAddress;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected AccessLogEntry() { }

    public AccessLogEntry(Long userId, String username, Event event, String target, String detail,
                          String sourceAddress, LocalDateTime occurredAt) {
        this.userId = userId;
        this.username = truncate(username, 80);
        this.event = event;
        this.target = truncate(target, 255);
        this.detail = truncate(detail, 500);
        this.sourceAddress = truncate(sourceAddress, 64);
        this.occurredAt = occurredAt;
    }

    /** A log row that fails to insert because a typed username was long would lose the event. */
    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId()                { return id; }
    public Long getUserId()            { return userId; }
    public String getUsername()        { return username; }
    public Event getEvent()            { return event; }
    public String getTarget()          { return target; }
    public String getDetail()          { return detail; }
    public String getSourceAddress()   { return sourceAddress; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
