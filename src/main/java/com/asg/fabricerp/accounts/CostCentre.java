package com.asg.fabricerp.accounts;

import com.asg.fabricerp.accounts.AccountFlags.CostCentreType;
import com.asg.fabricerp.common.BaseOrgEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Where a cost lands - a posting dimension on every ledger line that carries one.
 *
 * <p>Nested, because the mill is: Weaving Shed 2 rolls up into Weaving rolls up into Production.
 * A direct centre's unabsorbed cost falls back to the overhead centre it {@link #absorbsInto}.
 */
@Entity
@Table(name = "acc_cost_centres")
public class CostCentre extends BaseOrgEntity {

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "centre_type", nullable = false, length = 20)
    private CostCentreType centreType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private CostCentre parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "absorbs_into_id")
    private CostCentre absorbsInto;

    /** Weaving, dyeing, finishing - the section a shift belongs to. */
    @Column(name = "section_code", length = 40)
    private String sectionCode;

    @Column(length = 500)
    private String remarks;

    protected CostCentre() { }

    public CostCentre(String code, String name, CostCentreType centreType) {
        this.code = code;
        this.name = name;
        this.centreType = centreType;
    }

    /** Direct labour posts to a production centre; everything else is indirect. */
    public boolean isDirect() { return centreType == CostCentreType.PRODUCTION; }

    public CostCentre under(CostCentre parent) {
        for (CostCentre at = parent; at != null; at = at.parent) {
            if (at == this || (at.getId() != null && at.getId().equals(getId()))) {
                throw new IllegalArgumentException("Placing " + code + " under " + parent.code + " would be a loop.");
            }
        }
        this.parent = parent;
        return this;
    }

    public CostCentre absorbingInto(CostCentre overhead) {
        if (overhead != null && overhead.centreType != CostCentreType.OVERHEAD) {
            throw new IllegalArgumentException("Cost centre " + code + " can only absorb into an overhead centre; "
                + overhead.code + " is " + overhead.centreType + ".");
        }
        this.absorbsInto = overhead;
        return this;
    }

    public void update(String name, CostCentreType centreType, String sectionCode, String remarks) {
        this.name = name;
        this.centreType = centreType;
        this.sectionCode = sectionCode;
        this.remarks = remarks;
    }

    public String getCode()               { return code; }
    public String getName()               { return name; }
    public CostCentreType getCentreType() { return centreType; }
    public CostCentre getParent()         { return parent; }
    public CostCentre getAbsorbsInto()    { return absorbsInto; }
    public String getSectionCode()        { return sectionCode; }
    public String getRemarks()            { return remarks; }
}
