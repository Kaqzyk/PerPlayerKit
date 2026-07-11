/*
 * Copyright 2022-2026 Noah Ross
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable bag of values a GUI is opened with (kit slot, inspect target,
 * page, ...). Placeholders in guis.yml resolve against these, and actions
 * read their arguments from them.
 */
public final class GuiContext {

    private static final GuiContext EMPTY = new GuiContext(Collections.emptyMap());

    private final Map<String, Object> values;
    // Lazily built, idempotent; volatile so a reader on another thread never
    // sees a partially constructed map. Everything else here is immutable.
    private volatile Map<String, String> stringValues;

    private GuiContext(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static GuiContext empty() {
        return EMPTY;
    }

    public GuiContext with(String key, Object value) {
        if (key == null || value == null) {
            return this;
        }

        Map<String, Object> updated = new LinkedHashMap<>(values);
        updated.put(key, value);
        return new GuiContext(updated);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public String getString(String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** All values stringified, for Lang's {token} substitution. Memoized. */
    public Map<String, String> stringValues() {
        if (stringValues == null) {
            Map<String, String> converted = new LinkedHashMap<>(values.size());
            values.forEach((key, value) -> converted.put(key, String.valueOf(value)));
            stringValues = Collections.unmodifiableMap(converted);
        }
        return stringValues;
    }

    public Integer getInt(String key) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            return Ints.tryParse(stringValue.trim());
        }
        return null;
    }

    public int getInt(String key, int defaultValue) {
        Integer value = getInt(key);
        return value != null ? value : defaultValue;
    }

    public UUID getUuid(String key) {
        Object value = values.get(key);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String stringValue) {
            try {
                return UUID.fromString(stringValue);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
