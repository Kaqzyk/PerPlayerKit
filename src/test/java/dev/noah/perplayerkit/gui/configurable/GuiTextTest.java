package dev.noah.perplayerkit.gui.configurable;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuiTextTest {

    private final Map<String, String> lang = Map.of(
            "gui.kit-slot-name", "<dark_aqua><b>Kit {slot}</b></dark_aqua>",
            "gui.main-menu-title-paged", "{player}'s Kits ({page}/{pages})");

    private final Map<String, String> context = Map.of(
            "slot", "5",
            "player", "Noah",
            "page", "2",
            "pages", "3",
            "primary_color", "<blue>");

    /** Mirrors the service wiring: the lang lookup applies context tokens itself, like Lang.raw(key, map). */
    private final UnaryOperator<String> langLookup = key -> {
        String value = lang.get(key);
        if (value == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : context.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    };

    private String resolve(String value) {
        return GuiText.resolve(value, langLookup, context::get);
    }

    @Test
    void expandsLangKeyWithItsTokens() {
        assertEquals("<dark_aqua><b>Kit 5</b></dark_aqua>", resolve("%lang:gui.kit-slot-name%"));
    }

    @Test
    void expandsPagedTitle() {
        assertEquals("<blue>Noah's Kits (2/3)", resolve("%primary_color%%lang:gui.main-menu-title-paged%"));
    }

    @Test
    void expandsPercentTokens() {
        assertEquals("Kit 5 for Noah", resolve("Kit %slot% for %player%"));
    }

    @Test
    void leavesUnknownPercentTokensForPlaceholderApi() {
        assertEquals("hello %papi_placeholder%", resolve("hello %papi_placeholder%"));
    }

    @Test
    void leavesUnknownLangKeysIntact() {
        assertEquals("%lang:gui.does-not-exist%", resolve("%lang:gui.does-not-exist%"));
    }

    @Test
    void nullPassesThrough() {
        assertNull(resolve(null));
    }

    @Test
    void resolveIntReadsNumbers() {
        assertEquals(7, GuiText.resolveInt(7, context::get));
    }

    @Test
    void resolveIntExpandsContextPlaceholders() {
        // Regression: a "slot: %slot%" action argument must resolve to the
        // context slot, not silently fall back to a default.
        assertEquals(5, GuiText.resolveInt("%slot%", context::get));
    }

    @Test
    void resolveIntNullWhenNotNumeric() {
        Function<String, String> empty = key -> null;
        assertNull(GuiText.resolveInt("%slot%", empty));
        assertNull(GuiText.resolveInt("abc", empty));
        assertNull(GuiText.resolveInt(null, empty));
    }
}
