package com.asg.fabricerp.security;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class RoleService {

    private final RoleRepository repository;
    private final PermissionRepository permissionRepository;
    private final FabricUserRepository userRepository;

    public RoleService(RoleRepository repository, PermissionRepository permissionRepository,
                       FabricUserRepository userRepository) {
        this.repository = repository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<Role> search(String query, Pageable pageable) {
        return repository.search(query, pageable);
    }

    @Transactional(readOnly = true)
    public Role get(Long id) {
        return repository.findWithPermissionsById(id)
            .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    @Transactional
    public Role save(Role submitted, Set<Long> permissionIds) {
        Set<Permission> permissions = resolvePermissions(permissionIds);

        if (submitted.getId() == null) {
            requireUniqueName(submitted.getName());
            submitted.setPermissions(permissions);
            return repository.save(submitted);
        }
        Role target = get(submitted.getId());
        if (!target.getName().equalsIgnoreCase(submitted.getName())) {
            requireUniqueName(submitted.getName());
            target.setName(submitted.getName());
        }
        target.setDescription(submitted.getDescription());
        target.setActive(submitted.getActive());
        target.setPermissions(permissions);
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

    private Set<Permission> resolvePermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Set.of();
        }
        List<Permission> found = permissionRepository.findAllById(permissionIds);
        if (found.size() != permissionIds.size()) {
            throw new IllegalArgumentException("One or more permission ids do not exist");
        }
        return Set.copyOf(found);
    }

    private void requireUniqueName(String name) {
        if (repository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Role '%s' already exists".formatted(name));
        }
    }
}
