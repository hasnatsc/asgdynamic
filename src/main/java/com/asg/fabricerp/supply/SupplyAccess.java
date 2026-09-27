package com.asg.fabricerp.supply;

import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.stereotype.Component;

/**
 * {@code @PreAuthorize("@supplyAccess.can(#slug, 'VIEW')")} - one controller serves every purchase
 * and store screen, so the authority is worked out from the path: {@code /mrr} needs
 * {@code SCREEN_MRR_VIEW}.
 */
@Component("supplyAccess")
public class SupplyAccess {

    public boolean can(String slug, String verb) {
        try {
            return AuthorityChecks.holds(SupplyStep.ofSlug(slug).authority(verb));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    public boolean canAny(String slug, String... verbs) {
        for (String verb : verbs) if (can(slug, verb)) return true;
        return false;
    }

    /** Whether the user may view any purchase or store screen - for the shared lookups. */
    public boolean anyScreen() {
        for (SupplyStep step : SupplyStep.values()) {
            if (AuthorityChecks.holds(step.authority("VIEW"))) return true;
        }
        return AuthorityChecks.holds("SCREEN_ITEM_STOCK_VIEW");
    }
}
