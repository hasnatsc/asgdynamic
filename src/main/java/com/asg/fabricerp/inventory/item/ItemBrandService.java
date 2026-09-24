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
public class ItemBrandService {

    /** Code prefix; the full code is prefix + unit + 4 digits, e.g. IBAF0001. */
    static final String CODE_PREFIX = "IB";

    public record BrandRequest(Long id, String name, String shortName, String countryOfOrigin,
                               String description, Boolean active) { }

    private final ItemBrandRepository repository;
    private final ItemModelRepository models;
    private final InventoryItemRepository items;
    private final DocumentNumberService numbering;
    private final OrgContext context;

    public ItemBrandService(ItemBrandRepository repository, ItemModelRepository models,
                            InventoryItemRepository items, DocumentNumberService numbering, OrgContext context) {
        this.repository = repository;
        this.models = models;
        this.items = items;
        this.numbering = numbering;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(String q, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), q, pageable).map(ItemBrandService::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return row(get(id));
    }

    @Transactional(readOnly = true)
    public List<Option> lookup() {
        return repository.lookup(context.requireOrganizationId()).stream()
            .map(b -> new Option(b.getId(), b.getCode(), b.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public ItemBrand get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Brand not found: " + id));
    }

    @Transactional
    public Map<String, Object> save(BrandRequest request) {
        ItemBrand target;
        if (request.id() == null) {
            target = new ItemBrand();
            target.setCode(numbering.nextCode(CODE_PREFIX, 4));
        } else {
            target = get(request.id());
        }
        target.setName(required(request.name(), "Brand name"));
        target.setShortName(clean(request.shortName()));
        target.setCountryOfOrigin(clean(request.countryOfOrigin()));
        target.setDescription(clean(request.description()));
        target.setActive(request.active() == null || request.active());
        return row(repository.save(target));
    }

    @Transactional
    public Map<String, Object> approve(Long id) {
        ItemBrand target = get(id);
        target.approve(context.username(), LocalDateTime.now());
        return row(repository.save(target));
    }

    @Transactional
    public void delete(Long id) {
        ItemBrand target = get(id);
        if (models.existsByBrand_IdAndDeletedFalse(id) || items.existsByBrand_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("This brand still has models or items. Deactivate it instead.");
        }
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    static Map<String, Object> row(ItemBrand b) {
        Map<String, Object> row = approvableRow(b);
        row.put("code", b.getCode());
        row.put("name", b.getName());
        row.put("shortName", b.getShortName());
        row.put("countryOfOrigin", b.getCountryOfOrigin());
        row.put("description", b.getDescription());
        return row;
    }
}
