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
public class ItemModelService {

    static final String CODE_PREFIX = "MD";

    public record ModelRequest(Long id, Long brandId, String name, String shortName,
                               String description, Boolean active) { }

    private final ItemModelRepository repository;
    private final ItemBrandService brands;
    private final InventoryItemRepository items;
    private final DocumentNumberService numbering;
    private final OrgContext context;

    public ItemModelService(ItemModelRepository repository, ItemBrandService brands,
                            InventoryItemRepository items, DocumentNumberService numbering, OrgContext context) {
        this.repository = repository;
        this.brands = brands;
        this.items = items;
        this.numbering = numbering;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(Long brandId, String q, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), brandId, q, pageable).map(ItemModelService::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return row(get(id));
    }

    @Transactional(readOnly = true)
    public List<Option> lookup(Long brandId) {
        return repository.lookup(context.requireOrganizationId(), brandId).stream()
            .map(m -> new Option(m.getId(), m.getCode(), m.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public ItemModel get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Model not found: " + id));
    }

    @Transactional
    public Map<String, Object> save(ModelRequest request) {
        ItemModel target;
        if (request.id() == null) {
            target = new ItemModel();
            target.setCode(numbering.nextCode(CODE_PREFIX, 4));
        } else {
            target = get(request.id());
        }
        target.setBrand(request.brandId() == null ? null : brands.get(request.brandId()));
        target.setName(required(request.name(), "Model name"));
        target.setShortName(clean(request.shortName()));
        target.setDescription(clean(request.description()));
        target.setActive(request.active() == null || request.active());
        return row(repository.save(target));
    }

    @Transactional
    public Map<String, Object> approve(Long id) {
        ItemModel target = get(id);
        target.approve(context.username(), LocalDateTime.now());
        return row(repository.save(target));
    }

    @Transactional
    public void delete(Long id) {
        ItemModel target = get(id);
        if (items.existsByModel_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("This model is used by existing items. Deactivate it instead.");
        }
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    static Map<String, Object> row(ItemModel m) {
        Map<String, Object> row = approvableRow(m);
        row.put("code", m.getCode());
        row.put("name", m.getName());
        row.put("shortName", m.getShortName());
        row.put("brandId", m.getBrand() == null ? null : m.getBrand().getId());
        row.put("brandName", m.getBrand() == null ? null : m.getBrand().getName());
        row.put("description", m.getDescription());
        return row;
    }
}
