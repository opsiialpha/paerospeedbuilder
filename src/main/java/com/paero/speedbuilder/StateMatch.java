package com.paero.speedbuilder;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Block state property comparison used by finish detection.
 *
 * The schematic side (WorldEdit) and the world side (Minecraft) describe the same state with different
 * property ordering and sometimes different letter case (for example facing=NORTH vs facing=north), so
 * comparing the two as raw strings can silently never match. This compares by property name and value
 * instead, ignoring order and case.
 */
final class StateMatch {
    /** Properties that change by themselves (redstone, light, random ticks) and must not block a finish. */
    static final Set<String> IGNORED_KEYS = Set.of("triggered", "powered", "lit", "power", "age", "stage");

    private StateMatch() {
    }

    /** True when every non-ignored property of {@code expected} is present in {@code actual} with the same value. */
    static boolean matches(Map<String, String> expected, Map<String, String> actual) {
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (IGNORED_KEYS.contains(key)) {
                continue;
            }
            String found = lookup(actual, key);
            if (found == null || !found.trim().equalsIgnoreCase(String.valueOf(entry.getValue()).trim())) {
                return false;
            }
        }
        return true;
    }

    private static String lookup(Map<String, String> map, String lowerKey) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(lowerKey)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
