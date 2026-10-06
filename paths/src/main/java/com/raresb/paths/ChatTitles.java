package com.raresb.paths;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Path titles in chat: "[Warbringer 15] Rares: hi", from the player's highest-level active path.
 * Chat runs off the main thread, so titles are worked out on the main thread every few seconds
 * (player data must not be read from another thread) and chat only reads the cached one.
 */
final class ChatTitles implements Listener {
    private final PathsPlugin plugin;
    private final Map<UUID, Component> titles = new ConcurrentHashMap<>();

    ChatTitles(PathsPlugin plugin) {
        this.plugin = plugin;
    }

    void refreshAll() {
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
    }

    void refresh(Player player) {
        Path best = null;
        int bestLevel = 0;
        for (Path path : plugin.active(player)) {
            int level = plugin.level(player, path);
            if (best == null || level > bestLevel) {
                best = path;
                bestLevel = level;
            }
        }
        if (best == null || !plugin.getConfig().getBoolean("chat-titles", true)) {
            titles.remove(player.getUniqueId());
        } else {
            titles.put(player.getUniqueId(), plugin.lang().get("chat-title", "title",
                    Component.text((plugin.hasTalent(player, best, Path.Talent.LEGEND) ? "★ " : "") + best.displayName() + " " + bestLevel, best.color())));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Component title = titles.get(event.getPlayer().getUniqueId());
        if (title == null) {
            return;
        }
        ChatRenderer original = event.renderer();
        event.renderer((source, displayName, message, viewer) -> title.append(original.render(source, displayName, message, viewer)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        titles.remove(event.getPlayer().getUniqueId());
        plugin.forget(event.getPlayer());
    }
}
