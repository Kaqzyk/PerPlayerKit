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

import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholder resolution for guis.yml text, kept free of Bukkit types so it
 * can be unit tested. Two placeholder forms are expanded, in order:
 *
 * <ol>
 * <li>{@code %lang:<key>%} — a message from the active language file. The
 * lang lookup is expected to substitute the message's own {@code {token}}
 * placeholders (Lang does this) before returning.</li>
 * <li>{@code %token%} — a GUI context value. Unknown tokens are left
 * untouched so PlaceholderAPI can claim them.</li>
 * </ol>
 */
public final class GuiText {

    private static final Pattern LANG_PATTERN = Pattern.compile("%lang:([a-zA-Z0-9_.-]+)%");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("%([a-zA-Z0-9_]+)%");

    private GuiText() {
    }

    /**
     * @param value  raw text from guis.yml
     * @param lang   resolves a language key to its message, context tokens
     *               already applied
     * @param values resolves a context token, or null when unknown
     */
    public static String resolve(String value, UnaryOperator<String> lang, Function<String, String> values) {
        if (value == null) {
            return null;
        }

        String result = replace(value, LANG_PATTERN, lang);
        return replace(result, PERCENT_PATTERN, values);
    }

    /**
     * Resolves a config value to an int, expanding placeholders in string
     * values first so "%slot%" means the context's slot rather than silently
     * falling back to a default. Returns null when the value is missing or
     * not numeric after expansion.
     */
    public static Integer resolveInt(Object rawValue, Function<String, String> values) {
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        if (rawValue instanceof String stringValue) {
            return Ints.tryParse(replace(stringValue, PERCENT_PATTERN, values).trim());
        }
        return null;
    }

    private static String replace(String value, Pattern pattern, Function<String, String> lookup) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return value;
        }

        StringBuilder buffer = new StringBuilder(value.length());
        do {
            String replacement = lookup.apply(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group()));
        } while (matcher.find());
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
