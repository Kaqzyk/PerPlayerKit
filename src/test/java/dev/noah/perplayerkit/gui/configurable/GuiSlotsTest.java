package dev.noah.perplayerkit.gui.configurable;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiSlotsTest {

    private final List<String> warnings = new ArrayList<>();

    private List<Integer> parse(Object value) {
        return GuiSlots.parse(value, 54, warnings::add);
    }

    @Test
    void parsesSingleNumber() {
        assertEquals(List.of(37), parse(37));
    }

    @Test
    void parsesRangeString() {
        assertEquals(List.of(9, 10, 11, 12), parse("9-12"));
    }

    @Test
    void parsesMixedStringPreservingOrder() {
        assertEquals(List.of(53, 0, 1, 2), parse("53, 0-2"));
    }

    @Test
    void parsesDescendingRange() {
        assertEquals(List.of(12, 11, 10), parse("12-10"));
    }

    @Test
    void parsesListOfNumbersAndRanges() {
        assertEquals(List.of(45, 47, 48, 49), parse(List.of(45, "47-49")));
    }

    @Test
    void dropsDuplicates() {
        assertEquals(List.of(1, 2, 3), parse("1-3,2"));
    }

    @Test
    void warnsAndSkipsSlotsOutsideMenu() {
        assertEquals(List.of(53), parse("53-55"));
        assertEquals(2, warnings.size());
    }

    @Test
    void warnsOnGarbage() {
        assertEquals(List.of(5), parse("5, oops"));
        assertEquals(1, warnings.size());
    }

    @Test
    void rejectsAbsurdRangesWithoutExpandingThem() {
        assertTrue(parse("0-999999999").isEmpty());
        assertTrue(parse("0-" + Integer.MAX_VALUE).isEmpty());
        assertEquals(2, warnings.size());
    }

    @Test
    void emptyWhenNull() {
        assertTrue(parse(null).isEmpty());
        assertTrue(warnings.isEmpty());
    }
}
