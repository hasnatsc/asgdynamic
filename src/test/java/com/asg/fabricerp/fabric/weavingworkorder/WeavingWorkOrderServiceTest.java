package com.asg.fabricerp.fabric.weavingworkorder;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A fourth confirmation that {@link ParentLineDrawService} generalizes — this time across
 * families (PRODUCTION, not SALES) — plus the one thing that is genuinely different about
 * this type: it has no {@code revise()} method at all, so there is nothing to test the
 * absence of beyond the fact that {@link WeavingWorkOrderService} compiles without one.
 */
class WeavingWorkOrderServiceTest {

    private static final Long ORG = 1L;
    private static final Long UNIT = 10L;
    private static final Long BPO_ID = 800L;
    private static final Long BPO_LINE_ID = 801L;

    private BusinessDocumentRepository repository;
    private WeavingWorkOrderService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        DocumentNumberService numbering = mock(DocumentNumberService.class);
        when(numbering.next(DocumentType.WEAVING_WORK_ORDER)).thenReturn("WWOAF000001");

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return UNIT; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
        };

        ParentLineDrawService parentDraw = new ParentLineDrawService(repository, context);
        service = new WeavingWorkOrderService(repository, numbering, parentDraw, context);
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BusinessDocument bpoWithOpenLine(BigDecimal quantity) {
        BusinessDocument bpo = new BusinessDocument();
        bpo.setId(BPO_ID);
        bpo.setOrganizationId(ORG);
        bpo.setBusinessUnitId(UNIT);
        bpo.setDocumentType(DocumentType.BULK_PRODUCTION_ORDER);
        bpo.setDocumentNo("BPOAF000001");
        bpo.setPartyId(99L);

        BusinessDocumentLine line = new BusinessDocumentLine();
        line.setId(BPO_LINE_ID);
        line.setLineNo(1);
        line.setQuantity(quantity);
        bpo.addLine(line);

        when(repository.findScopedWithLines(BPO_ID, ORG)).thenReturn(Optional.of(bpo));
        return bpo;
    }

    private BusinessDocument woRequest(BigDecimal quantity) {
        BusinessDocument wo = new BusinessDocument();
        wo.setDocumentDate(LocalDate.now());
        wo.setParentDocumentId(BPO_ID);

        BusinessDocumentLine line = new BusinessDocumentLine();
        line.setSourceLineId(BPO_LINE_ID);
        line.setQuantity(quantity);
        wo.setLines(List.of(line));
        return wo;
    }

    @Test
    void commitsLoomCapacityAgainstTheBpoLine() {
        BusinessDocument bpo = bpoWithOpenLine(new BigDecimal("1200"));

        BusinessDocument wo = service.save(woRequest(new BigDecimal("400")));

        assertThat(wo.getDocumentType()).isEqualTo(DocumentType.WEAVING_WORK_ORDER);
        assertThat(bpo.getLines().get(0).getFulfilledQuantity()).isEqualByComparingTo("400");
    }

    @Test
    void refusesToCommitMoreThanTheBpoLineHasOutstanding() {
        bpoWithOpenLine(new BigDecimal("1200"));

        assertThatThrownBy(() -> service.save(woRequest(new BigDecimal("1300"))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingADraftReleasesTheLoomCommitment() {
        BusinessDocument bpo = bpoWithOpenLine(new BigDecimal("1200"));
        BusinessDocument wo = service.save(woRequest(new BigDecimal("400")));
        wo.setId(950L);
        when(repository.findScopedWithLines(950L, ORG)).thenReturn(Optional.of(wo));

        service.delete(950L);

        assertThat(bpo.getLines().get(0).outstandingQuantity()).isEqualByComparingTo("1200");
        assertThat(wo.getDeleted()).isTrue();
    }
}
