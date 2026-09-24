package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.asg.fabricerp.inventory.item.Masters.*;

@Service
public class UnitOfMeasureService {

    public record UomRequest(Long id, String code, String name, String symbol, UomCategory category,
                             Boolean baseUnit, BigDecimal conversionFactor, Boolean active) { }

    private final UnitOfMeasureRepository repository;
    private final InventoryItemRepository items;
    private final OrgContext context;

    public UnitOfMeasureService(UnitOfMeasureRepository repository, InventoryItemRepository items,
                                OrgContext context) {
        this.repository = repository;
        this.items = items;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(String q, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), q, pageable).map(UnitOfMeasureService::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return row(get(id));
    }

    @Transactional(readOnly = true)
    public List<Option> lookup() {
        return repository.lookup(context.requireOrganizationId()).stream()
            .map(u -> new Option(u.getId(), u.getCode(), u.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public UnitOfMeasure get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + id));
    }

    @Transactional
    public Map<String, Object> save(UomRequest request) {
        Long orgId = context.requireOrganizationId();
        String code = required(request.code(), "Code").toUpperCase();
        UomCategory category = required(request.category(), "Category");
        boolean base = Boolean.TRUE.equals(request.baseUnit());
        BigDecimal factor = request.conversionFactor() == null ? BigDecimal.ONE : request.conversionFactor();

        if (factor.signum() <= 0) {
            throw new IllegalArgumentException("Conversion factor must be greater than zero.");
        }
        if (base && factor.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("A base unit's conversion factor must be 1.");
        }
        // Conversions are relative to the category's base unit, so a second one makes every
        // factor in that category ambiguous. SpindleERP allowed it; the database refuses it here too.
        if (base && repository.baseUnitExists(orgId, category, request.id())) {
            throw new IllegalArgumentException(
                "%s already has a base unit. Clear that one first.".formatted(category));
        }

        UnitOfMeasure target;
        if (request.id() == null) {
            requireUniqueCode(orgId, code);
            target = new UnitOfMeasure();
        } else {
            target = get(request.id());
            if (!target.getCode().equalsIgnoreCase(code)) requireUniqueCode(orgId, code);
            if (target.getCategory() != category && items.existsByBaseUnit_IdAndDeletedFalse(target.getId())) {
                throw new IllegalStateException("This unit is in use by items; its category cannot change.");
            }
        }
        target.setCode(code);
        target.setName(required(request.name(), "Name"));
        target.setSymbol(clean(request.symbol()));
        target.setCategory(category);
        target.setBaseUnit(base);
        target.setConversionFactor(factor);
        target.setActive(request.active() == null || request.active());
        return row(repository.save(target));
    }

    @Transactional
    public void delete(Long id) {
        UnitOfMeasure target = get(id);
        if (items.existsByBaseUnit_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("This unit is the base unit of existing items. Deactivate it instead.");
        }
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    private void requireUniqueCode(Long orgId, String code) {
        if (repository.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(orgId, code)) {
            throw new IllegalArgumentException("Unit code '%s' already exists.".formatted(code));
        }
    }

    static Map<String, Object> row(UnitOfMeasure u) {
        Map<String, Object> row = baseRow(u);
        row.put("code", u.getCode());
        row.put("name", u.getName());
        row.put("symbol", u.getSymbol());
        row.put("category", u.getCategory());
        row.put("baseUnit", u.getBaseUnit());
        row.put("conversionFactor", u.getConversionFactor().stripTrailingZeros().toPlainString());
        return row;
    }
}
