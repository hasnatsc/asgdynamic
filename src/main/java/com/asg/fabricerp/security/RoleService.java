package com.asg.fabricerp.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
public class RoleService {

    private final RoleRepository repository;
    private final FabricUserRepository userRepository;

    public RoleService(RoleRepository repository, FabricUserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<Role> search(String query, Pageable pageable) {
        return repository.search(query, pageable);
    }

    @Transactional(readOnly = true)
    public Role get(Long id) {
        return repository.findWithGrantsById(id)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    @Transactional
    public Role save(Role submitted, Map<Screen, Set<Verb>> grants) {
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
