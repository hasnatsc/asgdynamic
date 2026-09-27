package com.asg.fabricerp.security;

import com.asg.fabricerp.web.Navigation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The CHECK verb means something only on the Commercial screens, which have no page yet. */
class CommercialGrantsTest {

    @Test
    void checkIsKeptOnACommercialScreenAndDroppedEverywhereElse() {
        Role role = new Role("Everything", null);
        role.grant(Screen.PI, Verb.values());
        role.grant(Screen.BOOKING, Verb.values());

        assertThat(role.grantsByScreen().get(Screen.PI)).contains(Verb.CHECK, Verb.APPROVE);
        assertThat(role.grantsByScreen().get(Screen.BOOKING)).doesNotContain(Verb.CHECK).contains(Verb.APPROVE);
    }

    @Test
    void checkingAloneStillLetsTheCheckerOpenTheDocument() {
        Role checker = new Role("ROLE_LC_CHECKER", null);
        checker.grant(Screen.LC, Verb.CHECK);

        assertThat(checker.grantsByScreen().get(Screen.LC)).containsExactlyInAnyOrder(Verb.VIEW, Verb.CHECK);
        assertThat(Screen.LC.authority(Verb.CHECK)).isEqualTo("SCREEN_LC_CHECK");
    }

    @Test
    void theCommercialScreensStayOutOfTheMenuUntilTheyHaveAPage() {
        var sections = Navigation.build(Set.of("SCREEN_PI_VIEW", "SCREEN_LC_VIEW", "SCREEN_CI_VIEW"), "/");

        assertThat(Screen.PI.isBuilt()).isFalse();
        assertThat(sections).isEmpty();
    }
}
