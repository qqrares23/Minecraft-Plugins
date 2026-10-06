package com.raresb.magicenchants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Armor enchantments. */
final class ArmorEffects implements Listener {
    private static final long SECOND_WIND_COOLDOWN = 120_000;

    private final MagicEnchantsPlugin plugin;
    private final Map<UUID, Long> secondWindReady = new HashMap<>();

    ArmorEffects(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickWornEffects, 20, 20);
    }

    /** Spring Heels and Night Vision: refresh their effects while the armor is worn. */
    private void tickWornEffects() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.enabledIn(player.getWorld())) {
                continue;
            }
            int heels = plugin.level(player.getInventory().getBoots(), MagicEnchant.SPRING_HEELS);
            if (heels > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, heels - 1, true, false, true));
            }
            if (plugin.level(player.getInventory().getHelmet(), MagicEnchant.NIGHT_VISION) > 0) {
                PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
                // Keep it above 10 seconds, when vanilla starts flickering the screen.
                if (current == null || current.getDuration() < 260) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, true));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player) {
            int heels = plugin.level(player.getInventory().getBoots(), MagicEnchant.SPRING_HEELS);
            if (heels > 0) {
                event.setDamage(event.getDamage() * (1 - 0.25 * heels));
            }
        }
    }

    /** Evasion: a chance to dodge an attack completely. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttacked(EntityDamageByEntityEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        // Vanguard (spear enchantment): spear in hand and a shield in the off hand take less melee damage.
        if (victim instanceof Player holder && !(event.getDamager() instanceof org.bukkit.entity.Projectile)
                && holder.getInventory().getItemInOffHand().getType() == org.bukkit.Material.SHIELD) {
            int vanguard = plugin.level(holder.getInventory().getItemInMainHand(), MagicEnchant.VANGUARD);
            if (vanguard > 0) {
                event.setDamage(event.getDamage() * (1 - 0.05 * vanguard));
            }
        }
        int evasion = plugin.armorLevel(victim, MagicEnchant.EVASION);
        if (evasion > 0 && ThreadLocalRandom.current().nextDouble() < 0.01 + 0.03 * evasion) {
            event.setCancelled(true);
            Location at = victim.getLocation().add(0, 1, 0);
            victim.getWorld().spawnParticle(Particle.CLOUD, at, 12, 0.3, 0.5, 0.3, 0.05);
            victim.getWorld().playSound(at, Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.6f);
            if (victim instanceof Player player) {
                player.sendActionBar(plugin.lang().get("evaded"));
            }
            return;
        }

        // Thorns of Frost: melee attackers get slowed and frozen. Mobs can spawn wearing it too, so it must ignore
        // skill and bonus damage (tagged NO_CHAIN, e.g. a Javelin from far away) and anyone out of melee reach.
        int frost = plugin.armorLevel(victim, MagicEnchant.THORNS_OF_FROST);
        if (frost > 0 && !(event.getDamager() instanceof Projectile) && event.getDamager() instanceof LivingEntity attacker
                && !attacker.getScoreboardTags().contains(MagicEnchantsPlugin.NO_CHAIN)
                && attacker.getWorld().equals(victim.getWorld()) && attacker.getLocation().distanceSquared(victim.getLocation()) <= 36
                && ThreadLocalRandom.current().nextDouble() < 0.25 * frost) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, frost - 1));
            attacker.setFreezeTicks(Math.max(attacker.getFreezeTicks(), 100 + 40 * frost));
            attacker.getWorld().spawnParticle(Particle.SNOWFLAKE, attacker.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1f, 1.2f);
        }
    }

    /** Second Wind: dropping below 20% health gives a burst of healing, on a long cooldown. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)
                || plugin.level(player.getInventory().getChestplate(), MagicEnchant.SECOND_WIND) <= 0) {
            return;
        }
        double after = player.getHealth() - event.getFinalDamage();
        double max = WeaponEffects.maxHealth(player);
        long now = System.currentTimeMillis();
        if (after <= 0 || after >= max * 0.2 || secondWindReady.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        secondWindReady.put(player.getUniqueId(), now + SECOND_WIND_COOLDOWN);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1));
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 1.4f);
        player.sendActionBar(plugin.lang().get("second-wind"));
    }
}
