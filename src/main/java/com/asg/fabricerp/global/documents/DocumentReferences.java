package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.AuditableEntity;
import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.inventory.item.InventoryItem;
import com.asg.fabricerp.inventory.item.InventoryItemRepository;
import com.asg.fabricerp.inventory.item.UnitOfMeasure;
import com.asg.fabricerp.inventory.item.UnitOfMeasureRepository;
import com.asg.fabricerp.party.Party;
import com.asg.fabricerp.party.PartyRoleType;
import com.asg.fabricerp.party.PartyService;
import com.asg.fabricerp.security.FabricUser;
import com.asg.fabricerp.security.FabricUserRepository;
import org.springframework.stereotype.Component;

/**
 * Turns the associations a request names into real, tenant-checked entities.
 *
 * <p>Document controllers bind {@link BusinessDocument} straight from the request body, so a
 * party arrives as {@code {"party": {"id": 42}}} - a stand-in carrying nothing but an id. Saved
 * as-is, Hibernate would write that id without asking whether it exists, belongs to this
 * organization, or may be named on this type of document. (The same was true of the plain
 * {@code party_id} columns these associations replaced.) Every save calls {@link #resolve} first:
 * each stand-in is swapped for the organization's own row, or the save is refused.
 *
 * <p>Warehouse, party, item and unit are resolved here because the request supplies them. The
 * business unit, marketing team and revision root are never taken from the request (they are
 * read-only in JSON) - services stamp those, through {@link #currentBusinessUnit()} and
 * {@link #marketingTeam(Long)}.
 */
@Component
public class DocumentReferences {

    private final PartyService parties;
    private final WarehouseRepository warehouses;
    private final BusinessUnitRepository businessUnits;
    private final MarketingTeamRepository marketingTeams;
    private final InventoryItemRepository items;
    private final UnitOfMeasureRepository units;
    private final FabricUserRepository users;
    private final OrgContext context;

    public DocumentReferences(PartyService parties, WarehouseRepository warehouses,
                              BusinessUnitRepository businessUnits, MarketingTeamRepository marketingTeams,
                              InventoryItemRepository items, UnitOfMeasureRepository units,
                              FabricUserRepository users, OrgContext context) {
        this.parties = parties;
        this.warehouses = warehouses;
        this.businessUnits = businessUnits;
        this.marketingTeams = marketingTeams;
        this.items = items;
        this.units = units;
        this.users = users;
        this.context = context;
    }

    /**
     * Resolves a submitted document's party (checked against {@code type}'s
     * {@link DocumentType#requiredPartyRole()}), warehouse, and every line group's item and unit.
     *
     * <p>Call it first thing in a save, on the submitted document itself - before its fields are
     * copied onto a managed document and before anything else is saved. A stand-in copied onto a
     * managed entity is still unresolved when the next query auto-flushes, and Hibernate refuses
     * to flush a reference to a detached row it has never seen ("uninitialized version").
     *
     * @param type the document's type; a submitted body does not carry one
     */
    public void resolve(BusinessDocument doc, DocumentType type) {
        Long orgId = context.requireOrganizationId();
        Long partyId = AuditableEntity.idOf(doc.getParty());
        doc.setParty(partyId == null ? null : parties.requireHolder(partyId, type.requiredPartyRole()));
        doc.setWarehouse(warehouse(orgId, doc.getWarehouse()));
        Long brandId = AuditableEntity.idOf(doc.getBrand());
        doc.setBrand(brandId == null ? null : parties.requireHolder(brandId, PartyRoleType.BRAND));
        Long garmentsId = AuditableEntity.idOf(doc.getGarments());
        doc.setGarments(garmentsId == null ? null : parties.requireHolder(garmentsId, PartyRoleType.GARMENT_FACTORY));
        doc.setMarketingPerson(user(orgId, doc.getMarketingPersonId()));
        for (BusinessDocumentLineGroup group : doc.getLineGroups()) {
            group.setItem(item(orgId, group.getItem()));
            group.setUom(uom(orgId, group.getUom()));
        }
    }

    /** The caller's operating unit, which stamps every new document. */
    public BusinessUnit currentBusinessUnit() {
        return businessUnits.getReferenceById(context.requireBusinessUnitId());
    }

    /** A marketing team by id, or null - for stamping a team decided by the server (ADM-7). */
    public MarketingTeam marketingTeam(Long teamId) {
        return teamId == null ? null : marketingTeams.getReferenceById(teamId);
    }

    private Warehouse warehouse(Long orgId, Warehouse submitted) {
        Long id = AuditableEntity.idOf(submitted);
        return id == null ? null : warehouses.findScoped(id, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + id));
    }

    /** A user of this organization by id, or null. Also used to default the marketing person. */
    public FabricUser user(Long orgId, Long id) {
        return id == null ? null : users.findScoped(id, orgId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    private InventoryItem item(Long orgId, InventoryItem submitted) {
        Long id = AuditableEntity.idOf(submitted);
        return id == null ? null : items.findScoped(id, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found: " + id));
    }

    private UnitOfMeasure uom(Long orgId, UnitOfMeasure submitted) {
        Long id = AuditableEntity.idOf(submitted);
        return id == null ? null : units.findScoped(id, orgId)
            .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + id));
    }
}
