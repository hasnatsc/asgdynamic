package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.common.LookupPage;
import com.asg.fabricerp.inventory.item.ItemCategoryService.CategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.asg.fabricerp.inventory.item.ItemTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * The positional category codes (ported from SpindleERP, and matching the legacy CAF110000 roots)
 * and the tree rules SpindleERP did not enforce.
 */
class ItemCategoryServiceTest {

    private ItemCategoryRepository repository;
    private InventoryItemRepository items;
    private ItemCategoryService service;

    private final ItemCategory rawMaterial = category(1L, "CAF110000", null, null);

    @BeforeEach
    void setUp() {
        repository = mock(ItemCategoryRepository.class);
        items = mock(InventoryItemRepository.class);
        service = new ItemCategoryService(repository, items, context("tester"));
        when(repository.save(any(ItemCategory.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.findScoped(1L, ORG)).thenReturn(Optional.of(rawMaterial));
        // The six legacy roots seeded by V13.
        when(repository.rootCodes(ORG)).thenReturn(List.of(
            "CAF110000", "CAF120000", "CAF130000", "CAF140000", "CAF150000", "CAF160000"));
    }

    // ---------------------------------------------------------------------------- codes

    @Test
    void aNewRootContinuesAfterTheLegacyRoots() {
        Map<String, Object> saved = service.save(new CategoryRequest(null, null, "Packing", "CAF", null, null, true));
        assertThat(saved.get("code")).isEqualTo("CAF170000");
        assertThat(saved.get("layer")).isEqualTo(ItemCategory.Layer.ROOT);
    }

    @Test
    void aRootWithoutAPrefixTakesTheFirstTwoConsonantsOfItsName() {
        assertThat(ItemCategoryService.resolvePrefix(null, "Cotton")).isEqualTo("CT");
        assertThat(ItemCategoryService.resolvePrefix("  ", "Viscose")).isEqualTo("VS");
        assertThat(ItemCategoryService.resolvePrefix(null, "Yarn")).isEqualTo("YR");
        assertThat(ItemCategoryService.resolvePrefix("caf", "Anything")).isEqualTo("CAF");
    }

    @Test
    void theFirstGroupUnderARootIsEleven_andTheNextFollowsTheHighestSibling() {
        when(repository.childCodes(ORG, 1L)).thenReturn(List.of());
        assertThat(service.save(new CategoryRequest(null, 1L, "Yarn", null, null, null, true)).get("code"))
            .isEqualTo("CAF111100");

        // A deleted sibling still counts: codes are never reissued.
        when(repository.childCodes(ORG, 1L)).thenReturn(List.of("CAF111100", "CAF111300"));
        assertThat(service.save(new CategoryRequest(null, 1L, "Dyes", null, null, null, true)).get("code"))
            .isEqualTo("CAF111400");
    }

    @Test
    void anItemCategoryInheritsItsGroupsPosition() {
        ItemCategory group = category(2L, "CAF111200", rawMaterial, null);
        when(repository.findScoped(2L, ORG)).thenReturn(Optional.of(group));
        when(repository.childCodes(ORG, 2L)).thenReturn(List.of("CAF111211"));

        Map<String, Object> saved = service.save(new CategoryRequest(null, 2L, "Cotton yarn", null, ItemType.YARN, null, true));

        assertThat(saved.get("code")).isEqualTo("CAF111212");
        assertThat(saved.get("layer")).isEqualTo(ItemCategory.Layer.ITEM);
        assertThat(saved.get("prefix")).isEqualTo("CAF");
    }

    @Test
    void theLegacyTreesIrregularCodesAreNumberedAfter_notCollidedWith() {
        // CAF111201 started its group at 01; the next child follows it rather than jumping to 11.
        assertThat(ItemCategoryService.nextSequence(List.of("CAF111201"), 4)).isEqualTo(2);
        // SFB121113 sits under a CAF group; only its digits count.
        assertThat(ItemCategoryService.nextSequence(List.of("CAF121111", "CAF121112", "SFB121113"), 4)).isEqualTo(14);
    }

    @Test
    void aLevelRunsOutAfterNinetyNine() {
        assertThatThrownBy(() -> ItemCategoryService.nextSequence(List.of("CAF990000"), 0))
            .isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------------------------- tree rules

    @Test
    void anItemCategoryMustSayWhichItemTypeItHolds() {
        ItemCategory group = category(2L, "CAF111200", rawMaterial, null);
        when(repository.findScoped(2L, ORG)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> service.save(new CategoryRequest(null, 2L, "Untyped", null, null, null, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("item type");
    }

    @Test
    void anItemCategoryCannotHaveChildren() {
        ItemCategory group = category(2L, "CAF111200", rawMaterial, null);
        ItemCategory leaf = category(3L, "CAF111211", group, ItemType.YARN);
        when(repository.findScoped(3L, ORG)).thenReturn(Optional.of(leaf));

        assertThatThrownBy(() -> service.save(new CategoryRequest(null, 3L, "Too deep", null, ItemType.YARN, null, true)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aCategoryWithChildrenCannotMove() {
        ItemCategory group = category(2L, "CAF111200", rawMaterial, null);
        ItemCategory otherRoot = category(4L, "CAF120000", null, null);
        when(repository.findScoped(2L, ORG)).thenReturn(Optional.of(group));
        when(repository.findScoped(4L, ORG)).thenReturn(Optional.of(otherRoot));
        when(repository.existsByParent_IdAndDeletedFalse(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.save(new CategoryRequest(2L, 4L, "Moved", null, null, null, true)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sub-categories");
    }

    @Test
    void aCategoryOnlyMovesWithinItsLevel_andIsRecodedUnderItsNewParent() {
        ItemCategory group = category(2L, "CAF111200", rawMaterial, null);
        ItemCategory otherRoot = category(4L, "CAF120000", null, null);
        when(repository.findScoped(2L, ORG)).thenReturn(Optional.of(group));
        when(repository.findScoped(4L, ORG)).thenReturn(Optional.of(otherRoot));
        when(repository.childCodes(ORG, 4L)).thenReturn(List.of());

        Map<String, Object> moved = service.save(new CategoryRequest(2L, 4L, "Moved", null, null, null, true));
        assertThat(moved.get("code")).isEqualTo("CAF121100");
        assertThat(moved.get("parentId")).isEqualTo(4L);

        // A group cannot become a root.
        assertThatThrownBy(() -> service.save(new CategoryRequest(2L, null, "Moved", null, null, null, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("same level");
    }

    @Test
    void theItemTypeIsFixedOnceItemsAreFiledThere() {
        ItemCategory group = category(2L, "CAF111200", rawMaterial, null);
        ItemCategory leaf = category(3L, "CAF111211", group, ItemType.YARN);
        when(repository.findScoped(3L, ORG)).thenReturn(Optional.of(leaf));
        when(items.existsByCategory_IdAndDeletedFalse(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.save(new CategoryRequest(3L, 2L, "Yarn", null, ItemType.FIBER, null, true)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteIsRefusedWhileChildrenOrItemsRemain() {
        when(repository.existsByParent_IdAndDeletedFalse(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class);

        when(repository.existsByParent_IdAndDeletedFalse(1L)).thenReturn(false);
        when(items.existsByCategory_IdAndDeletedFalse(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theTreeIsDepthFirstInCodeOrder() {
        ItemCategory group = category(2L, "CAF111100", rawMaterial, null);
        ItemCategory leaf = category(3L, "CAF111111", group, ItemType.YARN);
        ItemCategory secondRoot = category(4L, "CAF120000", null, null);
        when(repository.findTree(ORG)).thenReturn(List.of(secondRoot, leaf, rawMaterial, group));

        List<Map<String, Object>> tree = service.tree();

        assertThat(tree).extracting(r -> r.get("code"))
            .containsExactly("CAF110000", "CAF111100", "CAF111111", "CAF120000");
        assertThat(tree).extracting(r -> r.get("depth")).containsExactly(0, 1, 2, 0);
    }

    // ---------------------------------------------------------------------------- parent picker

    @Test
    void aNewCategoryMayGoUnderAnyRootOrGroup() {
        when(repository.search(eq(ORG), any(), isNull(), isNull(), eq("%yarn%"), any()))
            .thenReturn(new SliceImpl<>(List.of(rawMaterial), PageRequest.of(0, 20), true));

        LookupPage<LookupPage.Option> page = service.parentLookup(null, " Yarn ", 1, 20);

        verify(repository).search(eq(ORG), eq(EnumSet.of(ItemCategory.Layer.ROOT, ItemCategory.Layer.GROUP)),
            isNull(), isNull(), eq("%yarn%"), eq(PageRequest.of(0, 20)));
        assertThat(page.results()).extracting(LookupPage.Option::id).containsExactly(1L);
        assertThat(page.pagination().more()).isTrue();
    }

    @Test
    void aMoveOffersOnlyParentsOnTheSameLevelAndNeverItself() {
        ItemCategory group = category(2L, "CAF111100", rawMaterial, null);
        when(repository.findScoped(2L, ORG)).thenReturn(Optional.of(group));
        when(repository.search(any(), any(), any(), any(), any(), any()))
            .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 20), false));

        service.parentLookup(2L, null, null, null);

        verify(repository).search(eq(ORG), eq(EnumSet.of(ItemCategory.Layer.ROOT)), isNull(), eq(2L), eq("%"), any());
        assertThat(service.parentLookup(1L, null, null, null).results()).isEmpty();   // a root has no parent
    }

    @Test
    void anOptionSaysWhereTheCategorySits() {
        ItemCategory group = category(2L, "CAF111100", rawMaterial, null);
        ItemCategory leaf = category(3L, "CAF111111", group, ItemType.YARN);

        assertThat(ItemCategoryService.option(rawMaterial).sub()).isEqualTo("Root");
        assertThat(ItemCategoryService.option(leaf).sub())
            .isEqualTo("Item category in Category CAF110000 › Category CAF111100");
    }
}
