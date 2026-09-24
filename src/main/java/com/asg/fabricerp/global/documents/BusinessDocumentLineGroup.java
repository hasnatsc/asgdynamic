package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import jakarta.persistence.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One fabric specification within a document — construction, weave, composition, GSM —
 * entered once and shared by every colour drawn against it. asgdynamic's {@code dtlSet}.
 *
 * <p>Was collapsed into a single flat "line" (fabric spec + one colour) until a real
 * Booking payload showed a construction commonly carries several colours, each needing its
 * own reference fields — see {@link FabricSpec}'s javadoc for the full account. This class
 * is the corrected middle level; {@link BusinessDocumentColorLine} is the bottom one.
 */
@Entity
@Table(
    name = "gbl_business_document_line_groups",
    indexes = {
        @Index(name = "ix_gbdlg_document", columnList = "document_id"),
        @Index(name = "ix_gbdlg_item",     columnList = "item_id"),
        @Index(name = "ix_gbdlg_org",      columnList = "organization_id")
    })
public class BusinessDocumentLineGroup extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_gbdlg_document"))
    private BusinessDocument document;

    @Column(name = "group_no", nullable = false)
    private Integer groupNo = 0;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "uom_id")
    private Long uomId;

    @Embedded
    private FabricSpec fabric = new FabricSpec();

    @Valid
    @OneToMany(mappedBy = "lineGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BusinessDocumentColorLine> colorLines = new ArrayList<>();

    public BusinessDocument getDocument()              { return document; }
    public void setDocument(BusinessDocument d)        { this.document = d; }
    public Integer getGroupNo()                        { return groupNo; }
    public void setGroupNo(Integer v)                  { this.groupNo = v; }
    public Long getItemId()                            { return itemId; }
    public void setItemId(Long v)                      { this.itemId = v; }
    public Long getUomId()                             { return uomId; }
    public void setUomId(Long v)                       { this.uomId = v; }
    public FabricSpec getFabric()                      { return fabric; }
    public void setFabric(FabricSpec v)                { this.fabric = v == null ? new FabricSpec() : v; }
    public List<BusinessDocumentColorLine> getColorLines() { return colorLines; }

    public void setColorLines(List<BusinessDocumentColorLine> incoming) {
        this.colorLines.clear();
        if (incoming != null) incoming.forEach(this::addColorLine);
    }

    public void addColorLine(BusinessDocumentColorLine line) {
        line.setLineGroup(this);
        line.setOrganizationId(getOrganizationId());
        this.colorLines.add(line);
    }

    /** Sum of this group's colour-line amounts and quantities. */
    public BigDecimal groupAmount() {
        return colorLines.stream()
            .map(BusinessDocumentColorLine::getLineAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal groupQuantity() {
        return colorLines.stream()
            .map(BusinessDocumentColorLine::getQuantity)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
