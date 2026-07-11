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
import dev.noah.perplayerkit.ItemFilter;
import dev.noah.perplayerkit.KitManager;
import dev.noah.perplayerkit.KitRoomDataManager;
import dev.noah.perplayerkit.PublicKit;
import dev.noah.perplayerkit.gui.GuiCompat;
import dev.noah.perplayerkit.gui.ItemUtil;
import dev.noah.perplayerkit.gui.configurable.EditorSession.EditorType;
import dev.noah.perplayerkit.util.BroadcastManager;
import dev.noah.perplayerkit.util.IDUtil;
import dev.noah.perplayerkit.util.KitSlots;
import dev.noah.perplayerkit.util.Lang;
import dev.noah.perplayerkit.util.PlayerUtil;
import dev.noah.perplayerkit.util.SoundManager;
import dev.noah.perplayerkit.util.StyleManager;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.ipvp.canvas.Menu;
import org.ipvp.canvas.slot.ClickOptions;
import org.ipvp.canvas.slot.Slot;
import org.ipvp.canvas.type.ChestMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * Builds and opens the plugin's menus from the layouts in guis.yml.
 *
 * <p>Menu structure (slots, items, text, click actions) is config-driven;
 * behavior stays in code as named components and action types the config
 * refers to. Editor saving reuses the session model proven in the old GUI
 * class: an {@link EditorSession} is registered when an editor opens, flushed
 * either by InventoryCloseEvent or before opening the next menu — the latter
 * because canvas redraw navigation reuses the open inventory and never fires
 * a close event.
 */
public class ConfigurableGuiService {

    private static final String MAIN_MENU = "main-menu";
    private static final String PLAYER_KIT_EDITOR = "player-kit-editor";
    private static final String PUBLIC_KIT_EDITOR = "public-kit-editor";
    private static final String ENDERCHEST_EDITOR = "enderchest-editor";
    private static final String INSPECT_KIT = "inspect-kit";
    private static final String INSPECT_ENDERCHEST = "inspect-enderchest";
    private static final String KIT_ROOM = "kit-room";
    private static final String PUBLIC_KIT_MENU = "public-kit-menu";
    private static final String PUBLIC_KIT_VIEWER = "public-kit-viewer";
    private static final String VIEW_ONLY_ENDERCHEST = "view-only-enderchest";

    private static final int KIT_DATA_SIZE = 41;
    private static final int EC_DATA_SIZE = 27;

    private static ConfigurableGuiService instance;

    private final Plugin plugin;
    private final GuiConfigManager guiConfig;
    private final boolean filterItemsOnImport;
    private final Map<UUID, EditorSession> editorSessions = new HashMap<>();
    // Set by delete actions so closing the editor afterwards does not
    // immediately re-save the deleted kit.
    private final Set<UUID> skipNextSave = new HashSet<>();
    // Last main-menu page each player viewed, so back buttons from submenus
    // return to it instead of resetting to page 1.
    private final Map<UUID, Integer> lastMainMenuPage = new HashMap<>();
    // Data-component slot lists, re-resolved on every editor open otherwise.
    // The config is parsed once at startup, so entries never go stale.
    private final Map<String, List<Integer>> componentSlotsCache = new HashMap<>();

    public ConfigurableGuiService(Plugin plugin) {
        this.plugin = plugin;
        this.guiConfig = new GuiConfigManager(plugin);
        this.filterItemsOnImport = plugin.getConfig().getBoolean("anti-exploit.import-filter", false);
        instance = this;
    }

    public static ConfigurableGuiService get() {
        if (instance == null) {
            throw new IllegalStateException("ConfigurableGuiService has not been initialized");
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------

    public void openMainMenu(Player player) {
        // Commands always start on page 1; only submenu back buttons resume
        // the remembered page (see executeOpenGui).
        openMainMenu(player, 0);
    }

    public void openMainMenu(Player player, int page) {
        int pages = KitSlots.pageCount();
        page = Ints.constrainToRange(page, 0, pages - 1);
        if (pages > 1) {
            lastMainMenuPage.put(player.getUniqueId(), page);
        }

        openGui(MAIN_MENU, player, GuiContext.empty()
                .with("player", player.getName())
                .with("page_index", page)
                .with("pages", pages));
    }

    public void openPlayerKitEditor(Player player, int slot) {
        GuiContext context = GuiContext.empty()
                .with("slot", slot)
                .with("slot_page", KitSlots.pageOf(slot));
        if (openGui(PLAYER_KIT_EDITOR, player, context) != null) {
            registerEditor(player, PLAYER_KIT_EDITOR, "kit-data", EditorType.KIT, slot, null, null, null);
        }
    }

    public void openPublicKitEditor(Player player, String publicKitId) {
        GuiContext context = enrichPublicKitContext(GuiContext.empty().with("id", publicKitId), publicKitId);
        if (openGui(PUBLIC_KIT_EDITOR, player, context) != null) {
            registerEditor(player, PUBLIC_KIT_EDITOR, "kit-data", EditorType.PUBLIC_KIT, 0, publicKitId, null, null);
        }
    }

    public void openEnderchestEditor(Player player, int slot) {
        GuiContext context = GuiContext.empty()
                .with("slot", slot)
                .with("slot_page", KitSlots.pageOf(slot));
        if (openGui(ENDERCHEST_EDITOR, player, context) != null) {
            registerEditor(player, ENDERCHEST_EDITOR, "enderchest-data", EditorType.ENDERCHEST, slot, null, null, null);
        }
    }

    public void openInspectKit(Player player, UUID target, int slot) {
        String targetName = PlayerUtil.getPlayerName(target);
        GuiContext context = GuiContext.empty()
                .with("slot", slot)
                .with("player", targetName)
                .with("target_uuid", target)
                .with("target_name", targetName);
        if (openGui(INSPECT_KIT, player, context) != null) {
            registerEditor(player, INSPECT_KIT, "kit-data", EditorType.INSPECT_KIT, slot, null, target, targetName);
        }
    }

    public void openInspectEnderchest(Player player, UUID target, int slot) {
        String targetName = PlayerUtil.getPlayerName(target);
        GuiContext context = GuiContext.empty()
                .with("slot", slot)
                .with("player", targetName)
                .with("target_uuid", target)
                .with("target_name", targetName);
        if (openGui(INSPECT_ENDERCHEST, player, context) != null) {
            registerEditor(player, INSPECT_ENDERCHEST, "enderchest-data", EditorType.INSPECT_ENDERCHEST, slot, null, target, targetName);
        }
    }

    public void openKitRoom(Player player) {
        openKitRoom(player, 0);
    }

    public void openKitRoom(Player player, int page) {
        page = Ints.constrainToRange(page, 0, KitRoomDataManager.get().pageCount() - 1);
        openGui(KIT_ROOM, player, GuiContext.empty().with("page_index", page));
    }

    public void openPublicKitMenu(Player player) {
        openGui(PUBLIC_KIT_MENU, player, GuiContext.empty());
    }

    public void openPublicKitViewer(Player player, String publicKitId) {
        if (KitManager.get().getPublicKit(publicKitId) == null) {
            Lang.get().send(player, "error.kit-not-found-display");
            if (player.hasPermission("perplayerkit.admin")) {
                Lang.get().send(player, "info.assign-publickit-instruction");
            }
            return;
        }
        openGui(PUBLIC_KIT_VIEWER, player, enrichPublicKitContext(GuiContext.empty().with("id", publicKitId), publicKitId));
    }

    public void openViewOnlyEnderchest(Player player) {
        openGui(VIEW_ONLY_ENDERCHEST, player, GuiContext.empty());
    }

    // ------------------------------------------------------------------
    // Editor sessions
    // ------------------------------------------------------------------

    /** Invoked by KitMenuCloseListener when a menu inventory actually closes. */
    public void handleInventoryClose(Player player, Inventory inventory) {
        flush(player, inventory);
    }

    public void handlePlayerQuit(UUID player) {
        editorSessions.remove(player);
        skipNextSave.remove(player);
        lastMainMenuPage.remove(player);
    }

    private void registerEditor(Player player, String guiId, String dataComponent, EditorType type,
                                int slot, String publicKitId, UUID target, String targetName) {
        ConfigurationSection guiSection = guiConfig.getGuiSection(guiId);
        if (guiSection == null) {
            return;
        }
        int menuSize = rows(guiSection) * 9;
        List<Integer> dataSlots = componentSlots(guiId, dataComponent);
        if (dataSlots.isEmpty()) {
            plugin.getLogger().warning("GUI '" + guiId + "' has no " + dataComponent + " component; edits will not be saved");
            return;
        }
        skipNextSave.remove(player.getUniqueId());
        editorSessions.put(player.getUniqueId(),
                new EditorSession(type, slot, publicKitId, target, targetName, dataSlots, menuSize));
    }

    private void flush(Player player, Inventory inventory) {
        EditorSession session = editorSessions.get(player.getUniqueId());
        if (session == null || !session.matches(inventory)) {
            return;
        }
        editorSessions.remove(player.getUniqueId());
        saveEditor(player, session, inventory);
    }

    private void flushOpenEditor(Player player) {
        flush(player, player.getOpenInventory().getTopInventory());
        // Opening another menu invalidates any editor session (and pending
        // skip flag) regardless of whether the open inventory still matched.
        editorSessions.remove(player.getUniqueId());
        skipNextSave.remove(player.getUniqueId());
    }

    private void saveEditor(Player player, EditorSession session, Inventory inventory) {
        if (skipNextSave.remove(player.getUniqueId())) {
            return;
        }

        switch (session.type()) {
            case KIT -> KitManager.get().savekit(player.getUniqueId(), session.slot(),
                    session.extractContents(inventory, KIT_DATA_SIZE));
            case PUBLIC_KIT -> {
                if (session.publicKitId() != null && !session.publicKitId().isEmpty()) {
                    KitManager.get().savePublicKit(player, session.publicKitId(),
                            session.extractContents(inventory, KIT_DATA_SIZE));
                }
            }
            case ENDERCHEST -> KitManager.get().saveEC(player.getUniqueId(), session.slot(),
                    session.extractContents(inventory, EC_DATA_SIZE));
            case INSPECT_KIT -> {
                if (!player.hasPermission("perplayerkit.admin") || session.target() == null) {
                    return;
                }
                if (KitManager.get().savekit(session.target(), session.slot(),
                        session.extractContents(inventory, KIT_DATA_SIZE), true)) {
                    Lang.get().send(player, "success.admin-kit-updated",
                            "slot", String.valueOf(session.slot()), "player", session.targetName());
                } else {
                    Lang.get().send(player, "error.failed-to-update-kit", "player", session.targetName());
                }
            }
            case INSPECT_ENDERCHEST -> {
                if (!player.hasPermission("perplayerkit.admin") || session.target() == null) {
                    return;
                }
                if (KitManager.get().saveECSilent(session.target(), session.slot(),
                        session.extractContents(inventory, EC_DATA_SIZE))) {
                    Lang.get().send(player, "success.admin-ec-updated",
                            "slot", String.valueOf(session.slot()), "player", session.targetName());
                } else {
                    Lang.get().send(player, "error.failed-to-update-ec", "player", session.targetName());
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Menu construction
    // ------------------------------------------------------------------

    private Menu openGui(String guiId, Player viewer, GuiContext context) {
        // Flush before rendering, not just before opening: components read kit
        // data that a still-open editor may not have persisted yet.
        flushOpenEditor(viewer);

        ConfigurationSection guiSection = guiConfig.getGuiSection(guiId);
        if (guiSection == null) {
            plugin.getLogger().warning("Missing GUI definition: " + guiId);
            return null;
        }

        // The 1-based display page always accompanies the 0-based logic page,
        // so titles and lore can use {page} wherever page_index flows.
        if (context.has("page_index") && !context.has("page")) {
            context = context.with("page", context.getInt("page_index", 0) + 1);
        }

        int rows = rows(guiSection);
        String title = resolveTitle(guiSection, viewer, context);

        // Redraw lets canvas swap menus inside the already-open inventory so
        // the client keeps its cursor position; the stale title it leaves
        // behind is updated in place below. Only safe with setTitle support.
        Menu menu = ChestMenu.builder(rows)
                .title(title)
                .redraw(GuiCompat.supportsTitleUpdate())
                .build();
        menu.setCursorDropHandler(Menu.ALLOW_CURSOR_DROPPING);

        for (ConfigurationSection elementSection : orderedElements(guiSection)) {
            renderElement(menu, viewer, context, elementSection, rows * 9);
        }

        menu.open(viewer);
        GuiCompat.updateTitle(viewer, title);
        if (guiSection.getBoolean("open-sound", false)) {
            SoundManager.playOpenGui(viewer);
        }
        return menu;
    }

    private int rows(ConfigurationSection guiSection) {
        return Ints.constrainToRange(guiSection.getInt("rows", 6), 1, 6);
    }

    private String resolveTitle(ConfigurationSection guiSection, Player viewer, GuiContext context) {
        String titleKey = "title";
        Integer pages = context.getInt("pages");
        if (pages != null && pages > 1 && guiSection.isString("title-paged")) {
            titleKey = "title-paged";
        }
        String resolved = resolveText(guiSection.getString(titleKey, "Menu"), viewer, context);
        return StyleManager.convertMiniMessage(resolved);
    }

    private List<ConfigurationSection> orderedElements(ConfigurationSection guiSection) {
        ConfigurationSection elementsSection = guiSection.getConfigurationSection("elements");
        if (elementsSection == null) {
            return Collections.emptyList();
        }

        List<ConfigurationSection> sections = new ArrayList<>();
        for (String key : elementsSection.getKeys(false)) {
            ConfigurationSection section = elementsSection.getConfigurationSection(key);
            if (section != null) {
                sections.add(section);
            }
        }
        // Stable sort: explicit "order" keys guarantee layering (fill under
        // data under buttons) even for elements merged in from YAML templates,
        // whose iteration position is parser-defined.
        sections.sort((left, right) -> Integer.compare(left.getInt("order", 0), right.getInt("order", 0)));
        return sections;
    }

    private void renderElement(Menu menu, Player viewer, GuiContext context, ConfigurationSection elementSection, int menuSize) {
        if (!isVisibleToViewer(elementSection, viewer)) {
            return;
        }

        String type = elementSection.getString("type", "static").toLowerCase(Locale.ROOT);
        switch (type) {
            case "fill", "static" -> renderStaticElement(menu, viewer, context, elementSection, menuSize);
            case "component" -> renderComponent(menu, viewer, context, elementSection, menuSize);
            default -> plugin.getLogger().warning("Unknown GUI element type '" + type + "' in " + elementSection.getCurrentPath());
        }
    }

    private void renderStaticElement(Menu menu, Player viewer, GuiContext context, ConfigurationSection elementSection, int menuSize) {
        ItemStack item = buildItem(elementSection.getConfigurationSection("item"), viewer, context);
        ConfigurationSection actionsSection = elementSection.getConfigurationSection("actions");
        boolean editable = editableFor(elementSection, viewer);

        for (int slotIndex : parseSlots(elementSection.get("slots"), menuSize)) {
            Slot slot = menu.getSlot(slotIndex);
            if (item != null) {
                slot.setItem(item.clone());
            }
            if (editable) {
                slot.setClickOptions(ClickOptions.ALLOW_ALL);
            }
            bindActions(slot, actionsSection, context);
        }
    }

    private void renderComponent(Menu menu, Player viewer, GuiContext context, ConfigurationSection elementSection, int menuSize) {
        String component = elementSection.getString("component", "").toLowerCase(Locale.ROOT);
        List<Integer> slots = parseSlots(elementSection.get("slots"), menuSize);

        switch (component) {
            case "kit-slot-selector" -> renderKitSlotSelector(menu, viewer, slots, elementSection, context);
            case "main-menu-pagination" -> renderMainMenuPagination(menu, viewer, elementSection, context, menuSize);
            case "public-kit-list" -> renderPublicKitList(menu, viewer, slots, elementSection, context);
            case "kit-data" -> renderItemData(menu, viewer, slots, elementSection, resolveKitData(viewer, context));
            case "enderchest-data" -> renderItemData(menu, viewer, slots, elementSection, resolveEnderchestData(viewer, context));
            case "player-enderchest-data" -> renderItemData(menu, viewer, slots, elementSection, viewer.getEnderChest().getContents());
            case "kit-room-data" -> renderItemData(menu, viewer, slots, elementSection,
                    KitRoomDataManager.get().getKitRoomPage(context.getInt("page_index", 0)));
            case "kit-room-category-buttons" -> renderKitRoomCategoryButtons(menu, viewer, slots, elementSection, context);
            default -> plugin.getLogger().warning("Unknown GUI component '" + component + "' in " + elementSection.getCurrentPath());
        }
    }

    /**
     * One button per kit slot on the current main-menu page. Kit slot numbers
     * are derived from the page in the context, so the same element serves
     * every page of a raised max-kits limit. "source: enderchest" checks
     * enderchest existence for the exists/missing variants; default is kits.
     */
    private void renderKitSlotSelector(Menu menu, Player viewer, List<Integer> slots, ConfigurationSection elementSection, GuiContext context) {
        boolean enderchest = "enderchest".equalsIgnoreCase(elementSection.getString("source", "kit"));
        IntPredicate exists = enderchest
                ? slotNumber -> KitManager.get().hasEC(viewer.getUniqueId(), slotNumber)
                : slotNumber -> KitManager.get().hasKit(viewer.getUniqueId(), slotNumber);
        int page = context.getInt("page_index", 0);

        for (int i = 0; i < slots.size(); i++) {
            int slotNumber = page * KitSlots.SLOTS_PER_PAGE + i + 1;
            if (slotNumber > KitSlots.maxKits()) {
                break;
            }

            GuiContext slotContext = context.with("slot", slotNumber);
            String variant = exists.test(slotNumber) ? "exists" : "missing";
            applyVariant(menu.getSlot(slots.get(i)), viewer, elementSection, variant, slotContext);
        }
    }

    private void renderMainMenuPagination(Menu menu, Player viewer, ConfigurationSection elementSection, GuiContext context, int menuSize) {
        int page = context.getInt("page_index", 0);
        int pages = context.getInt("pages", 1);

        if (page > 0) {
            applyPageArrow(menu, viewer, elementSection, "previous", elementSection.get("previous-slot"), page - 1, pages, menuSize);
        }
        if (page < pages - 1) {
            applyPageArrow(menu, viewer, elementSection, "next", elementSection.get("next-slot"), page + 1, pages, menuSize);
        }
    }

    private void applyPageArrow(Menu menu, Player viewer, ConfigurationSection elementSection, String variant,
                                Object slotDeclaration, int targetPage, int pages, int menuSize) {
        List<Integer> slots = parseSlots(slotDeclaration, menuSize);
        if (slots.isEmpty()) {
            return;
        }
        GuiContext arrowContext = GuiContext.empty()
                .with("page_index", targetPage)
                .with("page", targetPage + 1)
                .with("pages", pages);
        applyVariant(menu.getSlot(slots.get(0)), viewer, elementSection, variant, arrowContext);
    }

    private void renderPublicKitList(Menu menu, Player viewer, List<Integer> slots, ConfigurationSection elementSection, GuiContext context) {
        List<PublicKit> publicKits = KitManager.get().getPublicKitList();
        boolean admin = viewer.hasPermission("perplayerkit.admin");

        if (publicKits.size() > slots.size()) {
            plugin.getLogger().warning("public-kit-list has " + slots.size() + " slots but " + publicKits.size()
                    + " public kits are defined; the rest are not shown");
        }

        for (int i = 0; i < Math.min(slots.size(), publicKits.size()); i++) {
            PublicKit publicKit = publicKits.get(i);
            boolean assigned = KitManager.get().hasPublicKit(publicKit.id);
            String variant = (admin ? "admin_" : "") + (assigned ? "assigned" : "unassigned");

            GuiContext slotContext = context
                    .with("id", publicKit.id)
                    .with("public_kit_name", publicKit.name)
                    .with("public_kit_icon", publicKit.icon.name());

            applyVariant(menu.getSlot(slots.get(i)), viewer, elementSection, variant, slotContext);
        }
    }

    private void renderItemData(Menu menu, Player viewer, List<Integer> slots, ConfigurationSection elementSection, ItemStack[] data) {
        boolean editable = editableFor(elementSection, viewer);

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = menu.getSlot(slots.get(i));
            ItemStack item = data != null && i < data.length && data[i] != null ? data[i].clone() : null;
            slot.setItem(item);
            if (editable) {
                slot.setClickOptions(ClickOptions.ALLOW_ALL);
            }
        }
    }

    private void renderKitRoomCategoryButtons(Menu menu, Player viewer, List<Integer> slots, ConfigurationSection elementSection, GuiContext context) {
        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("kitroom.items");
        int categoryCount = categories != null ? categories.getKeys(false).size() : 0;
        int pages = KitRoomDataManager.get().pageCount();
        if (categoryCount != slots.size() || categoryCount > pages) {
            plugin.getLogger().warning("kit-room-category-buttons has " + slots.size() + " slots for "
                    + categoryCount + " kitroom categories (storage holds " + pages + " pages)");
        }

        int currentPage = context.getInt("page_index", 0);
        for (int i = 0; i < Math.min(Math.min(slots.size(), categoryCount), pages); i++) {
            String basePath = "kitroom.items." + (i + 1);
            GuiContext slotContext = context
                    .with("page_index", i)
                    .with("page", i + 1)
                    .with("kitroom_name", plugin.getConfig().getString(basePath + ".name", "Page " + (i + 1)))
                    .with("kitroom_material", plugin.getConfig().getString(basePath + ".material", "BOOK"));

            String variant = i == currentPage ? "active" : "default";
            applyVariant(menu.getSlot(slots.get(i)), viewer, elementSection, variant, slotContext);
        }
    }

    private void applyVariant(Slot slot, Player viewer, ConfigurationSection elementSection, String variantName, GuiContext context) {
        List<ConfigurationSection> chain = variantChain(elementSection, variantName);

        ConfigurationSection itemSource = firstDefining(chain, "item");
        ItemStack item = itemSource != null ? buildItem(itemSource.getConfigurationSection("item"), viewer, context) : null;
        if (item != null) {
            slot.setItem(item);
        }

        ConfigurationSection editableSource = firstDefining(chain, "editable");
        if (editableSource != null && editableFor(editableSource, viewer)) {
            slot.setClickOptions(ClickOptions.ALLOW_ALL);
        }

        ConfigurationSection actionsSource = firstDefining(chain, "actions");
        bindActions(slot, actionsSource != null ? actionsSource.getConfigurationSection("actions") : null, context);
    }

    /**
     * Per-key fallback order for a variant: the named variant, then
     * "variants.default", then the element itself — so a variant that only
     * overrides its item still inherits shared actions or editability.
     */
    private List<ConfigurationSection> variantChain(ConfigurationSection elementSection, String variantName) {
        ConfigurationSection variantsSection = elementSection.getConfigurationSection("variants");
        if (variantsSection == null) {
            return List.of(elementSection);
        }

        List<ConfigurationSection> chain = new ArrayList<>(3);
        ConfigurationSection named = variantsSection.getConfigurationSection(variantName);
        if (named != null) {
            chain.add(named);
        }
        ConfigurationSection defaultSection = variantsSection.getConfigurationSection("default");
        if (defaultSection != null && defaultSection != named) {
            chain.add(defaultSection);
        }
        chain.add(elementSection);
        return chain;
    }

    private ConfigurationSection firstDefining(List<ConfigurationSection> chain, String key) {
        for (ConfigurationSection section : chain) {
            if (section.contains(key)) {
                return section;
            }
        }
        return null;
    }

    private boolean isVisibleToViewer(ConfigurationSection section, Player viewer) {
        String permission = section.getString("permission");
        if (permission != null && !permission.isEmpty() && !viewer.hasPermission(permission)) {
            return false;
        }
        String excludedPermission = section.getString("exclude-permission");
        return excludedPermission == null || excludedPermission.isEmpty() || !viewer.hasPermission(excludedPermission);
    }

    /**
     * "editable" is either a boolean or a permission node that grants editing
     * to viewers who hold it.
     */
    private boolean editableFor(ConfigurationSection section, Player viewer) {
        if (section.isBoolean("editable")) {
            return section.getBoolean("editable");
        }
        String permission = section.getString("editable");
        return permission != null && !permission.isEmpty() && viewer.hasPermission(permission);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void bindActions(Slot slot, ConfigurationSection actionsSection, GuiContext context) {
        if (actionsSection == null) {
            return;
        }

        slot.setClickHandler((player, info) -> {
            // Feedback on every click of an interactive slot, even when the
            // click type matches no action (e.g. left click on a shift-only
            // button) — a silent button reads as broken.
            SoundManager.playClick(player);
            for (Map<?, ?> action : actionsForClick(actionsSection, info.getClickType())) {
                executeAction(player, info.getClickedMenu(), context, action);
            }
        });
    }

    /**
     * Picks the action list for a click, most specific key first: the exact
     * type ("shift_left"), then "shift", "left"/"right"/"middle", then "any".
     */
    private List<Map<?, ?>> actionsForClick(ConfigurationSection actionsSection, ClickType clickType) {
        List<String> candidates = new ArrayList<>(4);
        candidates.add(clickType.name().toLowerCase(Locale.ROOT));
        if (clickType.isShiftClick()) {
            candidates.add("shift");
        }
        boolean mouseButton = false;
        if (clickType.isLeftClick()) {
            candidates.add("left");
            mouseButton = true;
        } else if (clickType.isRightClick()) {
            candidates.add("right");
            mouseButton = true;
        } else if (clickType == ClickType.MIDDLE) {
            candidates.add("middle");
            mouseButton = true;
        }
        // Keyboard-driven clicks (number keys, drop, offhand swap) only match
        // explicitly named keys; "any" means any mouse button.
        if (mouseButton) {
            candidates.add("any");
        }

        for (String candidate : candidates) {
            List<Map<?, ?>> actions = actionList(actionsSection, candidate);
            if (!actions.isEmpty()) {
                return actions;
            }
        }
        return Collections.emptyList();
    }

    private List<Map<?, ?>> actionList(ConfigurationSection actionsSection, String path) {
        List<?> rawActions = actionsSection.getList(path);
        if (rawActions == null || rawActions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<?, ?>> actions = new ArrayList<>(rawActions.size());
        for (Object rawAction : rawActions) {
            if (rawAction instanceof Map<?, ?> map) {
                actions.add(map);
            }
        }
        return actions;
    }

    private void executeAction(Player player, Menu menu, GuiContext context, Map<?, ?> action) {
        String type = stringValue(action, "type");
        if (type == null || type.isEmpty()) {
            return;
        }

        String permission = stringValue(action, "permission");
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
            return;
        }

        switch (type.toLowerCase(Locale.ROOT)) {
            case "open-gui" -> executeOpenGui(player, context, action);
            case "load-player-kit" -> {
                KitManager.get().loadKit(player, actionInt(action, "slot", context));
                closeAfterAction(player, menu, action);
            }
            case "load-enderchest" -> {
                KitManager.get().loadEnderchest(player, actionInt(action, "slot", context));
                closeAfterAction(player, menu, action);
            }
            case "load-public-kit" -> {
                String publicKitId = actionString(action, "public-kit-id", context, context.getString("id"));
                if (publicKitId != null && !publicKitId.isEmpty()) {
                    KitManager.get().loadPublicKit(player, publicKitId);
                }
                closeAfterAction(player, menu, action);
            }
            case "close" -> {
                menu.close(player);
                SoundManager.playCloseGui(player);
            }
            case "clear-editor" -> clearEditorSlots(player, menu, action);
            case "import-player-inventory" -> importIntoEditor(player, menu, action, filteredContents(player.getInventory().getContents()));
            case "import-player-enderchest" -> importIntoEditor(player, menu, action, filteredContents(player.getEnderChest().getContents()));
            case "clear-player-inventory" -> {
                player.getInventory().clear();
                Lang.get().send(player, "success.inventory-cleared");
                SoundManager.playSuccess(player);
            }
            case "repair-player-items" -> {
                BroadcastManager.get().broadcastPlayerRepaired(player);
                PlayerUtil.repairAll(player);
                player.updateInventory();
                SoundManager.playSuccess(player);
            }
            case "delete-player-kit" -> deleteInspectedKit(player, menu, context, action, false);
            case "delete-player-enderchest" -> deleteInspectedKit(player, menu, context, action, true);
            case "save-kit-room-page" -> saveKitRoomPage(player, context);
            case "broadcast-kit-room-opened" -> BroadcastManager.get().broadcastPlayerOpenedKitRoom(player);
            default -> plugin.getLogger().warning("Unknown GUI action type '" + type + "'");
        }
    }

    /**
     * Editors open through their typed entry points so an
     * {@link EditorSession} is registered, and main menu / kit room through
     * theirs so paging context is normalized (page memory, clamping);
     * everything else — including menus server owners add themselves — opens
     * generically.
     */
    private void executeOpenGui(Player player, GuiContext context, Map<?, ?> action) {
        String guiId = stringValue(action, "gui");
        if (guiId == null || guiId.isEmpty()) {
            return;
        }

        GuiContext nextContext = actionContext(context, action.get("context"));
        switch (guiId) {
            case MAIN_MENU -> {
                Integer page = nextContext.getInt("page_index");
                openMainMenu(player, page != null ? page : lastMainMenuPage.getOrDefault(player.getUniqueId(), 0));
            }
            case KIT_ROOM -> openKitRoom(player, nextContext.getInt("page_index", 0));
            case PLAYER_KIT_EDITOR -> openPlayerKitEditor(player, nextContext.getInt("slot", 1));
            case ENDERCHEST_EDITOR -> openEnderchestEditor(player, nextContext.getInt("slot", 1));
            case PUBLIC_KIT_EDITOR -> openPublicKitEditor(player, nextContext.getString("id"));
            case PUBLIC_KIT_VIEWER -> openPublicKitViewer(player, nextContext.getString("id"));
            default -> openGui(guiId, player, nextContext);
        }
    }

    private void deleteInspectedKit(Player player, Menu menu, GuiContext context, Map<?, ?> action, boolean enderchest) {
        UUID target = context.getUuid("target_uuid");
        if (target == null) {
            return;
        }
        int slot = actionInt(action, "slot", context);

        boolean success = enderchest
                ? KitManager.get().deleteEnderchest(target, slot)
                : KitManager.get().deleteKit(target, slot);
        if (success) {
            Lang.get().send(player, enderchest ? "success.admin-ec-deleted" : "success.admin-kit-deleted",
                    "slot", String.valueOf(slot));
            SoundManager.playSuccess(player);
        } else {
            SoundManager.playFailure(player);
        }

        // Even a failed delete (kit already gone, e.g. deleted by another
        // admin mid-inspect) must suppress the close-save: the menu still
        // shows the stale contents and saving them would resurrect the kit.
        skipNextSave.add(player.getUniqueId());
        menu.close(player);
        SoundManager.playCloseGui(player);
    }

    private void saveKitRoomPage(Player player, GuiContext context) {
        List<Integer> dataSlots = componentSlots(KIT_ROOM, "kit-room-data");
        if (dataSlots.isEmpty()) {
            return;
        }

        Inventory top = player.getOpenInventory().getTopInventory();
        ItemStack[] data = new ItemStack[dataSlots.size()];
        for (int i = 0; i < dataSlots.size(); i++) {
            ItemStack item = top.getItem(dataSlots.get(i));
            data[i] = item == null ? null : item.clone();
        }

        KitRoomDataManager.get().setKitRoom(context.getInt("page_index", 0), data);
        KitRoomDataManager.get().saveToDBAsync();
        Lang.get().send(player, "success.kitroom-menu-saved");
        SoundManager.playSuccess(player);
    }

    private void clearEditorSlots(Player player, Menu menu, Map<?, ?> action) {
        for (int slotIndex : editorActionSlots(player, menu, action)) {
            menu.getSlot(slotIndex).setItem((ItemStack) null);
        }
    }

    private void importIntoEditor(Player player, Menu menu, Map<?, ?> action, ItemStack[] source) {
        List<Integer> slots = editorActionSlots(player, menu, action);
        for (int i = 0; i < slots.size(); i++) {
            ItemStack item = i < source.length && source[i] != null ? source[i].clone() : null;
            menu.getSlot(slots.get(i)).setItem(item);
        }
    }

    /**
     * The slots an editor action operates on: the open session's data slots,
     * or an explicit "slots" override on the action.
     */
    private List<Integer> editorActionSlots(Player player, Menu menu, Map<?, ?> action) {
        Object override = action.get("slots");
        if (override != null) {
            return parseSlots(override, menu.getDimensions().getArea());
        }
        EditorSession session = editorSessions.get(player.getUniqueId());
        return session != null ? session.dataSlots() : Collections.emptyList();
    }

    private ItemStack[] filteredContents(ItemStack[] contents) {
        return filterItemsOnImport ? ItemFilter.get().filterItemStack(contents) : contents;
    }

    private void closeAfterAction(Player player, Menu menu, Map<?, ?> action) {
        if (booleanValue(action, "close")) {
            menu.close(player);
        }
    }

    // ------------------------------------------------------------------
    // Data resolution
    // ------------------------------------------------------------------

    private ItemStack[] resolveKitData(Player viewer, GuiContext context) {
        String publicKitId = context.getString("id");
        if (publicKitId != null && !publicKitId.isEmpty()) {
            return KitManager.get().getItemStackArrayById(IDUtil.getPublicKitId(publicKitId));
        }

        UUID owner = context.getUuid("target_uuid");
        return KitManager.get().getItemStackArrayById(
                IDUtil.getPlayerKitId(owner != null ? owner : viewer.getUniqueId(), context.getInt("slot", 1)));
    }

    private ItemStack[] resolveEnderchestData(Player viewer, GuiContext context) {
        UUID owner = context.getUuid("target_uuid");
        return KitManager.get().getItemStackArrayById(
                IDUtil.getECId(owner != null ? owner : viewer.getUniqueId(), context.getInt("slot", 1)));
    }

    private GuiContext enrichPublicKitContext(GuiContext context, String publicKitId) {
        for (PublicKit publicKit : KitManager.get().getPublicKitList()) {
            if (publicKit.id.equals(publicKitId)) {
                return context
                        .with("public_kit_name", publicKit.name)
                        .with("public_kit_icon", publicKit.icon.name());
            }
        }
        return context;
    }

    // ------------------------------------------------------------------
    // Items and text
    // ------------------------------------------------------------------

    private ItemStack buildItem(ConfigurationSection itemSection, Player viewer, GuiContext context) {
        if (itemSection == null) {
            return null;
        }

        String materialSpec = itemSection.getString("material");
        if (materialSpec == null || materialSpec.isEmpty()) {
            return null;
        }

        boolean styledGlass = "@glass".equalsIgnoreCase(materialSpec);
        if (styledGlass && !itemSection.isString("name")) {
            return ItemUtil.createGlassPane();
        }

        Material material;
        if (styledGlass) {
            material = StyleManager.get().getGlassMaterial();
        } else {
            String materialName = resolveText(materialSpec, viewer, context);
            material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Unknown material '" + materialName + "' in " + itemSection.getCurrentPath());
                return null;
            }
        }

        Integer amountValue = GuiText.resolveInt(itemSection.get("amount"), key -> placeholderValue(key, viewer, context));
        int amount = amountValue != null ? Math.max(1, amountValue) : 1;
        String name = resolveText(itemSection.getString("name"), viewer, context);

        List<String> loreLines = itemSection.getStringList("lore");
        String[] lore = new String[loreLines.size()];
        for (int i = 0; i < loreLines.size(); i++) {
            lore[i] = resolveText(loreLines.get(i), viewer, context);
        }

        ItemStack item = ItemUtil.createItem(material, amount, name, lore);
        if (itemSection.getBoolean("hide-flags", false)) {
            ItemUtil.addHideFlags(item);
        }
        if (itemSection.getBoolean("glow", false)) {
            ItemUtil.addEnchantLook(item);
        }
        return item;
    }

    /**
     * Expands %lang:key% (with context values applied as the lang message's
     * {token} placeholders) and %token%, then PlaceholderAPI when installed.
     * MiniMessage conversion is left to the consumer (ItemUtil for items,
     * resolveTitle for titles).
     */
    private String resolveText(String value, Player viewer, GuiContext context) {
        if (value == null) {
            return null;
        }

        String resolved = GuiText.resolve(value,
                key -> Lang.get().raw(key, context.stringValues()),
                key -> placeholderValue(key, viewer, context));
        // Only unknown %tokens% remain; skip the PAPI scan when there are none.
        if (resolved.indexOf('%') >= 0 && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            resolved = applyPlaceholderApi(viewer, resolved);
        }
        return resolved;
    }

    /** Separate method so the optional PlaceholderAPI class loads lazily. */
    private String applyPlaceholderApi(Player viewer, String text) {
        return PlaceholderAPI.setPlaceholders(viewer, text);
    }

    private String placeholderValue(String key, Player viewer, GuiContext context) {
        Object contextValue = context.get(key);
        if (contextValue != null) {
            return String.valueOf(contextValue);
        }

        return switch (key) {
            case "viewer_name" -> viewer.getName();
            case "viewer_uuid" -> viewer.getUniqueId().toString();
            case "primary_color" -> StyleManager.get().getPrimaryColorTag();
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Config access helpers
    // ------------------------------------------------------------------

    private List<Integer> parseSlots(Object rawValue, int menuSize) {
        return GuiSlots.parse(rawValue, menuSize, message -> plugin.getLogger().warning(message + " in guis.yml"));
    }

    private List<Integer> componentSlots(String guiId, String componentName) {
        return componentSlotsCache.computeIfAbsent(guiId + ":" + componentName, key -> {
            ConfigurationSection guiSection = guiConfig.getGuiSection(guiId);
            if (guiSection == null) {
                return Collections.emptyList();
            }
            int menuSize = rows(guiSection) * 9;
            for (ConfigurationSection elementSection : orderedElements(guiSection)) {
                if ("component".equalsIgnoreCase(elementSection.getString("type", ""))
                        && componentName.equalsIgnoreCase(elementSection.getString("component", ""))) {
                    return List.copyOf(parseSlots(elementSection.get("slots"), menuSize));
                }
            }
            return Collections.emptyList();
        });
    }

    /**
     * Builds the context an open-gui action passes along. Only explicitly
     * declared keys are forwarded — the current menu's context does not leak
     * into the next one. String values resolve %token% placeholders against
     * the current context; a value that is exactly one token ("%slot%")
     * passes the original object through so numbers stay numbers.
     */
    private GuiContext actionContext(GuiContext currentContext, Object rawContext) {
        GuiContext nextContext = GuiContext.empty();
        if (!(rawContext instanceof Map<?, ?> rawContextMap)) {
            return nextContext;
        }

        for (Map.Entry<?, ?> entry : rawContextMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof String stringValue) {
                Object passthrough = contextPassthrough(stringValue, currentContext);
                value = passthrough != null ? passthrough
                        : GuiText.resolve(stringValue, k -> Lang.get().raw(k, currentContext.stringValues()), currentContext::getString);
            }
            nextContext = nextContext.with(key, value);
        }
        return nextContext;
    }

    private Object contextPassthrough(String value, GuiContext context) {
        if (value.length() > 2 && value.startsWith("%") && value.endsWith("%")
                && value.indexOf('%', 1) == value.length() - 1) {
            return context.get(value.substring(1, value.length() - 1));
        }
        return null;
    }

    private String actionString(Map<?, ?> action, String key, GuiContext context, String fallback) {
        String rawValue = stringValue(action, key);
        if (rawValue == null || rawValue.isEmpty()) {
            return fallback;
        }
        Object passthrough = contextPassthrough(rawValue, context);
        if (passthrough != null) {
            return String.valueOf(passthrough);
        }
        return GuiText.resolve(rawValue, k -> Lang.get().raw(k, context.stringValues()), context::getString);
    }

    /**
     * An action's int argument: the explicit value (with %token% placeholders
     * resolved), or the context's value under the same key.
     */
    private int actionInt(Map<?, ?> action, String key, GuiContext context) {
        Object rawValue = action.get(key);
        if (rawValue == null) {
            return context.getInt(key, 1);
        }
        Integer resolved = GuiText.resolveInt(rawValue, context::getString);
        if (resolved == null) {
            int fallback = context.getInt(key, 1);
            plugin.getLogger().warning("Could not resolve '" + key + "' value '" + rawValue + "' in guis.yml action, using " + fallback);
            return fallback;
        }
        return resolved;
    }

    private String stringValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String stringValue && Boolean.parseBoolean(stringValue);
    }
}
