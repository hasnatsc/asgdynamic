package com.asg.fabricerp.approval;

import com.asg.fabricerp.common.BusinessUnitRepository;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.documents.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The Commercial maker → checker → approver triad against real PostgreSQL: Flyway builds the schema
 * through V31, V31's grants land on the legacy role shells, and a proforma invoice is signed through
 * the real engine - the centralised inbox's query included.
 *
 * <p>Opt-in, like the other {@code *DatabaseIT}s. Point {@code FABRICERP_IT_DB_URL} at a
 * <b>throwaway</b> database:
 * <pre>
 *   createdb fabricerp_it
 *   FABRICERP_IT_DB_URL=jdbc:postgresql://localhost:5432/fabricerp_it mvn -Dtest=CommercialApprovalDatabaseIT test
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FABRICERP_IT_DB_URL", matches = "jdbc:postgresql:.+")
class CommercialApprovalDatabaseIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FABRICERP_IT_DB_URL"));
    }

    @Autowired private ApprovalService approvals;
    @Autowired private BusinessDocumentRepository repository;
    @Autowired private BusinessUnitRepository units;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;

    @MockitoBean private OrgContext context;
    @MockitoBean private ApprovalActors actors;

    private long orgId;
    private long unitId;
    /** Who is acting; the audit stamp on history rows follows it, as the real OrgContext would. */
    private Approver.Actor acting;
    private static int seq;

    @BeforeEach
    void setUp() {
        orgId = jdbc.queryForObject("SELECT id FROM org_organizations WHERE code = 'ASG'", Long.class);
        unitId = jdbc.queryForObject("SELECT id FROM org_business_units WHERE organization_id = ? AND code = 'AF'", Long.class, orgId);
        when(context.organizationId()).thenReturn(orgId);
        when(context.requireOrganizationId()).thenReturn(orgId);
        when(context.businessUnitId()).thenReturn(unitId);
        when(context.requireBusinessUnitId()).thenReturn(unitId);
        when(context.businessUnitCode()).thenReturn("AF");
        when(context.requireBusinessUnitCode()).thenReturn("AF");
        when(context.username()).thenAnswer(i -> acting == null ? "it" : acting.username());
        when(context.rowScope()).thenReturn(RowScope.unrestrictedScope());
        when(context.requireRowScope()).thenReturn(RowScope.unrestrictedScope());
        when(actors.current()).thenAnswer(i -> acting);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void v31GivesTheLegacyRoleShellsTheirStage() {
        assertThat(grant("ROLE_PI_MAKER", "PI")).isEqualTo("create amend delete");
        assertThat(grant("ROLE_LC_CHECKER", "LC")).isEqualTo("check");
        assertThat(grant("ROLE_CI_APPROVAL", "CI")).isEqualTo("approve");
        Long anyRole = jdbc.queryForObject("SELECT min(id) FROM sec_fabric_roles", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO sec_fabric_role_screen_grants (role_id, screen_code, can_view, can_check, version)
            VALUES (?, 'BOOKING', TRUE, TRUE, 0)""", anyRole))
            .hasMessageContaining("ck_fab_grant_check_commercial");
    }

    @Test
    void aProformaInvoiceIsCheckedThenApprovedThroughTheInbox() {
        long pi = draftProformaInvoice();
        String tag = "-" + System.nanoTime();
        actingAs(9001L, "pi.maker" + tag, "SCREEN_PI_CREATE");
        approvals.submit(pi);

        // Waiting for a checker: the checker's inbox lists it, the approver's does not.
        actingAs(9002L, "pi.checker" + tag, "SCREEN_PI_CHECK");
        assertThat(inboxHas(pi)).isTrue();
        actingAs(9003L, "pi.approver" + tag, "SCREEN_PI_APPROVE");
        assertThat(inboxHas(pi)).isFalse();
        assertThatThrownBy(() -> approvals.approve(pi, null)).isInstanceOf(AccessDeniedException.class);

        // A senior holding both verbs checks it - and may then not approve it too.
        actingAs(9004L, "pi.senior" + tag, "SCREEN_PI_CHECK", "SCREEN_PI_APPROVE");
        approvals.approve(pi, "matches the booking");
        assertThat(inboxHas(pi)).isFalse();
        assertThatThrownBy(() -> approvals.approve(pi, null))
            .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Segregation of duties");

        actingAs(9003L, "pi.approver" + tag, "SCREEN_PI_APPROVE");
        assertThat(inboxHas(pi)).isTrue();
        approvals.approve(pi, null);

        assertThat(repository.findById(pi).orElseThrow().getStatus()).isEqualTo(BusinessDocumentStatus.APPROVED);
        assertThat(approvals.historyOf(pi)).extracting(ApprovalHistory::getAction)
            .containsExactly(ApprovalAction.APPROVED, ApprovalAction.CHECKED, ApprovalAction.SUBMITTED);
    }

    // ------------------------------------------------------------------------------- fixtures

    private void actingAs(Long userId, String username, String... authorities) {
        acting = new Approver.Actor(userId, username, Set.of(), Set.of(authorities));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private boolean inboxHas(long documentId) {
        return approvals.inbox(ApprovalService.InboxFilter.NONE, PageRequest.of(0, 100)).getContent().stream()
            .anyMatch(row -> row.documentId() == documentId);
    }

    private String grant(String role, String screen) {
        return jdbc.queryForObject("""
            SELECT concat_ws(' ', CASE WHEN can_create THEN 'create' END, CASE WHEN can_amend THEN 'amend' END,
                                  CASE WHEN can_delete THEN 'delete' END, CASE WHEN can_check THEN 'check' END,
                                  CASE WHEN can_approve THEN 'approve' END)
            FROM sec_fabric_role_screen_grants g JOIN sec_fabric_roles r ON r.id = g.role_id
            WHERE r.name = ? AND g.screen_code = ? AND g.can_view""", String.class, role, screen);
    }

    private long draftProformaInvoice() {
        return tx.execute(s -> {
            BusinessDocument d = new BusinessDocument();
            d.setOrganizationId(orgId);
            d.setDocumentType(DocumentType.EXPORT_PROFORMA_INVOICE);
            d.setBusinessUnit(units.getReferenceById(unitId));
            d.setDocumentNo("IT-EPI-" + System.nanoTime() + "-" + (++seq));
            d.setDocumentDate(LocalDate.now());
            d.setCurrencyCode("USD");
            BusinessDocumentLineGroup g = new BusinessDocumentLineGroup();
            g.getFabric().setConstruction("40x40/133x72");
            BusinessDocumentColorLine l = new BusinessDocumentColorLine();
            l.setColorName("Navy");
            l.setQuantity(new BigDecimal("1000"));
            l.setRate(new BigDecimal("2.5"));
            g.addColorLine(l);
            d.addLineGroup(g);
            g.setOrganizationId(orgId);
            l.setOrganizationId(orgId);
            d.recalculateTotals();
            return repository.save(d).getId();
        });
    }
}
