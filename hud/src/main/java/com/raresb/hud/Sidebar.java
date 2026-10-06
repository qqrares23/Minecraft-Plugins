package com.raresb.hud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.RayTraceResult;

/**
 * The sidebar: active paths and their levels, the hit combo, running cooldowns, the held weapon's
 * mastery, the professions being worked on and the magic enchantments on the player's gear.
 * Everything is read from the player's data by key (written by the other plugins) or from their
 * items, so this works with any of those plugins missing.
 */
final class Sidebar {
    private static final int MAX_ROWS = 15;
    private static final int MAX_COOLDOWNS = 4;
    private static final int MAX_PROFESSIONS = 2;
    private static final int MAX_ENCHANTS = 3;
    private static final long PROFESSION_SHOWN_MILLIS = 60_000;
    private static final NamespacedKey ACTIVE_PATHS = new NamespacedKey("paths", "active");
    private static final NamespacedKey HELD_WEAPON = new NamespacedKey("weaponskills", "held");
    private static final List<NamespacedKey> COOLDOWNS = List.of(
            new NamespacedKey("weaponskills", "cooldowns"), new NamespacedKey("paths", "cooldowns"));

    private final HudPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** What each player's sidebar shows now, so unchanged sidebars aren't re-sent. */
    private final Map<UUID, List<Component>> shown = new HashMap<>();
    /** Each player's profession XP as last seen, and when each profession last gained XP. */
    private final Map<UUID, Map<String, Double>> professionXp = new HashMap<>();
    private final Map<UUID, Map<String, Long>> professionActive = new HashMap<>();

    Sidebar(HudPlugin plugin) {
        this.plugin = plugin;
    }

    void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (plugin.sidebarOn(player)) {
                update(player);
            } else {
                hide(player);
            }
        }
    }

    void hide(Player player) {
        shown.remove(player.getUniqueId());
        Scoreboard board = boards.remove(player.getUniqueId());
        if (board != null && player.getScoreboard() == board) {
            player.setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
        }
    }

    void forget(Player player) {
        boards.remove(player.getUniqueId());
        shown.remove(player.getUniqueId());
        professionXp.remove(player.getUniqueId());
        professionActive.remove(player.getUniqueId());
    }

    private void update(Player player) {
        List<Component> lines = lines(player);
        List<Component> before = shown.put(player.getUniqueId(), lines);
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = plugin.getServer().getScoreboardManager().getNewScoreboard();
            boards.put(player.getUniqueId(), board);
            before = null;
        }
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        if (lines.equals(before)) {
            return;
        }
        Objective objective = board.getObjective("hud");
        if (objective == null) {
            objective = board.registerNewObjective("hud", Criteria.DUMMY, Component.empty());
            objective.numberFormat(NumberFormat.blank());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.displayName(plugin.sidebarTitle());
        // One fixed entry per row; the score orders the rows and the custom name is what shows.
        for (int row = 0; row < lines.size(); row++) {
            Score score = objective.getScore("row" + row);
            score.setScore(lines.size() - row);
            score.customName(lines.get(row));
        }
        for (int row = lines.size(); before != null && row < before.size(); row++) {
            board.resetScores("row" + row);
        }
    }

    /**
     * The sidebar's sections, most useful first. A sidebar holds 15 rows: when there is more than
     * that, the blank rows between sections go first, then the sections at the bottom.
     */
    private List<Component> lines(Player player) {
        List<List<Component>> sections = new ArrayList<>();

        List<Component> target = target(player);
        if (!target.isEmpty()) {
            sections.add(target);
        }

        if (section("paths") && plugin.getServer().getPluginManager().getPlugin("Paths") != null) {
            List<Component> paths = new ArrayList<>();
            paths.add(plugin.lang().get("sidebar.paths"));
            paths.addAll(paths(player));
            if (paths.size() == 1) {
                paths.add(plugin.lang().get("sidebar.no-path"));
            }
            sections.add(paths);
        }

        int combo = plugin.combo(player);
        if (combo >= 1 && section("combo")) {
            sections.add(List.of(plugin.lang().get("sidebar.combo", "combo", combo)));
        }

        Map<Long, String> cooldowns = section("cooldowns") ? cooldowns(player) : Map.of();
        if (!cooldowns.isEmpty()) {
            List<Component> section = new ArrayList<>();
            section.add(plugin.lang().get("sidebar.cooldowns"));
            long now = System.currentTimeMillis();
            int count = 0;
            for (Map.Entry<Long, String> entry : cooldowns.entrySet()) {
                if (count++ == MAX_COOLDOWNS) {
                    section.add(plugin.lang().get("sidebar.more", "count", cooldowns.size() - MAX_COOLDOWNS));
                    break;
                }
                section.add(Component.text("  " + entry.getValue() + " ", NamedTextColor.GRAY)
                        .append(Component.text((int) Math.ceil((entry.getKey() - now) / 1000.0) + "s", NamedTextColor.RED)));
            }
            sections.add(section);
        }

        String held = player.getPersistentDataContainer().get(HELD_WEAPON, PersistentDataType.STRING);
        if (held != null && held.contains("|") && section("weapon")) {
            sections.add(List.of(plugin.lang().get("sidebar.weapon", "weapon", held.substring(0, held.indexOf('|')),
                    "mastery", held.substring(held.indexOf('|') + 1))));
        }

        List<Component> professions = section("professions") ? professions(player) : List.of();
        if (!professions.isEmpty()) {
            List<Component> section = new ArrayList<>();
            section.add(plugin.lang().get("sidebar.professions"));
            section.addAll(professions);
            sections.add(section);
        }

        List<Component> enchants = section("enchantments") ? enchantments(player) : List.of();
        if (!enchants.isEmpty()) {
            List<Component> section = new ArrayList<>();
            section.add(plugin.lang().get("sidebar.enchantments"));
            section.addAll(enchants);
            sections.add(section);
        }

        int rows = sections.stream().mapToInt(List::size).sum();
        boolean spaced = rows + sections.size() - 1 <= MAX_ROWS;
        List<Component> lines = new ArrayList<>();
        for (List<Component> section : sections) {
            if (spaced && !lines.isEmpty()) {
                lines.add(Component.empty());
            }
            lines.addAll(section);
        }
        return lines.size() > MAX_ROWS ? new ArrayList<>(lines.subList(0, MAX_ROWS)) : lines;
    }

    /**
     * The professions the player is working on: those that gained XP in the last minute, newest
     * first, as "  Mining Lv 12 43%". Found by watching the XP the Professions plugin stores.
     */
    private List<Component> professions(Player player) {
        List<Component> lines = new ArrayList<>();
        Plugin professions = plugin.getServer().getPluginManager().getPlugin("Professions");
        if (professions == null) {
            return lines;
        }
        long now = System.currentTimeMillis();
        Map<String, Double> known = professionXp.get(player.getUniqueId());
        boolean first = known == null;
        if (first) {
            known = new HashMap<>();
            professionXp.put(player.getUniqueId(), known);
        }
        Map<String, Long> active = professionActive.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        for (NamespacedKey key : player.getPersistentDataContainer().getKeys()) {
            if (!key.getNamespace().equals("professions") || !key.getKey().startsWith("xp_")) {
                continue;
            }
            String id = key.getKey().substring(3);
            double xp = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.DOUBLE, 0.0);
            Double before = known.put(id, xp);
            if (!first && (before == null || xp > before)) {
                active.put(id, now);
            }
        }
        active.values().removeIf(since -> now - since > PROFESSION_SHOWN_MILLIS);
        FileConfiguration config = professions.getConfig();
        int maxLevel = config.getInt("max-level", 50);
        Map<String, Double> xpNow = known;
        active.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(MAX_PROFESSIONS).forEach(entry -> {
            double xp = xpNow.getOrDefault(entry.getKey(), 0.0);
            int level = 1;
            int needed = config.getInt("xp-base", 50) + config.getInt("xp-per-level", 15);
            while (level < maxLevel && xp >= needed) {
                xp -= needed;
                level++;
                needed = config.getInt("xp-base", 50) + config.getInt("xp-per-level", 15) * level;
            }
            Component line = plugin.lang().get(level >= maxLevel ? "sidebar.level-max" : "sidebar.level", "name", titleCase(entry.getKey()), "level", level);
            if (level < maxLevel) {
                line = line.append(Component.text(" " + (int) (100 * xp / needed) + "%", NamedTextColor.DARK_GRAY));
            }
            lines.add(line);
        });
        return lines;
    }

    /** The MagicEnchants enchantments on what the player holds and wears, e.g. "  Bleeding III". */
    private List<Component> enchantments(Player player) {
        Map<Enchantment, Integer> found = new LinkedHashMap<>();
        List<ItemStack> gear = new ArrayList<>();
        gear.add(player.getInventory().getItemInMainHand());
        gear.add(player.getInventory().getItemInOffHand());
        gear.addAll(Arrays.asList(player.getInventory().getArmorContents()));
        for (ItemStack item : gear) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
                if (entry.getKey().getKey().getNamespace().equals("magicenchants")) {
                    found.merge(entry.getKey(), entry.getValue(), Math::max);
                }
            }
        }
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : found.entrySet()) {
            if (lines.size() == MAX_ENCHANTS) {
                lines.add(plugin.lang().get("sidebar.more", "count", found.size() - MAX_ENCHANTS));
                break;
            }
            lines.add(Component.text("  ").append(entry.getKey().displayName(entry.getValue())));
        }
        return lines;
    }

    /** "frost_warden" -> "Frost Warden". */
    /** Headhunter's Eye (a MagicEnchants helmet enchantment): the player or mob being looked at, and its health. */
    private List<Component> target(Player player) {
        if (plugin.magicLevel(player.getInventory().getHelmet(), "headhunters_eye") <= 0) {
            return List.of();
        }
        RayTraceResult hit = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(), 32,
                FluidCollisionMode.NEVER, true, 0.3,
                e -> e instanceof LivingEntity && !e.equals(player) && !(e instanceof ArmorStand) && !e.isInvisible());
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity living)) {
            return List.of();
        }
        var max = living.getAttribute(Attribute.MAX_HEALTH);
        Component name = living instanceof Player other ? Component.text(other.getName())
                : Component.translatable(living.getType().translationKey());
        return List.of(plugin.lang().get("sidebar.target", "name", name),
                plugin.lang().get("sidebar.target-health", "health", String.format(Locale.ROOT, "%.1f", living.getHealth() / 2),
                        "max", String.format(Locale.ROOT, "%.0f", (max != null ? max.getValue() : 20) / 2)));
    }

    /** sidebar.sections.<name> in config.yml. */
    private boolean section(String name) {
        return plugin.getConfig().getBoolean("sidebar.sections." + name, true);
    }

    private static String titleCase(String id) {
        StringBuilder name = new StringBuilder();
        for (String word : id.split("_")) {
            if (!word.isEmpty()) {
                name.append(name.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return name.toString();
    }

    /** "  Duelist Lv 7" for each active path; levels use the Paths plugin's own XP settings. */
    private List<Component> paths(Player player) {
        List<Component> lines = new ArrayList<>();
        Plugin paths = plugin.getServer().getPluginManager().getPlugin("Paths");
        String active = player.getPersistentDataContainer().get(ACTIVE_PATHS, PersistentDataType.STRING);
        if (paths == null || active == null || active.isBlank()) {
            return lines;
        }
        FileConfiguration config = paths.getConfig();
        int maxLevel = config.getInt("max-level", 20);
        for (String id : active.split(",")) {
            double xp = player.getPersistentDataContainer().getOrDefault(new NamespacedKey("paths", "xp_" + id), PersistentDataType.DOUBLE, 0.0);
            int level = 1;
            while (level < maxLevel && xp >= config.getInt("xp-base", 40) + config.getInt("xp-per-level", 20) * level) {
                xp -= config.getInt("xp-base", 40) + config.getInt("xp-per-level", 20) * level;
                level++;
            }
            lines.add(plugin.lang().get(level >= maxLevel ? "sidebar.level-max" : "sidebar.level", "name", titleCase(id), "level", level));
        }
        return lines;
    }

    /** Running cooldowns, soonest first: ready-at time (ms) -> skill name. */
    private Map<Long, String> cooldowns(Player player) {
        Map<Long, String> running = new TreeMap<>();
        long now = System.currentTimeMillis();
        for (NamespacedKey key : COOLDOWNS) {
            String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (raw == null) {
                continue;
            }
            for (String entry : raw.split(";")) {
                int split = entry.lastIndexOf('=');
                if (split <= 0) {
                    continue;
                }
                try {
                    long readyAt = Long.parseLong(entry.substring(split + 1));
                    if (readyAt > now) {
                        // Nudge equal times apart so both skills are listed.
                        while (running.containsKey(readyAt)) {
                            readyAt++;
                        }
                        running.put(readyAt, entry.substring(0, split));
                    }
                } catch (NumberFormatException ignored) {
                    // not ours to fix: skip the entry
                }
            }
        }
        return running;
    }
}
