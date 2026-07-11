package dev.noah.perplayerkit.gui.configurable;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity checks on the guis.yml shipped in the jar: every menu the service
 * opens by id must exist, editor data components must expose exactly the
 * slot counts KitManager's save methods expect, and the YAML template
 * anchors must survive a YamlConfiguration (SnakeYAML) load — the same
 * library the server uses.
 */
class BundledGuisConfigTest {

    private static YamlConfiguration config;

    @BeforeAll
    static void load() throws Exception {
        try (var stream = BundledGuisConfigTest.class.getClassLoader().getResourceAsStream("guis.yml")) {
            assertNotNull(stream, "guis.yml missing from resources");
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private static ConfigurationSection gui(String id) {
        ConfigurationSection section = config.getConfigurationSection("guis." + id);
        assertNotNull(section, "guis.yml is missing the '" + id + "' menu");
        return section;
    }

    private static ConfigurationSection element(String guiId, String elementKey) {
        ConfigurationSection section = gui(guiId).getConfigurationSection("elements." + elementKey);
        assertNotNull(section, guiId + " is missing element '" + elementKey + "'");
        return section;
    }

    private static List<Integer> elementSlots(String guiId, String elementKey) {
        int menuSize = gui(guiId).getInt("rows", 6) * 9;
        return GuiSlots.parse(element(guiId, elementKey).get("slots"), menuSize,
                message -> {
                    throw new AssertionError(message);
                });
    }

    @Test
    void hasConfigVersion() {
        assertTrue(config.getInt("config-version", 0) >= 1);
    }

    @Test
    void definesEveryMenuTheServiceOpens() {
        for (String id : List.of("main-menu", "player-kit-editor", "public-kit-editor", "enderchest-editor",
                "inspect-kit", "inspect-enderchest", "kit-room", "public-kit-menu", "public-kit-viewer",
                "view-only-enderchest")) {
            gui(id);
        }
    }

    @Test
    void kitEditorsExposeFullKitContents() {
        // 41 = 36 inventory + 4 armor + offhand; KitManager indexes armor at
        // 36-39, so fewer slots would corrupt saves.
        for (String id : List.of("player-kit-editor", "public-kit-editor", "inspect-kit", "public-kit-viewer")) {
            assertEquals("kit-data", element(id, "data").getString("component"), id);
            assertEquals(41, elementSlots(id, "data").size(), id);
        }
    }

    @Test
    void enderchestEditorsExposeFullEnderchest() {
        // Component names decide whose data renders: the stored enderchest
        // kit vs the player's live enderchest (view-only).
        assertEquals("enderchest-data", element("enderchest-editor", "data").getString("component"));
        assertEquals("enderchest-data", element("inspect-enderchest", "data").getString("component"));
        assertEquals("player-enderchest-data", element("view-only-enderchest", "data").getString("component"));
        for (String id : List.of("enderchest-editor", "inspect-enderchest", "view-only-enderchest")) {
            assertEquals(27, elementSlots(id, "data").size(), id);
        }
    }

    @Test
    void adminUnassignedPublicKitsGiveFeedbackOnClick() {
        // Left/right on an unassigned kit route through the load/viewer
        // paths whose guards send the "how to assign" instructions; without
        // these actions the admin workflow is a silent no-op.
        ConfigurationSection variant = element("public-kit-menu", "kits")
                .getConfigurationSection("variants.admin_unassigned");
        assertNotNull(variant);
        assertEquals("load-public-kit", firstActionType(variant, "left"));
        assertEquals("open-gui", firstActionType(variant, "right"));
    }

    @Test
    void kitRoomExposesFullPage() {
        assertEquals("kit-room-data", element("kit-room", "data").getString("component"));
        assertEquals(45, elementSlots("kit-room", "data").size());
    }

    @Test
    void mainMenuSelectorsCoverNineColumns() {
        // One column per kit slot of a page; SLOTS_PER_PAGE is 9.
        for (String elementKey : List.of("kits", "enderchests", "kit-status")) {
            assertEquals("kit-slot-selector", element("main-menu", elementKey).getString("component"), elementKey);
            assertEquals(9, elementSlots("main-menu", elementKey).size(), elementKey);
        }
    }

    @Test
    void templateAnchorsResolveThroughYamlConfiguration() {
        // The armor indicators and editor tools are YAML merge keys; if
        // SnakeYAML stopped flattening them, these elements would vanish.
        for (String id : List.of("player-kit-editor", "public-kit-editor", "inspect-kit", "public-kit-viewer")) {
            for (String indicator : List.of("boots", "leggings", "chestplate", "helmet", "offhand")) {
                element(id, indicator);
            }
        }
        for (String id : List.of("player-kit-editor", "public-kit-editor")) {
            assertEquals("import-player-inventory",
                    firstActionType(element(id, "import"), "any"), id);
            assertEquals("clear-editor",
                    firstActionType(element(id, "clear"), "shift"), id);
        }
    }

    @Test
    void kitRoomControlIsPermissionGated() {
        assertEquals("perplayerkit.editkitroom", element("kit-room", "back").getString("exclude-permission"));
        assertEquals("perplayerkit.editkitroom", element("kit-room", "save").getString("permission"));
        assertEquals(elementSlots("kit-room", "back"), elementSlots("kit-room", "save"));
    }

    private static String firstActionType(ConfigurationSection element, String clickKey) {
        List<?> actions = element.getList("actions." + clickKey);
        assertNotNull(actions, element.getCurrentPath() + " has no actions." + clickKey);
        Object first = actions.get(0);
        assertTrue(first instanceof java.util.Map<?, ?>, "action is not a map");
        return String.valueOf(((java.util.Map<?, ?>) first).get("type"));
    }
}
