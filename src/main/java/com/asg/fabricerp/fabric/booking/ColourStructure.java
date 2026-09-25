package com.asg.fabricerp.fabric.booking;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How many colours one fabric line of a given fabric type may be booked in. A solid-dyed cloth
 * is piece-dyed one shade, so its line takes a single colour; each further shade is a line of
 * its own. Yarn-dyed and printed cloth carries several yarn or print colours, so its line takes
 * a colour breakdown.
 *
 * <p>Fabric types are matched loosely - case, spaces and punctuation ignored - because the same
 * type is spelled more than one way ("Greige Solid Dyed (LUNGI)", "Greige Solid Dyed Lungi").
 * A type not listed here, or none at all, takes any number of colours.
 */
public enum ColourStructure {
    SINGLE, MULTIPLE;

    /** B_6, B_7, B_12, B_13, B_14 of {@code so_dtlSet_fabricsType}. */
    static final List<String> SINGLE_COLOUR_TYPES = List.of(
        "Solid Dyed", "Solid Dyed Spandex",
        "Greige Solid Dyed", "Greige Solid Dyed Spandex", "Greige Solid Dyed (LUNGI)");

    private static final Set<String> SINGLE_KEYS = SINGLE_COLOUR_TYPES.stream()
        .map(ColourStructure::key).collect(Collectors.toUnmodifiableSet());

    public static ColourStructure of(String fabricType) {
        return fabricType != null && SINGLE_KEYS.contains(key(fabricType)) ? SINGLE : MULTIPLE;
    }

    /** The comparison key: lower case, letters and digits only. booking.js normalises the same way. */
    static String key(String fabricType) {
        return fabricType.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
