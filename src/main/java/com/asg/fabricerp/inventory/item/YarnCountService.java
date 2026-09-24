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
public class YarnCountService {

    static final String CODE_PREFIX = "YC";

    public record YarnCountRequest(Long id, String name, String description, Boolean active) { }

    private final YarnCountRepository repository;
    private final InventoryItemRepository items;
    private final DocumentNumberService numbering;
    private final OrgContext context;

    public YarnCountService(YarnCountRepository repository, InventoryItemRepository items,
                            DocumentNumberService numbering, OrgContext context) {
        this.repository = repository;
        this.items = items;
        this.numbering = numbering;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(String q, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), q, pageable).map(YarnCountService::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return row(get(id));
    }

    @Transactional(readOnly = true)
    public List<Option> lookup() {
        return repository.lookup(context.requireOrganizationId()).stream()
            .map(c -> new Option(c.getId(), c.getCode(), c.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public YarnCount get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Yarn count not found: " + id));
    }

    @Transactional
    public Map<String, Object> save(YarnCountRequest request) {
        YarnCount target;
        if (request.id() == null) {
            target = new YarnCount();
            target.setCode(numbering.nextCode(CODE_PREFIX, 4));
        } else {
            target = get(request.id());
        }
        target.setName(required(request.name(), "Count"));
        target.setDescription(clean(request.description()));
        target.setActive(request.active() == null || request.active());
        return row(repository.save(target));
    }

    @Transactional
    public Map<String, Object> approve(Long id) {
        YarnCount target = get(id);
        target.approve(context.username(), LocalDateTime.now());
        return row(repository.save(target));
    }

    @Transactional
    public void delete(Long id) {
        YarnCount target = get(id);
        if (items.existsByYarnCount_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("This yarn count is used by yarn items. Deactivate it instead.");
        }
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    static Map<String, Object> row(YarnCount c) {
        Map<String, Object> row = approvableRow(c);
        row.put("code", c.getCode());
        row.put("name", c.getName());
        row.put("description", c.getDescription());
        return row;
    }
}
