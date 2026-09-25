package com.asg.fabricerp.party;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.numbering.BusinessSeries;
import com.asg.fabricerp.party.PartyAdminService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PartyAdminServiceTest {

    private static final Long ORG = 1L;

    private PartyRepository parties;
    private BusinessNumberService numbering;
    private PartyAdminService service;

    @BeforeEach
    void setUp() {
        parties = mock(PartyRepository.class);
        numbering = mock(BusinessNumberService.class);
        when(numbering.next(BusinessSeries.PARTY)).thenReturn("PT-2026-000001");
        when(numbering.next(BusinessSeries.CUSTOMER)).thenReturn("CUS-2026-000001");
        when(numbering.next(BusinessSeries.SUPPLIER)).thenReturn("SUP-2026-000001");
        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return 10L; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
            @Override public RowScope rowScope()       { return RowScope.unrestrictedScope(); }
        };
        service = new PartyAdminService(parties, numbering, context);
        // save() returns detail(), which re-reads the party: hand back whatever was saved.
        when(parties.save(any(Party.class))).thenAnswer(i -> {
            Party p = i.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            when(parties.findScoped(p.getId(), ORG)).thenReturn(Optional.of(p));
            return p;
        });
    }

    private static PartyRequest request(Long id, Long version, String code, String name, List<RoleRequest> roles,
                                        List<AddressRequest> addresses, List<ContactRequest> contacts,
                                        List<BankAccountRequest> accounts) {
        return new PartyRequest(id, version, code, name, Party.PartyType.ORGANISATION, null, "Bangladesh",
            "123456789012", null, null, true, roles, addresses, contacts, accounts, Map.of("IRC number", "IRC-7"));
    }

    private static List<RoleRequest> customer() {
        return List.of(new RoleRequest(PartyRoleType.CUSTOMER, PartyRoleType.MARKETING, "C-001"));
    }

    private Party existing(Long id, PartyRoleType role, String qualifier) {
        Party p = new Party("P" + id, "Party " + id, Party.PartyType.ORGANISATION);
        p.setId(id);
        p.setOrganizationId(ORG);
        ReflectionTestUtils.setField(p, "version", 3L);
        p.grantRole(role, qualifier, null, LocalDate.of(2025, 1, 1));
        when(parties.findScoped(id, ORG)).thenReturn(Optional.of(p));
        return p;
    }

    // ---------------------------------------------------------------------------- create

    @Test
    void aBlankCodeIsNumbered_andTheWholeRecordIsSaved() {
        Map<String, Object> saved = service.save(request(null, null, " ", "Acme Garments", customer(),
            List.of(new AddressRequest(null, PartyAddress.AddressType.REGISTERED, "Plot 1, Gazipur", null, "Gazipur", null, false)),
            List.of(new ContactRequest(null, "Rahim", "Merchandiser", null, "01711000000", "rahim@acme.test", false)),
            List.of()));

        assertThat(saved.get("code")).isEqualTo("PT-2026-000001");
        assertThat(saved.get("attributes")).isEqualTo(Map.of("IRC number", "IRC-7"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> addresses = (List<Map<String, Object>>) saved.get("addresses");
        assertThat(addresses).singleElement().satisfies(a -> assertThat(a.get("primary")).isEqualTo(true));
    }

    @Test
    void aTypedCodeIsUpperCased_andMustBeUnique() {
        when(parties.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(ORG, "CUS001")).thenReturn(true);
        assertThatThrownBy(() -> service.save(request(null, null, "cus001", "Acme", customer(), null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CUS001");
    }

    @Test
    void aPartyNeedsARole() {
        assertThatThrownBy(() -> service.save(request(null, null, null, "Acme", List.of(), null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one role");
    }

    // ---------------------------------------------------------------------------- roles

    @Test
    void untickingARoleRevokesIt_andTickingItAgainRestoresTheSameRow() {
        Party p = existing(5L, PartyRoleType.SUPPLIER, null);
        PartyRole supplierRow = p.getRoles().getFirst();

        service.save(request(5L, 3L, null, "Party 5", customer(), null, null, null));
        assertThat(supplierRow.isCurrent()).isFalse();
        assertThat(supplierRow.getRevokedOn()).isEqualTo(LocalDate.now());
        assertThat(p.activeRoles()).containsExactly(PartyRoleType.CUSTOMER);

        ReflectionTestUtils.setField(p, "version", 3L);
        service.save(request(5L, 3L, null, "Party 5",
            List.of(new RoleRequest(PartyRoleType.SUPPLIER, null, "S-9")), null, null, null));
        assertThat(supplierRow.isCurrent()).isTrue();
        assertThat(supplierRow.getGrantedOn()).isEqualTo(LocalDate.of(2025, 1, 1));   // "since when" survives
        assertThat(supplierRow.getRoleCode()).isEqualTo("S-9");
    }

    private Party savedParty() {
        ArgumentCaptor<Party> captor = ArgumentCaptor.forClass(Party.class);
        verify(parties, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static String codeOf(Party p, PartyRoleType type, String qualifier) {
        return p.getRoles().stream().filter(r -> r.matches(type, qualifier)).findFirst().orElseThrow().getRoleCode();
    }

    @Test
    void customerAndSupplierRolesWithoutACodeAreIssuedOne_otherRolesAreNot() {
        service.save(request(null, null, null, "Acme", List.of(
            new RoleRequest(PartyRoleType.CUSTOMER, PartyRoleType.MARKETING, null),
            new RoleRequest(PartyRoleType.SUPPLIER, null, " "),
            new RoleRequest(PartyRoleType.BANK, null, null)), null, null, null));

        Party p = savedParty();
        assertThat(codeOf(p, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING)).isEqualTo("CUS-2026-000001");
        assertThat(codeOf(p, PartyRoleType.SUPPLIER, null)).isEqualTo("SUP-2026-000001");
        assertThat(codeOf(p, PartyRoleType.BANK, null)).isNull();
    }

    @Test
    void aCompanyOnBothCustomerSidesHasOneCustomerCode() {
        service.save(request(null, null, null, "Acme", List.of(
            new RoleRequest(PartyRoleType.CUSTOMER, PartyRoleType.MARKETING, null),
            new RoleRequest(PartyRoleType.CUSTOMER, PartyRoleType.COMMERCIAL, null)), null, null, null));

        Party p = savedParty();
        assertThat(codeOf(p, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING)).isEqualTo("CUS-2026-000001");
        assertThat(codeOf(p, PartyRoleType.CUSTOMER, PartyRoleType.COMMERCIAL)).isEqualTo("CUS-2026-000001");
        verify(numbering, times(1)).next(BusinessSeries.CUSTOMER);
    }

    @Test
    void anIssuedCustomerCodeSurvivesABlankField() {
        Party p = existing(13L, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING);
        p.getRoles().getFirst().setRoleCode("CUS-2026-000007");

        service.save(request(13L, 3L, null, "Party 13",
            List.of(new RoleRequest(PartyRoleType.CUSTOMER, PartyRoleType.MARKETING, null)), null, null, null));

        assertThat(codeOf(p, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING)).isEqualTo("CUS-2026-000007");
        verify(numbering, never()).next(BusinessSeries.CUSTOMER);
    }

    @Test
    void aTypedPartyCodeIsReserved_afterRoleCodesAreDrawn() {
        service.save(request(null, null, "acme-1", "Acme",
            List.of(new RoleRequest(PartyRoleType.CUSTOMER, PartyRoleType.MARKETING, null)), null, null, null));

        InOrder order = inOrder(numbering);
        order.verify(numbering).next(BusinessSeries.CUSTOMER);
        order.verify(numbering).reserve(BusinessSeries.PARTY, "ACME-1");
        verify(numbering, never()).next(BusinessSeries.PARTY);
    }

    @Test
    void aTypedPartyCodeAlreadyIssuedIsRefused() {
        doThrow(new IllegalArgumentException("'PT-2026-000003' has already been issued."))
            .when(numbering).reserve(BusinessSeries.PARTY, "PT-2026-000003");

        assertThatThrownBy(() -> service.save(request(null, null, "PT-2026-000003", "Acme", customer(), null, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already been issued");
        verify(parties, never()).save(any(Party.class));
    }

    @Test
    void aBankOthersHoldAccountsAtKeepsTheBankRole() {
        existing(6L, PartyRoleType.BANK, null);
        when(parties.accountsHeldAt(6L)).thenReturn(2L);

        assertThatThrownBy(() -> service.save(request(6L, 3L, null, "Party 6", customer(), null, null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must keep the Bank role");
    }

    // ---------------------------------------------------------------------------- child rows

    @Test
    void childRowsAreUpdatedInPlace_droppedOnesRemoved_andOnePrimaryKept() {
        Party p = existing(7L, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING);
        PartyContact first = p.addContact("First", null);
        first.setId(71L);
        PartyContact second = p.addContact("Second", null);
        second.setId(72L);

        service.save(request(7L, 3L, null, "Party 7", customer(), null, List.of(
            new ContactRequest(72L, "Second, renamed", null, null, null, null, true),
            new ContactRequest(null, "Third", null, null, null, null, true)), null));

        assertThat(p.getContacts()).extracting(PartyContact::getName).containsExactly("Second, renamed", "Third");
        assertThat(p.getContacts().getFirst()).isSameAs(second);   // same row, same id
        assertThat(p.getContacts()).filteredOn(PartyContact::isPrimary).hasSize(1).first().isSameAs(second);
    }

    @Test
    void aRowFromAnotherPartyIsRefused() {
        existing(8L, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING);
        assertThatThrownBy(() -> service.save(request(8L, 3L, null, "Party 8", customer(), null,
                List.of(new ContactRequest(999L, "Stranger", null, null, null, null, false)), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not belong to this party");
    }

    @Test
    void anAccountMustBeHeldAtABank() {
        existing(9L, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING);
        existing(10L, PartyRoleType.SUPPLIER, null);   // not a bank

        assertThatThrownBy(() -> service.save(request(9L, 3L, null, "Party 9", customer(), null, null,
                List.of(new BankAccountRequest(null, 10L, "Party 9", "001", null, null, null, "BDT", true)))))
            .isInstanceOf(PartyRoleNotHeldException.class);
    }

    @Test
    void anEmailMustLookLikeOne() {
        assertThatThrownBy(() -> service.save(request(null, null, null, "Acme", customer(), null,
                List.of(new ContactRequest(null, "Rahim", null, null, null, "not-an-email", false)), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not an email address");
    }

    // ---------------------------------------------------------------------------- concurrency & delete

    @Test
    void aStaleEditIsRefused() {
        existing(11L, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING);   // at version 3
        assertThatThrownBy(() -> service.save(request(11L, 2L, null, "Party 11", customer(), null, null, null)))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void aPartyStillInUseIsNotDeleted() {
        Party p = existing(12L, PartyRoleType.CUSTOMER, PartyRoleType.MARKETING);
        when(parties.documentsNaming(12L)).thenReturn(4L);
        assertThatThrownBy(() -> service.delete(12L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("4 document(s)");

        when(parties.documentsNaming(12L)).thenReturn(0L);
        service.delete(12L);
        assertThat(p.getDeleted()).isTrue();
        assertThat(p.getActive()).isFalse();
    }

    @Test
    void exportCellsCannotRunAsFormulas() {
        assertThat(PartyAdminController.csv(List.of("=HYPERLINK(\"x\")", "Acme \"Ltd\"", "")))
            .isEqualTo("\"'=HYPERLINK(\"\"x\"\")\",\"Acme \"\"Ltd\"\"\",\"\"");
    }
}
