package com.asg.fabricerp.fabric.fabricsdelivery;

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

/** The last hop of the sales chain: draws against a Delivery Order, not a Request-for-PI. */
class FabricsDeliveryServiceTest {

    private static final Long ORG = 1L;
    private static final Long UNIT = 10L;
    private static final Long DLO_ID = 840L;
    private static final Long DLO_COLOR_LINE_ID = 841L;

    private BusinessDocumentRepository repository;
    private FabricsDeliveryService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        DocumentNumberService numbering = mock(DocumentNumberService.class);
        when(numbering.next(DocumentType.FABRICS_DELIVERY)).thenReturn("FDAF000001");

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return UNIT; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
            @Override public RowScope rowScope()         { return RowScope.unrestrictedScope(); }
        };

        ParentLineDrawService parentDraw = new ParentLineDrawService(repository, context);
        service = new FabricsDeliveryService(repository, numbering, parentDraw, DocumentRefs.references(UNIT), context);
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BusinessDocument deliveryOrderWithOpenLine(BigDecimal quantity) {
        BusinessDocument dlo = new BusinessDocument();
        dlo.setId(DLO_ID);
        dlo.setOrganizationId(ORG);
        dlo.setBusinessUnit(DocumentRefs.unit(UNIT));
        dlo.setDocumentType(DocumentType.DELIVERY_ORDER);
        dlo.setDocumentNo("DOAF000002");

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setId(DLO_COLOR_LINE_ID);
        colorLine.setColorLineNo(1);
        colorLine.setQuantity(quantity);

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.setGroupNo(1);
        group.addColorLine(colorLine);
        dlo.addLineGroup(group);

        when(repository.findScopedWithLines(DLO_ID, ORG)).thenReturn(Optional.of(dlo));
        return dlo;
    }

    private BusinessDocumentColorLine onlyColorLine(BusinessDocument doc) {
        return doc.getLineGroups().get(0).getColorLines().get(0);
    }

    private BusinessDocument fdRequest(BigDecimal quantity) {
        BusinessDocument fd = new BusinessDocument();
        fd.setDocumentDate(LocalDate.now());
        fd.setParentDocument(DocumentRefs.document(DLO_ID));

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setSourceColorLine(DocumentRefs.colorLine(DLO_COLOR_LINE_ID));
        colorLine.setQuantity(quantity);

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.addColorLine(colorLine);
        fd.setLineGroups(List.of(group));
        return fd;
    }

    @Test
    void drawsAgainstTheDeliveryOrderLine() {
        BusinessDocument dlo = deliveryOrderWithOpenLine(new BigDecimal("200"));

        service.save(fdRequest(new BigDecimal("80")));

        assertThat(onlyColorLine(dlo).getFulfilledQuantity()).isEqualByComparingTo("80");
    }

    @Test
    void refusesToExceedTheDeliveryOrderLine() {
        deliveryOrderWithOpenLine(new BigDecimal("200"));

        assertThatThrownBy(() -> service.save(fdRequest(new BigDecimal("250"))))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deletingADraftReleasesTheDeliveryOrderReservation() {
        BusinessDocument dlo = deliveryOrderWithOpenLine(new BigDecimal("200"));
        BusinessDocument fd = service.save(fdRequest(new BigDecimal("80")));
        fd.setId(980L);
        when(repository.findScopedWithLines(980L, ORG)).thenReturn(Optional.of(fd));

        service.delete(980L);

        assertThat(onlyColorLine(dlo).outstandingQuantity()).isEqualByComparingTo("200");
    }
}
