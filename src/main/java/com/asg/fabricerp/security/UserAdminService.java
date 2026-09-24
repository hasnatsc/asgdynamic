package com.asg.fabricerp.security;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** CRUD for {@link FabricUser} — the "create a real login" half of the admin screens. */
@Service
public class UserAdminService {

    private final FabricUserRepository repository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrgContext context;

    public UserAdminService(FabricUserRepository repository, RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder, OrgContext context) {
        this.repository = repository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<FabricUser> search(String query, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), query, pageable);
    }

    @Transactional(readOnly = true)
    public FabricUser get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    /** {@code password} required; {@code roleIds} may be empty (a login with no authorities yet). */
    @Transactional
    public FabricUser create(String username, String password, String fullName,
                             Long businessUnitId, String businessUnitCode, Long warehouseId,
                             Set<Long> roleIds) {
        requireUniqueUsername(username);
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        FabricUser user = new FabricUser(
            username, passwordEncoder.encode(password), businessUnitId, businessUnitCode);
        user.setOrganizationId(context.requireOrganizationId());
        user.setFullName(fullName);
        user.setWarehouseId(warehouseId);
        user.setRoles(resolveRoles(roleIds));
        return repository.save(user);
    }

    /** Everything except username and password — see {@link #resetPassword} for that. */
    @Transactional
    public FabricUser update(Long id, String fullName, Long businessUnitId,
                             String businessUnitCode, Long warehouseId, Set<Long> roleIds) {
        FabricUser user = get(id);
        user.setFullName(fullName);
        user.setBusinessUnitId(businessUnitId);
        user.setBusinessUnitCode(businessUnitCode);
        user.setWarehouseId(warehouseId);
        user.setRoles(resolveRoles(roleIds));
        return repository.save(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        FabricUser user = get(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        repository.save(user);
    }

    @Transactional
    public void setLocked(Long id, boolean locked) {
        FabricUser user = get(id);
        user.setAccountLocked(locked);
        repository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        FabricUser user = get(id);
        user.markDeleted();
        user.setActive(false);
        repository.save(user);
    }

    private Set<Role> resolveRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Set.of();
        }
        List<Role> found = roleRepository.findAllById(roleIds);
        if (found.size() != roleIds.size()) {
            throw new IllegalArgumentException("One or more role ids do not exist");
        }
        return Set.copyOf(found);
    }

    private void requireUniqueUsername(String username) {
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username '%s' already exists".formatted(username));
        }
    }
}
