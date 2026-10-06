package com.raresb.mightymobs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import com.raresb.mightymobs.common.ConfigCommand;
import com.raresb.mightymobs.common.ConfigFiles;
import com.raresb.mightymobs.common.Integrations;
import com.raresb.mightymobs.common.Lang;
import com.raresb.mightymobs.common.Worlds;

public final class MightyMobsPlugin extends JavaPlugin implements TabExecutor {
    /** How often (in ticks) timed abilities are checked. */
    private static final long ABILITY_TICK = 10;
    /** Leaper cooldown in ability ticks (3 seconds). */
    private static final int LEAP_COOLDOWN = 6;

    private MightyMobs mobs;
    private SpecialSpawns specials;
    private BloodMoon bloodMoon;
    private Set<Ability> enabledAbilities = EnumSet.noneOf(Ability.class);
    private Set<SpawnReason> spawnReasons = EnumSet.of(SpawnReason.NATURAL);
    private final Map<UUID, Integer> leapCooldowns = new HashMap<>();
    private long abilityTicks;
    private final Lang lang = new Lang(this);
    private final ConfigCommand admin = new ConfigCommand(this, "/mm admin", this::loadSettings);
    private Set<EntityType> ignoredMobs = EnumSet.noneOf(EntityType.class);

    @Override
    public void onEnable() {
        ConfigFiles.updateConfig(this);
        loadSettings();
        bloodMoon = new BloodMoon(this);
        getServer().getPluginManager().registerEvents(bloodMoon, this);
        mobs = new MightyMobs(this);
        getServer().getPluginManager().registerEvents(new MobListener(this, mobs), this);
        Loot loot = new Loot(this, mobs);
        getServer().getPluginManager().registerEvents(loot, this);
        Mounts mounts = new Mounts(this);
        getServer().getPluginManager().registerEvents(mounts, this);
        specials = new SpecialSpawns(this, mobs, loot, mounts);
        getServer().getPluginManager().registerEvents(specials, this);
        var command = getCommand("mightymobs");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        // Pick up mobs that were already loaded (e.g. after /reload).
        for (World world : getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (mobs.isMighty(entity)) {
                    mobs.track(entity);
                }
                mobs.updateName(entity);
            }
        }
        getServer().getScheduler().runTaskTimer(this, this::tickAbilities, ABILITY_TICK, ABILITY_TICK);
        getServer().getScheduler().runTaskTimer(this, bloodMoon::tick, 20, 20);
        int removed = mounts.sweepLoaded();
        if (removed > 0) {
            getLogger().info("Removed " + removed + " leftover special-spawn mounts.");
        }
        getServer().getScheduler().runTaskTimer(this, mounts::tick, 200, 200);
        Integrations.log(this, "Hud", "damage numbers and sidebar", "ItemCleaner", "keeps glowing rare drops",
                "MagicEnchants", "its enchantments appear in rare book drops");
    }

    @Override
    public void onDisable() {
        if (bloodMoon != null) {
            bloodMoon.shutdown();
        }
    }

    BloodMoon bloodMoon() {
        return bloodMoon;
    }

    private void loadSettings() {
        reloadConfig();
        lang.load();
        EnumSet<EntityType> ignored = EnumSet.noneOf(EntityType.class);
        for (String name : getConfig().getStringList("ignored-mobs")) {
            EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT)));
            if (type == null) {
                getLogger().warning("Unknown mob in ignored-mobs: " + name);
            } else {
                ignored.add(type);
            }
        }
        ignoredMobs = ignored;
        EnumSet<Ability> abilities = EnumSet.noneOf(Ability.class);
        for (Ability ability : Ability.values()) {
            if (getConfig().getBoolean("abilities." + ability.id(), true)) {
                abilities.add(ability);
            }
        }
        enabledAbilities = abilities;

        EnumSet<SpawnReason> reasons = EnumSet.noneOf(SpawnReason.class);
        for (String name : getConfig().getStringList("spawn-reasons")) {
            try {
                reasons.add(SpawnReason.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Unknown spawn reason in config: " + name);
            }
        }
        spawnReasons = reasons;
    }

    Set<Ability> enabledAbilities() {
        return enabledAbilities;
    }

    Set<SpawnReason> spawnReasons() {
        return spawnReasons;
    }

    boolean worldAllowed(World world) {
        return Worlds.enabled(this, world);
    }

    Lang lang() {
        return lang;
    }

    /** Mob types listed in ignored-mobs never become mighty or special. */
    boolean ignored(EntityType type) {
        return ignoredMobs.contains(type);
    }

    /** Ability name shown above mobs (lang key ability.<id>, English by default). */
    String abilityName(Ability ability) {
        String key = "ability." + ability.id();
        return lang.has(key) ? lang.raw(key) : ability.displayName();
    }

    // --- Timed abilities ---

    private void tickAbilities() {
        abilityTicks++;
        for (UUID id : new ArrayList<>(mobs.loaded())) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                mobs.untrack(id);
                leapCooldowns.remove(id);
                continue;
            }
            Set<Ability> abilities = mobs.abilities(living);
            AbilityEffects.ambient(living, abilities, abilityTicks);
            if (abilities.contains(Ability.REGENERATING) && abilityTicks % 2 == 0 && !Counters.purified(living)) {
                double max = mobs.maxHealth(living);
                if (living.getHealth() < max) {
                    living.setHealth(Math.min(max, living.getHealth() + 1));
                    mobs.updateName(living);
                }
            }
            if (abilities.contains(Ability.LEAPER)) {
                tryLeap(living);
            }
        }
    }

    private void tryLeap(LivingEntity living) {
        UUID id = living.getUniqueId();
        int cooldown = leapCooldowns.getOrDefault(id, 0);
        if (cooldown > 0) {
            leapCooldowns.put(id, cooldown - 1);
            return;
        }
        if (!(living instanceof Mob mob) || mob.getTarget() == null || !mob.isOnGround()) {
            return;
        }
        Location from = mob.getLocation();
        Location to = mob.getTarget().getLocation();
        if (from.getWorld() != to.getWorld()) {
            return;
        }
        double distance = from.distance(to);
        if (distance < 4 || distance > 14) {
            return;
        }
        Vector leap = to.toVector().subtract(from.toVector()).setY(0).normalize().multiply(Math.min(1.4, distance / 8)).setY(0.55);
        mob.setVelocity(leap);
        mob.getWorld().playSound(from, org.bukkit.Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.6f);
        leapCooldowns.put(id, LEAP_COOLDOWN);
    }

    // --- /mightymobs ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " status | chance <percent> | doublechance <percent>"
                    + " | ability <name> <on|off> | special [type] [percent|on|off|spawn] | spawn <mob> [ability...]"
                    + " | bloodmoon [status|start|stop|chance <percent>] | reload | config | toggle | lang",
                    NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload", "config", "toggle", "lang" -> admin.handle(sender, args);
            case "admin" -> {
                if (!admin.handle(sender, Arrays.copyOfRange(args, 1, args.length))) {
                    sender.sendMessage(Component.text("/" + label + " admin <reload|config|toggle|lang> ...", NamedTextColor.RED));
                }
            }
            case "spawn" -> spawnCommand(sender, label, args);
            case "status" -> status(sender);
            case "chance", "doublechance" -> setPercent(sender, label, args,
                    args[0].equalsIgnoreCase("chance") ? "spawn-chance" : "double-ability-chance");
            case "ability" -> abilityCommand(sender, label, args);
            case "special" -> specialCommand(sender, label, args);
            case "bloodmoon" -> bloodMoonCommand(sender, label, args);
            default -> sender.sendMessage(Component.text("Unknown subcommand. Type /" + label + " for help.", NamedTextColor.RED));
        }
        return true;
    }

    private void spawnCommand(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can spawn mighty mobs.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /" + label + " spawn <mob> [ability...]", NamedTextColor.YELLOW));
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(args[1].toLowerCase(Locale.ROOT));
        EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
        if (type == null || type.getEntityClass() == null || !type.isSpawnable() || !type.isAlive()) {
            sender.sendMessage(Component.text("Unknown mob: " + args[1], NamedTextColor.RED));
            return;
        }
        Set<Ability> abilities = EnumSet.noneOf(Ability.class);
        for (String id : Arrays.copyOfRange(args, 2, args.length)) {
            Ability ability = Ability.fromId(id);
            if (ability == null) {
                sender.sendMessage(Component.text("Unknown ability: " + id, NamedTextColor.RED));
                return;
            }
            abilities.add(ability);
        }
        if (abilities.isEmpty()) {
            abilities = mobs.rollAbilities();
        }
        Set<Ability> chosen = abilities;
        Location spot = player.getTargetBlockExact(20) != null
                ? player.getTargetBlockExact(20).getLocation().add(0.5, 1, 0.5)
                : player.getLocation();
        Entity spawned = player.getWorld().spawnEntity(spot, type, SpawnReason.COMMAND);
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            sender.sendMessage(Component.text("That mob can't have abilities.", NamedTextColor.RED));
            return;
        }
        mobs.makeMighty(living, chosen);
        sender.sendMessage(Component.text("Spawned a mighty " + type.getKey().getKey() + " with "
                + chosen.stream().map(Ability::displayName).collect(Collectors.joining(", ")) + ".", NamedTextColor.GREEN));
    }

    private void status(CommandSender sender) {
        sender.sendMessage(Component.text("MightyMobs", NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD));
        sender.sendMessage(Component.text(String.format(Locale.ROOT, " Mighty mob chance: %.1f%% of natural hostile spawns",
                getConfig().getDouble("spawn-chance", 0.08) * 100), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(String.format(Locale.ROOT, " Second ability chance: %.1f%%",
                getConfig().getDouble("double-ability-chance", 0.2) * 100), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" Abilities on: " + enabledAbilities.stream().map(Ability::id).collect(Collectors.joining(", ")),
                NamedTextColor.GRAY));
        boolean specialsOn = getConfig().getBoolean("special-spawns.enabled", true);
        sender.sendMessage(Component.text(" Special spawns: " + (specialsOn ? "on" : "off"), specialsOn ? NamedTextColor.GREEN : NamedTextColor.RED));
        for (SpecialSpawns.Type type : SpecialSpawns.Type.values()) {
            sender.sendMessage(Component.text(String.format(Locale.ROOT, "  %s: %.1f%% of natural %s spawns", type.displayName(),
                    specials.chance(type), type.trigger().getKey().getKey()), NamedTextColor.GRAY));
        }
        bloodMoonStatus(sender);
    }

    private void bloodMoonStatus(CommandSender sender) {
        boolean on = bloodMoon.enabled();
        sender.sendMessage(Component.text(String.format(Locale.ROOT, " Blood Moon: %s, %.1f%% chance per night, at least %d days apart",
                on ? "on" : "off (only /mm bloodmoon start)", getConfig().getDouble("blood-moon.chance", 0.12) * 100,
                getConfig().getInt("blood-moon.min-days-between", 4)), on ? NamedTextColor.YELLOW : NamedTextColor.RED));
        for (World world : getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL || !worldAllowed(world)) {
                continue;
            }
            String state = bloodMoon.isActive(world) ? "RUNNING now" : bloodMoon.scheduledTonight(world) ? "tonight" : "not tonight";
            sender.sendMessage(Component.text("  " + world.getName() + ": " + state, NamedTextColor.GRAY));
        }
    }

    private void bloodMoonCommand(CommandSender sender, String label, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        if (action.equals("chance")) {
            Double percent = args.length >= 3 ? parsePercent(args[2]) : null;
            if (percent == null) {
                sender.sendMessage(Component.text("Usage: /" + label + " bloodmoon chance <0-100>", NamedTextColor.YELLOW));
                return;
            }
            getConfig().set("blood-moon.chance", percent / 100.0);
            saveConfig();
            sender.sendMessage(Component.text("Blood Moon chance set to " + percent + "% per night.", NamedTextColor.GREEN));
            return;
        }
        if (!action.equals("start") && !action.equals("stop")) {
            bloodMoonStatus(sender);
            return;
        }
        World world = sender instanceof Player player ? player.getWorld() : getServer().getWorlds().getFirst();
        if (world.getEnvironment() != World.Environment.NORMAL || !worldAllowed(world)) {
            sender.sendMessage(Component.text("Blood Moons only happen in the overworld.", NamedTextColor.RED));
            return;
        }
        if (action.equals("stop")) {
            bloodMoon.forceStop(world);
            sender.sendMessage(Component.text("Blood Moon stopped/cancelled in " + world.getName() + ".", NamedTextColor.GREEN));
        } else if (bloodMoon.forceStart(world)) {
            sender.sendMessage(Component.text("Blood Moon started in " + world.getName() + ".", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("It's daytime: the Blood Moon is announced for tonight in " + world.getName() + ".",
                    NamedTextColor.GREEN));
        }
    }

    private static Double parsePercent(String text) {
        try {
            double value = Double.parseDouble(text.replace("%", ""));
            return value < 0 || value > 100 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setPercent(CommandSender sender, String label, String[] args, String key) {
        Double percent = args.length >= 2 ? parsePercent(args[1]) : null;
        if (percent == null) {
            sender.sendMessage(Component.text("Usage: /" + label + " " + args[0] + " <0-100>", NamedTextColor.YELLOW));
            return;
        }
        getConfig().set(key, percent / 100.0);
        saveConfig();
        sender.sendMessage(Component.text("Set " + args[0] + " to " + percent + "%.", NamedTextColor.GREEN));
    }

    private void abilityCommand(CommandSender sender, String label, String[] args) {
        Ability ability = args.length >= 3 ? Ability.fromId(args[1]) : null;
        if (ability == null || !(args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("off"))) {
            sender.sendMessage(Component.text("Usage: /" + label + " ability <name> <on|off>", NamedTextColor.YELLOW));
            return;
        }
        getConfig().set("abilities." + ability.id(), args[2].equalsIgnoreCase("on"));
        saveConfig();
        loadSettings();
        sender.sendMessage(Component.text(ability.displayName() + " is now " + args[2].toLowerCase(Locale.ROOT) + ".", NamedTextColor.GREEN));
    }

    private void specialCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 1) {
            status(sender);
            return;
        }
        if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off")) {
            getConfig().set("special-spawns.enabled", args[1].equalsIgnoreCase("on"));
            saveConfig();
            sender.sendMessage(Component.text("Special spawns are now " + args[1].toLowerCase(Locale.ROOT) + ".", NamedTextColor.GREEN));
            return;
        }
        SpecialSpawns.Type type = SpecialSpawns.Type.fromId(args[1]);
        if (type == null || args.length < 3) {
            sender.sendMessage(Component.text("Usage: /" + label + " special <on|off> | special <type> <percent|spawn>", NamedTextColor.YELLOW));
            return;
        }
        if (args[2].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can spawn specials.", NamedTextColor.RED));
                return;
            }
            Location spot = player.getTargetBlockExact(20) != null
                    ? player.getTargetBlockExact(20).getLocation().add(0.5, 1, 0.5) : player.getLocation();
            Entity base = player.getWorld().spawnEntity(spot, type.trigger(), SpawnReason.COMMAND);
            if (base instanceof LivingEntity living) {
                living.getScheduler().run(this, task -> specials.apply(type, living), null);
            }
            sender.sendMessage(Component.text("Spawned a " + type.displayName() + ".", NamedTextColor.GREEN));
            return;
        }
        Double percent = parsePercent(args[2]);
        if (percent == null) {
            sender.sendMessage(Component.text("Give a percent between 0 and 100.", NamedTextColor.RED));
            return;
        }
        specials.setChance(type, percent);
        sender.sendMessage(Component.text(type.displayName() + " now appears in " + percent + "% of natural "
                + type.trigger().getKey().getKey() + " spawns.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("spawn", "reload", "status", "chance", "doublechance", "ability", "special", "bloodmoon",
                    "config", "toggle", "lang", "admin"), args[0]);
        }
        if (args[0].equalsIgnoreCase("admin")) {
            return admin.complete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (List.of("config", "toggle", "lang").contains(args[0].toLowerCase(Locale.ROOT))) {
            return admin.complete(args);
        }
        if (args[0].equalsIgnoreCase("bloodmoon")) {
            return args.length == 2 ? filter(List.of("status", "start", "stop", "chance"), args[1]) : Collections.emptyList();
        }
        if (args[0].equalsIgnoreCase("ability")) {
            return args.length == 2 ? filter(Arrays.stream(Ability.values()).map(Ability::id).toList(), args[1])
                    : args.length == 3 ? filter(List.of("on", "off"), args[2]) : Collections.emptyList();
        }
        if (args[0].equalsIgnoreCase("special")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>(List.of("on", "off"));
                Arrays.stream(SpecialSpawns.Type.values()).map(SpecialSpawns.Type::id).forEach(options::add);
                return filter(options, args[1]);
            }
            return args.length == 3 ? filter(List.of("spawn", "5", "10", "0"), args[2]) : Collections.emptyList();
        }
        if (!args[0].equalsIgnoreCase("spawn")) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            List<String> types = new ArrayList<>();
            for (EntityType type : Registry.ENTITY_TYPE) {
                if (type.isSpawnable() && type.isAlive() && type.getEntityClass() != null
                        && MobListener.canBeMighty(type.getEntityClass())) {
                    types.add(type.getKey().getKey());
                }
            }
            return filter(types, args[1]);
        }
        return filter(Arrays.stream(Ability.values()).map(Ability::id).toList(), args[args.length - 1]);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(lower)).sorted().toList();
    }
}
