package com.raresb.weaponskills.common;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Player-facing text from lang/&lt;language&gt;.yml (MiniMessage format). The files bundled in the jar
 * (en, ro) are copied to the plugin folder and get new keys added on every start; a server can edit
 * them or add its own language file and set {@code language:} in config.yml. Lookups fall back to
 * the bundled file of the same language, then to English, then to the key itself.
 *
 * <p>Placeholders are given as name/value pairs: {@code lang.get("joined", "player", name, "level", 5)}
 * fills {@code <player>} and {@code <level>}. A {@link ComponentLike} value is inserted as is, anything
 * else as plain (unparsed) text.
 */
public final class Lang {
    public static final String[] BUNDLED = {"en", "ro"};
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private String code = "en";
    private YamlConfiguration active = new YamlConfiguration();
    private YamlConfiguration bundledActive = new YamlConfiguration();
    private YamlConfiguration bundledEnglish = new YamlConfiguration();

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)loads the language set in config.yml ({@code language}, default en). */
    public void load() {
        for (String bundled : BUNDLED) {
            ConfigFiles.update(plugin, "lang/" + bundled + ".yml");
        }
        code = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("lang/" + code + ".yml not found - using English.");
            code = "en";
            file = new File(plugin.getDataFolder(), "lang/en.yml");
        }
        active = YamlConfiguration.loadConfiguration(file);
        bundledActive = bundled(code);
        bundledEnglish = bundled("en");
    }

    public String code() {
        return code;
    }

    private YamlConfiguration bundled(String language) {
        try (InputStream in = plugin.getResource("lang/" + language + ".yml")) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            return new YamlConfiguration();
        }
    }

    public boolean has(String key) {
        return active.contains(key) || bundledActive.contains(key) || bundledEnglish.contains(key);
    }

    /** The raw MiniMessage string for a key. */
    public String raw(String key) {
        for (YamlConfiguration source : new YamlConfiguration[]{active, bundledActive, bundledEnglish}) {
            if (source.isString(key)) {
                return source.getString(key);
            }
            if (source.isList(key)) {
                return String.join("\n", source.getStringList(key));
            }
        }
        return key;
    }

    /** The raw lines for a key holding a list (a single string counts as one line). */
    public List<String> rawList(String key) {
        for (YamlConfiguration source : new YamlConfiguration[]{active, bundledActive, bundledEnglish}) {
            if (source.isList(key)) {
                return source.getStringList(key);
            }
            if (source.isString(key)) {
                return List.of(source.getString(key).split("\n", -1));
            }
        }
        return List.of(key);
    }

    public Component get(String key, Object... placeholders) {
        return MM.deserialize(raw(key), resolver(placeholders));
    }

    /** Like {@link #get} but not italic (item names and lore are italic by default). */
    public Component item(String key, Object... placeholders) {
        return get(key, placeholders).decoration(TextDecoration.ITALIC, false);
    }

    public List<Component> lines(String key, Object... placeholders) {
        TagResolver resolver = resolver(placeholders);
        List<Component> lines = new ArrayList<>();
        for (String line : rawList(key)) {
            lines.add(MM.deserialize(line, resolver));
        }
        return lines;
    }

    /** {@link #lines} without italics, for item lore. */
    public List<Component> lore(String key, Object... placeholders) {
        List<Component> lines = lines(key, placeholders);
        lines.replaceAll(line -> line.decoration(TextDecoration.ITALIC, false));
        return lines;
    }

    /** Plain text (tags stripped), for logs, titles of inventories built from strings, etc. */
    public String plain(String key, Object... placeholders) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(get(key, placeholders));
    }

    /**
     * A counted word: key.one / key.few / key.other, each with a {@code <n>} placeholder.
     * English: 1 → one, else other. Romanian: 1 → one, 0 and 2-19 (mod 100) → few, else other ("20 de ...").
     * Missing forms fall back to other.
     */
    public String count(String key, long n) {
        String form;
        if (n == 1) {
            form = "one";
        } else if (code.equals("ro")) {
            long lastTwo = Math.abs(n) % 100;
            form = n == 0 || (lastTwo >= 1 && lastTwo <= 19) ? "few" : "other";
        } else {
            form = "other";
        }
        String pick = has(key + "." + form) ? key + "." + form : key + ".other";
        return raw(pick).replace("<n>", Long.toString(n));
    }

    public static TagResolver resolver(Object... placeholders) {
        if (placeholders.length == 0) {
            return TagResolver.empty();
        }
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String name = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];
            if (value instanceof ComponentLike component) {
                builder.resolver(Placeholder.component(name, component));
            } else {
                builder.resolver(Placeholder.unparsed(name, String.valueOf(value)));
            }
        }
        return builder.build();
    }

    /** Parses a MiniMessage string that isn't in a lang file (e.g. a title set in config.yml). */
    public static Component parse(String miniMessage, Object... placeholders) {
        return MM.deserialize(miniMessage, resolver(placeholders));
    }
}
