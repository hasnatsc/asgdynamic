package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.documents.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The two things this engine exists to guarantee: a document can only be approved by
 * someone holding the role for its type, and never by whoever created it.
 */
class ApprovalServiceTest {

    private static final Long ORG = 1L;
    private static final Long DOC_ID = 100L;

    private BusinessDocumentRepository repository;
    private ApprovalHistoryRepository historyRepository;
    private ApprovalService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String... authorities) {
        var grants = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(username, null, grants));
    }

    private void setUpWithContextUsername(String username) {
        repository = mock(BusinessDocumentRepository.class);
        historyRepository = mock(ApprovalHistoryRepository.class);
        when(historyRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return 10L; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return username; }
            @Override public RowScope rowScope()         { return RowScope.unrestrictedScope(); }
        };
        service = new ApprovalService(repository, historyRepository, context);
    }

    private BusinessDocument bookingCreatedBy(String creator, BusinessDocumentStatus status) {
        BusinessDocument doc = new BusinessDocument();
        doc.setId(DOC_ID);
        doc.setOrganizationId(ORG);
        doc.setDocumentType(DocumentType.BOOKING);
        doc.setDocumentNo("BKAF000001");
        doc.setDocumentDate(LocalDate.now());
        setCreatedBy(doc, creator);

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setQuantity(new BigDecimal("10"));
        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.addColorLine(colorLine);
        doc.addLineGroup(group);

        if (status == BusinessDocumentStatus.SUBMITTED) {
            doc.transitionTo(BusinessDocumentStatus.SUBMITTED);
        }

        when(repository.findScopedWithLines(DOC_ID, ORG)).thenReturn(Optional.of(doc));
        return doc;
    }

    /** createdBy is stamped by OrgContextListener in production; set directly for the test. */
    private void setCreatedBy(BusinessDocument doc, String creator) {
        try {
            var method = com.asg.fabricerp.common.AuditableEntity.class
                .getDeclaredMethod("stampCreated", String.class, java.time.LocalDateTime.class);
            method.setAccessible(true);
            method.invoke(doc, creator, java.time.LocalDateTime.now());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void submitRequiresTheMakerRole() {
        setUpWithContextUsername("maker1");
        bookingCreatedBy("maker1", BusinessDocumentStatus.DRAFT);
        authenticateAs("maker1");   // no ROLE_BOOKING_MAKER

        assertThatThrownBy(() -> service.submit(DOC_ID))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("ROLE_BOOKING_MAKER");
    }

    @Test
    void submitTransitionsAndRecordsHistory() {
        setUpWithContextUsername("maker1");
        BusinessDocument doc = bookingCreatedBy("maker1", BusinessDocumentStatus.DRAFT);
        authenticateAs("maker1", "ROLE_BOOKING_MAKER");

        service.submit(DOC_ID);

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.SUBMITTED);
        ApprovalHistory recorded = capturedHistory();
        assertThat(recorded.getAction()).isEqualTo(ApprovalAction.SUBMITTED);
        assertThat(recorded.getFromStatus()).isEqualTo(BusinessDocumentStatus.DRAFT);
        assertThat(recorded.getToStatus()).isEqualTo(BusinessDocumentStatus.SUBMITTED);
    }

    @Test
    void submitRefusesADocumentWithNoLines() {
        setUpWithContextUsername("maker1");
        BusinessDocument doc = new BusinessDocument();
        doc.setId(DOC_ID);
        doc.setOrganizationId(ORG);
        doc.setDocumentType(DocumentType.BOOKING);
        doc.setDocumentNo("BKAF000002");
        when(repository.findScopedWithLines(DOC_ID, ORG)).thenReturn(Optional.of(doc));
        authenticateAs("maker1", "ROLE_BOOKING_MAKER");

        assertThatThrownBy(() -> service.submit(DOC_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no lines");
    }

    @Test
    void aDifferentUserWithTheApprovalRoleCanApprove() {
        setUpWithContextUsername("approver1");
        BusinessDocument doc = bookingCreatedBy("maker1", BusinessDocumentStatus.SUBMITTED);
        authenticateAs("approver1", "ROLE_APPROVAL");

        service.approve(DOC_ID, "looks correct");

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
        assertThat(capturedHistory().getRemarks()).isEqualTo("looks correct");
    }

    @Test
    void theCreatorCannotApproveTheirOwnDocumentEvenWithTheRole() {
        setUpWithContextUsername("maker1");
        bookingCreatedBy("maker1", BusinessDocumentStatus.SUBMITTED);
        authenticateAs("maker1", "ROLE_APPROVAL");   // holds the role, but is the creator

        assertThatThrownBy(() -> service.approve(DOC_ID, null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("Segregation of duties");
    }

    @Test
    void approvalIsRefusedWithoutTheRoleEvenForADifferentUser() {
        setUpWithContextUsername("someone-else");
        bookingCreatedBy("maker1", BusinessDocumentStatus.SUBMITTED);
        authenticateAs("someone-else");   // authenticated, but no ROLE_APPROVAL

        assertThatThrownBy(() -> service.approve(DOC_ID, null))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("ROLE_APPROVAL");
    }

    @Test
    void rejectIsNotSubjectToTheFourEyesRule() {
        // Sending your own submission back for correction is not the risk self-approval is.
        setUpWithContextUsername("maker1");
        BusinessDocument doc = bookingCreatedBy("maker1", BusinessDocumentStatus.SUBMITTED);
        authenticateAs("maker1", "ROLE_APPROVAL");

        service.reject(DOC_ID, "wrong construction");

        assertThat(doc.getStatus()).isEqualTo(BusinessDocumentStatus.REJECTED);
    }

    @Test
    void historyIsScopedToTheOrganizationLikeEveryOtherRead() {
        setUpWithContextUsername("viewer1");
        when(repository.findScopedWithLines(DOC_ID, ORG)).thenReturn(Optional.empty());
        authenticateAs("viewer1");

        assertThatThrownBy(() -> service.historyOf(DOC_ID))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private ApprovalHistory capturedHistory() {
        var captor = org.mockito.ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(historyRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
