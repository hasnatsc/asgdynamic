package com.asg.fabricerp.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * The single audit contract for every persistent type.
 *
 * <h2>Why one class</h2>
 * SpindleERP declares the same six audit columns three times — in {@code BaseOrgEntity},
 * {@code BaseOrgLineEntity} and {@code BaseAuditEntity} (the last of which sits in the
 * {@code inventory.item} package while serving the global document family). Its own javadoc
 * records the cost of that split: the Spring Data annotations on one of the three never ran,
 * because {@code @EnableJpaAuditing} was never declared, and every audit column on those
 * entities was written NULL until someone noticed.
 *
 * <p>Here the columns are declared once and stamped by exactly one mechanism
 * ({@link OrgContextListener}). There is no second path to fall out of sync with.
 *
 * <p>Timestamps are taken from the JVM clock, which {@code AppZone} pins to
 * {@code app.timezone} at startup, so they are wall clocks in the business zone
 * regardless of where the server runs.
 */
@MappedSuperclass
@EntityListeners(OrgContextListener.class)
public abstract class AuditableEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking. SpindleERP has this on {@code BaseOrgEntity} but not on
     * {@code BaseOrgLineEntity}, so document headers are protected from concurrent edits
     * while their lines are not — and the lines are what carry quantity and money.
     * Applied to everything here.
     */
    @Version
    @Column(name = "version")
    private Long version;

    public Long getId()                 { return id; }
    public void setId(Long id)          { this.id = id; }
    public String getCreatedBy()        { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getUpdatedBy()        { return updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion()            { return version; }

    // Package-private: only the listener stamps these.
    void stampCreated(String user, LocalDateTime at) {
        this.createdBy = user;
        this.createdAt = at;
        this.updatedBy = user;
        this.updatedAt = at;
    }

    void stampUpdated(String user, LocalDateTime at) {
        this.updatedBy = user;
        this.updatedAt = at;
    }

    public boolean isNew() {
        return id == null;
    }

    /**
     * The id of a possibly-null association. Safe on an uninitialised lazy proxy: Hibernate
     * answers the identifier getter without loading the row, so a controller mapping a document
     * outside its transaction can still read {@code idOf(doc.getParty())}.
     */
    public static Long idOf(AuditableEntity entity) {
        return entity == null ? null : entity.getId();
    }
}
