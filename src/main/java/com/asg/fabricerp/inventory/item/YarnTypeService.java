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

import static com.asg.fabricerp.inventory.item.Masters.*;

@Service
public class YarnTypeService {

    static final String CODE_PREFIX = "YT";

    public record YarnTypeRequest(Long id, String name, String shortName, String description, Boolean active) { }

    private final YarnTypeRepository repository;
    private final InventoryItemRepository items;
    private final DocumentNumberService numbering;
    private final OrgContext context;

    public YarnTypeService(YarnTypeRepository repository, InventoryItemRepository items,
                           DocumentNumberService numbering, OrgContext context) {
        this.repository = repository;
        this.items = items;
        this.numbering = numbering;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(String q, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), q, pageable).map(YarnTypeService::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return row(get(id));
    }

    @Transactional(readOnly = true)
    public List<Option> lookup() {
        return repository.lookup(context.requireOrganizationId()).stream()
            .map(t -> new Option(t.getId(), t.getCode(),
                t.getShortName() == null ? t.getName() : t.getName() + " (" + t.getShortName() + ")"))
            .toList();
    }

    @Transactional(readOnly = true)
    public YarnType get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Yarn type not found: " + id));
    }

    @Transactional
    public Map<String, Object> save(YarnTypeRequest request) {
        YarnType target;
        if (request.id() == null) {
            target = new YarnType();
            target.setCode(numbering.nextCode(CODE_PREFIX, 4));
        } else {
            target = get(request.id());
        }
        target.setName(required(request.name(), "Yarn type name"));
        target.setShortName(clean(request.shortName()));
        target.setDescription(clean(request.description()));
        target.setActive(request.active() == null || request.active());
        return row(repository.save(target));
    }

    @Transactional
    public Map<String, Object> approve(Long id) {
        YarnType target = get(id);
        target.approve(context.username(), LocalDateTime.now());
        return row(repository.save(target));
    }

    @Transactional
    public void delete(Long id) {
        YarnType target = get(id);
        if (items.existsByYarnType_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("This yarn type is used by yarn items. Deactivate it instead.");
        }
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    static Map<String, Object> row(YarnType t) {
        Map<String, Object> row = approvableRow(t);
        row.put("code", t.getCode());
        row.put("name", t.getName());
        row.put("shortName", t.getShortName());
        row.put("description", t.getDescription());
        return row;
    }
}
