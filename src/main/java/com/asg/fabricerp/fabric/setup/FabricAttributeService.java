package com.asg.fabricerp.fabric.setup;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for every fabric reference list. One service, seven lists.
 */
@Service
public class FabricAttributeService {

    private final FabricAttributeRepository repository;
    private final OrgContext context;

    public FabricAttributeService(FabricAttributeRepository repository, OrgContext context) {
        this.repository = repository;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public List<FabricAttribute> lookup(AttributeType type) {
        return repository.lookup(context.requireOrganizationId(), type);
    }

    @Transactional(readOnly = true)
    public Page<FabricAttribute> search(AttributeType type, String query, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), type, query, pageable);
    }

    @Transactional(readOnly = true)
    public FabricAttribute get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Attribute not found: " + id));
    }

    @Transactional
    public FabricAttribute save(AttributeType type, FabricAttribute submitted) {
        if (submitted.getId() == null) {
            requireUniqueCode(type, submitted.getCode());
            submitted.setAttributeType(type);
            return repository.save(submitted);
        }
        FabricAttribute target = get(submitted.getId());
        if (!target.getCode().equalsIgnoreCase(submitted.getCode())) {
            requireUniqueCode(type, submitted.getCode());
            target.setCode(submitted.getCode());
        }
        target.setName(submitted.getName());
        target.setDescription(submitted.getDescription());
        target.setDisplayOrder(submitted.getDisplayOrder());
        target.setActive(submitted.getActive());
        return repository.save(target);
    }

    /**
     * Soft delete. A hard delete would orphan the value on every historical document line
     * that recorded it, because lines store the resolved name rather than a foreign key.
     */
    @Transactional
    public void delete(Long id) {
        FabricAttribute target = get(id);
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    private void requireUniqueCode(AttributeType type, String code) {
        boolean clash = repository
            .existsByOrganizationIdAndAttributeTypeAndCodeAndDeletedFalse(
                context.requireOrganizationId(), type, code);
        if (clash) {
            throw new IllegalArgumentException(
                "%s code '%s' already exists".formatted(type.label(), code));
        }
    }
}
