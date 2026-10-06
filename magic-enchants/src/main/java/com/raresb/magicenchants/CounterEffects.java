package com.raresb.magicenchants;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 1.7.0 enchantments that answer other plugins' systems: Afterimage (WeaponSkills dodge roll),
 * Anchor and Purifier (MightyMobs Blinker / Regenerating / Vampire) and Crimson Ward (Blood Moon).
 */
final class CounterEffects implements Listener {
    /** Written by WeaponSkills when the player dodge-rolls (ms). */
    private static final NamespacedKey DODGED_AT = new NamespacedKey("weaponskills", "dodged_at");
    /** Read by MightyMobs: no Blinker teleport / no Regenerating or Vampire healing until (ms). */
    static final NamespacedKey ANCHORED_UNTIL = new NamespacedKey("magicenchants", "anchored_until");
    static final NamespacedKey PURIFIED_UNTIL = new NamespacedKey("magicenchants", "purified_until");

    private final MagicEnchantsPlugin plugin;

    CounterEffects(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    /** The player behind a hit (melee or a projectile they shot), or null. */
    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        return event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player player = attacker(event);
        if (player == null || !(event.getEntity() instanceof LivingEntity target) || !plugin.enabledIn(player.getWorld())
                || plugin.isDealingBonusDamage()) {
            return;
        }
        long now = System.currentTimeMillis();
        // Afterimage: the first hit within 2 s after a dodge roll.
        int afterimage = plugin.level(player.getInventory().getBoots(), MagicEnchant.AFTERIMAGE);
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (afterimage > 0 && data.getOrDefault(DODGED_AT, PersistentDataType.LONG, 0L) + 2000 >= now) {
            data.remove(DODGED_AT);
            event.setDamage(event.getDamage() * (1 + 0.15 * afterimage));
            target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, target.getHeight() / 2, 0), 10, 0.3, 0.4, 0.3, 0.05);
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (plugin.level(weapon, MagicEnchant.ANCHOR) > 0) {
            target.getPersistentDataContainer().set(ANCHORED_UNTIL, PersistentDataType.LONG, now + 3000);
            target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.getLocation().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3, 0.02);
        }
        if (plugin.level(weapon, MagicEnchant.PURIFIER) > 0) {
            target.getPersistentDataContainer().set(PURIFIED_UNTIL, PersistentDataType.LONG, now + 4000);
            target.getWorld().spawnParticle(Particle.WAX_OFF, target.getLocation().add(0, target.getHeight() / 2, 0), 6, 0.3, 0.3, 0.3, 0);
        }
    }

    /** Crimson Ward: less damage taken during a Blood Moon. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.enabledIn(player.getWorld()) || !MagicEffects.bloodMoon(player.getWorld())) {
            return;
        }
        int level = plugin.armorLevel(player, MagicEnchant.CRIMSON_WARD);
        if (level > 0) {
            event.setDamage(event.getDamage() * (1 - 0.08 * level));
        }
    }
}
