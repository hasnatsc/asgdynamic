package com.asg.fabricerp.fabric.booking;

import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.MarketingTeamRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import com.asg.fabricerp.costing.CostingService;
import com.asg.fabricerp.costing.CostingTranslator;
import com.asg.fabricerp.global.documents.*;
import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.terms.ConditionType;
import com.asg.fabricerp.global.terms.TermsConditionService;
import com.asg.fabricerp.security.DataScopeRepository;
import com.asg.fabricerp.security.FabricUserRepository;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** What the server decides on save, whatever the browser sent. */
class BookingServiceSaveTest {

    private static final Long ORG = 1L;

    private BusinessDocumentRepository repository;
    private CostingService costing;
    private TermsConditionService terms;
    private MarketingTeamRepository teams;
    private DataScopeRepository scopes;
    private DocumentReferences references;
    /** Who is saving: unrestricted unless a test narrows it. */
    private RowScope scope = RowScope.unrestrictedScope();
    private BookingService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        BusinessNumberService numbering = mock(BusinessNumberService.class);
        costing = mock(CostingService.class);
        terms = mock(TermsConditionService.class);
        teams = mock(MarketingTeamRepository.class);
        scopes = mock(DataScopeRepository.class);
        // The saving user is a member of team 3 unless a test says otherwise.
        when(scopes.findValuesHeldOn(any(), eq(ScopeDimension.MARKETING_TEAM), any())).thenReturn(List.of(3L));
        references = DocumentRefs.references(10L);

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return 10L; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return null; }
            @Override public String username()         { return "tester"; }
            @Override public RowScope rowScope()       { return scope; }
        };
        service = new BookingService(repository, numbering, costing,
            new CostingTranslator(new ObjectMapper()),
            new DocumentRevisionService(repository, numbering), references,
            terms, mock(FabricUserRepository.class), context,
            teams, mock(com.asg.fabricerp.approval.ApprovalRequestRepository.class), scopes);

        when(numbering.next(eq(DocumentType.BOOKING), any(LocalDate.class), any())).thenReturn("BKAF000031");
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(terms.defaultTermsFor(ConditionType.BOOKING)).thenAnswer(i -> List.of(
            new BusinessDocumentTerm(1, "Dead Yarn & Naps Should Be Not Allowed."),
            new BusinessDocumentTerm(2, "Pls Provide TAP within 48 Hours after receiving the BPO.")));
        // The costing system quotes 3.95 / yd and a GSM of 110 for this code.
        doAnswer(i -> {
            FabricSpec spec = i.getArgument(0);
            spec.setQuotedPrice(new BigDecimal("3.95"));
            spec.setBreakEvenPrice(new BigDecimal("3.897"));
            spec.setGsm(new BigDecimal("110"));
            return new BigDecimal("3.897");
        }).when(costing).applyTo(any(FabricSpec.class));
    }

    private static BusinessDocument newBooking() {
        BusinessDocument doc = new BusinessDocument();
        doc.setParty(DocumentRefs.party(77L));
        doc.setDocumentDate(LocalDate.of(2025, 10, 23));
        doc.setRequiredDate(LocalDate.of(2025, 12, 10));
        doc.setCurrencyCode("USD");
        return doc;
    }

    private static BusinessDocumentLineGroup spec(String costingCode, BusinessDocumentColorLine... lines) {
        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.getFabric().setCostingCode(costingCode);
        group.getFabric().setWarpCount1("34");
        group.getFabric().setWeftCount1("20");
        group.getFabric().setEpi(new BigDecimal("78"));
        group.getFabric().setPpi(new BigDecimal("48"));
        for (BusinessDocumentColorLine line : lines) group.addColorLine(line);
        return group;
    }

    private static BusinessDocumentColorLine colour(String name, String qty, String rate) {
        BusinessDocumentColorLine line = new BusinessDocumentColorLine();
        line.setColorName(name);
        line.setQuantity(new BigDecimal(qty));
        line.setRate(rate == null ? null : new BigDecimal(rate));
        return line;
    }

    @Test
    void aNewBookingStartsWithTheDefaultTermsWhenNoneWereSent() {
        BusinessDocument saved = service.save(newBooking());

        assertThat(saved.getTerms()).extracting(BusinessDocumentTerm::getBodyText)
            .containsExactly("Dead Yarn & Naps Should Be Not Allowed.",
                             "Pls Provide TAP within 48 Hours after receiving the BPO.");
        assertThat(saved.getBookingType()).isEqualTo(BookingType.BULK);
        assertThat(saved.getDocumentNo()).isEqualTo("BKAF000031");
    }

    @Test
    void termsTheUserSentAreKeptInTheirOrderAndRenumbered() {
        BusinessDocument doc = newBooking();
        doc.setTerms(List.of(new BusinessDocumentTerm(5, "Last clause"),
                             new BusinessDocumentTerm(2, "First clause")));

        BusinessDocument saved = service.save(doc);

        verify(terms, never()).defaultTermsFor(any());
        assertThat(saved.getTerms()).extracting(BusinessDocumentTerm::getBodyText)
            .containsExactly("First clause", "Last clause");
        assertThat(saved.getTerms()).extracting(BusinessDocumentTerm::getSerialNo).containsExactly(1, 2);
    }

    @Test
    void anEmptyTermsListMeansNoTermsNotTheDefaults() {
        BusinessDocument doc = newBooking();
        doc.setTerms(List.of());

        assertThat(service.save(doc).getTerms()).isEmpty();
    }

    @Test
    void aColourLeftUnpricedTakesTheQuotedPriceAndTheMetrePriceIsDerived() {
        BusinessDocument doc = newBooking();
        doc.addLineGroup(spec("18102503583", colour("PRT SS YELLOW BEACH", "10500", null),
                                             colour("PRT SS BLACK PAISLEY", "5230", "4.10")));

        BusinessDocument saved = service.save(doc);

        BusinessDocumentLineGroup group = saved.getLineGroups().get(0);
        BusinessDocumentColorLine yellow = group.getColorLines().get(0);
        BusinessDocumentColorLine black = group.getColorLines().get(1);
        assertThat(yellow.getRate()).isEqualByComparingTo("3.95");     // quoted, never break-even
        assertThat(black.getRate()).isEqualByComparingTo("4.10");      // what was agreed stays
        // 4.10 / yd is 4.48 / m on the legacy screen.
        assertThat(black.getPriceInMeter().setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("4.48");
        assertThat(black.getLineAmount()).isEqualByComparingTo("21443");
        assertThat(group.getFabric().getGsm()).isEqualByComparingTo("110");
        assertThat(group.getFabric().getConstruction()).isEqualTo("34X20/78X48");
        assertThat(saved.getTotalQuantity()).isEqualByComparingTo("15730");
    }

    @Test
    void aMetrePricedBookingDerivesTheYardPriceAndTakesTheAmountInMetres() {
        BusinessDocument doc = newBooking();
        doc.setPriceInMeter(true);
        BusinessDocumentColorLine line = colour("Olive", "1000", null);
        line.setPriceInMeter(new BigDecimal("5.00"));
        doc.addLineGroup(spec(null, line));

        BusinessDocumentColorLine saved = service.save(doc).getLineGroups().get(0).getColorLines().get(0);

        assertThat(saved.getRate()).isEqualByComparingTo("4.572");     // 5.00 x 0.9144
        assertThat(saved.getLineAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void aSpecificationWithoutACostingCarriesNoCostingFigures() {
        BusinessDocument doc = newBooking();
        BusinessDocumentLineGroup group = spec(null, colour("Olive", "100", "2"));
        group.getFabric().setQuotedPrice(new BigDecimal("0.01"));   // a browser cannot claim a quote
        group.getFabric().setBreakEvenPrice(new BigDecimal("0.01"));
        doc.addLineGroup(group);

        FabricSpec saved = service.save(doc).getLineGroups().get(0).getFabric();

        verify(costing, never()).applyTo(any());
        assertThat(saved.getQuotedPrice()).isNull();
        assertThat(saved.getBreakEvenPrice()).isNull();
    }

    @Test
    void deliveryBeforeTheBookingDateIsRefused() {
        BusinessDocument doc = newBooking();
        doc.setRequiredDate(LocalDate.of(2025, 10, 1));

        assertThatThrownBy(() -> service.save(doc))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("before the booking date");
    }

    @Test
    void aSingleColourFabricBookedInTwoColoursOnOneLineIsRefused() {
        BusinessDocument doc = newBooking();
        BusinessDocumentLineGroup group = spec(null, colour("Navy", "1000", "2"), colour("Black", "500", "2"));
        group.getFabric().setFabricType("Solid Dyed Spandex");
        doc.addLineGroup(group);

        assertThatThrownBy(() -> service.save(doc))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Solid Dyed Spandex is a single-colour fabric");
    }

    @Test
    void aSingleColourFabricTakesOneColourPerLineAndAMultiColourFabricTakesMany() {
        BusinessDocument doc = newBooking();
        BusinessDocumentLineGroup solid = spec(null, colour("Navy", "1000", "2"));
        solid.getFabric().setFabricType("Greige Solid Dyed Lungi");
        BusinessDocumentLineGroup print = spec(null, colour("Navy", "1000", "2"), colour("Black", "500", "2"));
        print.getFabric().setFabricType("Solid Dyed Print");
        doc.addLineGroup(solid);
        doc.addLineGroup(print);

        assertThat(service.save(doc).getLineGroups()).hasSize(2);
    }

    @Test
    void aColourWithoutAQuantityIsRefused() {
        BusinessDocument doc = newBooking();
        doc.addLineGroup(spec(null, colour("Olive", "0", "2")));

        assertThatThrownBy(() -> service.save(doc))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Olive needs a quantity");
    }

    @Test
    void aRevisionCarriesTheTermsAcross() {
        BusinessDocument original = newBooking();
        original.setId(5L);
        original.setOrganizationId(ORG);
        original.setDocumentType(DocumentType.BOOKING);
        original.setBusinessUnit(DocumentRefs.unit(10L));
        original.setDocumentNo("BKAF000030");
        ReflectionTestUtils.setField(original, "createdBy", "tester");
        original.addTerm(new BusinessDocumentTerm(1, "Dead Yarn & Naps Should Be Not Allowed."));
        original.transitionTo(BusinessDocumentStatus.SUBMITTED);
        original.transitionTo(BusinessDocumentStatus.APPROVED);
        when(repository.findScopedWithLines(5L, ORG)).thenReturn(Optional.of(original));

        BusinessDocument revision = service.revise(5L, "price change");

        assertThat(revision.getTerms()).extracting(BusinessDocumentTerm::getBodyText)
            .containsExactly("Dead Yarn & Naps Should Be Not Allowed.");
        assertThat(revision.getTerms().get(0)).isNotSameAs(original.getTerms().get(0));
    }

    // ------------------------------------------------------------------ marketing team (ADM-4, ADM-7)

    private static MarketingTeam team(Long id, String name) {
        MarketingTeam team = new MarketingTeam("T" + id, name);
        team.setId(id);
        return team;
    }

    private static RowScope restrictedTo(Long teamId) {
        return new RowScope(false, java.util.Map.of(ScopeDimension.MARKETING_TEAM, java.util.Set.of(teamId)));
    }

    @Test
    void aRestrictedUsersBookingIsFiledUnderTheirOwnTeamWhateverTheRequestNames() {
        scope = restrictedTo(3L);
        BusinessDocument booking = newBooking();
        booking.setMarketingTeamId(5L);          // another team's id, sent by hand

        BusinessDocument saved = service.save(booking);

        assertThat(saved.getMarketingTeam().getId()).isEqualTo(3L);
        verifyNoInteractions(teams);             // the request's team was never even looked up
    }

    @Test
    void anUnrestrictedUserInATeamFilesTheBookingUnderThatTeamWhateverTheRequestNames() {
        BusinessDocument booking = newBooking();
        booking.setMarketingTeamId(5L);

        assertThat(service.save(booking).getMarketingTeam().getId()).isEqualTo(3L);
    }

    @Test
    void aUserInNoMarketingTeamCannotCreateABooking() {
        when(scopes.findValuesHeldOn(any(), eq(ScopeDimension.MARKETING_TEAM), any())).thenReturn(List.of());

        assertThat(service.canCreate()).isFalse();
        assertThatThrownBy(() -> service.save(newBooking()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not in a marketing team");
        verify(repository, never()).save(any());
    }

    @Test
    void aRestrictedUserInMoreThanOneTeamCannotRaiseABooking() {
        scope = new RowScope(false, java.util.Map.of(ScopeDimension.MARKETING_TEAM, java.util.Set.of(3L, 4L)));

        assertThatThrownBy(() -> service.save(newBooking()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("more than one marketing team");
    }

    // ------------------------------------------------------------------ marketing person and ownership

    @Test
    void theMarketingPersonIsNeverTakenFromTheRequest() {
        BusinessDocument booking = newBooking();
        booking.setMarketingPersonId(42L);      // someone else, sent by hand

        service.save(booking);

        verify(references, never()).user(anyLong(), eq(42L));
    }

    private BusinessDocument savedBookingCreatedBy(String username) {
        BusinessDocument doc = newBooking();
        doc.setId(8L);
        doc.setOrganizationId(ORG);
        doc.setDocumentType(DocumentType.BOOKING);
        doc.setBusinessUnit(DocumentRefs.unit(10L));
        ReflectionTestUtils.setField(doc, "createdBy", username);
        when(repository.findScopedWithLines(8L, ORG)).thenReturn(Optional.of(doc));
        return doc;
    }

    @Test
    void anotherUsersBookingCannotBeOpenedEditedOrDeleted() {
        savedBookingCreatedBy("someone-else");
        BusinessDocument edit = newBooking();
        edit.setId(8L);

        assertThatThrownBy(() -> service.get(8L)).hasMessageContaining("Booking not found");
        assertThatThrownBy(() -> service.save(edit)).hasMessageContaining("Booking not found");
        assertThatThrownBy(() -> service.delete(8L)).hasMessageContaining("Booking not found");
    }

    @Test
    void theCreatorCanOpenTheirOwnBooking() {
        BusinessDocument mine = savedBookingCreatedBy("tester");

        assertThat(service.get(8L)).isSameAs(mine);
    }

    @Test
    void theListIsNarrowedToTheSignedInUsersBookings() {
        when(repository.search(any(), any(), any(), any(), any(), any(), any(), any(), eq("tester"), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());

        service.search(null, null, null, null, org.springframework.data.domain.Pageable.unpaged());

        verify(repository).search(eq(ORG), eq(10L), eq(DocumentType.BOOKING), isNull(), isNull(), isNull(), isNull(),
            any(RowScope.class), eq("tester"), any());
    }
}
