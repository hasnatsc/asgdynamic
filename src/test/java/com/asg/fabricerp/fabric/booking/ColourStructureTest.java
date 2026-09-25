package com.asg.fabricerp.fabric.booking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Which fabric types a booking line may carry one colour of, and which several. */
class ColourStructureTest {

    @ParameterizedTest
    @ValueSource(strings = {"Solid Dyed", "Solid Dyed Spandex", "Greige Solid Dyed", "Greige Solid Dyed Spandex",
                            "Greige Solid Dyed (LUNGI)", "Greige Solid Dyed Lungi", " solid  dyed "})
    void solidDyedTypesTakeOneColour(String type) {
        assertThat(ColourStructure.of(type)).isEqualTo(ColourStructure.SINGLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Yarn Dyed", "Yarn Dyed Print", "Yarn Dyed Spandex", "Yarn Dyed Print Spandex",
                            "Yarn Dyed LUNGI (Greige)", "Solid Dyed Print", "Solid Dyed Print Spandex",
                            "Greige Yarn Dyed", "Greige Yarn Dyed Spandex", "Indigo Denim", ""})
    void everyOtherTypeTakesSeveral(String type) {
        assertThat(ColourStructure.of(type)).isEqualTo(ColourStructure.MULTIPLE);
    }

    @Test
    void noTypeYetTakesSeveral() {
        assertThat(ColourStructure.of(null)).isEqualTo(ColourStructure.MULTIPLE);
    }
}
