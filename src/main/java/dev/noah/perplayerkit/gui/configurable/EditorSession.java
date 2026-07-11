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

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Tracks an open kit/enderchest editor so its contents can be persisted when
 * the menu goes away — whether by a real InventoryCloseEvent or by navigating
 * to another menu that reuses the open inventory (canvas redraw), where no
 * close event ever fires.
 *
 * @param dataSlots menu slots holding kit data, in kit-index order, resolved
 *                  from guis.yml when the editor was opened
 * @param menuSize  expected top-inventory size, to avoid saving from an
 *                  inventory that is not this editor
 */
public record EditorSession(EditorType type, int slot, String publicKitId, UUID target, String targetName,
                            List<Integer> dataSlots, int menuSize) {

    public enum EditorType {
        KIT,
        PUBLIC_KIT,
        ENDERCHEST,
        INSPECT_KIT,
        INSPECT_ENDERCHEST
    }

    public boolean matches(Inventory inventory) {
        return inventory.getSize() == menuSize && inventory.getLocation() == null;
    }

    /**
     * Reads the data slots out of the closing inventory, cloned. The result
     * is at least {@code minSize} long because KitManager's save methods index
     * fixed positions (armor at 36-39) even when a customized layout exposes
     * fewer slots.
     */
    public ItemStack[] extractContents(Inventory inventory, int minSize) {
        ItemStack[] contents = new ItemStack[Math.max(dataSlots.size(), minSize)];
        for (int i = 0; i < dataSlots.size(); i++) {
            ItemStack item = inventory.getItem(dataSlots.get(i));
            contents[i] = item == null ? null : item.clone();
        }
        return contents;
    }
}
