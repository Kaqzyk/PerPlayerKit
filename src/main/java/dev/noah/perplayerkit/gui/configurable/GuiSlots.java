/*
 * Copyright 2022-2025 Noah Ross
 *
 * This file is part of PerPlayerKit.
 *
 * PerPlayerKit is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * PerPlayerKit is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with PerPlayerKit. If not, see <https://www.gnu.org/licenses/>.
 */
package dev.noah.perplayerkit.gui.configurable;

import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parses slot declarations from guis.yml. A declaration may be a single
 * number, a string of comma-separated entries and ranges ("0-8, 17, 26-18"),
 * or a YAML list mixing both. Order is preserved and duplicates dropped,
 * because item-data components map data indexes onto slots positionally.
 */
public final class GuiSlots {

    private static final int MAX_RANGE_SIZE = 128;

    private GuiSlots() {
    }

    public static List<Integer> parse(Object rawValue, int menuSize, Consumer<String> warn) {
        if (rawValue == null) {
            return Collections.emptyList();
        }

        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        if (rawValue instanceof Number number) {
            slots.add(number.intValue());
        } else if (rawValue instanceof String stringValue) {
            parseString(stringValue, slots, warn);
        } else if (rawValue instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Number number) {
                    slots.add(number.intValue());
                } else if (entry instanceof String stringValue) {
                    parseString(stringValue, slots, warn);
                }
            }
        } else {
            warn.accept("Unsupported slot declaration '" + rawValue + "'");
        }

        List<Integer> result = new ArrayList<>(slots.size());
        for (int slot : slots) {
            if (slot < 0 || slot >= menuSize) {
                warn.accept("Slot " + slot + " is outside the menu (size " + menuSize + "), skipping");
                continue;
            }
            result.add(slot);
        }
        return result;
    }

    private static void parseString(String value, LinkedHashSet<Integer> slots, Consumer<String> warn) {
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int dash = trimmed.indexOf('-', trimmed.startsWith("-") ? 1 : 0);
            if (dash > 0) {
                Integer start = Ints.tryParse(trimmed.substring(0, dash).trim());
                Integer end = Ints.tryParse(trimmed.substring(dash + 1).trim());
                if (start == null || end == null) {
                    warn.accept("Invalid slot range '" + trimmed + "'");
                    continue;
                }
                // No menu has more than 54 slots; a huge range is a typo, and
                // expanding it first would balloon the set (or overflow at the
                // int boundary) before the bounds check could reject it.
                if (Math.abs((long) end - start) >= MAX_RANGE_SIZE) {
                    warn.accept("Slot range '" + trimmed + "' is far larger than any menu, skipping");
                    continue;
                }
                int step = start <= end ? 1 : -1;
                for (int slot = start; slot != end + step; slot += step) {
                    slots.add(slot);
                }
            } else {
                Integer slot = Ints.tryParse(trimmed);
                if (slot == null) {
                    warn.accept("Invalid slot entry '" + trimmed + "'");
                    continue;
                }
                slots.add(slot);
            }
        }
    }
}
