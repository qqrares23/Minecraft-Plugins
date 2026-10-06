package com.raresb.paths;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.raresb.paths.common.ConfigCommand;
import com.raresb.paths.common.ConfigFiles;
import com.raresb.paths.common.Integrations;
import com.raresb.paths.common.Lang;
import com.raresb.paths.common.Perms;
import com.raresb.paths.common.Worlds;
import java.util.Arrays;

public final class PathsPlugin extends JavaPlugin {
    private NamespacedKey activeKey;
    private NamespacedKey lastChangeKey;
    private PathEffects effects;
    private PathMenu menu;
    /** Player data read on every hit, kept in memory (see DataCache). */
    private final DataCache data = new DataCache();
    private final Lang lang = new Lang(this);
    private final ConfigCommand config = new ConfigCommand(this, "/paths admin", this::loadSettings);
    private static PathsPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        ConfigFiles.updateConfig(this);
        loadSettings();
        Perms.register(this, "paths.path", Arrays.stream(Path.values()).map(Path::id).toList(), "path");
        activeKey = new NamespacedKey(this, "active");
        lastChangeKey = new NamespacedKey(this, "last_change");
        effects = new PathEffects(this);
        menu = new PathMenu(this);
        getServer().getPluginManager().registerEvents(effects, this);
        getServer().getPluginManager().registerEvents(effects.more(), this);
        getServer().getPluginManager().registerEvents(effects.magic(), this);
        getServer().getPluginManager().registerEvents(menu, this);
        ChatTitles titles = new ChatTitles(this);
        getServer().getPluginManager().registerEvents(titles, this);
        getServer().getScheduler().runTaskTimer(this, titles::refreshAll, 100, 100);
        Integrations.log(this, "Hud", "sidebar shows active paths, levels and M2 cooldowns, custom menu icons",
                "WeaponSkills", "skill hits count as path hits (on-hit passives, XP)", "MagicEnchants", "enchantments trigger on path skills");
    }

    private void loadSettings() {
        reloadConfig();
        lang.load();
        if (effects != null) {
            getServer().getOnlinePlayers().forEach(effects::refresh);
        }
    }

    Lang lang() {
        return lang;
    }

    /** Text from the language file for enum descriptions; the enum's own text if the key is missing. */
    static String text(String key, String fallback) {
        return instance != null && instance.lang.has(key) ? instance.lang.raw(key) : fallback;
    }

    static List<String> textList(String key, List<String> fallback) {
        return instance != null && instance.lang.has(key) ? instance.lang.rawList(key) : fallback;
    }

    /** paths.<id>.enabled in config.yml. */
    boolean enabled(Path path) {
        return getConfig().getBoolean("paths." + path.id() + ".enabled", true);
    }

    /** The player may pick this path: enabled and allowed by permission. */
    boolean available(Player player, Path path) {
        return enabled(path) && player.hasPermission("paths.path." + path.id());
    }

    boolean talentEnabled(Path.Talent talent) {
        return getConfig().getBoolean("talents." + talent.name().toLowerCase(Locale.ROOT), true);
    }

    int maxActive() {
        return Math.max(1, getConfig().getInt("max-active-paths", 2));
    }

    @Override
    public void onDisable() {
        if (effects != null) {
            effects.shutdown();
        }
    }

    PathEffects effects() {
        return effects;
    }

    // --- Active paths ---

    /**
     * The paths that work for the player right now: picked, enabled, allowed by permission and in an
     * enabled world. Every effect goes through this, so a disabled path simply stops working.
     */
    Set<Path> active(Player player) {
        Set<Path> paths = storedActive(player);
        if (!Worlds.enabled(this, player.getWorld()) || !player.hasPermission("paths.use")) {
            paths.clear();
        }
        paths.removeIf(path -> !available(player, path));
        return paths;
    }

    /** The paths the player picked (saved), whether or not they work right now. */
    Set<Path> storedActive(Player player) {
        String raw = data.getString(player, activeKey);
        Set<Path> paths = EnumSet.noneOf(Path.class);
        if (raw != null) {
            for (String id : raw.split(",")) {
                Path path = Path.fromId(id);
                if (path != null) {
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    boolean isActive(Player player, Path path) {
        return active(player).contains(path);
    }

    /** The active path played with this weapon, or null. */
    Path activeFor(Player player, Path.Weapon weapon) {
        if (weapon == null) {
            return null;
        }
        for (Path path : active(player)) {
            if (path.weapon() == weapon) {
                return path;
            }
        }
        return null;
    }

    void setActive(Player player, Set<Path> paths) {
        data.setString(player, activeKey, paths.stream().map(Path::id).collect(Collectors.joining(",")));
    }

    /** Milliseconds until the player may drop a path again. */
    long switchCooldownLeft(Player player) {
        long last = player.getPersistentDataContainer().getOrDefault(lastChangeKey, PersistentDataType.LONG, 0L);
        long cooldown = getConfig().getLong("switch-cooldown-minutes", 60) * 60_000L;
        return Math.max(0, last + cooldown - System.currentTimeMillis());
    }

    void markDropped(Player player) {
        player.getPersistentDataContainer().set(lastChangeKey, PersistentDataType.LONG, System.currentTimeMillis());
    }

    // --- Chosen abilities and talents ---

    private final java.util.Map<String, NamespacedKey> keys = new java.util.HashMap<>();

    private NamespacedKey key(String name) {
        return keys.computeIfAbsent(name, n -> new NamespacedKey(this, n));
    }

    private NamespacedKey abilityKey(Path path, Path.Slot slot) {
        return key("ability_" + path.id() + "_" + slot.name().toLowerCase(Locale.ROOT));
    }

    private NamespacedKey talentKey(Path path, int tier) {
        return key("talent_" + path.id() + "_" + tier);
    }

    /** The ability the player picked for a slot (the slot's first ability until they pick). */
    Ability ability(Player player, Path path, Path.Slot slot) {
        String stored = data.getString(player, abilityKey(path, slot));
        Ability chosen = stored == null ? null : Ability.fromId(stored);
        if (chosen != null && chosen.path() == path && chosen.slot() == slot) {
            return chosen;
        }
        return path.abilities(slot).getFirst();
    }

    /** Whether the player has this ability working right now: its path is active and it is the one picked. */
    boolean has(Player player, Ability ability) {
        return isActive(player, ability.path()) && ability(player, ability.path(), ability.slot()) == ability;
    }

    void choose(Player player, Ability ability) {
        data.setString(player, abilityKey(ability.path(), ability.slot()), ability.id());
    }

    /** The talent picked at a tier, or null if none was picked yet. */
    Path.Talent talent(Player player, Path path, int tier) {
        String stored = data.getString(player, talentKey(path, tier));
        if (stored == null) {
            return null;
        }
        for (Path.Talent talent : path.talents(tier)) {
            if (talent.name().equalsIgnoreCase(stored)) {
                return talent;
            }
        }
        return null;
    }

    void chooseTalent(Player player, Path path, int tier, Path.Talent talent) {
        data.setString(player, talentKey(path, tier), talent.name().toLowerCase(Locale.ROOT));
    }

    /** Whether an active path gives the player this talent (picked, and the path's level has reached the tier). */
    boolean hasTalent(Player player, Path path, Path.Talent talent) {
        if (!isActive(player, path)) {
            return false;
        }
        int level = level(player, path); // the real level: tiers 30-50 are above the strength cap
        if (!talentEnabled(talent)) {
            return false;
        }
        for (int tier : Path.TALENT_TIERS) {
            if (level >= tier && talent(player, path, tier) == talent) {
                return true;
            }
        }
        return false;
    }

    /** Whether any active path gives the player this talent. */
    boolean hasTalentAnywhere(Player player, Path.Talent talent) {
        for (Path path : active(player)) {
            if (hasTalent(player, path, talent)) {
                return true;
            }
        }
        return false;
    }

    /** Levels above 20 still count: +0.5% path damage per level (up to +15% at 50). */
    double veteranBonus(Player player, Path path) {
        return 1 + 0.005 * Math.max(0, level(player, path) - 20);
    }

    /** Talent multipliers for the three slots: 1.3 with the matching talent, else 1. */
    double hitPower(Player player, Path path) {
        return hasTalent(player, path, Path.Talent.HIT_POWER) ? 1.3 : 1;
    }

    double passivePower(Player player, Path path) {
        return hasTalent(player, path, Path.Talent.PASSIVE_POWER) ? 1.3 : 1;
    }

    double skillPower(Player player, Path path) {
        return hasTalent(player, path, Path.Talent.SKILL_POWER) ? 1.3 : 1;
    }

    /** Talent tiers the player has reached but not picked yet, for an active path. */
    List<Integer> openTalents(Player player, Path path) {
        List<Integer> open = new ArrayList<>();
        int level = level(player, path);
        for (int tier : Path.TALENT_TIERS) {
            if (level >= tier && talent(player, path, tier) == null) {
                open.add(tier);
            }
        }
        return open;
    }

    // --- XP and levels (kept for every path, active or not) ---

    private NamespacedKey xpKey(Path path) {
        return key("xp_" + path.id());
    }

    /** Drops a player's cached data when they leave. */
    void forget(Player player) {
        data.forget(player);
    }

    int maxLevel() {
        return getConfig().getInt("max-level", 20);
    }

    int xpForNext(int level) {
        return getConfig().getInt("xp-base", 40) + getConfig().getInt("xp-per-level", 20) * level;
    }

    double xp(Player player, Path path) {
        return data.getDouble(player, xpKey(path));
    }

    int level(Player player, Path path) {
        double xp = xp(player, path);
        int level = 1;
        while (level < maxLevel() && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
        }
        return level;
    }

    /**
     * The level that counts for a path's strength. Levels above bonus-cap-level can be earned
     * but don't make the path stronger yet.
     */
    int power(Player player, Path path) {
        return Math.min(level(player, path), getConfig().getInt("bonus-cap-level", 20));
    }

    double[] progress(Player player, Path path) {
        double xp = xp(player, path);
        int level = 1;
        while (level < maxLevel() && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
        }
        return level >= maxLevel() ? new double[] {0, 0} : new double[] {xp, xpForNext(level)};
    }

    /** MagicEnchants enchantments looked up by id (empty when that plugin isn't installed). */
    private final java.util.Map<String, java.util.Optional<org.bukkit.enchantments.Enchantment>> magicEnchants = new java.util.HashMap<>();

    /** The level of a MagicEnchants enchantment on an item (0 when that plugin isn't installed). */
    int magicLevel(org.bukkit.inventory.ItemStack item, String id) {
        var enchantment = magicEnchants.computeIfAbsent(id, key -> java.util.Optional.ofNullable(io.papermc.paper.registry.RegistryAccess
                .registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                .get(net.kyori.adventure.key.Key.key("magicenchants", key)))).orElse(null);
        return enchantment == null || item == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    /** Only active paths earn XP. */
    void addXp(Player player, Path path, double amount) {
        if (amount <= 0 || !isActive(player, path)) {
            return;
        }
        int before = level(player, path);
        if (before >= maxLevel()) {
            return;
        }
        // Pathbound (a MagicEnchants enchantment on the weapon): +10% path XP per level.
        double pathbound = 1 + 0.1 * magicLevel(player.getInventory().getItemInMainHand(), "pathbound");
        data.setDouble(player, xpKey(path), xp(player, path) + amount * pathbound * getConfig().getDouble("xp-multiplier", 1.0)
                * getConfig().getDouble("paths." + path.id() + ".xp-multiplier", 1.0));
        int after = level(player, path);
        if (after > before) {
            player.showTitle(Title.title(
                    Component.text(path.displayName(), path.color(), TextDecoration.BOLD),
                    lang.get(after >= maxLevel() ? "level-up.subtitle-max" : "level-up.subtitle", "level", after),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(500))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.8f);
            player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 1);
            player.sendMessage(lang.get("level-up.chat", "path", Component.text(path.displayName(), path.color()), "level", after));
            for (Path.Slot slot : path.slots()) {
                for (String upgrade : ability(player, path, slot).upgrades()) {
                    if (Ability.upgradeLevel(upgrade) == after) {
                        player.sendMessage(lang.get("level-up.upgrade", "ability", ability(player, path, slot).displayName(),
                                "upgrade", upgrade.substring(upgrade.indexOf(':') + 1).trim()));
                    }
                }
            }
            for (int tier : Path.TALENT_TIERS) {
                if (tier == after) {
                    player.sendMessage(lang.get("level-up.new-talent", "path", path.displayName()));
                }
            }
            effects.refresh(player);
        }
    }

    void setLevel(Player player, Path path, int level) {
        double xp = 0;
        for (int n = 1; n < Math.min(maxLevel(), level); n++) {
            xp += xpForNext(n);
        }
        data.setDouble(player, xpKey(path), xp);
    }

    // --- Commands ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, label, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can choose paths. Admins: /" + label + " admin ...");
            return true;
        }
        if (!player.hasPermission("paths.use")) {
            player.sendMessage(lang.get("no-permission"));
            return true;
        }
        menu.open(player);
        return true;
    }

    private void admin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("paths.admin")) {
            sender.sendMessage(lang.get("no-permission"));
            return;
        }
        if (config.handle(sender, Arrays.copyOfRange(args, 1, args.length))) {
            return;
        }
        String usage = "/" + label + " admin setlevel <player> <path|all> <level> | resetcooldown <player> | info <player>"
                + " | setactive <player> <path,path|none> | reload | config <get|set|reset|list> | toggle <feature> | lang <code>";
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "info" -> {
                Player target = args.length >= 3 ? getServer().getPlayer(args[2]) : null;
                if (target == null) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                sender.sendMessage(Component.text(target.getName() + " - paths (picked: " + storedActive(target).stream().map(Path::displayName)
                        .collect(Collectors.joining(", ")) + "; working now: " + active(target).stream().map(Path::displayName)
                        .collect(Collectors.joining(", ")) + ")", NamedTextColor.GOLD));
                for (Path path : Path.values()) {
                    int level = level(target, path);
                    if (level <= 1 && !storedActive(target).contains(path)) {
                        continue;
                    }
                    StringBuilder line = new StringBuilder("  " + path.displayName() + " " + level + ": ");
                    line.append(path.slots().stream().map(slot -> ability(target, path, slot).displayName())
                            .collect(Collectors.joining(", ")));
                    sender.sendMessage(Component.text(line.toString(), NamedTextColor.GRAY));
                }
            }
            case "setactive" -> {
                Player target = args.length >= 4 ? getServer().getPlayer(args[2]) : null;
                if (target == null) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                Set<Path> chosen = EnumSet.noneOf(Path.class);
                if (!args[3].equalsIgnoreCase("none")) {
                    for (String id : args[3].split(",")) {
                        Path path = Path.fromId(id);
                        if (path == null) {
                            sender.sendMessage(Component.text("Unknown path " + id + ".", NamedTextColor.RED));
                            return;
                        }
                        chosen.add(path);
                    }
                }
                setActive(target, chosen);
                effects.refresh(target);
                sender.sendMessage(Component.text(target.getName() + "'s paths: " + (chosen.isEmpty() ? "none"
                        : chosen.stream().map(Path::displayName).collect(Collectors.joining(", "))) + ".", NamedTextColor.GREEN));
            }
            case "resetcooldown" -> {
                Player target = args.length >= 3 ? getServer().getPlayer(args[2]) : null;
                if (target == null) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                target.getPersistentDataContainer().remove(lastChangeKey);
                effects.resetCooldowns(target);
                sender.sendMessage(Component.text(target.getName() + " can change paths now (M2 cooldowns reset too).", NamedTextColor.GREEN));
            }
            case "setlevel" -> {
                Player target = args.length >= 5 ? getServer().getPlayer(args[2]) : null;
                List<Path> paths = args.length >= 5 ? (args[3].equalsIgnoreCase("all") ? List.of(Path.values())
                        : Path.fromId(args[3]) == null ? List.of() : List.of(Path.fromId(args[3]))) : List.of();
                int level;
                try {
                    level = args.length >= 5 ? Integer.parseInt(args[4]) : -1;
                } catch (NumberFormatException e) {
                    level = -1;
                }
                if (target == null || paths.isEmpty() || level < 1) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                int clamped = Math.min(maxLevel(), level);
                for (Path path : paths) {
                    setLevel(target, path, clamped);
                }
                effects.refresh(target);
                sender.sendMessage(Component.text("Set " + args[3] + " to level " + clamped + " for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("paths.admin")) {
            return List.of();
        }
        if (args.length >= 3 && ConfigCommand.SUBCOMMANDS.contains(args[1].toLowerCase(Locale.ROOT))) {
            return config.complete(Arrays.copyOfRange(args, 1, args.length));
        }
        List<String> options = new ArrayList<>();
        switch (args.length) {
            case 1 -> options.add("admin");
            case 2 -> {
                options.addAll(List.of("setlevel", "resetcooldown", "info", "setactive"));
                options.addAll(ConfigCommand.SUBCOMMANDS);
            }
            case 3 -> getServer().getOnlinePlayers().forEach(p -> options.add(p.getName()));
            case 4 -> {
                options.add("all");
                for (Path path : Path.values()) {
                    options.add(path.id());
                }
            }
            default -> {
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
