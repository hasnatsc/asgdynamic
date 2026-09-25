package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.Warehouse;
import com.asg.fabricerp.common.WarehouseRepository;
import com.asg.fabricerp.inventory.item.InventoryItem;
import com.asg.fabricerp.inventory.item.InventoryItemRepository;
import com.asg.fabricerp.inventory.item.UnitOfMeasure;
import com.asg.fabricerp.inventory.item.UnitOfMeasureRepository;
import com.asg.fabricerp.party.Party;
import com.asg.fabricerp.party.PartyRepository;
import com.asg.fabricerp.party.PartyRoleNotHeldException;
import com.asg.fabricerp.party.PartyRoleType;
import com.asg.fabricerp.party.PartyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A request names associations as {@code {"id": ...}} stand-ins. Before a save, each must become
 * the organization's own row - and a party must hold the role the document type demands.
 */
class DocumentReferencesTest {

    private static final Long ORG = 1L;

    private PartyRepository parties;
    private WarehouseRepository warehouses;
    private InventoryItemRepository items;
    private UnitOfMeasureRepository units;
    private DocumentReferences references;

    @BeforeEach
    void setUp() {
        parties = mock(PartyRepository.class);
        warehouses = mock(WarehouseRepository.class);
        items = mock(InventoryItemRepository.class);
        units = mock(UnitOfMeasureRepository.class);
        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return 10L; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
            @Override public RowScope rowScope()       { return RowScope.unrestrictedScope(); }
        };
        references = new DocumentReferences(new PartyService(parties, context), warehouses,
            mock(BusinessUnitRepository.class), mock(MarketingTeamRepository.class), items, units, context);
    }

    private static BusinessDocument booking() {
        BusinessDocument doc = new BusinessDocument();
        doc.setDocumentType(DocumentType.BOOKING);
        return doc;
    }

    private static Party holding(Long id, PartyRoleType role, String qualifier) {
        Party party = new Party("P" + id, "Party " + id, Party.PartyType.ORGANISATION);
        party.setId(id);
        party.grantRole(role, qualifier, null, LocalDate.of(2026, 1, 1));
        return party;
    }

    @Test
    void standInsAreReplacedByTheOrganizationsOwnRows() {
        Party customer = holding(42L, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING);
        Warehouse store = new Warehouse("W1", "Main store");
        store.setId(5L);
        InventoryItem yarn = new InventoryItem();
        yarn.setId(7L);
        UnitOfMeasure kg = new UnitOfMeasure();
        kg.setId(12L);
        when(parties.findScoped(42L, ORG)).thenReturn(Optional.of(customer));
        when(warehouses.findScoped(5L, ORG)).thenReturn(Optional.of(store));
        when(items.findScoped(7L, ORG)).thenReturn(Optional.of(yarn));
        when(units.findScoped(12L, ORG)).thenReturn(Optional.of(kg));

        BusinessDocument doc = booking();
        doc.setParty(DocumentRefs.party(42L));
        doc.setWarehouse(DocumentRefs.warehouse(5L));
        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        InventoryItem itemRef = new InventoryItem();
        itemRef.setId(7L);
        UnitOfMeasure uomRef = new UnitOfMeasure();
        uomRef.setId(12L);
        group.setItem(itemRef);
        group.setUom(uomRef);
        doc.addLineGroup(group);

        references.resolve(doc);

        assertThat(doc.getParty()).isSameAs(customer);
        assertThat(doc.getWarehouse()).isSameAs(store);
        assertThat(group.getItem()).isSameAs(yarn);
        assertThat(group.getUom()).isSameAs(kg);
    }

    @Test
    void aBookingCannotNameASupplier() {
        when(parties.findScoped(9L, ORG)).thenReturn(Optional.of(holding(9L, PartyRoleType.SUPPLIER, null)));
        BusinessDocument doc = booking();
        doc.setParty(DocumentRefs.party(9L));

        assertThatThrownBy(() -> references.resolve(doc))
            .isInstanceOf(PartyRoleNotHeldException.class)
            .hasMessageContaining("does not hold role CUSTOMER");
    }

    @Test
    void anotherOrganizationsRowsDoNotResolve() {
        // findScoped filters by organization, so another tenant's id simply is not found.
        BusinessDocument doc = booking();
        doc.setParty(DocumentRefs.party(500L));
        assertThatThrownBy(() -> references.resolve(doc))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("No party with id 500");

        BusinessDocument other = booking();
        other.setWarehouse(DocumentRefs.warehouse(600L));
        assertThatThrownBy(() -> references.resolve(other))
            .hasMessage("Warehouse not found: 600");
    }

    @Test
    void absentReferencesStayAbsent() {
        BusinessDocument doc = booking();
        references.resolve(doc);
        assertThat(doc.getParty()).isNull();
        assertThat(doc.getWarehouse()).isNull();
        verifyNoInteractions(parties, warehouses);
    }
}
