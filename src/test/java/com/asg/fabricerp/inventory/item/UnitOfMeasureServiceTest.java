package com.asg.fabricerp.inventory.item;

import com.asg.fabricerp.inventory.item.UnitOfMeasureService.UomRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static com.asg.fabricerp.inventory.item.ItemTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnitOfMeasureServiceTest {

    private UnitOfMeasureRepository repository;
    private InventoryItemRepository items;
    private UnitOfMeasureService service;

    @BeforeEach
    void setUp() {
        repository = mock(UnitOfMeasureRepository.class);
        items = mock(InventoryItemRepository.class);
        service = new UnitOfMeasureService(repository, items, context("tester"));
        when(repository.save(any(UnitOfMeasure.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void codesAreUpperCased_andConvertWithTheirFactor() {
        Map<String, Object> saved = service.save(new UomRequest(null, "gm", "Gram", "g", UomCategory.WEIGHT,
            false, new BigDecimal("0.001"), true));
        assertThat(saved.get("code")).isEqualTo("GM");

        UnitOfMeasure gram = unit(1L, "GM");
        gram.setConversionFactor(new BigDecimal("0.001"));
        assertThat(gram.toBase(new BigDecimal("2500"))).isEqualByComparingTo("2.5");
        assertThat(gram.fromBase(new BigDecimal("2.5"))).isEqualByComparingTo("2500");
    }

    @Test
    void aBaseUnitHasFactorOne_andACategoryHasOnlyOne() {
        assertThatThrownBy(() -> service.save(new UomRequest(null, "KG", "Kilogram", "kg", UomCategory.WEIGHT,
                true, new BigDecimal("2"), true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be 1");

        when(repository.baseUnitExists(ORG, UomCategory.WEIGHT, null)).thenReturn(true);
        assertThatThrownBy(() -> service.save(new UomRequest(null, "KG2", "Kilogram", "kg", UomCategory.WEIGHT,
                true, BigDecimal.ONE, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already has a base unit");
    }

    @Test
    void theFactorMustBePositive() {
        assertThatThrownBy(() -> service.save(new UomRequest(null, "X", "X", null, UomCategory.COUNT,
                false, BigDecimal.ZERO, true)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aUnitInUseKeepsItsCategory_andCannotBeDeleted() {
        UnitOfMeasure kg = unit(5L, "U111");
        when(repository.findScoped(5L, ORG)).thenReturn(Optional.of(kg));
        when(items.existsByBaseUnit_IdAndDeletedFalse(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.save(new UomRequest(5L, "U111", "Kilogram", "kg", UomCategory.COUNT,
                false, BigDecimal.ONE, true)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(IllegalStateException.class);
    }
}
