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
public class HsCodeService {

    public record HsCodeRequest(Long id, String hsCode, String description, String shortDescription,
                                HsCode.HsType hsType, BigDecimal customsDutyPercent, BigDecimal vatPercent,
                                BigDecimal supplementaryDutyPercent, BigDecimal aitPercent,
                                Boolean bondedAllowed, Boolean requiresExportPermit,
                                Boolean requiresImportPermit, Boolean active) { }

    private final HsCodeRepository repository;
    private final InventoryItemRepository items;
    private final OrgContext context;

    public HsCodeService(HsCodeRepository repository, InventoryItemRepository items, OrgContext context) {
        this.repository = repository;
        this.items = items;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(String q, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), q, pageable).map(HsCodeService::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return row(get(id));
    }

    @Transactional(readOnly = true)
    public List<Option> lookup() {
        return repository.lookup(context.requireOrganizationId()).stream()
            .map(h -> new Option(h.getId(), h.getHsCode(),
                h.getShortDescription() == null ? h.getHsCode() : h.getHsCode() + " - " + h.getShortDescription()))
            .toList();
    }

    @Transactional(readOnly = true)
    public HsCode get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("HS code not found: " + id));
    }

    @Transactional
    public Map<String, Object> save(HsCodeRequest request) {
        Long orgId = context.requireOrganizationId();
        String code = required(request.hsCode(), "HS code");

        HsCode target;
        if (request.id() == null) {
            requireUniqueCode(orgId, code);
            target = new HsCode();
        } else {
            target = get(request.id());
            if (!target.getHsCode().equalsIgnoreCase(code)) requireUniqueCode(orgId, code);
        }
        target.setHsCode(code);
        target.setDescription(clean(request.description()));
        target.setShortDescription(clean(request.shortDescription()));
        target.setHsType(required(request.hsType(), "HS type"));
        target.setCustomsDutyPercent(nonNegative(request.customsDutyPercent(), "Customs duty"));
        target.setVatPercent(nonNegative(request.vatPercent(), "VAT"));
        target.setSupplementaryDutyPercent(nonNegative(request.supplementaryDutyPercent(), "Supplementary duty"));
        target.setAitPercent(nonNegative(request.aitPercent(), "AIT"));
        target.setBondedAllowed(request.bondedAllowed());
        target.setRequiresExportPermit(request.requiresExportPermit());
        target.setRequiresImportPermit(request.requiresImportPermit());
        target.setActive(request.active() == null || request.active());
        return row(repository.save(target));
    }

    @Transactional
    public void delete(Long id) {
        HsCode target = get(id);
        if (items.existsByHsCode_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("This HS code is used by existing items. Deactivate it instead.");
        }
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    private void requireUniqueCode(Long orgId, String code) {
        if (repository.existsByOrganizationIdAndHsCodeIgnoreCaseAndDeletedFalse(orgId, code)) {
            throw new IllegalArgumentException("HS code '%s' already exists.".formatted(code));
        }
    }

    static Map<String, Object> row(HsCode h) {
        Map<String, Object> row = baseRow(h);
        row.put("hsCode", h.getHsCode());
        row.put("description", h.getDescription());
        row.put("shortDescription", h.getShortDescription());
        row.put("hsType", h.getHsType());
        row.put("customsDutyPercent", h.getCustomsDutyPercent());
        row.put("vatPercent", h.getVatPercent());
        row.put("supplementaryDutyPercent", h.getSupplementaryDutyPercent());
        row.put("aitPercent", h.getAitPercent());
        row.put("bondedAllowed", h.getBondedAllowed());
        row.put("requiresExportPermit", h.getRequiresExportPermit());
        row.put("requiresImportPermit", h.getRequiresImportPermit());
        return row;
    }
}
