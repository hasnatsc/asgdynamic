package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.BaseOrgLineEntity;
import com.asg.fabricerp.inventory.item.InventoryItem;
import com.asg.fabricerp.inventory.item.ItemBrand;
import com.asg.fabricerp.inventory.item.ItemModel;
import com.asg.fabricerp.inventory.item.UnitOfMeasure;
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
        @Index(name = "ix_gbdlg_uom",      columnList = "uom_id"),
        @Index(name = "ix_gbdlg_org",      columnList = "organization_id")
    })
public class BusinessDocumentLineGroup extends BaseOrgLineEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_gbdlg_document"))
    private BusinessDocument document;

    @Column(name = "group_no", nullable = false)
    private Integer groupNo = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", foreignKey = @ForeignKey(name = "fk_gbdlg_item"))
    private InventoryItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uom_id", foreignKey = @ForeignKey(name = "fk_gbdlg_uom"))
    private UnitOfMeasure uom;

    /** Purchase and store lines: the brand and model asked for or received, which may differ from the item's own. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_brand_id", foreignKey = @ForeignKey(name = "fk_gbdlg_item_brand"))
    private ItemBrand itemBrand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_model_id", foreignKey = @ForeignKey(name = "fk_gbdlg_item_model"))
    private ItemModel itemModel;

    @Column(name = "item_specification", length = 500)
    private String itemSpecification;

    /** MRR: the country the goods came from. */
    @Column(name = "origin_country", length = 60)
    private String originCountry;

    @Embedded
    private FabricSpec fabric = new FabricSpec();

    /** The process route, on production orders and every document raised against them. */
    @Embedded
    private RouteSnapshot route = new RouteSnapshot();

    /** On a revision: the line group of the superseded version this one continues. */
    @Column(name = "revised_from_group_id")
    private Long revisedFromGroupId;

    @Valid
    @OneToMany(mappedBy = "lineGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BusinessDocumentColorLine> colorLines = new ArrayList<>();

    public BusinessDocument getDocument()              { return document; }
    public void setDocument(BusinessDocument d)        { this.document = d; }
    public Integer getGroupNo()                        { return groupNo; }
    public void setGroupNo(Integer v)                  { this.groupNo = v; }
    public InventoryItem getItem()                     { return item; }
    public void setItem(InventoryItem v)               { this.item = v; }
    public UnitOfMeasure getUom()                      { return uom; }
    public void setUom(UnitOfMeasure v)                { this.uom = v; }
    public ItemBrand getItemBrand()                    { return itemBrand; }
    public void setItemBrand(ItemBrand v)              { this.itemBrand = v; }
    public ItemModel getItemModel()                    { return itemModel; }
    public void setItemModel(ItemModel v)              { this.itemModel = v; }
    public String getItemSpecification()               { return itemSpecification; }
    public void setItemSpecification(String v)         { this.itemSpecification = v; }
    public String getOriginCountry()                   { return originCountry; }
    public void setOriginCountry(String v)             { this.originCountry = v; }
    /** Never null: Hibernate loads an embedded spec whose columns are all empty (an item line's) as null. */
    public FabricSpec getFabric()                      { return fabric == null ? (fabric = new FabricSpec()) : fabric; }
    public void setFabric(FabricSpec v)                { this.fabric = v == null ? new FabricSpec() : v; }
    public List<BusinessDocumentColorLine> getColorLines() { return colorLines; }
    /** Never null: Hibernate hands back null for an embeddable whose columns are all empty. */
    public RouteSnapshot getRoute()                    { return route == null ? (route = new RouteSnapshot()) : route; }
    public void setRoute(RouteSnapshot v)              { this.route = v == null ? new RouteSnapshot() : v; }
    public Long getRevisedFromGroupId()                { return revisedFromGroupId; }
    public void setRevisedFromGroupId(Long v)          { this.revisedFromGroupId = v; }

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
