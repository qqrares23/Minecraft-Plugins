package com.raresb.weaponskills.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps the YAML files in the plugin folder in step with the defaults bundled in the jar. */
public final class ConfigFiles {
    private ConfigFiles() {
    }

    /**
     * Copies a bundled file to the plugin folder if it is missing; otherwise adds every key (with its
     * comments) that the bundled file has and the server's file lacks. Existing values are never changed.
     * Returns how many keys were added.
     */
    public static int update(JavaPlugin plugin, String resource) {
        File file = new File(plugin.getDataFolder(), resource);
        if (!file.exists()) {
            if (plugin.getResource(resource) != null) {
                plugin.saveResource(resource, false);
            }
            return 0;
        }
        YamlConfiguration defaults;
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return 0;
            }
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return 0;
        }
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (current.contains(key) || blockedByValue(current, key)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                current.createSection(key);
            } else {
                current.set(key, defaults.get(key));
                added++;
            }
            current.setComments(key, defaults.getComments(key));
            current.setInlineComments(key, defaults.getInlineComments(key));
        }
        if (added > 0) {
            try {
                current.save(file);
                plugin.getLogger().info("Added " + added + " new setting(s) to " + resource + ".");
            } catch (IOException e) {
                plugin.getLogger().warning("Could not update " + resource + ": " + e.getMessage());
            }
        }
        return added;
    }

    /** True if a parent of the key holds a plain value in the server's file (e.g. an old-format list). */
    private static boolean blockedByValue(YamlConfiguration current, String key) {
        int dot = key.lastIndexOf('.');
        while (dot > 0) {
            String parent = key.substring(0, dot);
            if (current.contains(parent) && !current.isConfigurationSection(parent)) {
                return true;
            }
            dot = parent.lastIndexOf('.');
        }
        return false;
    }

    /** Updates config.yml and reloads it into {@code plugin.getConfig()}. */
    public static void updateConfig(JavaPlugin plugin) {
        update(plugin, "config.yml");
        plugin.reloadConfig();
    }
}
