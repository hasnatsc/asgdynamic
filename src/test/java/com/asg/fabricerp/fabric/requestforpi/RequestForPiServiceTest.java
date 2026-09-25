package com.asg.fabricerp.fabric.requestforpi;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
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
 * {@code BpoServiceTest} proved the ceiling and release mechanics generalize once. This
 * confirms it a third time — against a BPO instead of a Booking — through the same shared
 * {@link ParentLineDrawService}, and covers what is actually specific to this type: no
 * costing involvement, and a revision path.
 */
class RequestForPiServiceTest {

    private static final Long ORG = 1L;
    private static final Long UNIT = 10L;
    private static final Long BPO_ID = 700L;
    private static final Long BPO_COLOR_LINE_ID = 701L;

    private BusinessDocumentRepository repository;
    private DocumentNumberService numbering;
    private RequestForPiService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        numbering = mock(DocumentNumberService.class);
        // Consecutive-call stubbing: the original get "RPIAF000001", its revision gets
        // "RPIAF000002" — a real DocumentNumberService would never hand out the same
        // number twice, and this mock shouldn't pretend otherwise.
        when(numbering.next(DocumentType.REQUEST_FOR_PI)).thenReturn("RPIAF000001", "RPIAF000002");
        DocumentRevisionService revisions = new DocumentRevisionService(repository, numbering);

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return UNIT; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
            @Override public RowScope rowScope()         { return RowScope.unrestrictedScope(); }
        };

        ParentLineDrawService parentDraw = new ParentLineDrawService(repository, context);
        service = new RequestForPiService(repository, numbering, revisions, parentDraw, DocumentRefs.references(UNIT), context);
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BusinessDocument bpoWithOpenLine(BigDecimal quantity) {
        BusinessDocument bpo = new BusinessDocument();
        bpo.setId(BPO_ID);
        bpo.setOrganizationId(ORG);
        bpo.setBusinessUnit(DocumentRefs.unit(UNIT));
        bpo.setDocumentType(DocumentType.BULK_PRODUCTION_ORDER);
        bpo.setDocumentNo("BPOAF000001");
        bpo.setParty(DocumentRefs.party(88L));

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setId(BPO_COLOR_LINE_ID);
        colorLine.setColorLineNo(1);
        colorLine.setQuantity(quantity);

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.setGroupNo(1);
        group.addColorLine(colorLine);
        bpo.addLineGroup(group);

        when(repository.findScopedWithLines(BPO_ID, ORG)).thenReturn(Optional.of(bpo));
        return bpo;
    }

    private BusinessDocumentColorLine onlyColorLine(BusinessDocument doc) {
        return doc.getLineGroups().get(0).getColorLines().get(0);
    }

    private BusinessDocument rpiRequest(BigDecimal quantity) {
        BusinessDocument rpi = new BusinessDocument();
        rpi.setDocumentDate(LocalDate.now());
        rpi.setParentDocument(DocumentRefs.document(BPO_ID));

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setSourceColorLine(DocumentRefs.colorLine(BPO_COLOR_LINE_ID));
        colorLine.setQuantity(quantity);

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.addColorLine(colorLine);
        rpi.setLineGroups(List.of(group));
        return rpi;
    }

    @Test
    void drawsAgainstTheBpoLineAndInheritsItsParty() {
        BusinessDocument bpo = bpoWithOpenLine(new BigDecimal("500"));

        BusinessDocument rpi = service.save(rpiRequest(new BigDecimal("200")));

        assertThat(rpi.getDocumentType()).isEqualTo(DocumentType.REQUEST_FOR_PI);
        assertThat(DocumentRefs.id(rpi.getParty())).isEqualTo(88L);
        assertThat(onlyColorLine(bpo).getFulfilledQuantity()).isEqualByComparingTo("200");
    }

    @Test
    void refusesToScheduleMoreThanTheBpoLineHasOutstanding() {
        bpoWithOpenLine(new BigDecimal("500"));

        assertThatThrownBy(() -> service.save(rpiRequest(new BigDecimal("600"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exceed the ordered quantity");
    }

    @Test
    void deletingADraftReleasesTheBpoReservation() {
        BusinessDocument bpo = bpoWithOpenLine(new BigDecimal("500"));
        BusinessDocument rpi = service.save(rpiRequest(new BigDecimal("200")));
        rpi.setId(900L);
        when(repository.findScopedWithLines(900L, ORG)).thenReturn(Optional.of(rpi));

        service.delete(900L);

        assertThat(onlyColorLine(bpo).outstandingQuantity()).isEqualByComparingTo("500");
    }

    @Test
    void reviseCarriesTheLineSpecificationForwardWithoutReDrawing() {
        BusinessDocument bpo = bpoWithOpenLine(new BigDecimal("500"));
        BusinessDocument rpi = service.save(rpiRequest(new BigDecimal("200")));
        rpi.setId(900L);
        rpi.transitionTo(BusinessDocumentStatus.SUBMITTED);
        rpi.transitionTo(BusinessDocumentStatus.APPROVED);
        when(repository.findScopedWithLines(900L, ORG)).thenReturn(Optional.of(rpi));

        BusinessDocument revision = service.revise(900L, "buyer moved the date");

        assertThat(revision.getDocumentNo()).isEqualTo("RPIAF000002");
        assertThat(revision.getRevisionNo()).isEqualTo(1);
        assertThat(DocumentRefs.id(onlyColorLine(revision).getSourceColorLine())).isEqualTo(BPO_COLOR_LINE_ID);
        // The BPO ledger is untouched by the revision itself — only the original draw counts.
        assertThat(onlyColorLine(bpo).getFulfilledQuantity()).isEqualByComparingTo("200");
    }
}
