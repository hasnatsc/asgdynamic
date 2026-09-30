package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.MarketingTeam;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.documents.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The approval engine: routing by team and amount, levels in order, four-eyes, and the default
 * rule for a type nobody has configured.
 */
class ApprovalServiceTest {

    private static final Long ORG = 1L;
    private static final Long UNIT = 10L;
    private static final Long DOC_ID = 100L;
    private static final Long MANAGER_ROLE = 5L;
    private static final Long DIRECTOR = 9L;

    private BusinessDocumentRepository repository;
    private ApprovalHistoryRepository history;
    private ApprovalRequestRepository requests;
    private ApprovalMatrixRepository matrices;
    private ApprovalActors actors;
    /** Handles nothing unless a test says so. */
    private ApprovalListener listener;
    private ApprovalNotifier notifier;
    private ApprovalService service;

    /** The saved request, as the fake repository holds it. */
    private ApprovalRequest live;
    /** The actor's row scope: unrestricted unless a test narrows it. */
    private RowScope scope = RowScope.unrestrictedScope();

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        history = mock(ApprovalHistoryRepository.class);
        requests = mock(ApprovalRequestRepository.class);
        matrices = mock(ApprovalMatrixRepository.class);
        actors = mock(ApprovalActors.class);
        listener = mock(ApprovalListener.class);
        notifier = mock(ApprovalNotifier.class);
        ApprovalLabels labels = mock(ApprovalLabels.class);
        when(labels.approver(any(), any())).thenAnswer(i -> String.valueOf(i.getArgument(0, Approver.class).kind()));

        when(history.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(requests.save(any())).thenAnswer(i -> {
            live = i.getArgument(0);
            if (live.getId() == null) live.setId(500L);
            return live;
        });
        when(requests.findById(any())).thenAnswer(i -> Optional.ofNullable(live));
        when(requests.findByDocumentIdAndPendingTrue(DOC_ID))
            .thenAnswer(i -> Optional.ofNullable(live).filter(ApprovalRequest::isPending));
        when(matrices.findTeamWise(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(matrices.findUnitWide(any(), any(), any())).thenReturn(Optional.empty());

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return UNIT; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "whoever"; }
            @Override public RowScope rowScope()       { return scope; }
        };
        service = new ApprovalService(repository, history, requests, matrices, actors, labels, context,
            List.of(new com.asg.fabricerp.fabric.booking.BookingSubmissionCheck()), List.of(listener),
            notifier);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ fixtures

    private void actingAs(Long userId, String username, Set<Long> roles, String... authorities) {
        when(actors.current()).thenReturn(new Approver.Actor(userId, username, roles, Set.of(authorities)));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null,
            java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private BusinessDocument booking(String creator, BusinessDocumentStatus status, String amount, Long teamId) {
        BusinessDocument doc = new BusinessDocument();
        doc.setId(DOC_ID);
        doc.setOrganizationId(ORG);
        doc.setDocumentType(DocumentType.BOOKING);
        doc.setDocumentNo("BKAF000001");
        doc.setDocumentDate(LocalDate.now());
        doc.setBusinessUnit(DocumentRefs.unit(UNIT));
        doc.setParty(DocumentRefs.party(77L));
        doc.setRequiredDate(LocalDate.now().plusMonths(2));
        if (teamId != null) doc.stampMarketingTeam(DocumentRefs.team(teamId));
        setCreatedBy(doc, creator);

        BusinessDocumentColorLine line = new BusinessDocumentColorLine();
        line.setQuantity(new BigDecimal("10"));
        line.setRate(new BigDecimal("2.50"));
        line.setColorName("Navy");
        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.addColorLine(line);
        doc.addLineGroup(group);
        setAmount(doc, amount);
        if (status == BusinessDocumentStatus.SUBMITTED) doc.transitionTo(BusinessDocumentStatus.SUBMITTED);

        when(repository.findScopedWithLines(DOC_ID, ORG)).thenReturn(Optional.of(doc));
        when(repository.findScoped(DOC_ID, ORG)).thenReturn(Optional.of(doc));
        return doc;
    }

    /** Levels in order; each is {roleId or null, userId or null, min, max}. */
    private ApprovalMatrix matrix(Long id, Long teamId, boolean active, ApprovalLevel... levels) {
        ApprovalMatrix m = new ApprovalMatrix(ORG, UNIT, teamId, DocumentType.BOOKING, teamId == null ? "Booking" : "Team booking");
        m.setId(id);
        m.setActive(active);
        m.replaceLevels(List.of(levels));
        when(matrices.findScoped(id, ORG)).thenReturn(Optional.of(m));
        if (teamId == null) when(matrices.findUnitWide(ORG, UNIT, DocumentType.BOOKING)).thenReturn(Optional.of(m));
        else when(matrices.findTeamWise(ORG, UNIT, DocumentType.BOOKING, teamId)).thenReturn(Optional.of(m));
        return m;
    }

    private static ApprovalLevel role(Long roleId, String min, String max) {
        return new ApprovalLevel(roleId, null, min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max));
    }

    private static ApprovalLevel user(Long userId) {
        return new ApprovalLevel(null, userId, null, null);
    }

    private static void setCreatedBy(BusinessDocument doc, String creator) {
        try {
            var method = com.asg.fabricerp.common.AuditableEntity.class
                .getDeclaredMethod("stampCreated", String.class, java.time.LocalDateTime.class);
            method.setAccessible(true);
            method.invoke(doc, creator, java.time.LocalDateTime.now());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setAmount(BusinessDocument doc, String amount) {
        try {
            var field = BusinessDocument.class.getDeclaredField("subtotalAmount");
            field.setAccessible(true);
            field.set(doc, amount == null ? null : new BigDecimal(amount));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void submitAs(String maker) {
        actingAs(1L, maker, Set.of(), "SCREEN_BOOKING_CREATE");
        service.submit(DOC_ID);
    }

    // ------------------------------------------------------------------ submit

    @Test
    void submitRequiresTheMakerRole() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        actingAs(1L, "maker", Set.of());

        assertThatThrownBy(() -> service.submit(DOC_ID))
            .isInstanceOf(AccessDeniedException.class).hasMessageContaining("SCREEN_BOOKING_CREATE");
    }

    @Test
    void submitRefusesADocumentWithNoLines() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        doc.setLineGroups(List.of());
        actingAs(1L, "maker", Set.of(), "SCREEN_BOOKING_CREATE");

        assertThatThrownBy(() -> service.submit(DOC_ID)).isInstanceOf(IllegalStateException.class).hasMessageContaining("no lines");
    }

    @Test
    void aTypeWithNoMatrixGetsOneLevelUnderTheDefaultRule() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        submitAs("maker");

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.SUBMITTED);
        assertThat(live.getMatrixId()).isNull();
        assertThat(live.getTotalLevels()).isEqualTo(1);
        assertThat(live.getRaisedBy()).isEqualTo("maker");
        ArgumentCaptor<ApprovalHistory> recorded = ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(history).save(recorded.capture());
        assertThat(recorded.getValue().getAction()).isEqualTo(ApprovalAction.SUBMITTED);
        assertThat(recorded.getValue().getRequestId()).isEqualTo(500L);
    }

    @Test
    void theTeamsOwnMatrixIsPreferredAndOnlyTheLevelsCoveringTheAmountApply() {
        booking("maker", BusinessDocumentStatus.DRAFT, "250000", 3L);
        matrix(20L, null, true, role(MANAGER_ROLE, null, null));
        matrix(30L, 3L, true, role(MANAGER_ROLE, null, null), role(MANAGER_ROLE, "100000", null), user(DIRECTOR));
        submitAs("maker");

        assertThat(live.getMatrixId()).isEqualTo(30L);
        assertThat(live.getOwningTeamId()).isEqualTo(3L);
        assertThat(live.getTotalLevels()).isEqualTo(3);
    }

    @Test
    void aSmallAmountSkipsTheLevelsBandedAboveIt() {
        booking("maker", BusinessDocumentStatus.DRAFT, "5000", 3L);
        matrix(30L, 3L, true, role(MANAGER_ROLE, null, null), role(MANAGER_ROLE, "100000", null), user(DIRECTOR));
        submitAs("maker");

        assertThat(live.getTotalLevels()).isEqualTo(2);
    }

    @Test
    void anInactiveTeamMatrixFallsBackToTheUnitWideOne() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", 3L);
        matrix(20L, null, true, role(MANAGER_ROLE, null, null));
        matrix(30L, 3L, false, user(DIRECTOR));
        submitAs("maker");

        assertThat(live.getMatrixId()).isEqualTo(20L);
    }

    @Test
    void aMatrixWithNoLevelForTheAmountRefusesTheSubmission() {
        booking("maker", BusinessDocumentStatus.DRAFT, "50", null);
        matrix(20L, null, true, role(MANAGER_ROLE, "1000", null));
        actingAs(1L, "maker", Set.of(), "SCREEN_BOOKING_CREATE");

        assertThatThrownBy(() -> service.submit(DOC_ID))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("no level covering");
    }

    @Test
    void submittingTwiceIsRefused() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        submitAs("maker");

        assertThatThrownBy(() -> service.submit(DOC_ID))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("already awaiting approval");
    }

    @Test
    void aBookingWithAnUnpricedColourIsRefusedAndStaysADraft() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        doc.getLineGroups().get(0).getColorLines().get(0).setRate(null);
        actingAs(1L, "maker", Set.of(), "SCREEN_BOOKING_CREATE");

        assertThatThrownBy(() -> service.submit(DOC_ID))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Navy on fabric line 1 has no price");
        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.DRAFT);
        verify(requests, never()).save(any());
    }

    @Test
    void aBookingWithNoBuyerIsRefused() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        doc.setParty(null);
        actingAs(1L, "maker", Set.of(), "SCREEN_BOOKING_CREATE");

        assertThatThrownBy(() -> service.submit(DOC_ID))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Choose the buyer");
    }

    @Test
    void aFabricLineWithNoColoursIsRefused() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        doc.addLineGroup(new BusinessDocumentLineGroup());
        actingAs(1L, "maker", Set.of(), "SCREEN_BOOKING_CREATE");

        assertThatThrownBy(() -> service.submit(DOC_ID))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Fabric line 2").hasMessageContaining("no colours");
    }

    // ------------------------------------------------------------------ decide

    @Test
    void underTheDefaultRuleAnyoneWithTheApproveVerbApproves() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        submitAs("maker");
        actingAs(2L, "checker", Set.of(), "SCREEN_BOOKING_APPROVE");

        service.approve(DOC_ID, null);

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
        assertThat(live.isPending()).isFalse();
        assertThat(live.getOutcome()).isEqualTo(ApprovalDecision.APPROVED);
    }

    @Test
    void aStoreDocumentIsLeftReadyToPostAndActsOnlyWhenPosted() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        doc.setDocumentType(DocumentType.GREIGE_RECEIVE);
        when(listener.handles(any())).thenReturn(true);
        actingAs(1L, "maker", Set.of(), "SCREEN_GR_CREATE");
        service.submit(DOC_ID);
        actingAs(2L, "checker", Set.of(), "SCREEN_GR_APPROVE");

        service.approve(DOC_ID, null);

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.READY_TO_POST);
        assertThat(live.getOutcome()).isEqualTo(ApprovalDecision.APPROVED);
        // Its consequences - stock moved, lots reserved - wait for the posting.
        verify(listener, never()).onApproved(any());
    }

    @Test
    void anOrderRunsItsListenersWhenItsLastLevelSigns() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        when(listener.handles(DocumentType.BOOKING)).thenReturn(true);
        submitAs("maker");
        actingAs(2L, "checker", Set.of(), "SCREEN_BOOKING_APPROVE");

        service.approve(DOC_ID, null);

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
        verify(listener).onApproved(doc);
    }

    @Test
    void withoutTheVerbTheDefaultRuleRefuses() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        submitAs("maker");
        actingAs(2L, "clerk", Set.of());

        assertThatThrownBy(() -> service.approve(DOC_ID, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void whoeverSubmittedCannotDecideItHoweverSenior() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        submitAs("maker");
        actingAs(1L, "maker", Set.of(MANAGER_ROLE), "SCREEN_BOOKING_APPROVE");

        assertThatThrownBy(() -> service.approve(DOC_ID, null))
            .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Segregation of duties");
    }

    @Test
    void levelsAreSignedInOrderAndOnlyTheLastApprovesTheDocument() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "250000", 3L);
        matrix(30L, 3L, true, role(MANAGER_ROLE, null, null), user(DIRECTOR));
        submitAs("maker");

        actingAs(2L, "manager", Set.of(MANAGER_ROLE));
        service.approve(DOC_ID, "fine");
        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.SUBMITTED);
        assertThat(live.getCurrentLevel()).isEqualTo(2);

        // the manager cannot sign the director's level
        assertThatThrownBy(() -> service.approve(DOC_ID, null))
            .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Level 2 of 2");

        actingAs(DIRECTOR, "director", Set.of());
        service.approve(DOC_ID, null);
        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
        verify(history, times(3)).save(any());   // submitted, level 1, level 2
    }

    @Test
    void returnSendsItBackAsADraftAndNeedsAReason() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        submitAs("maker");
        actingAs(2L, "checker", Set.of(), "SCREEN_BOOKING_APPROVE");

        assertThatThrownBy(() -> service.returnToMaker(DOC_ID, " "))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Say why");

        service.returnToMaker(DOC_ID, "Wrong buyer");
        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.DRAFT);
        assertThat(live.getOutcome()).isEqualTo(ApprovalDecision.RETURNED);
        assertThat(doc.getStatus().isEditable()).isTrue();
    }

    @Test
    void rejectRefusesItAndEndsTheRequest() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        submitAs("maker");
        actingAs(2L, "checker", Set.of(), "SCREEN_BOOKING_APPROVE");

        service.reject(DOC_ID, "Price below break-even");

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.REJECTED);
        assertThat(live.isPending()).isFalse();
        assertThat(live.getOutcome()).isEqualTo(ApprovalDecision.REJECTED);
    }

    @Test
    void aDocumentSubmittedBeforeTheEngineIsEnrolledWithItsCreatorAsTheRaiser() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.SUBMITTED, "100", null);

        actingAs(3L, "maker", Set.of(), "SCREEN_BOOKING_APPROVE");
        assertThatThrownBy(() -> service.approve(DOC_ID, null)).isInstanceOf(AccessDeniedException.class);

        actingAs(2L, "checker", Set.of(), "SCREEN_BOOKING_APPROVE");
        service.approve(DOC_ID, null);
        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
    }

    @Test
    void aDocumentNotAwaitingApprovalCannotBeDecided() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        actingAs(2L, "checker", Set.of(), "SCREEN_BOOKING_APPROVE");

        assertThatThrownBy(() -> service.approve(DOC_ID, null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("not awaiting approval");
    }

    // ------------------------------------------------------------------ read

    @Test
    void theStateSaysWhoIsNextAndWhetherItIsYou() {
        booking("maker", BusinessDocumentStatus.DRAFT, "250000", 3L);
        matrix(30L, 3L, true, role(MANAGER_ROLE, null, null), user(DIRECTOR));
        submitAs("maker");
        when(requests.findFirstByDocumentIdOrderByIdDesc(DOC_ID)).thenAnswer(i -> Optional.of(live));

        actingAs(2L, "manager", Set.of(MANAGER_ROLE));
        ApprovalStateView mine = service.stateOf(DOC_ID);
        assertThat(mine.pending()).isTrue();
        assertThat(mine.level()).isEqualTo(1);
        assertThat(mine.totalLevels()).isEqualTo(2);
        assertThat(mine.canAct()).isTrue();

        actingAs(1L, "maker", Set.of(MANAGER_ROLE));
        ApprovalStateView own = service.stateOf(DOC_ID);
        assertThat(own.canAct()).isFalse();
        assertThat(own.waitingReason()).contains("You submitted it");
    }

    @Test
    void theInboxAsksOneQueryForWhatTheCallerMaySignNow() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "250000", 3L);
        matrix(30L, 3L, true, role(MANAGER_ROLE, null, null), user(DIRECTOR));
        submitAs("maker");
        when(requests.inbox(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), anyBoolean(), any(),
                anyBoolean(), any(), any()))
            .thenAnswer(i -> new org.springframework.data.domain.PageImpl<>(List.of(live)));
        when(repository.findScopedWithParty(any(), eq(ORG))).thenReturn(List.of(doc));

        actingAs(2L, "manager", Set.of(MANAGER_ROLE));
        var page = service.inbox(ApprovalService.InboxFilter.NONE, org.springframework.data.domain.PageRequest.of(0, 25));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).documentId()).isEqualTo(DOC_ID);
        // the manager's roles and user id went into the query; an unrestricted user sees every team
        verify(requests).inbox(eq(ORG), eq(UNIT), eq(2L), eq("manager"), isNull(), isNull(),
            eq(true), any(), eq(true), eq(Set.of(MANAGER_ROLE)), eq(false), any(), any());
    }

    // ------------------------------------------------------------------ routing index (centralised inbox)

    @Test
    void theRequestCarriesWhoItsCurrentLevelWaitsFor() {
        booking("maker", BusinessDocumentStatus.DRAFT, "250000", 3L);
        matrix(30L, 3L, true, role(MANAGER_ROLE, null, null), user(DIRECTOR));
        submitAs("maker");
        assertThat(live.getCurrentRoleId()).isEqualTo(MANAGER_ROLE);
        assertThat(live.getCurrentUserId()).isNull();

        actingAs(2L, "manager", Set.of(MANAGER_ROLE));
        service.approve(DOC_ID, null);
        assertThat(live.getCurrentRoleId()).isNull();
        assertThat(live.getCurrentUserId()).isEqualTo(DIRECTOR);

        actingAs(DIRECTOR, "director", Set.of());
        service.approve(DOC_ID, null);
        assertThat(live.getCurrentRoleId()).isNull();
        assertThat(live.getCurrentUserId()).isNull();   // settled: waits for nobody
    }

    // ------------------------------------------------------------------ team-only approval

    @Test
    void aRoleHolderFromAnotherTeamCannotDecideThisTeamsDocument() {
        booking("maker", BusinessDocumentStatus.DRAFT, "250000", 3L);
        matrix(30L, 3L, true, role(MANAGER_ROLE, null, null));
        submitAs("maker");

        scope = new RowScope(false, java.util.Map.of(com.asg.fabricerp.common.ScopeDimension.MARKETING_TEAM, Set.of(4L)));
        actingAs(2L, "other-team-manager", Set.of(MANAGER_ROLE));

        assertThatThrownBy(() -> service.approve(DOC_ID, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
    }

    @Test
    void aRoleHolderInTheDocumentsTeamDecidesIt() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "250000", 3L);
        matrix(30L, 3L, true, role(MANAGER_ROLE, null, null));
        submitAs("maker");

        scope = new RowScope(false, java.util.Map.of(com.asg.fabricerp.common.ScopeDimension.MARKETING_TEAM, Set.of(3L)));
        actingAs(2L, "team-manager", Set.of(MANAGER_ROLE));
        service.approve(DOC_ID, null);

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
    }

    @Test
    void aPersonNamedOnTheLevelDecidesItWhateverTheirOwnTeam() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "250000", 3L);
        matrix(30L, 3L, true, user(DIRECTOR));
        submitAs("maker");

        scope = new RowScope(false, java.util.Map.of(com.asg.fabricerp.common.ScopeDimension.MARKETING_TEAM, Set.of(4L)));
        actingAs(DIRECTOR, "director", Set.of());
        assertThat(service.canReview(doc)).isTrue();
        service.approve(DOC_ID, null);

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
    }

    @Test
    void someoneWhoIsNotTheApproverCannotReviewIt() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "250000", 3L);
        matrix(30L, 3L, true, user(DIRECTOR));
        submitAs("maker");

        actingAs(2L, "bystander", Set.of(MANAGER_ROLE), "SCREEN_BOOKING_APPROVE");
        assertThat(service.canReview(doc)).isFalse();
    }

    // ------------------------------------------------------------------ correct and submit again

    @Test
    void aRejectedDocumentCanBeCorrectedAndSubmittedAgain() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        submitAs("maker");
        ApprovalRequest first = live;
        actingAs(2L, "checker", Set.of(), "SCREEN_BOOKING_APPROVE");
        service.reject(DOC_ID, "Price below break-even");
        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.REJECTED);

        submitAs("maker");

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.SUBMITTED);
        assertThat(live).isNotSameAs(first);
        assertThat(live.isPending()).isTrue();
        ArgumentCaptor<ApprovalHistory> saved = ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(history, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ApprovalHistory::getAction)
            .containsExactly(ApprovalAction.SUBMITTED, ApprovalAction.REJECTED, ApprovalAction.RESUBMITTED);
    }

    @Test
    void aReturnedDocumentIsADraftAndCanBeSubmittedAgain() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        submitAs("maker");
        actingAs(2L, "checker", Set.of(), "SCREEN_BOOKING_APPROVE");
        service.returnToMaker(DOC_ID, "Wrong buyer");

        submitAs("maker");

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.SUBMITTED);
        assertThat(live.isPending()).isTrue();
    }

    // ------------------------------------------------------------------ deadlines

    private static ApprovalLevel timed(Long roleId, Long userId, int minutes, TimeoutAction action, Long escalateUser) {
        return new ApprovalLevel(roleId, userId, null, null, minutes, action, null, escalateUser);
    }

    /** Moves one of the live request's clock fields into the past, as though that time had come. */
    private void backdate(String field) {
        try {
            var f = ApprovalRequest.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(live, java.time.LocalDateTime.now().minusSeconds(30));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private List<ApprovalAction> recordedActions() {
        ArgumentCaptor<ApprovalHistory> recorded = ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(history, atLeastOnce()).save(recorded.capture());
        return recorded.getAllValues().stream().map(ApprovalHistory::getAction).toList();
    }

    @Test
    void submittingStartsTheFirstLevelsClockAndTellsItsApprovers() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        matrix(20L, null, true, timed(MANAGER_ROLE, null, 60, TimeoutAction.REMIND, null));
        java.time.LocalDateTime before = java.time.LocalDateTime.now();
        submitAs("maker");

        assertThat(live.getLevelDueAt()).isBetween(before.plusMinutes(60), before.plusMinutes(61));
        assertThat(live.getLevelRemindAt()).isBetween(before.plusMinutes(45), before.plusMinutes(46));
        assertThat(live.isLevelReminded()).isFalse();
        verify(notifier).awaiting(any(), eq(live), eq(Approver.role(MANAGER_ROLE)));
    }

    @Test
    void aLevelWithoutATimeLimitHasNoDeadline() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        matrix(20L, null, true, role(MANAGER_ROLE, null, null));
        submitAs("maker");

        assertThat(live.getLevelDueAt()).isNull();
        assertThat(live.getLevelRemindAt()).isNull();
    }

    @Test
    void theReminderGoesOutOnceItIsDueAndOnlyOnce() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        matrix(20L, null, true, timed(MANAGER_ROLE, null, 60, TimeoutAction.REMIND, null));
        submitAs("maker");

        service.remind(live.getId());   // not due yet
        verify(notifier, never()).reminder(any(), any(), any(), any());

        backdate("levelRemindAt");
        service.remind(live.getId());
        service.remind(live.getId());
        verify(notifier, times(1)).reminder(any(), eq(live), eq(Approver.role(MANAGER_ROLE)), any());
        assertThat(live.isLevelReminded()).isTrue();
    }

    @Test
    void remindOnTimeoutFlagsTheLevelOverdueAndKeepsWaitingOnce() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        matrix(20L, null, true, timed(MANAGER_ROLE, null, 60, TimeoutAction.REMIND, null));
        submitAs("maker");
        backdate("levelDueAt");

        service.timeOut(live.getId());
        service.timeOut(live.getId());   // a level times out once

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.SUBMITTED);
        assertThat(live.isPending()).isTrue();
        assertThat(live.isLevelTimedOut()).isTrue();
        assertThat(live.getCurrentRoleId()).isEqualTo(MANAGER_ROLE);
        assertThat(recordedActions()).containsExactly(ApprovalAction.SUBMITTED, ApprovalAction.OVERDUE);
        verify(notifier, times(1)).overdue(any(), eq(live), eq(Approver.role(MANAGER_ROLE)), contains("still waiting"));
    }

    @Test
    void escalatingHandsTheLevelToItsTargetWhoAloneMayThenDecideIt() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        matrix(20L, null, true, timed(MANAGER_ROLE, null, 60, TimeoutAction.ESCALATE, DIRECTOR));
        submitAs("maker");
        backdate("levelDueAt");

        service.timeOut(live.getId());

        assertThat(live.isEscalated()).isTrue();
        assertThat(live.getCurrentUserId()).isEqualTo(DIRECTOR);
        assertThat(live.getCurrentRoleId()).isNull();
        assertThat(recordedActions()).containsExactly(ApprovalAction.SUBMITTED, ApprovalAction.ESCALATED);
        verify(notifier).escalated(any(), eq(live), eq(Approver.role(MANAGER_ROLE)), eq(Approver.user(DIRECTOR)), anyString());

        actingAs(2L, "manager", Set.of(MANAGER_ROLE));
        assertThatThrownBy(() -> service.approve(DOC_ID, null)).isInstanceOf(AccessDeniedException.class);
        actingAs(DIRECTOR, "director", Set.of());
        service.approve(DOC_ID, null);
        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
    }

    @Test
    void autoApproveSignsTheLevelAndStartsTheNextLevelsClock() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        matrix(20L, null, true, timed(MANAGER_ROLE, null, 60, TimeoutAction.AUTO_APPROVE, null),
            timed(null, DIRECTOR, 120, TimeoutAction.REMIND, null));
        submitAs("maker");
        backdate("levelDueAt");

        service.timeOut(live.getId());

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.SUBMITTED);
        assertThat(live.getCurrentLevel()).isEqualTo(2);
        assertThat(live.getCurrentUserId()).isEqualTo(DIRECTOR);
        assertThat(live.isLevelTimedOut()).isFalse();
        assertThat(live.getLevelDueAt()).isAfter(java.time.LocalDateTime.now().plusMinutes(119));
        assertThat(recordedActions()).containsExactly(ApprovalAction.SUBMITTED, ApprovalAction.APPROVED);
        verify(notifier).awaiting(any(), eq(live), eq(Approver.user(DIRECTOR)));
    }

    @Test
    void autoApproveAtTheLastLevelApprovesTheDocumentAndRunsItsConsequences() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        matrix(20L, null, true, timed(MANAGER_ROLE, null, 30, TimeoutAction.AUTO_APPROVE, null));
        when(listener.handles(DocumentType.BOOKING)).thenReturn(true);
        submitAs("maker");
        backdate("levelDueAt");

        service.timeOut(live.getId());

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
        assertThat(live.isPending()).isFalse();
        assertThat(live.getOutcome()).isEqualTo(ApprovalDecision.APPROVED);
        verify(listener).onApproved(doc);
        verify(notifier).decided(eq(doc), eq(live), eq(ApprovalDecision.APPROVED), contains("approved it automatically"), eq(true));
    }

    @Test
    void autoRejectRefusesTheDocumentWithTheReasonOnItsTimeline() {
        BusinessDocument doc = booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        matrix(20L, null, true, timed(MANAGER_ROLE, null, 1440, TimeoutAction.AUTO_REJECT, null));
        submitAs("maker");
        backdate("levelDueAt");

        service.timeOut(live.getId());

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.REJECTED);
        ArgumentCaptor<ApprovalHistory> recorded = ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(history, times(2)).save(recorded.capture());
        assertThat(recorded.getValue().getRemarks()).contains("not decided within 1 day").contains("rejected it automatically");
    }

    @Test
    void aTimeoutWhoseDecisionIsRefusedIsMarkedOverdueInstead() {
        booking("maker", BusinessDocumentStatus.DRAFT, "100", null);
        matrix(20L, null, true, timed(MANAGER_ROLE, null, 30, TimeoutAction.AUTO_APPROVE, null));
        submitAs("maker");
        backdate("levelDueAt");

        service.timeOutFailed(live.getId(), "The revision draws more than is left.");

        assertThat(live.isPending()).isTrue();
        assertThat(live.isLevelTimedOut()).isTrue();
        assertThat(recordedActions()).containsExactly(ApprovalAction.SUBMITTED, ApprovalAction.OVERDUE);
        verify(notifier).overdue(any(), eq(live), any(), contains("could not be made"));
    }

    @Test
    void aLevelEscalatesOnlyToSomeoneAndOnlyWhenEscalating() {
        assertThatThrownBy(() -> new ApprovalLevel(MANAGER_ROLE, null, null, null, 60, TimeoutAction.ESCALATE, null, null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("role or one person");
        ApprovalLevel remind = new ApprovalLevel(MANAGER_ROLE, null, null, null, 60, TimeoutAction.REMIND, null, DIRECTOR);
        assertThat(remind.getEscalateUserId()).isNull();
        assertThat(remind.escalationApprover()).isNull();
        assertThatThrownBy(() -> new ApprovalLevel(MANAGER_ROLE, null, null, null, 0, TimeoutAction.REMIND, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
