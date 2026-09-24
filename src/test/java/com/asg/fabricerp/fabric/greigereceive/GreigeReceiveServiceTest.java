package com.asg.fabricerp.fabric.greigereceive;

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

class GreigeReceiveServiceTest {

    private static final Long ORG = 1L;
    private static final Long UNIT = 10L;
    private static final Long BPO_ID = 820L;
    private static final Long BPO_COLOR_LINE_ID = 821L;

    private BusinessDocumentRepository repository;
    private GreigeReceiveService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        DocumentNumberService numbering = mock(DocumentNumberService.class);
        when(numbering.next(DocumentType.GREIGE_RECEIVE)).thenReturn("GRAF000001");

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return UNIT; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
            @Override public RowScope rowScope()         { return RowScope.unrestrictedScope(); }
        };

        ParentLineDrawService parentDraw = new ParentLineDrawService(repository, context);
        service = new GreigeReceiveService(repository, numbering, parentDraw, context);
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BusinessDocument bpoWithOpenLine(BigDecimal quantity) {
        BusinessDocument bpo = new BusinessDocument();
        bpo.setId(BPO_ID);
        bpo.setOrganizationId(ORG);
        bpo.setBusinessUnitId(UNIT);
        bpo.setDocumentType(DocumentType.BULK_PRODUCTION_ORDER);
        bpo.setDocumentNo("BPOAF000003");

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

    private BusinessDocument receiptRequest(BigDecimal quantity) {
        BusinessDocument receipt = new BusinessDocument();
        receipt.setDocumentDate(LocalDate.now());
        receipt.setParentDocumentId(BPO_ID);

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setSourceColorLineId(BPO_COLOR_LINE_ID);
        colorLine.setQuantity(quantity);

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.addColorLine(colorLine);
        receipt.setLineGroups(List.of(group));
        return receipt;
    }

    @Test
    void recordsGreigeReceivedAgainstTheBpoLine() {
        BusinessDocument bpo = bpoWithOpenLine(new BigDecimal("600"));

        service.save(receiptRequest(new BigDecimal("250")));

        assertThat(onlyColorLine(bpo).getFulfilledQuantity()).isEqualByComparingTo("250");
    }

    @Test
    void refusesToExceedTheBpoLine() {
        bpoWithOpenLine(new BigDecimal("600"));

        assertThatThrownBy(() -> service.save(receiptRequest(new BigDecimal("650"))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingADraftReleasesTheReceipt() {
        BusinessDocument bpo = bpoWithOpenLine(new BigDecimal("600"));
        BusinessDocument receipt = service.save(receiptRequest(new BigDecimal("250")));
        receipt.setId(970L);
        when(repository.findScopedWithLines(970L, ORG)).thenReturn(Optional.of(receipt));

        service.delete(970L);

        assertThat(onlyColorLine(bpo).outstandingQuantity()).isEqualByComparingTo("600");
    }
}
