package com.asg.fabricerp.global.numbering;

import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.common.OrgScoped;
import jakarta.persistence.*;

/**
 * How one organization numbers one {@link NumberSeries}. Created from the series' defaults the first
 * time it is numbered (or the setup screen is opened), then editable there. Changing it never
 * endangers numbers already issued: counters only ever go up, and every issued number is held in
 * {@code gbl_issued_numbers} under a unique key.
 */
@Entity
@Table(
    name = "gbl_numbering_schemes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_gns_org_series", columnNames = {"organization_id", "series_code"}),
        @UniqueConstraint(name = "uk_gns_org_prefix", columnNames = {"organization_id", "prefix"})
    })
public class NumberingScheme extends AuditableEntity implements OrgScoped {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "series_code", nullable = false, length = 40, updatable = false)
    private String seriesCode;

    @Column(nullable = false, length = 12)
    private String prefix;

    @Column(nullable = false, length = 80)
    private String pattern;

    @Column(name = "sequence_width", nullable = false)
    private Integer sequenceWidth;

    @Enumerated(EnumType.STRING)
    @Column(name = "reset_policy", nullable = false, length = 20)
    private ResetPolicy resetPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "counter_scope", nullable = false, length = 20)
    private CounterScope counterScope;

    protected NumberingScheme() { }

    /** Validates first, so an entity that exists is a configuration that cannot repeat a number. */
    public void configure(String prefix, String pattern, int width, ResetPolicy reset, CounterScope scope) {
        NumberPattern.validate(prefix, pattern, width, reset, scope);
        this.prefix = prefix;
        this.pattern = pattern;
        this.sequenceWidth = width;
        this.resetPolicy = reset;
        this.counterScope = scope;
    }

    @Override public Long getOrganizationId()        { return organizationId; }
    @Override public void setOrganizationId(Long id) { this.organizationId = id; }
    public String getSeriesCode()                    { return seriesCode; }
    public String getPrefix()                        { return prefix; }
    public String getPattern()                       { return pattern; }
    public int getSequenceWidth()                    { return sequenceWidth; }
    public ResetPolicy getResetPolicy()              { return resetPolicy; }
    public CounterScope getCounterScope()            { return counterScope; }
}
