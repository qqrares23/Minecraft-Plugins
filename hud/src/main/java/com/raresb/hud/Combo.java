package com.raresb.hud;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * The combo: hits landed in quick succession count up (x1, x2 ... up to combo.max), each step
 * adding damage. The hit that reaches the top is a finisher: extra damage, bonus XP, and the
 * combo starts over. Waiting too long drops the combo (and getting hurt, if combo.reset-when-hurt is on).
 */
final class Combo implements Listener {
    /** Hits closer together than this are one swing (an area skill hitting five mobs is one step). */
    private static final long STEP_MILLIS = 200;

    private final HudPlugin plugin;
    private final NamespacedKey bestKey;
    private final NamespacedKey finishersKey;
    private final Map<UUID, Integer> count = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final Map<UUID, Long> lastStep = new HashMap<>();
    /** Players whose current hit was boosted as a finisher (set while the damage is worked out). */
    private final Set<UUID> finishing = new HashSet<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    Combo(HudPlugin plugin) {
        this.plugin = plugin;
        this.bestKey = new NamespacedKey(plugin, "best_combo");
        this.finishersKey = new NamespacedKey(plugin, "finishers");
    }

    private int max() {
        return Math.max(2, plugin.getConfig().getInt("combo.max", 10));
    }

    /** How long the combo waits for the next hit; Combo Keeper (MagicEnchants, on the chestplate) adds 1 s per level. */
    private long window(Player player) {
        return (long) (plugin.getConfig().getDouble("combo.window-seconds", 3) * 1000)
                + 1000L * plugin.magicLevel(player.getInventory().getChestplate(), "combo_keeper");
    }

    /** The player's running combo (0 once the window has passed). */
    int of(Player player) {
        if (System.currentTimeMillis() - lastHit.getOrDefault(player.getUniqueId(), 0L) > window(player)) {
            return 0;
        }
        return count.getOrDefault(player.getUniqueId(), 0);
    }

    int best(Player player) {
        return player.getPersistentDataContainer().getOrDefault(bestKey, PersistentDataType.INTEGER, 0);
    }

    int finishers(Player player) {
        return player.getPersistentDataContainer().getOrDefault(finishersKey, PersistentDataType.INTEGER, 0);
    }

    /** The player behind a hit on a living target, or null if this hit doesn't count for combos. */
    static Player attacker(EntityDamageByEntityEvent event) {
        Player attacker = event.getDamager() instanceof Player player ? player
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter
                : null;
        if (attacker == null || attacker == event.getEntity() || !(event.getEntity() instanceof LivingEntity)
                || event.getEntity() instanceof ArmorStand) {
            return null;
        }
        return attacker;
    }

    private boolean newStep(Player player, long now) {
        return now - lastStep.getOrDefault(player.getUniqueId(), 0L) >= STEP_MILLIS;
    }

    /** Adds the combo's damage bonus before armor and other reductions are applied. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event);
        if (attacker == null) {
            return;
        }
        finishing.remove(attacker.getUniqueId());
        int combo = of(attacker);
        double bonus = plugin.getConfig().getDouble("combo.damage-per-step", 0.03) * combo;
        if (combo + 1 >= max() && newStep(attacker, System.currentTimeMillis())) {
            bonus += plugin.getConfig().getDouble("combo.finisher-damage", 0.5);
            finishing.add(attacker.getUniqueId());
        }
        if (bonus > 0) {
            event.setDamage(event.getDamage() * (1 + bonus));
        }
    }

    /** Counts a hit that landed. Called once the damage is final. */
    void landed(Player attacker, LivingEntity target) {
        UUID id = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        int combo = of(attacker);
        if (newStep(attacker, now)) {
            combo++;
            lastStep.put(id, now);
        }
        lastHit.put(id, now);
        if (combo > best(attacker)) {
            attacker.getPersistentDataContainer().set(bestKey, PersistentDataType.INTEGER, combo);
        }
        if (finishing.remove(id) && combo >= max()) {
            finisher(attacker, target);
            combo = 0;
        }
        count.put(id, combo);
        refreshBar(attacker);
    }

    private void finisher(Player attacker, LivingEntity target) {
        attacker.getPersistentDataContainer().set(finishersKey, PersistentDataType.INTEGER, finishers(attacker) + 1);
        attacker.giveExp(plugin.getConfig().getInt("combo.finisher-xp", 10));
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() / 2, 0), 40, 0.4, 0.5, 0.4, 0.4);
        target.getWorld().spawnParticle(Particle.FLASH, target.getLocation().add(0, target.getHeight() / 2, 0), 1, 0, 0, 0, 0,
                org.bukkit.Color.fromRGB(0xFFD24A));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
        attacker.playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.35f, 1.6f);
        attacker.sendActionBar(plugin.lang().get("combo.finisher", "max", max()));
        int crescendo = Math.min(2, plugin.magicLevel(attacker.getInventory().getItemInMainHand(), "crescendo"));
        if (crescendo > 0) {
            plugin.getServer().getScheduler().runTask(plugin, () -> crescendo(attacker, target, crescendo));
        }
    }

    /**
     * MagicEnchants Crescendo: the finisher also hits monsters within 3 blocks of the target (3 damage per
     * level). Dealt under the shared raresb_no_chain tag, so no area effects chain off it.
     */
    private void crescendo(Player attacker, LivingEntity target, int level) {
        if (!attacker.isOnline() || attacker.getScoreboardTags().contains("raresb_no_chain") || !target.getWorld().equals(attacker.getWorld())) {
            return;
        }
        var center = target.getLocation().add(0, target.getHeight() / 2, 0);
        center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 6, 1.5, 0.3, 1.5, 0);
        attacker.addScoreboardTag("raresb_no_chain");
        try {
            for (LivingEntity near : center.getNearbyLivingEntities(3)) {
                if (near instanceof org.bukkit.entity.Enemy && !near.equals(target) && !near.isDead()) {
                    near.setNoDamageTicks(0);
                    near.damage(3.0 * level, attacker);
                    near.setNoDamageTicks(0);
                }
            }
        } finally {
            attacker.removeScoreboardTag("raresb_no_chain");
        }
    }

    /** With combo.reset-when-hurt, getting hurt breaks the combo (a dodged or blocked hit doesn't). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0 && count.getOrDefault(player.getUniqueId(), 0) > 0
                && plugin.getConfig().getBoolean("combo.reset-when-hurt", false)) {
            count.put(player.getUniqueId(), 0);
            refreshBar(player);
        }
    }

    /** The bar at the top of the screen: the combo, its damage bonus and the time left to keep it going. */
    void refreshBar(Player player) {
        int combo = of(player);
        BossBar bar = bars.get(player.getUniqueId());
        if (combo < 1 || !plugin.getConfig().getBoolean("combo.bar", true)) {
            if (bar != null) {
                player.hideBossBar(bar);
                bars.remove(player.getUniqueId());
            }
            return;
        }
        int bonus = (int) Math.round(100 * plugin.getConfig().getDouble("combo.damage-per-step", 0.03) * combo);
        Component name = plugin.lang().get(combo >= max() - 1 ? "combo.bar-high" : "combo.bar", "combo", combo, "bonus", bonus);
        float left = Math.max(0f, Math.min(1f, 1f - (System.currentTimeMillis() - lastHit.getOrDefault(player.getUniqueId(), 0L)) / (float) window(player)));
        if (bar == null) {
            bar = BossBar.bossBar(name, left, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(name);
            bar.progress(left);
        }
    }

    void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (bars.containsKey(player.getUniqueId())) {
                refreshBar(player);
            }
        }
    }

    void forget(Player player) {
        UUID id = player.getUniqueId();
        count.remove(id);
        lastHit.remove(id);
        lastStep.remove(id);
        finishing.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    void shutdown() {
        plugin.getServer().getOnlinePlayers().forEach(this::forget);
    }
}
