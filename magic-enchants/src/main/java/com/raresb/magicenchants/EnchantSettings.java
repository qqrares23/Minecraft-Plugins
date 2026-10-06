package com.raresb.magicenchants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Per-enchantment settings under {@code enchantments.<id>} in config.yml. The bootstrapper reads
 * them before the plugin exists (enchantments are registered before the worlds load), so this class
 * works on the file directly. Missing entries are filled in from the defaults in {@link MagicEnchant}.
 *
 * <pre>
 * enchantments:
 *   bleeding:
 *     enabled: true          # false = can't be obtained any more and does nothing (items keep it)
 *     max-level: 3           # restart to apply
 *     weight: 5              # 10 common … 1 very rare; restart to apply
 *     enchanting-table: true # restart to apply
 *     loot: true             # chests, fishing, mob drops; restart to apply
 *     trades: true           # librarian books; restart to apply
 * </pre>
 */
final class EnchantSettings {
    private final YamlConfiguration config;

    private EnchantSettings(YamlConfiguration config) {
        this.config = config;
    }

    /** The settings in an already loaded config (the plugin's getConfig(), after /enchants admin reload). */
    static EnchantSettings of(YamlConfiguration config) {
        return new EnchantSettings(config);
    }

    /** Loads (creating or completing) config.yml in the plugin's data folder. */
    static EnchantSettings load(Path dataFolder) {
        File file = dataFolder.resolve("config.yml").toFile();
        YamlConfiguration config;
        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            config = new YamlConfiguration();
            try (InputStream in = EnchantSettings.class.getClassLoader().getResourceAsStream("config.yml")) {
                if (in != null) {
                    config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
                // defaults below
            }
        }
        boolean changed = !file.exists();
        for (MagicEnchant enchant : MagicEnchant.values()) {
            String base = "enchantments." + id(enchant);
            changed |= setDefault(config, base + ".enabled", true);
            changed |= setDefault(config, base + ".max-level", enchant.maxLevel());
            changed |= setDefault(config, base + ".weight", enchant.weight());
            changed |= setDefault(config, base + ".enchanting-table", !enchant.treasure());
            changed |= setDefault(config, base + ".loot", true);
            changed |= setDefault(config, base + ".trades", true);
        }
        if (config.getComments("enchantments").isEmpty()) {
            config.setComments("enchantments", List.of(
                    "Every enchantment: enabled (false = can't be obtained any more and has no effect; items keep it),",
                    "max-level, weight (10 common ... 1 very rare) and where it can be found.",
                    "Everything except 'enabled' needs a server restart (enchantments are registered at startup)."));
            changed = true;
        }
        if (changed) {
            try {
                Files.createDirectories(dataFolder);
                config.save(file);
            } catch (IOException ignored) {
                // read-only folder: run with the defaults
            }
        }
        return new EnchantSettings(config);
    }

    private static boolean setDefault(YamlConfiguration config, String key, Object value) {
        if (config.contains(key)) {
            return false;
        }
        config.set(key, value);
        return true;
    }

    static String id(MagicEnchant enchant) {
        return enchant.name().toLowerCase(Locale.ROOT);
    }

    private ConfigurationSection section(MagicEnchant enchant) {
        ConfigurationSection section = config.getConfigurationSection("enchantments." + id(enchant));
        return section != null ? section : new YamlConfiguration();
    }

    boolean enabled(MagicEnchant enchant) {
        return section(enchant).getBoolean("enabled", true);
    }

    int maxLevel(MagicEnchant enchant) {
        return Math.max(1, Math.min(255, section(enchant).getInt("max-level", enchant.maxLevel())));
    }

    int weight(MagicEnchant enchant) {
        return Math.max(1, Math.min(1024, section(enchant).getInt("weight", enchant.weight())));
    }

    boolean enchantingTable(MagicEnchant enchant) {
        return enabled(enchant) && !enchant.treasure() && section(enchant).getBoolean("enchanting-table", true);
    }

    boolean loot(MagicEnchant enchant) {
        return enabled(enchant) && section(enchant).getBoolean("loot", true);
    }

    boolean trades(MagicEnchant enchant) {
        return enabled(enchant) && section(enchant).getBoolean("trades", true);
    }
}
