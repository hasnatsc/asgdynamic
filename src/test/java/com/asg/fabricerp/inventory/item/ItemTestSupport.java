package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.OrgContext;
import com.asg.fabricerp.common.RowScope;
import org.springframework.test.util.ReflectionTestUtils;

/** Fixtures shared by the item master tests. */
final class ItemTestSupport {

    static final Long ORG = 1L;

    private ItemTestSupport() { }

    static OrgContext context(String username) {
        return new OrgContext() {
            @Override public Long organizationId()     { return ORG; }
            @Override public Long businessUnitId()     { return 10L; }
            @Override public String businessUnitCode() { return "AF"; }
            @Override public Long warehouseId()        { return 1L; }
            @Override public String username()         { return username; }
            @Override public RowScope rowScope()       { return RowScope.unrestrictedScope(); }
        };
    }

    /** The audit columns are stamped by the entity listener only; tests set them directly. */
    static <T> T createdBy(T entity, String user) {
        ReflectionTestUtils.setField(entity, "createdBy", user);
        return entity;
    }

    static ItemCategory category(Long id, String code, ItemCategory parent, ItemType type) {
        ItemCategory c = new ItemCategory();
        c.setId(id);
        c.setOrganizationId(ORG);
        c.placeUnder(parent);
        c.setCode(code);
        c.setName("Category " + code);
        c.setItemType(type);
        return c;
    }

    static InventoryItem fiber(Long id, String name) {
        InventoryItem item = new InventoryItem();
        item.setId(id);
        item.setOrganizationId(ORG);
        item.setItemType(ItemType.FIBER);
        item.setItemCode("ITMAF00000" + id);
        item.setName(name);
        return item;
    }

    static UnitOfMeasure unit(Long id, String code) {
        UnitOfMeasure u = new UnitOfMeasure();
        u.setId(id);
        u.setCode(code);
        u.setName(code);
        u.setCategory(UomCategory.WEIGHT);
        return u;
    }
}
