package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.numbering.BusinessSeries;
import com.asg.fabricerp.inventory.item.InventoryItemService.ItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.asg.fabricerp.inventory.item.ItemTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryItemServiceTest {

    private InventoryItemRepository repository;
    private ItemCategoryService categories;
    private ItemBrandService brands;
    private ItemModelService models;
    private YarnBlendRepository blendRepository;
    private InventoryItemService service;

    private final ItemCategory root = category(1L, "CAF110000", null, null);
    private final ItemCategory group = category(2L, "CAF111100", root, null);
    private final ItemCategory yarnCategory = category(3L, "CAF111111", group, ItemType.YARN);
    private final ItemCategory chemicalCategory = category(4L, "CAF111112", group, ItemType.CHEMICALS);
    private final ItemCategory fiberCategory = category(5L, "CAF111113", group, ItemType.FIBER);

    @BeforeEach
    void setUp() {
        repository = mock(InventoryItemRepository.class);
        categories = mock(ItemCategoryService.class);
        UnitOfMeasureService units = mock(UnitOfMeasureService.class);
        brands = mock(ItemBrandService.class);
        models = mock(ItemModelService.class);
        YarnTypeService yarnTypes = mock(YarnTypeService.class);
        YarnCountService yarnCounts = mock(YarnCountService.class);
        YarnPlyService yarnPlies = mock(YarnPlyService.class);
        YarnBlendService yarnBlends = mock(YarnBlendService.class);
        blendRepository = mock(YarnBlendRepository.class);
        BusinessNumberService numbering = mock(BusinessNumberService.class);
        when(numbering.next(BusinessSeries.ITEM)).thenReturn("ITM-2026-000001");

        service = new InventoryItemService(repository, categories, units, mock(HsCodeService.class), brands, models,
            yarnTypes, yarnCounts, yarnPlies, yarnBlends, blendRepository, numbering, context("maker"));

        when(repository.save(any(InventoryItem.class))).thenAnswer(i -> i.getArgument(0));
        when(categories.get(1L)).thenReturn(root);
        when(categories.get(3L)).thenReturn(yarnCategory);
        when(categories.get(4L)).thenReturn(chemicalCategory);
        when(categories.get(5L)).thenReturn(fiberCategory);
        when(units.get(11L)).thenReturn(unit(11L, "U111"));

        // The legacy yarn masters: count 30, ply 1, type Card (CD), blend 60% Cotton 40% Viscose.
        YarnType card = new YarnType();
        card.setId(21L);
        card.setName("Card");
        card.setShortName("CD");
        YarnCount thirty = new YarnCount();
        thirty.setId(22L);
        thirty.setName("30");
        YarnPly single = new YarnPly();
        single.setId(23L);
        single.setPlyNumber(1);
        single.setName("Single");
        YarnBlend blend = new YarnBlend();
        blend.setId(24L);
        blend.setName("60% Cotton 40% Viscose");
        when(yarnTypes.get(21L)).thenReturn(card);
        when(yarnCounts.get(22L)).thenReturn(thirty);
        when(yarnPlies.get(23L)).thenReturn(single);
        when(yarnBlends.get(24L)).thenReturn(blend);
    }

    private static ItemRequest request(Long id, ItemType type, String name, Long categoryId) {
        return new ItemRequest(id, type, name, null, null, categoryId, 11L, null, null, null, null, null, true,
            null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null, null,
            null, null, null, null, null, null,
            null, null, null, null);
    }

    private static ItemRequest yarn(Long id) {
        return new ItemRequest(id, ItemType.YARN, "typed name is ignored", null, null, 3L, 11L, null, null, null, null, null, true,
            null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            21L, 22L, 23L, 24L, "A",
            null, null, null, null, null, null,
            null, null, null, null, null, null,
            null, null, null, null);
    }

    // ---------------------------------------------------------------------------- yarn

    @Test
    void aYarnIsNamedFromCountPlyTypeAndBlend_andNumbered() {
        Map<String, Object> saved = service.save(yarn(null));

        assertThat(saved.get("name")).isEqualTo("30/1 CD 60% Cotton 40% Viscose");
        assertThat(saved.get("itemCode")).isEqualTo("ITM-2026-000001");
        assertThat(saved.get("yarnBlendId")).isEqualTo(24L);
        assertThat(saved.get("qualityGrade")).isEqualTo("A");
    }

    @Test
    void twoActiveYarnsCannotShareTypeCountPlyAndBlend() {
        InventoryItem existing = fiber(99L, "30/1 CD 60% Cotton 40% Viscose");
        when(repository.findYarnDuplicates(ORG, 21L, 22L, 23L, 24L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.save(yarn(null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same type, count, ply and blend");
    }

    @Test
    void aYarnNeedsAllFourAttributes() {
        assertThatThrownBy(() -> service.save(request(null, ItemType.YARN, null, 3L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Yarn type");
    }

    @Test
    void leavingYarnClearsTheYarnAttributes() {
        InventoryItem item = new InventoryItem();
        item.setId(7L);
        item.setItemType(ItemType.YARN);
        item.specifyYarn(new YarnType(), new YarnCount(), new YarnPly(), new YarnBlend());
        when(repository.findScoped(7L, ORG)).thenReturn(Optional.of(item));

        service.save(request(7L, ItemType.FIBER, "Cotton", 5L));

        assertThat(item.getYarnType()).isNull();
        assertThat(item.getYarnBlend()).isNull();
    }

    // ---------------------------------------------------------------------------- category & type rules

    @Test
    void itemsAreFiledOnlyUnderItemLevelCategoriesOfTheirOwnType() {
        assertThatThrownBy(() -> service.save(request(null, ItemType.GENERAL, "Stapler", 1L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("item-level category");

        assertThatThrownBy(() -> service.save(request(null, ItemType.FIBER, "Cotton", 3L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Yarn category");
    }

    @Test
    void aHazardousChemicalNeedsItsSafetyDataSheet() {
        ItemRequest hazardous = new ItemRequest(null, ItemType.CHEMICALS, "Caustic soda", null, null, 4L, 11L,
            null, null, null, null, null, true,
            null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null,
            "NaOH", null, true, "  ", null, null,
            null, null, null, null, null, null,
            null, null, null, null);

        assertThatThrownBy(() -> service.save(hazardous))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("safety data sheet");
    }

    @Test
    void aModelMustBelongToTheChosenBrand() {
        ItemBrand xianho = new ItemBrand();
        xianho.setId(31L);
        xianho.setName("Xianho");
        ItemBrand other = new ItemBrand();
        other.setId(32L);
        other.setName("Other");
        ItemModel model = new ItemModel();
        model.setId(41L);
        model.setName("X-100");
        model.setBrand(xianho);
        when(brands.get(32L)).thenReturn(other);
        when(models.get(41L)).thenReturn(model);

        ItemRequest mismatched = new ItemRequest(null, ItemType.CHEMICALS, "Dye", null, null, 4L, 11L,
            null, 32L, 41L, null, null, true,
            null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null, null,
            null, null, null, null, null, null,
            null, null, null, null);

        assertThatThrownBy(() -> service.save(mismatched))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("belongs to brand 'Xianho'");
    }

    @Test
    void minimumStockCannotExceedMaximum() {
        ItemRequest inverted = new ItemRequest(null, ItemType.CHEMICALS, "Dye", null, null, 4L, 11L,
            null, null, null, null, null, true,
            null, new BigDecimal("10"), new BigDecimal("5"), null, null, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null,
            null, null, null, null, null, null,
            null, null, null, null, null, null,
            null, null, null, null);

        assertThatThrownBy(() -> service.save(inverted))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Minimum stock");
    }

    @Test
    void aFiberThatBlendsUseStaysAFiber_andCannotBeDeleted() {
        InventoryItem cotton = fiber(8L, "Cotton");
        when(repository.findScoped(8L, ORG)).thenReturn(Optional.of(cotton));
        when(blendRepository.usesFiber(8L)).thenReturn(true);

        assertThatThrownBy(() -> service.save(request(8L, ItemType.CHEMICALS, "Cotton", 4L)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.delete(8L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void itemNamesAreUnique() {
        when(repository.nameTaken(ORG, "Cotton", null)).thenReturn(true);

        assertThatThrownBy(() -> service.save(request(null, ItemType.FIBER, " Cotton ", 5L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    // ---------------------------------------------------------------------------- approval

    @Test
    void theMakerCannotApproveTheirOwnItem_butSomeoneElseCan() {
        InventoryItem mine = createdBy(fiber(8L, "Cotton"), "maker");
        when(repository.findScoped(8L, ORG)).thenReturn(Optional.of(mine));
        assertThatThrownBy(() -> service.approve(8L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("created yourself");

        InventoryItem theirs = createdBy(fiber(9L, "Linen"), "someone.else");
        theirs.setCategory(fiberCategory);
        theirs.setBaseUnit(unit(11L, "U111"));
        when(repository.findScoped(9L, ORG)).thenReturn(Optional.of(theirs));
        Map<String, Object> approved = service.approve(9L);
        assertThat(approved.get("approved")).isEqualTo(true);
        assertThat(approved.get("approvedBy")).isEqualTo("maker");

        assertThatThrownBy(() -> theirs.approve("third.person", LocalDateTime.now()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already approved");
    }
}
