package com.asg.fabricerp.fabric.processingworkorder;

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

/** Same shape and same proof as {@code WeavingWorkOrderServiceTest} — a fifth confirmation
 *  that {@link ParentLineDrawService} generalizes, this time for the dyeing side. */
class ProcessingWorkOrderServiceTest {

    private static final Long ORG = 1L;
    private static final Long UNIT = 10L;
    private static final Long BPO_ID = 810L;
    private static final Long BPO_LINE_ID = 811L;

    private BusinessDocumentRepository repository;
    private ProcessingWorkOrderService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        DocumentNumberService numbering = mock(DocumentNumberService.class);
        when(numbering.next(DocumentType.PROCESSING_WORK_ORDER)).thenReturn("PWOAF000001");

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return UNIT; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
        };

        ParentLineDrawService parentDraw = new ParentLineDrawService(repository, context);
        service = new ProcessingWorkOrderService(repository, numbering, parentDraw, context);
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BusinessDocument bpoWithOpenLine(BigDecimal quantity) {
        BusinessDocument bpo = new BusinessDocument();
        bpo.setId(BPO_ID);
        bpo.setOrganizationId(ORG);
        bpo.setBusinessUnitId(UNIT);
        bpo.setDocumentType(DocumentType.BULK_PRODUCTION_ORDER);
        bpo.setDocumentNo("BPOAF000002");

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
    void commitsProcessingCapacityAgainstTheBpoLine() {
        BusinessDocument bpo = bpoWithOpenLine(new BigDecimal("900"));

        service.save(woRequest(new BigDecimal("350")));

        assertThat(bpo.getLines().get(0).getFulfilledQuantity()).isEqualByComparingTo("350");
    }

    @Test
    void refusesToExceedTheBpoLine() {
        bpoWithOpenLine(new BigDecimal("900"));

        assertThatThrownBy(() -> service.save(woRequest(new BigDecimal("950"))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingADraftReleasesTheCommitment() {
        BusinessDocument bpo = bpoWithOpenLine(new BigDecimal("900"));
        BusinessDocument wo = service.save(woRequest(new BigDecimal("350")));
        wo.setId(960L);
        when(repository.findScopedWithLines(960L, ORG)).thenReturn(Optional.of(wo));

        service.delete(960L);

        assertThat(bpo.getLines().get(0).outstandingQuantity()).isEqualByComparingTo("900");
    }
}
