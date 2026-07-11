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
package dev.noah.perplayerkit.gui;

import dev.noah.perplayerkit.gui.configurable.ConfigurableGuiService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Facade over {@link ConfigurableGuiService}, keeping the entry points (and
 * their historical names) that commands and listeners call. Menu layout and
 * behavior live in guis.yml and the service.
 */
public class GUI {

    public GUI(Plugin plugin) {
    }

    public static void forgetMainMenuPage(UUID player) {
        ConfigurableGuiService.get().handlePlayerQuit(player);
    }

    public void OpenMainMenu(Player player) {
        ConfigurableGuiService.get().openMainMenu(player);
    }

    public void OpenPublicKitMenu(Player player) {
        ConfigurableGuiService.get().openPublicKitMenu(player);
    }

    public void InspectKit(Player player, UUID target, int slot) {
        ConfigurableGuiService.get().openInspectKit(player, target, slot);
    }

    public void InspectEc(Player player, UUID target, int slot) {
        ConfigurableGuiService.get().openInspectEnderchest(player, target, slot);
    }
}
