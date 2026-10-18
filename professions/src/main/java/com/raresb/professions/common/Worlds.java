package com.raresb.professions.common;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
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
    // Parsed once per config object; Bukkit's reloadConfig() swaps the object, so identity change = reload.
    private static FileConfiguration cachedFor;
    private static Set<String> cachedWorlds = Set.of();
    private static boolean cachedWhitelist;

    private Worlds() {
    }

    public static boolean enabled(JavaPlugin plugin, World world) {
        return world != null && enabled(plugin, world.getName());
    }

    public static boolean enabled(JavaPlugin plugin, String world) {
        FileConfiguration config = plugin.getConfig();
        if (config != cachedFor) {
            rebuild(config);
        }
        return cachedWhitelist == cachedWorlds.contains(world);
    }

    private static void rebuild(FileConfiguration config) {
        if (config.isList("worlds")) {
            // old format: a whitelist; empty list = every world (modelled as an empty blacklist).
            List<String> list = config.getStringList("worlds");
            cachedWhitelist = !list.isEmpty();
            cachedWorlds = new HashSet<>(list);
        } else {
            cachedWorlds = new HashSet<>(config.getStringList("worlds.list"));
            cachedWhitelist = "whitelist".equalsIgnoreCase(config.getString("worlds.mode", "blacklist"));
        }
        cachedFor = config;
    }
}
