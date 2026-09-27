package com.asg.fabricerp.commercial;

import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.stereotype.Component;

/**
 * {@code @PreAuthorize("@commercialAccess.can(#slug, 'VIEW')")} - one controller serves the five
 * commercial documents, so the authority is worked out from the path: {@code /export-lc} needs
 * {@code SCREEN_ELC_VIEW}.
 */
@Component("commercialAccess")
public class CommercialAccess {

    public boolean can(String slug, String verb) {
        try {
            return AuthorityChecks.holds(CommercialStep.ofSlug(slug).authority(verb));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    public boolean canAny(String slug, String... verbs) {
        for (String verb : verbs) if (can(slug, verb)) return true;
        return false;
    }

    /** Any commercial screen - for the shared lookups (accounts, cost heads, document names). */
    public boolean anyScreen() {
        for (CommercialStep step : CommercialStep.values()) {
            if (AuthorityChecks.holds(step.authority("VIEW"))) return true;
        }
        return AuthorityChecks.holds("SCREEN_COM_REGISTER_VIEW") || AuthorityChecks.holds("SCREEN_COM_SETUP_VIEW");
    }
}
