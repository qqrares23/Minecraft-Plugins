package com.raresb.common;

import java.util.List;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Which worlds a plugin works in, from config.yml:
 * <pre>
 * worlds:
 *   mode: blacklist   # blacklist = everywhere except the list; whitelist = only the listed worlds
 *   list: []
 * </pre>
 * A plain list ({@code worlds: [a, b]}, the old MightyMobs format) is a whitelist; empty = every world.
 */
public final class Worlds {
    private Worlds() {
    }

    public static boolean enabled(JavaPlugin plugin, World world) {
        return world != null && enabled(plugin, world.getName());
    }

    public static boolean enabled(JavaPlugin plugin, String world) {
        var config = plugin.getConfig();
        if (config.isList("worlds")) {
            List<String> list = config.getStringList("worlds");
            return list.isEmpty() || list.contains(world);
        }
        List<String> list = config.getStringList("worlds.list");
        boolean whitelist = "whitelist".equalsIgnoreCase(config.getString("worlds.mode", "blacklist"));
        return whitelist == list.contains(world);
    }
}
