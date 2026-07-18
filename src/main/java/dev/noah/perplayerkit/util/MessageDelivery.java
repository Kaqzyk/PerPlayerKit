/*
 * Copyright 2026 Noah Ross
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
package dev.noah.perplayerkit.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Single delivery path for every chat message the plugin sends.
 *
 * adventure-platform-bukkit picks how to deliver messages by sniffing the
 * server implementation and version, and on servers it does not recognize
 * (newer Minecraft releases, hybrid forks) it can drop messages silently —
 * no error, the message just never appears while sounds and inventories
 * keep working. To avoid depending on that sniffing:
 *
 * - native: on Paper-family servers Player implements Adventure's Audience
 *   directly, so we hand the component to the server's own implementation.
 *   Because the plugin does not relocate Adventure, the server's bundled
 *   Adventure classes are the ones loaded at runtime, making the cast safe.
 * - platform: BukkitAudiences, for Spigot servers without native support.
 * - legacy: plain color-coded strings via sendMessage(String). Loses
 *   hover/click buttons but cannot be dropped; config escape hatch for
 *   servers where the other paths fail.
 *
 * Console and other non-player senders always get legacy strings — the
 * platform's console facet has the same silent-drop problem.
 */
public final class MessageDelivery {

    public enum Mode { NATIVE, PLATFORM, LEGACY }

    private static Mode mode;
    private static BukkitAudiences audiences;

    private MessageDelivery() {
    }

    public static void init(Plugin plugin) {
        close();

        boolean nativeSupported = Audience.class.isAssignableFrom(Player.class);
        String configured = plugin.getConfig().getString("message-delivery", "auto").toLowerCase(Locale.ROOT);

        switch (configured) {
            case "native":
                mode = Mode.NATIVE;
                break;
            case "platform":
                mode = Mode.PLATFORM;
                break;
            case "legacy":
                mode = Mode.LEGACY;
                break;
            case "auto":
                mode = nativeSupported ? Mode.NATIVE : Mode.PLATFORM;
                break;
            default:
                plugin.getLogger().warning("Unknown message-delivery option '" + configured + "', using auto");
                mode = nativeSupported ? Mode.NATIVE : Mode.PLATFORM;
                break;
        }

        if (mode == Mode.NATIVE && !nativeSupported) {
            plugin.getLogger().warning("message-delivery is set to native, but this server does not expose Adventure natively; using platform instead");
            mode = Mode.PLATFORM;
        }

        if (mode == Mode.PLATFORM) {
            audiences = BukkitAudiences.create(plugin);
        }

        plugin.getLogger().info("Chat message delivery: " + mode.name().toLowerCase(Locale.ROOT)
                + (mode == Mode.PLATFORM
                ? " (if plugin messages do not appear in chat, set message-delivery: legacy in config.yml)"
                : ""));
    }

    public static void close() {
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
        mode = null;
    }

    public static void send(CommandSender sender, Component message) {
        if (!(sender instanceof Player)) {
            sendLegacy(sender, message);
            return;
        }
        Player player = (Player) sender;

        Mode active = mode;
        if (active == Mode.NATIVE) {
            ((Audience) player).sendMessage(message);
        } else if (active == Mode.PLATFORM && audiences != null) {
            audiences.player(player).sendMessage(message);
        } else {
            // LEGACY, or not initialized (unit tests)
            sendLegacy(sender, message);
        }
    }

    private static void sendLegacy(CommandSender sender, Component message) {
        sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(message));
    }
}
