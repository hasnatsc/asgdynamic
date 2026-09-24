package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.DocumentNumberService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.asg.fabricerp.inventory.item.Masters.*;

@Service
public class YarnPlyService {

    static final String CODE_PREFIX = "YP";

    public record YarnPlyRequest(Long id, Integer plyNumber, String name, String description, Boolean active) { }

    private final YarnPlyRepository repository;
    private final InventoryItemRepository items;
    private final DocumentNumberService numbering;
    private final OrgContext context;

    public YarnPlyService(YarnPlyRepository repository, InventoryItemRepository items,
                          DocumentNumberService numbering, OrgContext context) {
        this.repository = repository;
        this.items = items;
        this.numbering = numbering;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(String q, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), q, pageable).map(YarnPlyService::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return row(get(id));
    }

    @Transactional(readOnly = true)
    public List<Option> lookup() {
        return repository.lookup(context.requireOrganizationId()).stream()
            .map(p -> new Option(p.getId(), p.getCode(), p.getDisplayName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public YarnPly get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Yarn ply not found: " + id));
    }

    @Transactional
    public Map<String, Object> save(YarnPlyRequest request) {
        Long orgId = context.requireOrganizationId();
        Integer number = required(request.plyNumber(), "Ply number");
        if (number < 1) throw new IllegalArgumentException("Ply number must be at least 1.");

        YarnPly target;
        if (request.id() == null) {
            requireUniqueNumber(orgId, number);
            target = new YarnPly();
            target.setCode(numbering.nextCode(CODE_PREFIX, 4));
        } else {
            target = get(request.id());
            if (!Objects.equals(target.getPlyNumber(), number)) {
                if (items.existsByYarnPly_IdAndDeletedFalse(target.getId())) {
                    // The number is part of every generated yarn name that uses it.
                    throw new IllegalStateException("This ply is used by yarn items; its number cannot change.");
                }
                requireUniqueNumber(orgId, number);
            }
        }
        target.setPlyNumber(number);
        target.setName(required(request.name(), "Ply name"));
        target.setDescription(clean(request.description()));
        target.setActive(request.active() == null || request.active());
        return row(repository.save(target));
    }

    @Transactional
    public Map<String, Object> approve(Long id) {
        YarnPly target = get(id);
        target.approve(context.username(), LocalDateTime.now());
        return row(repository.save(target));
    }

    @Transactional
    public void delete(Long id) {
        YarnPly target = get(id);
        if (items.existsByYarnPly_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("This yarn ply is used by yarn items. Deactivate it instead.");
        }
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    private void requireUniqueNumber(Long orgId, Integer number) {
        if (repository.existsByOrganizationIdAndPlyNumberAndDeletedFalse(orgId, number)) {
            throw new IllegalArgumentException("Ply number %d already exists.".formatted(number));
        }
    }

    static Map<String, Object> row(YarnPly p) {
        Map<String, Object> row = approvableRow(p);
        row.put("code", p.getCode());
        row.put("plyNumber", p.getPlyNumber());
        row.put("name", p.getName());
        row.put("description", p.getDescription());
        return row;
    }
}
