package com.asg.fabricerp.web;

import com.asg.fabricerp.global.documents.DocumentType;
import com.asg.fabricerp.security.Screen;
import com.asg.fabricerp.security.Verb;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Which app a document belongs to, and which apps a user has - the rules the home page and app pages share. */
class ModuleDashboardServiceTest {

    @Test
    void aDocumentTypeLivesOnTheScreenItsRoleRootNames() {
        assertThat(ModuleDashboardService.typesOf(Screen.BOOKING)).containsExactly(DocumentType.BOOKING);
        assertThat(ModuleDashboardService.typesOf(Screen.BPO)).containsExactly(DocumentType.BULK_PRODUCTION_ORDER);
        assertThat(ModuleDashboardService.typesOf(Screen.PROD_BOARD)).isEmpty();         // a board, not a document screen
        assertThat(ModuleDashboardService.screenOf(DocumentType.GREIGE_RECEIVE)).contains(Screen.GR);
        assertThat(ModuleDashboardService.screenOf(DocumentType.SALES_RETURN)).isEmpty(); // no screen yet
    }

    @Test
    void everyDocumentScreenOpensADocumentByIdOnItsOwnPath() {
        assertThat(ModuleDashboardService.openPath(Screen.BPO, 42L)).isEqualTo("/bpo?open=42");
        // Every document type with a screen is in one of the screen's modules.
        Arrays.stream(DocumentType.values()).forEach(t -> ModuleDashboardService.screenOf(t)
            .ifPresent(s -> assertThat(ModuleDashboardService.typesOf(s)).contains(t)));
    }

    @Test
    void anAppIsAModuleWithAtLeastOneScreenTheUserMayView() {
        Set<String> authorities = Set.of(Screen.BPO.authority(Verb.VIEW), Screen.WWO.authority(Verb.CREATE));
        assertThat(ModuleDashboardService.screens(Screen.Section.PRODUCTION, authorities)).containsExactly(Screen.BPO);
        assertThat(ModuleDashboardService.screens(Screen.Section.SALES, authorities)).isEmpty();
        assertThat(ModuleDashboardService.section("production")).contains(Screen.Section.PRODUCTION);
        assertThat(ModuleDashboardService.section("PRODUCTION")).isEmpty();
        assertThat(ModuleDashboardService.key(Screen.Section.STORES)).isEqualTo("stores");
    }
}
