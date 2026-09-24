package com.asg.fabricerp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one bootstrap login so a freshly migrated database is not otherwise unreachable.
 *
 * <p>Off by default ({@code app.seed-dev-user=false}) and never carries a literal password:
 * it reads {@code FABRIC_ADMIN_PASSWORD} from the environment and, if that is unset, skips
 * with an explanatory log line rather than inventing or printing one. Same rule as every
 * other secret in this project — see {@link SecurityConfig#rememberMeKey}.
 *
 * <p>Idempotent: does nothing once the username already exists, so it is safe to leave the
 * property on in a dev environment across restarts.
 */
@Component
@ConditionalOnProperty(name = "app.seed-dev-user", havingValue = "true")
public class DevUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);
    private static final String USERNAME = "admin";

    private final FabricUserRepository repository;
    private final PasswordEncoder encoder;

    public DevUserSeeder(FabricUserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (repository.existsByUsernameIgnoreCase(USERNAME)) {
            return;
        }

        String password = System.getenv("FABRIC_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            log.warn("app.seed-dev-user is true but FABRIC_ADMIN_PASSWORD is not set — "
                    + "skipping bootstrap user creation. Set it and restart to create '{}'.",
                    USERNAME);
            return;
        }

        FabricUser user = new FabricUser(USERNAME, encoder.encode(password), 1L, "AF");
        user.setOrganizationId(1L);
        user.setFullName("Development Administrator");
        // Matches exactly the roles already checked by @PreAuthorize on the controllers
        // built so far — nothing speculative added.
        user.grant("ROLE_FABRIC_SETUP");
        user.grant("ROLE_BOOKING_VIEW");
        user.grant("ROLE_BOOKING_MAKER");
        user.grant("ROLE_SALES");
        user.grant("ROLE_BPO_VIEW");
        user.grant("ROLE_BPO_MAKER");
        user.grant("ROLE_PRODUCTION");
        repository.save(user);

        log.info("Seeded bootstrap user '{}'. Disable app.seed-dev-user once real accounts exist.",
                USERNAME);
    }
}
