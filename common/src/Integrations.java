package com.raresb.common;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

/** Logs which of the companion plugins are present, so a server owner sees what links up. */
public final class Integrations {
    private Integrations() {
    }

    /** @param links pairs of plugin name and what it adds, e.g. "Hud", "sidebar shows path levels" */
    public static void log(JavaPlugin plugin, String... links) {
        List<String> found = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (int i = 0; i + 1 < links.length; i += 2) {
            boolean present = plugin.getServer().getPluginManager().getPlugin(links[i]) != null;
            (present ? found : missing).add(links[i] + " (" + links[i + 1] + ")");
        }
        if (!found.isEmpty()) {
            plugin.getLogger().info("Works with: " + String.join(", ", found));
        }
        if (!missing.isEmpty()) {
            plugin.getLogger().info("Optional, not installed: " + String.join(", ", missing));
        }
    }
}
