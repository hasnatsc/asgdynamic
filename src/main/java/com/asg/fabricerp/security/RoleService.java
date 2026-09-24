package com.asg.fabricerp.security;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class RoleService {

    private final RoleRepository repository;
    private final FabricUserRepository userRepository;
    private final OrgContext context;

    public RoleService(RoleRepository repository, FabricUserRepository userRepository, OrgContext context) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<Role> search(String query, Boolean hasGrants, Pageable pageable) {
        return repository.search(query, hasGrants, pageable);
    }

    /** Users in this organization holding each role, for one page of the grid in one query. */
    @Transactional(readOnly = true)
    public Map<Long, Long> userCounts(Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : userRepository.countUsersByRole(roleIds, context.requireOrganizationId())) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public Role get(Long id) {
        return repository.findWithGrantsById(id)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    @Transactional
    public Role save(Role submitted, Map<Screen, Set<Verb>> grants) {
        if (submitted.getName() == null || submitted.getName().isBlank()) {
            throw new IllegalArgumentException("A role needs a name");
        }
        submitted.setName(submitted.getName().trim());
        if (submitted.getId() == null) {
            requireUniqueName(submitted.getName());
            submitted.setGrants(grants);
            return repository.save(submitted);
        }
        Role target = get(submitted.getId());
        if (!target.getName().equalsIgnoreCase(submitted.getName())) {
            requireUniqueName(submitted.getName());
            target.setName(submitted.getName());
        }
        target.setDescription(submitted.getDescription());
        target.setActive(submitted.getActive());
        target.setGrants(grants);
        return repository.save(target);
    }

    /**
     * Hard block, not a soft delete: unlike {@code FabricAttribute} (kept for historical
     * document lines that recorded its resolved name), a role has no reason to survive once
     * unused — but deleting one still assigned would silently drop authorities from every user
     * holding it.
     */
    @Transactional
    public void delete(Long id) {
        Role target = get(id);
        if (userRepository.existsByRolesId(id)) {
            throw new IllegalStateException(
                "Role '%s' is still assigned to at least one user".formatted(target.getName()));
        }
        repository.delete(target);
    }

    private void requireUniqueName(String name) {
        if (repository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Role '%s' already exists".formatted(name));
        }
    }
}
