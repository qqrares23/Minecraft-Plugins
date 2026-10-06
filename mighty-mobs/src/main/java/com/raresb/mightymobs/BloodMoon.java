package com.raresb.mightymobs;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Blood Moon: now and then a night in the overworld where far more mobs are mighty, special
 * spawns are more common, the monster cap is raised and hostile mobs drop more XP. Rolled each
 * morning (so it is announced in advance), no sleeping through it, ends at dawn.
 *
 * State lives in the world's PDC, so a restart in the middle of a Blood Moon picks it up again:
 * {@code mightymobs:blood_moon_decided} (last day that was rolled), {@code blood_moon_night}
 * (day whose night is a Blood Moon), {@code blood_moon_last} (day of the last Blood Moon) and
 * {@code blood_moon_active} (byte, present while it runs – other plugins may read it).
 */
final class BloodMoon implements Listener {
    static final TextColor RED = TextColor.color(0xC0392B);
    private static final long DAY = 24000;
    /** Night in world time: 13000 (dusk, mobs start spawning) to 23000 (dawn). */
    private static final long NIGHT_START = 13000;
    private static final long NIGHT_END = 23000;
    private static final long REMINDER_TIME = 11500;

    private final MightyMobsPlugin plugin;
    private final NamespacedKey decidedKey;
    private final NamespacedKey nightKey;
    private final NamespacedKey lastKey;
    private final NamespacedKey activeKey;

    /** Running Blood Moons: world -> its boss bar. */
    private final Map<UUID, BossBar> active = new HashMap<>();
    /** Monster spawn limit before the Blood Moon raised it, restored at the end. */
    private final Map<UUID, Integer> oldSpawnLimits = new HashMap<>();
    /** Players who were sent the sunset reminder, per world. */
    private final Set<UUID> reminded = new HashSet<>();
    /** Hostile kills per player during the current Blood Moon(s). */
    private final Map<UUID, Integer> kills = new HashMap<>();
    private long ticks;

    BloodMoon(MightyMobsPlugin plugin) {
        this.plugin = plugin;
        this.decidedKey = new NamespacedKey(plugin, "blood_moon_decided");
        this.nightKey = new NamespacedKey(plugin, "blood_moon_night");
        this.lastKey = new NamespacedKey(plugin, "blood_moon_last");
        this.activeKey = new NamespacedKey(plugin, "blood_moon_active");
    }

    // --- Settings ---

    private double setting(String key, double fallback) {
        return plugin.getConfig().getDouble("blood-moon." + key, fallback);
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("blood-moon.enabled", true);
    }

    boolean isActive(World world) {
        return active.containsKey(world.getUID());
    }

    /** Chance for a natural hostile spawn to be mighty in this world right now. */
    double mightyChance(World world) {
        return isActive(world) ? setting("spawn-chance", 0.35) : plugin.getConfig().getDouble("spawn-chance", 0.08);
    }

    double doubleAbilityChance(World world) {
        return isActive(world) ? setting("double-ability-chance", 0.4) : plugin.getConfig().getDouble("double-ability-chance", 0.2);
    }

    double specialMultiplier(World world) {
        return isActive(world) ? setting("special-multiplier", 3.0) : 1.0;
    }

    double rareBonus(World world) {
        return isActive(world) ? setting("rare-chance-bonus", 0.05) : 0;
    }

    /** Worlds that have nights: the allowed overworlds. */
    private boolean hasNights(World world) {
        List<String> only = plugin.getConfig().getStringList("blood-moon.worlds");
        return world.getEnvironment() == World.Environment.NORMAL && plugin.worldAllowed(world)
                && (only.isEmpty() || only.contains(world.getName()));
    }

    // --- Clock (every second) ---

    void tick() {
        ticks++;
        for (World world : Bukkit.getWorlds()) {
            if (hasNights(world)) {
                tick(world);
            }
        }
    }

    private void tick(World world) {
        long full = world.getFullTime();
        long day = Math.floorDiv(full, DAY);
        long time = Math.floorMod(full, DAY);
        boolean night = time >= NIGHT_START && time < NIGHT_END;
        PersistentDataContainer data = world.getPersistentDataContainer();
        boolean tonight = data.getOrDefault(nightKey, PersistentDataType.LONG, Long.MIN_VALUE) == day;

        if (isActive(world)) {
            if (!night || !tonight) {
                end(world, true); // dawn (or the night was skipped with /time)
            } else {
                update(world, time);
            }
            return;
        }
        if (tonight && night) {
            start(world);
            return;
        }
        // Once per day, in the morning: is tonight a Blood Moon?
        if (enabled() && time < NIGHT_START - 1000 && data.getOrDefault(decidedKey, PersistentDataType.LONG, Long.MIN_VALUE) != day) {
            data.set(decidedKey, PersistentDataType.LONG, day);
            long last = data.getOrDefault(lastKey, PersistentDataType.LONG, Long.MIN_VALUE / 2);
            boolean rested = day - last >= plugin.getConfig().getInt("blood-moon.min-days-between", 4);
            if (rested && ThreadLocalRandom.current().nextDouble() < setting("chance", 0.12)) {
                schedule(world, day);
            }
        }
        if (tonight && time >= REMINDER_TIME && reminded.add(world.getUID())) {
            broadcast(world, plugin.lang().get("blood-moon.reminder"));
            for (Player player : world.getPlayers()) {
                player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.6f);
            }
        }
    }

    // --- Scheduling / starting / ending ---

    /** Marks tonight as a Blood Moon and tells everyone in the world. */
    void schedule(World world, long day) {
        world.getPersistentDataContainer().set(nightKey, PersistentDataType.LONG, day);
        if (Math.floorMod(world.getFullTime(), DAY) >= REMINDER_TIME) {
            reminded.add(world.getUID()); // announced this late: no separate reminder
        } else {
            reminded.remove(world.getUID());
        }
        broadcast(world, plugin.lang().get("blood-moon.announce"));
        for (Player player : world.getPlayers()) {
            player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 0.8f);
        }
    }

    /** /mm bloodmoon start: now if it is night, otherwise tonight. Returns true if it started now. */
    boolean forceStart(World world) {
        long full = world.getFullTime();
        long day = Math.floorDiv(full, DAY);
        long time = Math.floorMod(full, DAY);
        world.getPersistentDataContainer().set(decidedKey, PersistentDataType.LONG, day);
        if (time >= NIGHT_START && time < NIGHT_END) {
            world.getPersistentDataContainer().set(nightKey, PersistentDataType.LONG, day);
            if (!isActive(world)) {
                start(world);
            }
            return true;
        }
        schedule(world, day);
        return false;
    }

    /** /mm bloodmoon stop: ends a running one and cancels tonight's. */
    void forceStop(World world) {
        world.getPersistentDataContainer().remove(nightKey);
        if (isActive(world)) {
            end(world, false);
        }
    }

    boolean scheduledTonight(World world) {
        long day = Math.floorDiv(world.getFullTime(), DAY);
        return world.getPersistentDataContainer().getOrDefault(nightKey, PersistentDataType.LONG, Long.MIN_VALUE) == day;
    }

    private void start(World world) {
        BossBar bar = BossBar.bossBar(plugin.lang().get("blood-moon.bossbar"), 1f,
                BossBar.Color.RED, BossBar.Overlay.NOTCHED_10, Set.of(BossBar.Flag.DARKEN_SCREEN));
        active.put(world.getUID(), bar);
        world.getPersistentDataContainer().set(activeKey, PersistentDataType.BYTE, (byte) 1);
        world.getPersistentDataContainer().set(lastKey, PersistentDataType.LONG, Math.floorDiv(world.getFullTime(), DAY));

        int limit = world.getSpawnLimit(SpawnCategory.MONSTER);
        oldSpawnLimits.putIfAbsent(world.getUID(), limit);
        world.setSpawnLimit(SpawnCategory.MONSTER, (int) Math.round(limit * setting("spawn-limit-multiplier", 2.0)));

        Title title = Title.title(plugin.lang().get("blood-moon.title"), plugin.lang().get("blood-moon.subtitle"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        for (Player player : world.getPlayers()) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.7f);
        }
        broadcast(world, plugin.lang().get("blood-moon.started"));
        plugin.getLogger().info("Blood Moon started in " + world.getName() + ".");
        update(world, Math.floorMod(world.getFullTime(), DAY));
    }

    private void end(World world, boolean announce) {
        BossBar bar = active.remove(world.getUID());
        world.getPersistentDataContainer().remove(activeKey);
        reminded.remove(world.getUID());
        Integer limit = oldSpawnLimits.remove(world.getUID());
        if (limit != null) {
            world.setSpawnLimit(SpawnCategory.MONSTER, limit);
        }
        if (bar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
        }
        if (announce) {
            broadcast(world, plugin.lang().get("blood-moon.ended"));
            topHunter(world);
        }
        if (active.isEmpty()) {
            kills.clear();
        }
        plugin.getLogger().info("Blood Moon ended in " + world.getName() + ".");
    }

    private void topHunter(World world) {
        UUID best = null;
        int most = 0;
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            if (entry.getValue() > most) {
                best = entry.getKey();
                most = entry.getValue();
            }
        }
        if (best == null) {
            return;
        }
        String name = Bukkit.getOfflinePlayer(best).getName();
        broadcast(world, plugin.lang().get("blood-moon.top-hunter", "player", name != null ? name : "?", "kills", plugin.lang().count("unit.monster", most)));
    }

    /** Boss bar (time left until dawn) for players in the world, and some crimson spores. */
    private void update(World world, long time) {
        BossBar bar = active.get(world.getUID());
        float left = (float) (NIGHT_END - time) / (NIGHT_END - NIGHT_START);
        bar.progress(Math.max(0f, Math.min(1f, left)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == world) {
                player.showBossBar(bar);
                if (ticks % 2 == 0) {
                    player.spawnParticle(Particle.CRIMSON_SPORE, player.getLocation().add(0, 2, 0), 40, 8, 4, 8, 0);
                }
            } else {
                player.hideBossBar(bar);
            }
        }
    }

    /** Called on disable: hide bars and put the spawn limits back. The PDC keeps the night for the next start. */
    void shutdown() {
        for (World world : Bukkit.getWorlds()) {
            BossBar bar = active.remove(world.getUID());
            Integer limit = oldSpawnLimits.remove(world.getUID());
            if (limit != null) {
                world.setSpawnLimit(SpawnCategory.MONSTER, limit);
            }
            if (bar != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.hideBossBar(bar);
                }
            }
        }
    }

    private static void broadcast(World world, Component message) {
        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }

    // --- Events ---

    /** No sleeping through a Blood Moon (or the night one is announced for). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        World world = event.getPlayer().getWorld();
        if (isActive(world) || (hasNights(world) && scheduledTonight(world))) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(plugin.lang().get("blood-moon.no-sleep"));
        }
    }

    /** More XP from hostile mobs, on top of the mighty/toughness bonus; counts kills for the top hunter. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enemy) || !isActive(entity.getWorld())) {
            return;
        }
        if (event.getDroppedExp() > 0) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * setting("xp-multiplier", 2.0)));
        }
        Player killer = entity.getKiller();
        if (killer != null) {
            kills.merge(killer.getUniqueId(), 1, Integer::sum);
        }
    }
}
