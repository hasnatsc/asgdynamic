package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.global.numbering.BusinessNumberService;
import com.asg.fabricerp.global.numbering.BusinessSeries;
import com.asg.fabricerp.inventory.item.YarnBlendService.BlendRequest;
import com.asg.fabricerp.inventory.item.YarnBlendService.ComponentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.asg.fabricerp.inventory.item.ItemTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class YarnBlendServiceTest {

    private YarnBlendRepository repository;
    private InventoryItemRepository items;
    private YarnBlendService service;

    @BeforeEach
    void setUp() {
        repository = mock(YarnBlendRepository.class);
        items = mock(InventoryItemRepository.class);
        BusinessNumberService numbering = mock(BusinessNumberService.class);
        when(numbering.next(BusinessSeries.YARN_BLEND)).thenReturn("BL-2026-0001");
        service = new YarnBlendService(repository, items, numbering, context("tester"));

        when(repository.save(any(YarnBlend.class))).thenAnswer(i -> i.getArgument(0));
        when(items.findScoped(1L, ORG)).thenReturn(Optional.of(fiber(1L, "Cotton")));
        when(items.findScoped(2L, ORG)).thenReturn(Optional.of(fiber(2L, "Viscose")));
    }

    private static ComponentRequest part(long fiberId, String pct) {
        return new ComponentRequest(fiberId, new BigDecimal(pct), null, null);
    }

    @Test
    void anUnnamedBlendIsNamedAfterItsComposition_inTheLegacyWording() {
        Map<String, Object> saved = service.save(new BlendRequest(null, null, null, null, true,
            List.of(part(1, "60"), part(2, "40.00"))));

        assertThat(saved.get("code")).isEqualTo("BL-2026-0001");
        assertThat(saved.get("name")).isEqualTo("60% Cotton 40% Viscose");
        assertThat(saved.get("composition")).isEqualTo("60% Cotton 40% Viscose");
    }

    @Test
    void percentagesMustTotalExactlyOneHundred() {
        assertThatThrownBy(() -> service.save(new BlendRequest(null, null, null, null, true,
                List.of(part(1, "60"), part(2, "30")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("total exactly 100%")
            .hasMessageContaining("90");
    }

    @Test
    void aBlendNeedsAtLeastOneFiber_andNoFiberTwice() {
        assertThatThrownBy(() -> service.save(new BlendRequest(null, null, null, null, true, List.of())))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(new BlendRequest(null, null, null, null, true,
                List.of(part(1, "50"), part(1, "50")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("twice");
    }

    @Test
    void onlyFiberItemsCanBeBlended() {
        InventoryItem chemical = fiber(3L, "Softener");
        chemical.setItemType(ItemType.CHEMICALS);
        when(items.findScoped(3L, ORG)).thenReturn(Optional.of(chemical));

        assertThatThrownBy(() -> service.save(new BlendRequest(null, null, null, null, true, List.of(part(3, "100")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a fiber");
    }

    @Test
    void aBlendInUseCannotBeRecomposed_butItsRemarksAndNameCanChange() {
        YarnBlend blend = new YarnBlend();
        blend.setId(9L);
        blend.setCode("BLAF0001");
        blend.setName("60% Cotton 40% Viscose");
        blend.replaceComponents(List.of(
            new YarnBlendComponent(fiber(1L, "Cotton"), new BigDecimal("60"), null, null),
            new YarnBlendComponent(fiber(2L, "Viscose"), new BigDecimal("40"), null, null)));
        when(repository.findScoped(9L, ORG)).thenReturn(Optional.of(blend));
        when(items.existsByYarnBlend_IdAndDeletedFalse(9L)).thenReturn(true);

        assertThatThrownBy(() -> service.save(new BlendRequest(9L, null, null, null, true,
                List.of(part(1, "50"), part(2, "50")))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("fixed");

        Map<String, Object> saved = service.save(new BlendRequest(9L, "CVC 60/40", null, null, true, List.of(
            new ComponentRequest(1L, new BigDecimal("60.00"), null, "BCI lot"),
            part(2, "40"))));
        assertThat(saved.get("name")).isEqualTo("CVC 60/40");
        assertThat(blend.getComponents().getFirst().getRemarks()).isEqualTo("BCI lot");
    }

    @Test
    void blendNamesAreUnique() {
        when(repository.nameTaken(eq(ORG), eq("60% Cotton 40% Viscose"), isNull())).thenReturn(true);

        assertThatThrownBy(() -> service.save(new BlendRequest(null, null, null, null, true,
                List.of(part(1, "60"), part(2, "40")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }
}
