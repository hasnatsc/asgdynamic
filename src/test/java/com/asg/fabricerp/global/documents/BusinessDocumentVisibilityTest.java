package com.asg.fabricerp.global.documents;

import com.asg.fabricerp.common.RowScope;
import com.asg.fabricerp.common.ScopeDimension;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADM-3 on a single document — the rule every detail lookup, parent lookup and approval applies,
 * and which must agree with the predicate on {@link BusinessDocumentRepository#search}.
 */
class BusinessDocumentVisibilityTest {

    private static final Long UNIT = 10L;
    private static final Long TEAM_LONDON = 3L;
    private static final Long TEAM_TOKYO = 7L;
    private static final Long STORE_WEAVING = 21L;
    private static final Long STORE_PROCESSING = 22L;

    private static BusinessDocument document(Long team, Long warehouse) {
        BusinessDocument doc = new BusinessDocument();
        doc.setBusinessUnit(DocumentRefs.unit(UNIT));
        doc.setWarehouse(DocumentRefs.warehouse(warehouse));
        doc.stampMarketingTeam(DocumentRefs.team(team));
        return doc;
    }

    private static RowScope restrictedTo(ScopeDimension dimension, Long... ids) {
        return new RowScope(false, Map.of(dimension, Set.of(ids)));
    }

    @Test
    void anUnrestrictedUserSeesEverything() {
        RowScope all = RowScope.unrestrictedScope();

        assertThat(document(TEAM_LONDON, STORE_WEAVING).isVisibleTo(all)).isTrue();
        assertThat(document(null, null).isVisibleTo(all)).isTrue();
    }

    @Test
    void aTeamRestrictedUserSeesOnlyTheirOwnTeam() {
        RowScope london = restrictedTo(ScopeDimension.MARKETING_TEAM, TEAM_LONDON);

        assertThat(document(TEAM_LONDON, null).isVisibleTo(london)).isTrue();
        assertThat(document(TEAM_TOKYO, null).isVisibleTo(london)).isFalse();
    }

    /** Pre-V12 documents carry no team. ADM-4 is the privacy rule, so they are not a loophole. */
    @Test
    void aDocumentWithNoTeamIsHiddenFromATeamRestrictedUser() {
        RowScope london = restrictedTo(ScopeDimension.MARKETING_TEAM, TEAM_LONDON);

        assertThat(document(null, null).isVisibleTo(london)).isFalse();
    }

    @Test
    void aStoreRestrictedUserSeesTheirStoreAndDocumentsHeldInNoStore() {
        RowScope weaving = restrictedTo(ScopeDimension.WAREHOUSE, STORE_WEAVING);

        assertThat(document(null, STORE_WEAVING).isVisibleTo(weaving)).isTrue();
        assertThat(document(null, STORE_PROCESSING).isVisibleTo(weaving)).isFalse();
        // A BPO held in no store must stay reachable, or a store clerk cannot raise a receipt against it.
        assertThat(document(null, null).isVisibleTo(weaving)).isTrue();
    }

    /** Restricting one dimension says nothing about the others. */
    @Test
    void dimensionsNarrowIndependently() {
        RowScope londonWeaving = new RowScope(false, Map.of(
            ScopeDimension.MARKETING_TEAM, Set.of(TEAM_LONDON),
            ScopeDimension.WAREHOUSE, Set.of(STORE_WEAVING)));

        assertThat(document(TEAM_LONDON, STORE_WEAVING).isVisibleTo(londonWeaving)).isTrue();
        assertThat(document(TEAM_LONDON, STORE_PROCESSING).isVisibleTo(londonWeaving)).isFalse();
        assertThat(document(TEAM_TOKYO, STORE_WEAVING).isVisibleTo(londonWeaving)).isFalse();
    }

    @Test
    void aBusinessUnitRestrictionIsApplied() {
        assertThat(document(null, null).isVisibleTo(restrictedTo(ScopeDimension.BUSINESS_UNIT, UNIT))).isTrue();
        assertThat(document(null, null).isVisibleTo(restrictedTo(ScopeDimension.BUSINESS_UNIT, 99L))).isFalse();
    }

    @Test
    void queryParametersNeverCarryAnEmptyInList() {
        RowScope london = restrictedTo(ScopeDimension.MARKETING_TEAM, TEAM_LONDON);

        assertThat(london.idsForQuery(ScopeDimension.MARKETING_TEAM)).containsExactly(TEAM_LONDON);
        assertThat(london.idsForQuery(ScopeDimension.WAREHOUSE)).isNotEmpty();
        assertThat(RowScope.unrestrictedScope().idsForQuery(ScopeDimension.MARKETING_TEAM))
            .isEqualTo(List.of(-1L));
    }

    @Test
    void aRestrictedUserWithNoGrantsIsUnconfiguredNotOmniscient() {
        RowScope nothing = new RowScope(false, Map.of());

        assertThat(nothing.isConfigured()).isFalse();
        assertThat(RowScope.unrestrictedScope().isConfigured()).isTrue();
        assertThat(nothing.soleMarketingTeam()).isNull();
        assertThat(restrictedTo(ScopeDimension.MARKETING_TEAM, TEAM_TOKYO).soleMarketingTeam())
            .isEqualTo(TEAM_TOKYO);
    }
}
