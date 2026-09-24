package com.asg.fabricerp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the bootstrap logins so a freshly migrated database is not otherwise unreachable.
 *
 * <p>Off by default ({@code app.seed-dev-user=false}) and never carries a literal password:
 * each account reads its own password from the environment and, if that is unset, skips
 * with an explanatory log line rather than inventing or printing one. Same rule as every
 * other secret in this project — see {@link SecurityConfig#rememberMeKey}.
 *
 * <h2>Two accounts, not one</h2>
 * {@code ApprovalService} enforces four-eyes: whoever created a document cannot approve it.
 * A single seeded account could submit a booking but could never approve it — not a bug,
 * exactly the segregation of duties the rule exists for, but it means the approval step is
 * untestable through the UI without a second, distinct identity. {@code approver} exists so
 * that path is exercisable without weakening the rule to make local testing convenient.
 *
 * <p>Idempotent per username: safe to leave the property on in a dev environment across
 * restarts.
 *
 * <h2>Roles, not raw grants</h2>
 * Used to call {@code user.grant("ROLE_...")} once per authority (28 calls total). Now assigns
 * the two roles {@code V7__roles.sql} seeds — {@code Fabric Operations} and
 * {@code Document Approver} — which bundle exactly the same permissions. Effective access is
 * unchanged; the roles are now admin-editable via {@code RoleController} instead of requiring a
 * code change and restart.
 */
@Component
@ConditionalOnProperty(name = "app.seed-dev-user", havingValue = "true")
public class DevUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private final FabricUserRepository repository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;

    public DevUserSeeder(FabricUserRepository repository, RoleRepository roleRepository,
                         PasswordEncoder encoder) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        seedMaker();
        seedApprover();
    }

    /** Can create and submit documents in every fabric module built so far. */
    private void seedMaker() {
        String username = "admin";
        if (repository.existsByUsernameIgnoreCase(username)) return;

        String password = System.getenv("FABRIC_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            log.warn("app.seed-dev-user is true but FABRIC_ADMIN_PASSWORD is not set — "
                    + "skipping '{}'. Set it and restart to create this account.", username);
            return;
        }

        FabricUser user = new FabricUser(username, encoder.encode(password), 1L, "AF");
        user.setOrganizationId(1L);
        user.setFullName("Development Maker");
        user.addRole(requireRole("Fabric Operations"));
        repository.save(user);

        log.info("Seeded bootstrap user '{}'. Disable app.seed-dev-user once real accounts exist.",
                username);
    }

    /**
     * Holds the generic approver role and view roles, deliberately not the maker roles —
     * demonstrates the segregation both ways, not just that self-approval is blocked.
     */
    private void seedApprover() {
        String username = "approver";
        if (repository.existsByUsernameIgnoreCase(username)) return;

        String password = System.getenv("FABRIC_APPROVER_PASSWORD");
        if (password == null || password.isBlank()) {
            log.warn("app.seed-dev-user is true but FABRIC_APPROVER_PASSWORD is not set — "
                    + "skipping '{}'. Without it, submitted documents can never be approved "
                    + "locally (the 'admin' account created them and four-eyes blocks "
                    + "self-approval). Set it and restart to create this account.", username);
            return;
        }

        FabricUser user = new FabricUser(username, encoder.encode(password), 1L, "AF");
        user.setOrganizationId(1L);
        user.setFullName("Development Approver");
        user.addRole(requireRole("Document Approver"));
        repository.save(user);

        log.info("Seeded bootstrap user '{}'. Disable app.seed-dev-user once real accounts exist.",
                username);
    }

    private Role requireRole(String name) {
        return roleRepository.findByNameIgnoreCase(name)
            .orElseThrow(() -> new IllegalStateException(
                "Role '%s' not found — has V7__roles.sql been applied?".formatted(name)));
    }
}
