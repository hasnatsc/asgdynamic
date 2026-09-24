package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.OrgContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.asg.fabricerp.inventory.item.Masters.*;

/**
 * The item category tree and its positional codes.
 *
 * <h2>What changed from SpindleERP</h2>
 * <ul>
 *   <li><b>Moving is restricted to what keeps the tree valid.</b> SpindleERP lets any category
 *       be re-parented; {@code @PreUpdate} then recomputes the layer, so moving a GROUP that has
 *       ITEM children under another GROUP turns it into an ITEM with children - a shape the
 *       tree forbids - and its code keeps describing the old position. Here only a category
 *       with no children may move, only to a parent on the same level, and it is re-coded
 *       under its new parent.</li>
 *   <li><b>The prefix is fixed once the code is issued</b>, for the same reason: children
 *       inherit it from the parent's code.</li>
 *   <li><b>ITEM-layer categories must carry an item type.</b> SpindleERP's category form never
 *       sent one, so every category defaulted to GENERAL and the item-type filter on the
 *       category picker could never match anything else.</li>
 *   <li><b>Deleted categories still count when numbering</b>, so a code is never reissued.</li>
 * </ul>
 */
@Service
public class ItemCategoryService {

    /** Two digits per level; the first sibling is 11 (SpindleERP's and the legacy system's rule). */
    private static final int FIRST_SEQUENCE = 11;
    private static final int LAST_SEQUENCE = 99;

    public record CategoryRequest(Long id, Long parentId, String name, String prefix, ItemType itemType,
                                  String description, Boolean active) { }

    private final ItemCategoryRepository repository;
    private final InventoryItemRepository items;
    private final OrgContext context;

    public ItemCategoryService(ItemCategoryRepository repository, InventoryItemRepository items,
                               OrgContext context) {
        this.repository = repository;
        this.items = items;
        this.context = context;
    }

    // ---- Reads -----------------------------------------------------------------------------------

    /** The whole tree, depth-first in code order, each row carrying its depth for indentation. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> tree() {
        List<ItemCategory> all = repository.findTree(context.requireOrganizationId());
        Map<Long, List<ItemCategory>> children = new LinkedHashMap<>();
        List<ItemCategory> roots = new ArrayList<>();
        for (ItemCategory c : all) {
            if (c.getParent() == null) {
                roots.add(c);
            } else {
                children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        roots.sort(Comparator.comparing(ItemCategory::getCode));
        for (ItemCategory root : roots) {
            walk(root, 0, children, rows);
        }
        return rows;
    }

    private void walk(ItemCategory node, int depth, Map<Long, List<ItemCategory>> children,
                      List<Map<String, Object>> into) {
        List<ItemCategory> kids = children.getOrDefault(node.getId(), List.of());
        Map<String, Object> row = row(node);
        row.put("depth", depth);
        row.put("childCount", kids.size());
        into.add(row);
        kids.stream()
            .sorted(Comparator.comparing(ItemCategory::getCode))
            .forEach(kid -> walk(kid, depth + 1, children, into));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        return row(get(id));
    }

    /** Categories an item of {@code itemType} may be filed under (null = any type). */
    @Transactional(readOnly = true)
    public List<Option> itemCategoryLookup(ItemType itemType) {
        return repository.itemLayerLookup(context.requireOrganizationId(), itemType).stream()
            .map(c -> new Option(c.getId(), c.getCode(),
                c.getParent() == null ? c.getName() : c.getName() + " (" + c.getParent().getName() + ")"))
            .toList();
    }

    @Transactional(readOnly = true)
    public ItemCategory get(Long id) {
        return repository.findScoped(id, context.requireOrganizationId())
            .orElseThrow(() -> new IllegalArgumentException("Category not found: " + id));
    }

    // ---- Writes ----------------------------------------------------------------------------------

    @Transactional
    public Map<String, Object> save(CategoryRequest request) {
        ItemCategory target = request.id() == null ? create(request) : update(request);
        ItemType type = request.itemType();
        if (target.getLayer() == ItemCategory.Layer.ITEM && type == null) {
            throw new IllegalArgumentException("Choose the item type filed under this category.");
        }
        if (request.id() != null && type != target.getItemType()
                && items.existsByCategory_IdAndDeletedFalse(target.getId())) {
            throw new IllegalStateException("Items are already filed here; the item type cannot change.");
        }
        target.setItemType(type);
        target.setName(required(request.name(), "Category name"));
        target.setDescription(clean(request.description()));
        target.setActive(request.active() == null || request.active());
        return row(repository.save(target));
    }

    private ItemCategory create(CategoryRequest request) {
        ItemCategory parent = request.parentId() == null ? null : get(request.parentId());
        ItemCategory category = new ItemCategory();
        category.placeUnder(parent);    // throws for a child of an ITEM category
        String prefix = parent == null
            ? resolvePrefix(request.prefix(), request.name())
            : leadingLetters(parent.getCode());
        category.setPrefix(prefix);
        category.setCode(nextCode(parent, prefix));
        return category;
    }

    private ItemCategory update(CategoryRequest request) {
        ItemCategory category = get(request.id());
        if (!Objects.equals(category.getParentId(), request.parentId())) {
            move(category, request.parentId());
        }
        return category;
    }

    /** Re-parents a childless category to a parent on the same level, and re-codes it there. */
    private void move(ItemCategory category, Long newParentId) {
        if (repository.existsByParent_IdAndDeletedFalse(category.getId())) {
            throw new IllegalStateException(
                "Only a category without sub-categories can be moved; its children's codes depend on its position.");
        }
        ItemCategory newParent = newParentId == null ? null : get(newParentId);
        ItemCategory.Layer newLayer = newParent == null ? ItemCategory.Layer.ROOT : newParent.getLayer().child();
        if (newLayer != category.getLayer()) {
            throw new IllegalArgumentException(
                "A %s category can only move under a parent on the same level.".formatted(category.getLayer()));
        }
        // Numbered before re-parenting: the sibling query would otherwise auto-flush this row
        // into its new parent still wearing its old code.
        String prefix = newParent == null ? category.getPrefix() : leadingLetters(newParent.getCode());
        String code = nextCode(newParent, prefix);
        category.placeUnder(newParent);
        category.setPrefix(prefix);
        category.setCode(code);
    }

    @Transactional
    public void delete(Long id) {
        ItemCategory target = get(id);
        if (repository.existsByParent_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("Delete or move this category's sub-categories first.");
        }
        if (items.existsByCategory_IdAndDeletedFalse(id)) {
            throw new IllegalStateException("Items are filed under this category. Deactivate it instead.");
        }
        target.markDeleted();
        target.setActive(false);
        repository.save(target);
    }

    // ---- Codes -----------------------------------------------------------------------------------

    /**
     * The next free code under {@code parent} (null = a new root).
     * <pre>
     *   ROOT  : prefix + [root seq] + "0000"           CAF110000, CAF120000 ...
     *   GROUP : parent letters + [root] + [seq] + "00" CAF111100, CAF111200 ...
     *   ITEM  : parent letters + [root][group] + [seq] CAF111111, CAF111112 ...
     * </pre>
     */
    String nextCode(ItemCategory parent, String prefix) {
        Long orgId = context.requireOrganizationId();
        if (parent == null) {
            int next = nextSequence(repository.rootCodes(orgId), 0);
            return (prefix == null ? "" : prefix) + pad(next) + "0000";
        }
        String parentTail = digitTail(parent.getCode());
        if (parentTail.length() != 6) {
            throw new IllegalStateException("Parent category code '%s' is not in the six-digit shape."
                .formatted(parent.getCode()));
        }
        List<String> siblings = repository.childCodes(orgId, parent.getId());
        String letters = leadingLetters(parent.getCode());
        return switch (parent.getLayer()) {
            case ROOT -> letters + parentTail.substring(0, 2) + pad(nextSequence(siblings, 2)) + "00";
            case GROUP -> letters + parentTail.substring(0, 4) + pad(nextSequence(siblings, 4));
            case ITEM -> throw new IllegalStateException("An item category cannot have children.");
        };
    }

    /** Highest two-digit segment at {@code offset} in the siblings' digit tails, plus one. */
    static int nextSequence(List<String> siblingCodes, int offset) {
        int max = 0;
        for (String code : siblingCodes) {
            String tail = digitTail(code);
            if (tail.length() >= offset + 2) {
                max = Math.max(max, Integer.parseInt(tail.substring(offset, offset + 2)));
            }
        }
        int next = max == 0 ? FIRST_SEQUENCE : max + 1;
        if (next > LAST_SEQUENCE) {
            throw new IllegalStateException("This level is full: codes run out after " + LAST_SEQUENCE + ".");
        }
        return next;
    }

    /**
     * An explicit prefix wins; otherwise the first two consonants of the name -
     * Cotton becomes CT, Viscose VS, Yarn YR.
     */
    static String resolvePrefix(String submitted, String name) {
        String explicit = clean(submitted);
        if (explicit != null) return explicit.toUpperCase();
        if (name == null) return "";
        StringBuilder consonants = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (!Character.isLetter(c) || "aeiouAEIOU".indexOf(c) >= 0) continue;
            consonants.append(Character.toUpperCase(c));
            if (consonants.length() == 2) break;
        }
        return consonants.toString();
    }

    static String digitTail(String code) {
        return code.substring(leadingLetters(code).length());
    }

    static String leadingLetters(String code) {
        int i = 0;
        while (i < code.length() && !Character.isDigit(code.charAt(i))) i++;
        return code.substring(0, i);
    }

    private static String pad(int n) {
        return "%02d".formatted(n);
    }

    static Map<String, Object> row(ItemCategory c) {
        Map<String, Object> row = baseRow(c);
        row.put("code", c.getCode());
        row.put("prefix", c.getPrefix());
        row.put("name", c.getName());
        row.put("layer", c.getLayer());
        row.put("itemType", c.getItemType());
        row.put("parentId", c.getParentId());
        row.put("parentName", c.getParent() == null ? null : c.getParent().getName());
        row.put("description", c.getDescription());
        return row;
    }
}
