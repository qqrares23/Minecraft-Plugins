package com.raresb.paths.common;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The shared admin subcommands every plugin offers under {@code /<plugin> admin}:
 * <pre>
 * reload                      reload config.yml and the language file
 * config get &lt;key&gt;            show a setting
 * config set &lt;key&gt; &lt;value&gt;    change a setting (type-checked, saved, applied at once)
 * config reset &lt;key&gt;          back to the default from the jar
 * config list [section]       settings in a section
 * toggle &lt;feature&gt; [on|off]   flip an on/off setting (feature = key, or key without ".enabled")
 * lang &lt;code&gt;                 switch the language (lang/&lt;code&gt;.yml)
 * </pre>
 * Admin replies are English. {@code onChange} runs after every change (re-read settings etc.).
 */
public final class ConfigCommand {
    public static final List<String> SUBCOMMANDS = List.of("reload", "config", "toggle", "lang");

    private final JavaPlugin plugin;
    private final String usage;
    private final Runnable onChange;

    /** @param usage e.g. "/clearitems admin" */
    public ConfigCommand(JavaPlugin plugin, String usage, Runnable onChange) {
        this.plugin = plugin;
        this.usage = usage;
        this.onChange = onChange;
    }

    /** Handles args (after "admin"); false if the first arg isn't one of {@link #SUBCOMMANDS}. */
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0 || !SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            return false;
        }
        FileConfiguration config = plugin.getConfig();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                onChange.run();
                ok(sender, plugin.getName() + " config and language reloaded.");
            }
            case "lang" -> {
                if (args.length < 2) {
                    info(sender, "Language: " + config.getString("language", "en") + " - available: " + String.join(", ", languages()));
                    return true;
                }
                String code = args[1].toLowerCase(Locale.ROOT);
                if (!languages().contains(code)) {
                    error(sender, "No lang/" + code + ".yml. Available: " + String.join(", ", languages()));
                    return true;
                }
                set(sender, "language", code);
            }
            case "toggle" -> {
                if (args.length < 2) {
                    error(sender, "Usage: " + usage + " toggle <feature> [on|off]");
                    return true;
                }
                String key = toggleKey(args[1]);
                if (key == null) {
                    error(sender, "No on/off setting called " + args[1] + ".");
                    return true;
                }
                boolean value = args.length > 2 ? parseBoolean(args[2]) : !config.getBoolean(key);
                set(sender, key, value);
            }
            case "config" -> config(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> {
                return false;
            }
        }
        return true;
    }

    private void config(CommandSender sender, String[] args) {
        FileConfiguration config = plugin.getConfig();
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "get" -> {
                if (args.length < 2) {
                    error(sender, "Usage: " + usage + " config get <key>");
                } else if (config.isConfigurationSection(args[1])) {
                    list(sender, args[1]);
                } else if (!config.contains(args[1])) {
                    error(sender, "Unknown setting " + args[1] + ".");
                } else {
                    info(sender, args[1] + " = " + config.get(args[1]) + defaultNote(args[1]));
                }
            }
            case "set" -> {
                if (args.length < 3) {
                    error(sender, "Usage: " + usage + " config set <key> <value>");
                    return;
                }
                String key = args[1];
                if (config.isConfigurationSection(key)) {
                    error(sender, key + " is a section - set one of its keys (" + usage + " config list " + key + ").");
                    return;
                }
                Object current = config.contains(key) ? config.get(key) : defaults() == null ? null : defaults().get(key);
                if (current == null) {
                    error(sender, "Unknown setting " + key + ". Use " + usage + " config list.");
                    return;
                }
                String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                Object value;
                try {
                    value = parse(current, raw);
                } catch (IllegalArgumentException e) {
                    error(sender, e.getMessage());
                    return;
                }
                set(sender, key, value);
            }
            case "reset" -> {
                if (args.length < 2 || defaults() == null || !defaults().contains(args[1])) {
                    error(sender, "Usage: " + usage + " config reset <key> (a key with a default)");
                    return;
                }
                set(sender, args[1], defaults().get(args[1]));
            }
            case "list" -> list(sender, args.length > 1 ? args[1] : "");
            default -> error(sender, "Usage: " + usage + " config <get|set|reset|list> ...");
        }
    }

    private void set(CommandSender sender, String key, Object value) {
        plugin.getConfig().set(key, value);
        plugin.saveConfig();
        onChange.run();
        ok(sender, key + " = " + plugin.getConfig().get(key));
    }

    private void list(CommandSender sender, String section) {
        ConfigurationSection base = section.isEmpty() ? plugin.getConfig() : plugin.getConfig().getConfigurationSection(section);
        if (base == null) {
            error(sender, "Unknown section " + section + ".");
            return;
        }
        info(sender, plugin.getName() + " settings" + (section.isEmpty() ? "" : " in " + section) + ":");
        for (String key : base.getKeys(false)) {
            String full = section.isEmpty() ? key : section + "." + key;
            if (base.isConfigurationSection(key)) {
                sender.sendMessage(Component.text("  " + full + " ▸ (" + base.getConfigurationSection(key).getKeys(false).size() + " keys)", NamedTextColor.AQUA));
            } else {
                sender.sendMessage(Component.text("  " + full + " = " + base.get(key), NamedTextColor.GRAY));
            }
        }
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) {
            return filter(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String last = args[args.length - 1];
        switch (sub) {
            case "lang" -> {
                return args.length == 2 ? filter(languages(), last) : List.of();
            }
            case "toggle" -> {
                if (args.length == 2) {
                    return filter(toggles(), last);
                }
                return args.length == 3 ? filter(List.of("on", "off"), last) : List.of();
            }
            case "config" -> {
                if (args.length == 2) {
                    return filter(List.of("get", "set", "reset", "list"), last);
                }
                if (args.length == 3) {
                    boolean sections = args[1].equalsIgnoreCase("list");
                    return filter(keys(sections), last);
                }
                if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                    Object current = plugin.getConfig().get(args[2]);
                    if (current instanceof Boolean) {
                        return filter(List.of("true", "false"), last);
                    }
                    return current == null ? List.of() : List.of(String.valueOf(current));
                }
                return List.of();
            }
            default -> {
                return List.of();
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private Configuration defaults() {
        return plugin.getConfig().getDefaults();
    }

    private String defaultNote(String key) {
        Configuration defaults = defaults();
        if (defaults == null || !defaults.contains(key) || String.valueOf(defaults.get(key)).equals(String.valueOf(plugin.getConfig().get(key)))) {
            return "";
        }
        return "  (default " + defaults.get(key) + ")";
    }

    private List<String> keys(boolean sectionsOnly) {
        Set<String> keys = new TreeSet<>();
        for (String key : plugin.getConfig().getKeys(true)) {
            if (plugin.getConfig().isConfigurationSection(key) == sectionsOnly) {
                keys.add(key);
            }
        }
        return new ArrayList<>(keys);
    }

    private List<String> toggles() {
        Set<String> names = new TreeSet<>();
        for (String key : plugin.getConfig().getKeys(true)) {
            if (plugin.getConfig().isBoolean(key)) {
                names.add(key.endsWith(".enabled") ? key.substring(0, key.length() - ".enabled".length()) : key);
            }
        }
        return new ArrayList<>(names);
    }

    private String toggleKey(String name) {
        FileConfiguration config = plugin.getConfig();
        if (config.isBoolean(name)) {
            return name;
        }
        if (config.isBoolean(name + ".enabled")) {
            return name + ".enabled";
        }
        return null;
    }

    private List<String> languages() {
        Set<String> codes = new TreeSet<>(List.of(Lang.BUNDLED));
        File[] files = new File(plugin.getDataFolder(), "lang").listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                codes.add(file.getName().substring(0, file.getName().length() - 4));
            }
        }
        return new ArrayList<>(codes);
    }

    static Object parse(Object current, String raw) {
        if (current instanceof Boolean) {
            return parseBoolean(raw);
        }
        try {
            if (current instanceof Integer || current instanceof Long) {
                return Integer.parseInt(raw);
            }
            if (current instanceof Number) {
                return Double.parseDouble(raw);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number, got \"" + raw + "\".");
        }
        if (current instanceof List<?> list) {
            List<Object> values = new ArrayList<>();
            if (raw.isBlank() || raw.equals("[]")) {
                return values;
            }
            Object sample = list.isEmpty() ? "" : list.get(0);
            for (String part : raw.replaceAll("^\\[|]$", "").split(",")) {
                values.add(sample instanceof String ? part.trim() : parse(sample, part.trim()));
            }
            return values;
        }
        return raw;
    }

    static boolean parseBoolean(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1", "enable", "enabled" -> true;
            case "false", "off", "no", "0", "disable", "disabled" -> false;
            default -> throw new IllegalArgumentException("Expected on/off (true/false), got \"" + raw + "\".");
        };
    }

    public static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).limit(100).toList();
    }

    private static void ok(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GREEN));
    }

    private static void info(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.YELLOW));
    }

    private static void error(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.RED));
    }
}
