package com.raresb.magicenchants;

import java.util.Iterator;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Enchantments that build on the other plugins: Mightslayer (MightyMobs), Blood Moon's Gift (the Blood Moon)
 * and Soulbound. Arcane Echo and Cooldown Siphon are read by WeaponSkills, Pathbound by Paths, Scholar by
 * Professions and Combo Keeper by Hud (each looks the enchantment up by its key).
 */
final class MagicEffects implements Listener {
    /** MightyMobs marks its mighty mobs and special spawns with these. */
    private static final NamespacedKey MIGHTY_ABILITIES = new NamespacedKey("mightymobs", "abilities");
    private static final NamespacedKey SPECIAL_SPAWN = new NamespacedKey("mightymobs", "special");
    /** Set on a world by MightyMobs while a Blood Moon runs. */
    private static final NamespacedKey BLOOD_MOON = new NamespacedKey("mightymobs", "blood_moon_active");

    private final MagicEnchantsPlugin plugin;

    MagicEffects(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    /** The weapon behind a hit: the held item, the thrown trident, or the bow/crossbow that shot the arrow. */
    static ItemStack weapon(Entity damager) {
        if (damager instanceof Player player) {
            return player.getInventory().getItemInMainHand();
        }
        if (damager instanceof Trident trident) {
            return trident.getItemStack();
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            ItemStack main = shooter.getInventory().getItemInMainHand();
            return main.getType() == Material.BOW || main.getType() == Material.CROSSBOW ? main : shooter.getInventory().getItemInOffHand();
        }
        return null;
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
    }

    static boolean bloodMoon(World world) {
        return world.getPersistentDataContainer().has(BLOOD_MOON, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (plugin.isDealingBonusDamage() || !(event.getEntity() instanceof LivingEntity target)
                || attacker(event.getDamager()) == null || !plugin.enabledIn(target.getWorld())) {
            return;
        }
        ItemStack weapon = weapon(event.getDamager());

        int slayer = plugin.level(weapon, MagicEnchant.MIGHTSLAYER);
        var data = target.getPersistentDataContainer();
        if (slayer > 0 && (data.has(MIGHTY_ABILITIES) || data.has(SPECIAL_SPAWN))) {
            event.setDamage(event.getDamage() * (1 + 0.1 * slayer));
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() / 2, 0), 8,
                    0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0xCB4335), 1.3f));
        }

        int gift = plugin.level(weapon, MagicEnchant.BLOOD_MOONS_GIFT);
        if (gift > 0 && bloodMoon(target.getWorld())) {
            event.setDamage(event.getDamage() * (1 + 0.12 * gift));
            target.getWorld().spawnParticle(Particle.CRIMSON_SPORE, target.getLocation().add(0, target.getHeight() / 2, 0), 10, 0.3, 0.4, 0.3, 0);
        }
    }

    /** Blood Moon's Gift: kills during a Blood Moon heal the killer. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.isDead() || event.getEntity() instanceof Player || !plugin.enabledIn(killer.getWorld())
                || !bloodMoon(killer.getWorld())) {
            return;
        }
        int gift = plugin.level(killer.getInventory().getItemInMainHand(), MagicEnchant.BLOOD_MOONS_GIFT);
        if (gift > 0) {
            killer.setHealth(Math.min(WeaponEffects.maxHealth(killer), killer.getHealth() + 2 * gift));
            killer.getWorld().spawnParticle(Particle.HEART, killer.getLocation().add(0, killer.getHeight() + 0.3, 0), gift);
        }
    }

    /** Soulbound: those items stay with the player through death. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory() || !plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        boolean kept = false;
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack item = drops.next();
            if (plugin.level(item, MagicEnchant.SOULBOUND) > 0) {
                drops.remove();
                event.getItemsToKeep().add(item);
                kept = true;
            }
        }
        if (kept) {
            Player player = event.getEntity();
            player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.03);
            player.getWorld().playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.8f);
        }
    }
}
