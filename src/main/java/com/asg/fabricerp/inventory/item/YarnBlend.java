package com.asg.fabricerp.inventory.item;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A fiber blend, e.g. "60% Cotton 40% Viscose", defined by its {@link YarnBlendComponent}s,
 * whose percentages must total exactly 100.
 */
@Entity
@Table(
    name = "yrn_blends",
    uniqueConstraints = @UniqueConstraint(name = "uk_yrn_blend_org_code", columnNames = {"organization_id", "code"}))
public class YarnBlend extends ApprovableMaster {

    public static final BigDecimal FULL = new BigDecimal("100");

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "short_name", length = 500)
    private String shortName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "blend", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<YarnBlendComponent> components = new ArrayList<>();

    public YarnBlend() { }

    /** Replaces every component, numbering them in the order given. */
    public void replaceComponents(List<YarnBlendComponent> replacements) {
        components.clear();
        int order = 1;
        for (YarnBlendComponent component : replacements) {
            component.attachTo(this, order++);
            components.add(component);
        }
    }

    public BigDecimal totalPercentage() {
        return components.stream()
            .map(YarnBlendComponent::getPercentage)
            .filter(p -> p != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The composition as the legacy screens wrote blend names: "60% Cotton 40% Viscose",
     * "100% Cotton". SpindleERP built "Cotton 60% / Polyester 40%" here, and had to stop using
     * it in yarn item names because its slash read as a second count/ply separator.
     */
    public String composition() {
        return components.stream()
            .map(c -> c.getPercentage().stripTrailingZeros().toPlainString() + "% "
                      + (c.getFiber() == null ? "" : c.getFiber().getName()))
            .collect(Collectors.joining(" "))
            .trim();
    }

    public String getCode()                          { return code; }
    public void setCode(String v)                    { this.code = v; }
    public String getName()                          { return name; }
    public void setName(String v)                    { this.name = v; }
    public String getShortName()                     { return shortName; }
    public void setShortName(String v)               { this.shortName = v; }
    public String getDescription()                   { return description; }
    public void setDescription(String v)             { this.description = v; }
    public List<YarnBlendComponent> getComponents()  { return components; }
}
