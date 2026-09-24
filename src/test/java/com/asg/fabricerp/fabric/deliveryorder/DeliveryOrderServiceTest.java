package com.asg.fabricerp.fabric.deliveryorder;

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
 * The one thing worth proving specifically for this type: it draws against a
 * Request-for-PI, not a BPO — a Booking or a BPO named as the parent must be refused.
 */
class DeliveryOrderServiceTest {

    private static final Long ORG = 1L;
    private static final Long UNIT = 10L;
    private static final Long RPI_ID = 830L;
    private static final Long RPI_COLOR_LINE_ID = 831L;

    private BusinessDocumentRepository repository;
    private DeliveryOrderService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessDocumentRepository.class);
        DocumentNumberService numbering = mock(DocumentNumberService.class);
        when(numbering.next(DocumentType.DELIVERY_ORDER)).thenReturn("DOAF000001");

        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return UNIT; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return "tester"; }
            @Override public RowScope rowScope()         { return RowScope.unrestrictedScope(); }
        };

        ParentLineDrawService parentDraw = new ParentLineDrawService(repository, context);
        service = new DeliveryOrderService(repository, numbering, parentDraw, context);
        when(repository.save(any(BusinessDocument.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BusinessDocument scheduleWithOpenLine(DocumentType type, BigDecimal quantity) {
        BusinessDocument schedule = new BusinessDocument();
        schedule.setId(RPI_ID);
        schedule.setOrganizationId(ORG);
        schedule.setBusinessUnitId(UNIT);
        schedule.setDocumentType(type);
        schedule.setDocumentNo("XAF000001");
        schedule.setPartyId(55L);

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setId(RPI_COLOR_LINE_ID);
        colorLine.setColorLineNo(1);
        colorLine.setQuantity(quantity);

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.setGroupNo(1);
        group.addColorLine(colorLine);
        schedule.addLineGroup(group);

        when(repository.findScopedWithLines(RPI_ID, ORG)).thenReturn(Optional.of(schedule));
        return schedule;
    }

    private BusinessDocumentColorLine onlyColorLine(BusinessDocument doc) {
        return doc.getLineGroups().get(0).getColorLines().get(0);
    }

    private BusinessDocument dloRequest(BigDecimal quantity) {
        BusinessDocument dlo = new BusinessDocument();
        dlo.setDocumentDate(LocalDate.now());
        dlo.setParentDocumentId(RPI_ID);

        BusinessDocumentColorLine colorLine = new BusinessDocumentColorLine();
        colorLine.setSourceColorLineId(RPI_COLOR_LINE_ID);
        colorLine.setQuantity(quantity);

        BusinessDocumentLineGroup group = new BusinessDocumentLineGroup();
        group.addColorLine(colorLine);
        dlo.setLineGroups(List.of(group));
        return dlo;
    }

    @Test
    void drawsAgainstARequestForPiAndInheritsItsParty() {
        BusinessDocument rpi = scheduleWithOpenLine(DocumentType.REQUEST_FOR_PI, new BigDecimal("400"));

        BusinessDocument dlo = service.save(dloRequest(new BigDecimal("150")));

        assertThat(dlo.getPartyId()).isEqualTo(55L);
        assertThat(onlyColorLine(rpi).getFulfilledQuantity()).isEqualByComparingTo("150");
    }

    @Test
    void refusesABpoNamedAsTheParentEvenThoughTheIdExists() {
        // Same id, wrong type — the parent-type check must reject it, not just the id lookup.
        scheduleWithOpenLine(DocumentType.BULK_PRODUCTION_ORDER, new BigDecimal("400"));

        assertThatThrownBy(() -> service.save(dloRequest(new BigDecimal("150"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is not a Request For PI");
    }

    @Test
    void refusesToExceedTheScheduleLine() {
        scheduleWithOpenLine(DocumentType.REQUEST_FOR_PI, new BigDecimal("400"));

        assertThatThrownBy(() -> service.save(dloRequest(new BigDecimal("500"))))
            .isInstanceOf(IllegalStateException.class);
    }
}
