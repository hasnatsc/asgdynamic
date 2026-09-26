package com.asg.fabricerp.production;

import com.asg.fabricerp.security.AuthorityChecks;
import org.springframework.stereotype.Component;

/**
 * {@code @PreAuthorize("@chainAccess.can(#slug, 'VIEW')")} - one controller serves nine screens, so
 * the authority is worked out from the path: {@code /greige-issue} needs {@code SCREEN_GI_VIEW}.
 */
@Component("chainAccess")
public class ChainAccess {

    public boolean can(String slug, String verb) {
        try {
            return AuthorityChecks.holds(ChainStep.ofSlug(slug).authority(verb));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    public boolean canAny(String slug, String... verbs) {
        for (String verb : verbs) if (can(slug, verb)) return true;
        return false;
    }
}
