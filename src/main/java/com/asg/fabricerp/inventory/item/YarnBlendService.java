package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.global.documents.DocumentNumberService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.asg.fabricerp.inventory.item.Masters.*;

/**
 * Fiber blends. On top of SpindleERP's rules (at least one component, percentages totalling
 * exactly 100) this also refuses the same fiber twice, a component that is not a FIBER item,
 * and re-composing a blend that yarn items already use - that would silently change what
 * stock on hand is made of.
 */
@Service
public class YarnBlendService {

    static final String CODE_PREFIX = "BL";

    public record ComponentRequest(Long fiberId, BigDecimal percentage, Certification certification,
                                   String remarks) { }

    public record BlendRequest(Long id, String name, String shortName, String description, Boolean active,
                               List<ComponentRequest> components) { }

    private final YarnBlendRepository repository;
    private final InventoryItemRepository items;
    private final DocumentNumberService numbering;
    private final OrgContext context;

    public YarnBlendService(YarnBlendRepository repository, InventoryItemRepository items,
                            DocumentNumberService numbering, OrgContext context) {
        this.repository = repository;
        this.items = items;
        this.numbering = numbering;
        this.context = context;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> search(String q, Pageable pageable) {
        return repository.search(context.requireOrganizationId(), q, pageable).map(YarnBlendService::row);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        YarnBlend blend = get(id);
        Map<String, Object> row = row(blend);
        List<Map<String, Object>> components = new ArrayList<>();
        for (YarnBlendComponent c : blend.getComponents()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("fiberId", c.getFiber().getId());
            line.put("fiberCode", c.getFiber().getItemCode());
            line.put("fiberName", c.getFiber().getName());
            line.put("percentage", c.getPercentage());
            line.put("certification", c.getCertification());
            line.put("remarks", c.getRemarks());
            components.add(line);
        }
        row.put("components", components);
        row.put("inUse", items.existsByYarnBlend_IdAndDeletedFalse(id));
        return row;
    }

    @Transactional(readOnly = true)
    public List<Option> lookup() {
        return repository.lookup(context.requireOrganizationId()).stream()
            .map(b -> new Option(b.getId(), b.getCode(), b.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public YarnBlend get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Yarn blend not found: " + id));
    }

    @Transactional
    public Map<String, Object> save(BlendRequest request) {
        Long orgId = context.requireOrganizationId();
        List<YarnBlendComponent> components = buildComponents(orgId, request.components());

        YarnBlend target;
        if (request.id() == null) {
            target = new YarnBlend();
            target.setCode(numbering.nextCode(CODE_PREFIX, 4));
            target.replaceComponents(components);
        } else {
            target = get(request.id());
            if (!sameComposition(target.getComponents(), components)
                    && items.existsByYarnBlend_IdAndDeletedFalse(target.getId())) {
                throw new IllegalStateException(
                    "Yarn items already use this blend, so its fibers and percentages are fixed. Create a new blend instead.");
            }
            target.replaceComponents(components);   // remarks may still have changed
        }

        String name = clean(request.name());
        if (name == null) name = target.composition();
        if (repository.nameTaken(orgId, name, target.getId())) {
            throw new IllegalArgumentException("A blend named '%s' already exists.".formatted(name));
        }
        target.setName(name);
        target.setShortName(clean(request.shortName()));
        target.setDescription(clean(request.description()));
        target.setActive(request.active() == null || request.active());
        return row(repository.save(target));
    }

    private List<YarnBlendComponent> buildComponents(Long orgId, List<ComponentRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("Add at least one fiber.");
        }
        List<YarnBlendComponent> built = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ComponentRequest c : requested) {
            Long fiberId = required(c.fiberId(), "Fiber");
            BigDecimal pct = required(c.percentage(), "Percentage");
            if (pct.signum() <= 0 || pct.compareTo(YarnBlend.FULL) > 0) {
                throw new IllegalArgumentException("Each fiber's percentage must be above 0 and at most 100.");
            }
            if (!seen.add(fiberId)) {
                throw new IllegalArgumentException("The same fiber is listed twice.");
            }
            InventoryItem fiber = items.findScoped(fiberId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Fiber not found: " + fiberId));
            if (fiber.getItemType() != ItemType.FIBER) {
                throw new IllegalArgumentException("'%s' is not a fiber item.".formatted(fiber.getName()));
            }
            total = total.add(pct);
            built.add(new YarnBlendComponent(fiber, pct, c.certification(), clean(c.remarks())));
        }
        if (total.compareTo(YarnBlend.FULL) != 0) {
            throw new IllegalArgumentException(
                "Fiber percentages must total exactly 100%%; they total %s%%.".formatted(total.stripTrailingZeros().toPlainString()));
        }
        return built;
    }

    /** Same fibers, same order, same percentages and certifications. Remarks do not count. */
    private static boolean sameComposition(List<YarnBlendComponent> current, List<YarnBlendComponent> proposed) {
        if (current.size() != proposed.size()) return false;
        for (int i = 0; i < current.size(); i++) {
            YarnBlendComponent a = current.get(i);
            YarnBlendComponent b = proposed.get(i);
            if (!Objects.equals(a.getFiber().getId(), b.getFiber().getId())
                    || a.getPercentage().compareTo(b.getPercentage()) != 0
                    || a.getCertification() != b.getCertification()) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public Map<String, Object> approve(Long id) {
        YarnBlend target = get(id);
        target.approve(context.username(), LocalDateTime.now());
        return row(repository.save(target));
    }

    @Transactional
    public void delete(Long id) {
        YarnBlend target = get(id);
        if (items.existsByYarnBlend_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("This blend is used by yarn items. Deactivate it instead.");
        }
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    static Map<String, Object> row(YarnBlend b) {
        Map<String, Object> row = approvableRow(b);
        row.put("code", b.getCode());
        row.put("name", b.getName());
        row.put("shortName", b.getShortName());
        row.put("composition", b.composition());
        row.put("description", b.getDescription());
        return row;
    }
}
