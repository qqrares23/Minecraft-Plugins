package com.raresb.hud;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.raresb.hud.common.ConfigCommand;
import com.raresb.hud.common.ConfigFiles;
import com.raresb.hud.common.Integrations;
import com.raresb.hud.common.Lang;
import com.raresb.hud.common.Worlds;
import java.util.Arrays;

public final class HudPlugin extends JavaPlugin implements Listener {
    private NamespacedKey sidebarKey;
    private NamespacedKey damageKey;
    /** Written by WeaponSkills; read here by key so the plugins stay independent. */
    private final NamespacedKey skillsUsedKey = new NamespacedKey("weaponskills", "skills_used");
    private Sidebar sidebar;
    private DamageNumbers damageNumbers;
    private PackHost packHost;
    private Combo combo;
    private final Lang lang = new Lang(this);
    private final ConfigCommand admin = new ConfigCommand(this, "/hud", this::reloadSettings);
    private Component sidebarTitle = Component.empty();

    @Override
    public void onEnable() {
        ConfigFiles.updateConfig(this);
        loadSettings();
        sidebarKey = new NamespacedKey(this, "sidebar");
        damageKey = new NamespacedKey(this, "damage_numbers");
        sidebar = new Sidebar(this);
        damageNumbers = new DamageNumbers(this);
        combo = new Combo(this);
        getServer().getPluginManager().registerEvents(combo, this);
        getServer().getScheduler().runTaskTimer(this, combo::tick, 4, 4);
        packHost = new PackHost(this);
        packHost.enable();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(packHost, this);
        getServer().getScheduler().runTaskTimer(this, sidebar::tick, 10, 10);
        Integrations.log(this, "WeaponSkills", "skill cooldowns, held weapon, skills used in /stats",
                "Paths", "active paths and their cooldowns", "Professions", "profession progress",
                "MagicEnchants", "enchantments on your gear", "MightyMobs", "works alongside its health bars");
    }

    private void loadSettings() {
        reloadConfig();
        lang.load();
        String title = getConfig().getString("sidebar.title", "<gold><bold>Adventurer");
        // A title without tags (older configs) keeps the old gold bold look.
        sidebarTitle = Lang.parse(title.contains("<") ? title : "<gold><bold>" + title);
    }

    /** /hud reload and every /hud config change: settings, language and the resource pack. */
    private void reloadSettings() {
        loadSettings();
        packHost.reload();
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

    Lang lang() {
        return lang;
    }

    Component sidebarTitle() {
        return sidebarTitle;
    }

    boolean worldEnabled(Player player) {
        return Worlds.enabled(this, player.getWorld());
    }

    @Override
    public void onDisable() {
        if (sidebar != null) {
            getServer().getOnlinePlayers().forEach(sidebar::hide);
        }
        if (damageNumbers != null) {
            damageNumbers.shutdown();
        }
        if (packHost != null) {
            packHost.disable();
        }
        if (combo != null) {
            combo.shutdown();
        }
    }

    // --- Per-player switches ---

    boolean sidebarOn(Player player) {
        return getConfig().getBoolean("sidebar.enabled", true) && player.hasPermission("hud.sidebar") && worldEnabled(player)
                && enabled(player, sidebarKey, "sidebar.default-on");
    }

    boolean damageNumbersOn(Player player) {
        return getConfig().getBoolean("damage-numbers.enabled", true) && player.hasPermission("hud.damagenumbers") && worldEnabled(player)
                && enabled(player, damageKey, "damage-numbers.default-on");
    }

    private boolean enabled(Player player, NamespacedKey key, String defaultPath) {
        Byte chosen = player.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return chosen == null ? getConfig().getBoolean(defaultPath, true) : chosen != 0;
    }

    // --- Combo and damage numbers ---

    /** The player's running hit combo (0 once the window has passed). */
    int combo(Player player) {
        return combo.of(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player attacker = Combo.attacker(event);
        if (attacker == null || event.getFinalDamage() <= 0) {
            return;
        }
        LivingEntity target = (LivingEntity) event.getEntity();
        if (!worldEnabled(attacker)) {
            return;
        }
        combo.landed(attacker, target);
        if (damageNumbersOn(attacker)) {
            damageNumbers.show(attacker, target, event.getFinalDamage(), event.isCritical());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combo.forget(event.getPlayer());
        sidebar.forget(event.getPlayer());
        damageNumbers.forget(event.getPlayer());
    }

    // --- Commands ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName()) {
            case "hud" -> {
                String[] rest = args.length > 0 && args[0].equalsIgnoreCase("admin") ? Arrays.copyOfRange(args, 1, args.length) : args;
                if (rest.length > 0 && rest[0].equalsIgnoreCase("pack")) {
                    packCommand(sender, label, rest);
                } else if (!admin.handle(sender, rest)) {
                    sender.sendMessage(Component.text("/" + label + " reload | config <get|set|reset|list> | toggle <feature> [on|off]"
                            + " | lang <code> | pack <status|url <link>|on|off|reload>", NamedTextColor.YELLOW));
                }
            }
            case "stats" -> stats(sender, args);
            default -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(lang.get("players-only"));
                    return true;
                }
                boolean isSidebar = command.getName().equals("sidebar");
                if (!getConfig().getBoolean(isSidebar ? "sidebar.enabled" : "damage-numbers.enabled", true)) {
                    player.sendMessage(lang.get("feature-off"));
                    return true;
                }
                boolean on = !(isSidebar ? sidebarOn(player) : damageNumbersOn(player));
                player.getPersistentDataContainer().set(isSidebar ? sidebarKey : damageKey, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
                player.sendMessage(lang.get((isSidebar ? "toggle.sidebar-" : "toggle.damage-numbers-") + (on ? "on" : "off")));
            }
        }
        return true;
    }

    private void stats(CommandSender sender, String[] args) {
        Player target = args.length >= 1 ? getServer().getPlayer(args[0]) : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage(args.length >= 1 ? lang.get("not-online") : Component.text("/stats <player>", NamedTextColor.RED));
            return;
        }
        int mobKills = target.getStatistic(Statistic.MOB_KILLS);
        int playerKills = target.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = target.getStatistic(Statistic.DEATHS);
        sender.sendMessage(lang.get("stats.header", "player", target.getName()));
        statLine(sender, "stats.kills", lang.raw("stats.kills-value").replace("<total>", String.valueOf(mobKills + playerKills))
                .replace("<mobs>", String.valueOf(mobKills)).replace("<players>", String.valueOf(playerKills)));
        statLine(sender, "stats.deaths", String.valueOf(deaths));
        statLine(sender, "stats.kd", String.format(Locale.ROOT, "%.1f", (mobKills + playerKills) / (double) Math.max(1, deaths)));
        statLine(sender, "stats.best-combo", "x" + combo.best(target));
        statLine(sender, "stats.finishers", String.valueOf(combo.finishers(target)));
        if (getServer().getPluginManager().getPlugin("WeaponSkills") != null) {
            statLine(sender, "stats.skills-used", String.valueOf(
                    target.getPersistentDataContainer().getOrDefault(skillsUsedKey, PersistentDataType.INTEGER, 0)));
        }
    }

    private void statLine(CommandSender sender, String key, String value) {
        sender.sendMessage(lang.get("stats.line", "name", lang.get(key), "value", value));
    }

    private void packCommand(CommandSender sender, String label, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "url" -> {
                if (args.length < 3 || !args[2].startsWith("http")) {
                    sender.sendMessage(Component.text("/" + label + " pack url <https://...zip> - downloads it, saves it as pack.zip"
                            + " and offers it to players.", NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text("Downloading " + args[2] + " ...", NamedTextColor.YELLOW));
                packHost.download(args[2], sender);
            }
            case "on", "off" -> {
                getConfig().set("resource-pack.enabled", action.equals("on"));
                saveConfig();
                packHost.reload();
                sender.sendMessage(Component.text("Resource pack " + (action.equals("on") ? "enabled." : "disabled."), NamedTextColor.GREEN));
            }
            case "reload" -> {
                packHost.reload();
                sender.sendMessage(Component.text("pack.zip re-read and offered again: " + packHost.describe(), NamedTextColor.GREEN));
            }
            default -> {
                String url = getConfig().getString("resource-pack.url", "");
                sender.sendMessage(Component.text("Resource pack: " + (getConfig().getBoolean("resource-pack.enabled", true) ? "on" : "off")
                        + " | pack.zip: " + packHost.describe() + " | url: " + (url.isBlank() ? "(none)" : url), NamedTextColor.YELLOW));
                if (url.isBlank()) {
                    sender.sendMessage(Component.text("Host the pack anywhere public and run /" + label + " pack url <link>.", NamedTextColor.GRAY));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equals("stats") && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return getServer().getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (!command.getName().equals("hud")) {
            return List.of();
        }
        String[] rest = args.length > 0 && args[0].equalsIgnoreCase("admin") ? Arrays.copyOfRange(args, 1, args.length) : args;
        if (rest.length == 1) {
            List<String> options = new java.util.ArrayList<>(ConfigCommand.SUBCOMMANDS);
            options.add("pack");
            return ConfigCommand.filter(options, rest[0]);
        }
        if (rest.length == 2 && rest[0].equalsIgnoreCase("pack")) {
            return ConfigCommand.filter(List.of("status", "url", "on", "off", "reload"), rest[1]);
        }
        return admin.complete(rest);
    }
}
