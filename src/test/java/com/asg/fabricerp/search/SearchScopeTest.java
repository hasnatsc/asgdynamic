package com.asg.fabricerp.search;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Search sees what the user's screens list: their VIEW grants, their unit, their teams and stores. */
class SearchScopeTest {

    private static OrgContext context(RowScope rows, Long unit) {
        OrgContext c = mock(OrgContext.class);
        when(c.requireOrganizationId()).thenReturn(1L);
        when(c.businessUnitId()).thenReturn(unit);
        when(c.username()).thenReturn("rahim");
        when(c.requireRowScope()).thenReturn(rows);
        return c;
    }

    @Test
    void documentTypes_followTheScreensTheUserMayView() {
        SearchScope s = SearchScope.of(context(RowScope.unrestrictedScope(), 10L),
            Set.of("SCREEN_BPO_VIEW", "SCREEN_TRC_VIEW", "SCREEN_PARTY_VIEW"));

        // TRC is Transfer Receive's screen: the role root, not the numbering prefix.
        assertThat(s.documentTypes()).containsExactlyInAnyOrder("BULK_PRODUCTION_ORDER", "TRANSFER_RECEIVE");
        assertThat(s.kinds()).containsExactlyInAnyOrder(SearchKind.DOCUMENT, SearchKind.PARTY);
        assertThat(s.teamIds()).isNull();
        assertThat(s.warehouseIds()).isNull();
    }

    @Test
    void noScreens_seesNothing() {
        assertThat(SearchScope.of(context(RowScope.unrestrictedScope(), 10L), Set.of()).seesNothing()).isTrue();
    }

    @Test
    void withoutABusinessUnit_noDocuments() {
        SearchScope s = SearchScope.of(context(RowScope.unrestrictedScope(), null), Set.of("SCREEN_BPO_VIEW", "SCREEN_ITEM_VIEW"));
        assertThat(s.kinds()).containsExactly(SearchKind.ITEM);
    }

    @Test
    void restrictedRows_carryTheirTeamsAndStores() {
        RowScope rows = new RowScope(false, Map.of(
            ScopeDimension.MARKETING_TEAM, Set.of(7L), ScopeDimension.WAREHOUSE, Set.of(3L, 4L)));
        SearchScope s = SearchScope.of(context(rows, 10L), Set.of("SCREEN_BOOKING_VIEW"));
        assertThat(s.teamIds()).containsExactly(7L);
        assertThat(s.warehouseIds()).containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    void theFilterChips_areTheKindsTheUserCanSee() {
        assertThat(SearchKind.visibleTo(Set.of("SCREEN_MRR_VIEW", "SCREEN_ITEM_VIEW")))
            .containsExactly(SearchKind.DOCUMENT, SearchKind.ITEM);
        assertThat(SearchKind.visibleTo(Set.of("SCREEN_ACC_JOURNAL_VIEW"))).containsExactly(SearchKind.VOUCHER);
        assertThat(SearchKind.visibleTo(Set.of())).isEmpty();
    }

    @Test
    void linksOpenTheRecordOnItsOwnScreen() {
        assertThat(SearchKind.link(SearchKind.DOCUMENT, "BULK_PRODUCTION_ORDER", 12L)).isEqualTo("/bpo?open=12");
        assertThat(SearchKind.link(SearchKind.DOCUMENT, "EXPORT_LETTER_OF_CREDIT", 5L)).isEqualTo("/export-lc?open=5");
        assertThat(SearchKind.link(SearchKind.VOUCHER, "PAYMENT", 3L)).isEqualTo("/accounts/journals?open=3");
        assertThat(SearchKind.link(SearchKind.PARTY, "ORGANISATION", 4L)).isEqualTo("/setup/parties?id=4");
        assertThat(SearchKind.link(SearchKind.ITEM, "YARN", 9L)).isEqualTo("/inventory/items?view=9");
        // A type with no screen yet is neither linked nor searchable.
        assertThat(SearchKind.link(SearchKind.DOCUMENT, "SALES_RETURN", 1L)).isNull();
        assertThat(SearchKind.viewAuthority(SearchKind.DOCUMENT, "SALES_RETURN")).isNull();
    }
}
