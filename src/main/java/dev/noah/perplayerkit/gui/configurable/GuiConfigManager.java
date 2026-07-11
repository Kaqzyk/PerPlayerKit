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

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads guis.yml. GUIs missing from the user's file fall back to the copy
 * bundled in the jar, so upgrades that ship new menus keep working on
 * installs whose file predates them. A GUI the user's file does define is
 * used as-is — per-GUI, not per-key, so a customized menu is never a mix of
 * user and default elements.
 */
public class GuiConfigManager {

    private final Plugin plugin;
    private final File guiConfigFile;
    private FileConfiguration userConfig = new YamlConfiguration();
    private FileConfiguration bundledConfig = new YamlConfiguration();

    public GuiConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.guiConfigFile = new File(plugin.getDataFolder(), "guis.yml");
        load();
    }

    private void load() {
        if (!guiConfigFile.exists()) {
            plugin.saveResource("guis.yml", false);
        }
        userConfig = YamlConfiguration.loadConfiguration(guiConfigFile);

        InputStream bundled = plugin.getResource("guis.yml");
        if (bundled != null) {
            bundledConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8));
        }

        int userVersion = userConfig.getInt("config-version", 0);
        int bundledVersion = bundledConfig.getInt("config-version", 0);
        if (userVersion < bundledVersion) {
            migrate(userVersion);
        }
    }

    /**
     * A schema bump can rename components, actions, or keys, and a stale file
     * shadows the bundled menus wholesale (per-GUI fallback) — so archive it
     * and regenerate rather than run menus that half-work. The archived copy
     * keeps the owner's customizations for manual re-application.
     */
    private void migrate(int userVersion) {
        File archived = new File(plugin.getDataFolder(), "guis.yml.v" + userVersion + ".bak");
        if (guiConfigFile.renameTo(archived)) {
            plugin.saveResource("guis.yml", false);
            userConfig = YamlConfiguration.loadConfiguration(guiConfigFile);
            plugin.getLogger().warning("guis.yml used an older layout schema (config-version " + userVersion
                    + "); it was archived to " + archived.getName() + " and regenerated."
                    + " Re-apply any customizations from the archived file.");
        } else {
            plugin.getLogger().warning("guis.yml uses an older layout schema (config-version " + userVersion
                    + ") and could not be archived; menus it defines may misbehave."
                    + " Delete the file to regenerate it.");
        }
    }

    public ConfigurationSection getGuiSection(String guiId) {
        ConfigurationSection section = userConfig.getConfigurationSection("guis." + guiId);
        if (section != null) {
            return section;
        }
        return bundledConfig.getConfigurationSection("guis." + guiId);
    }
}
