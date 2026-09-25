package com.asg.fabricerp.global.numbering;

import com.asg.fabricerp.common.BusinessUnit;
import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.global.documents.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** What the service hands the allocator: whose number, dated when, for which branch. */
class BusinessNumberServiceTest {

    private NumberAllocator allocator;
    private BusinessNumberService service;

    @BeforeEach
    void setUp() {
        allocator = mock(NumberAllocator.class);
        when(allocator.allocate(any())).thenReturn("X-2026-000001");
        OrgContext context = new OrgContext() {
            @Override public Long organizationId()     { return 1L; }
            @Override public Long businessUnitId()     { return 10L; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return null; }
            @Override public String username()         { return "maker"; }
            @Override public RowScope rowScope()       { return RowScope.unrestrictedScope(); }
        };
        service = new BusinessNumberService(allocator, context);
    }

    private NumberAllocator.Request sent() {
        ArgumentCaptor<NumberAllocator.Request> captor = ArgumentCaptor.forClass(NumberAllocator.Request.class);
        verify(allocator).allocate(captor.capture());
        return captor.getValue();
    }

    @Test
    void aMasterIsNumberedTodayForTheCallersUnit() {
        service.next(BusinessSeries.CUSTOMER);
        NumberAllocator.Request r = sent();
        assertThat(r.organizationId()).isEqualTo(1L);
        assertThat(r.series()).isEqualTo(BusinessSeries.CUSTOMER);
        assertThat(r.date()).isEqualTo(LocalDate.now());
        assertThat(r.businessUnitId()).isEqualTo(10L);
        assertThat(r.businessUnitCode()).isEqualTo("AF");
        assertThat(r.issuedBy()).isEqualTo("maker");
    }

    @Test
    void aDocumentIsNumberedForItsOwnDateAndUnit() {
        BusinessUnit unit = new BusinessUnit("DY", "Dyeing");
        unit.setId(20L);
        LocalDate backdated = LocalDate.of(2026, 6, 30);

        service.next(DocumentType.BOOKING, backdated, unit);

        NumberAllocator.Request r = sent();
        assertThat(r.date()).isEqualTo(backdated);
        assertThat(r.businessUnitId()).isEqualTo(20L);
        assertThat(r.businessUnitCode()).isEqualTo("DY");
    }

    @Test
    void aNumberNeedsADate() {
        assertThatThrownBy(() -> service.next(DocumentType.BOOKING, null))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(allocator);
    }

    @Test
    void reservingANumberAlreadyIssuedIsRefused() {
        when(allocator.reserve(1L, BusinessSeries.PARTY, "PT-2026-000001", 10L, "maker")).thenReturn(false);
        assertThatThrownBy(() -> service.reserve(BusinessSeries.PARTY, "PT-2026-000001"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already been issued");
    }
}
