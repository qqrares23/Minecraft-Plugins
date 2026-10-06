package com.raresb.magicenchants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Mounts, pets and shears (1.7.0): Steed, Trample, Loyal Guard, Shearer's Bounty. */
final class CompanionEffects implements Listener {
    private final MagicEnchantsPlugin plugin;
    private final NamespacedKey steedKey;
    /** Trample: mob -> next time it can be trampled (ms). */
    private final Map<UUID, Long> trampled = new HashMap<>();

    CompanionEffects(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
        this.steedKey = new NamespacedKey(plugin, "steed");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickMounts, 5, 5);
    }

    private int bodyLevel(LivingEntity entity, MagicEnchant enchant) {
        return entity.getEquipment() == null ? 0 : plugin.level(entity.getEquipment().getItem(EquipmentSlot.BODY), enchant);
    }

    // --- Steed and Trample: horses being ridden ---

    private void tickMounts() {
        long now = System.currentTimeMillis();
        trampled.values().removeIf(until -> until < now);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!(player.getVehicle() instanceof AbstractHorse horse) || !plugin.enabledIn(player.getWorld())) {
                continue;
            }
            int steed = bodyLevel(horse, MagicEnchant.STEED);
            AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                AttributeModifier current = speed.getModifier(steedKey);
                double amount = 0.1 * steed;
                if (current == null || current.getAmount() != amount) {
                    if (current != null) {
                        speed.removeModifier(steedKey);
                    }
                    if (amount > 0) {
                        speed.addTransientModifier(new AttributeModifier(steedKey, amount, AttributeModifier.Operation.ADD_SCALAR));
                    }
                }
            }
            int trample = bodyLevel(horse, MagicEnchant.TRAMPLE);
            Vector motion = horse.getVelocity().setY(0);
            if (trample <= 0 || motion.lengthSquared() < 0.04) {
                continue;
            }
            Vector ahead = motion.clone().normalize();
            for (LivingEntity mob : horse.getLocation().add(ahead.clone().multiply(1.2)).getNearbyLivingEntities(1.6)) {
                if (!(mob instanceof Enemy) || trampled.containsKey(mob.getUniqueId())) {
                    continue;
                }
                trampled.put(mob.getUniqueId(), now + 1000);
                plugin.bonusDamage(mob, 2.0 * trample, player);
                mob.setVelocity(ahead.clone().multiply(0.9).setY(0.35));
                mob.getWorld().spawnParticle(Particle.BLOCK, mob.getLocation(), 12, 0.3, 0.1, 0.3, 0,
                        mob.getLocation().subtract(0, 0.5, 0).getBlock().getBlockData());
                mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1f, 0.8f);
            }
        }
    }

    // --- Loyal Guard: the owner's wolf takes part of the damage ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOwnerHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.enabledIn(player.getWorld())) {
            return;
        }
        switch (event.getCause()) {
            case VOID, KILL, SUICIDE, WORLD_BORDER, STARVATION, DROWNING, FALL -> {
                return;
            }
            default -> {
            }
        }
        Wolf guard = null;
        int best = 0;
        for (LivingEntity near : player.getLocation().getNearbyLivingEntities(8)) {
            if (near instanceof Wolf wolf && wolf.isTamed() && player.equals(wolf.getOwner()) && !wolf.isDead()) {
                int level = bodyLevel(wolf, MagicEnchant.LOYAL_GUARD);
                if (level > best) {
                    guard = wolf;
                    best = level;
                }
            }
        }
        if (guard == null) {
            return;
        }
        double share = event.getDamage() * 0.15 * best;
        if (share <= 0 || guard.getHealth() <= share + 2) {
            return; // never let the guard die for its owner
        }
        event.setDamage(event.getDamage() - share);
        guard.setHealth(guard.getHealth() - share);
        guard.playHurtAnimation(0);
        guard.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, guard.getLocation().add(0, 0.8, 0), 3, 0.2, 0.2, 0.2, 0);
    }

    // --- Shearer's Bounty ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (!(event.getEntity() instanceof Sheep) || !plugin.enabledIn(event.getPlayer().getWorld())) {
            return;
        }
        int level = plugin.level(event.getItem(), MagicEnchant.SHEARERS_BOUNTY);
        List<ItemStack> drops = new java.util.ArrayList<>(event.getDrops());
        if (level <= 0 || drops.isEmpty() || ThreadLocalRandom.current().nextDouble() >= 0.33 * level) {
            return;
        }
        drops.add(drops.getFirst().clone().asQuantity(1));
        event.setDrops(drops);
    }
}
