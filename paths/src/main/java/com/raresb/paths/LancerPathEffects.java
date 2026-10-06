package com.raresb.paths;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Lancer (spear): piercing hits, reach, mounted combat, a charging dash and a war banner. */
final class LancerPathEffects {
    private final PathsPlugin plugin;
    private final PathEffects core;
    private final NamespacedKey reachKey;
    private final NamespacedKey cavalierSpeedKey;
    /** War Banner stands still up, removed on disable. */
    private final Set<ArmorStand> banners = new HashSet<>();

    LancerPathEffects(PathsPlugin plugin, PathEffects core) {
        this.plugin = plugin;
        this.core = core;
        this.reachKey = new NamespacedKey(plugin, "lancer_reach");
        this.cavalierSpeedKey = new NamespacedKey(plugin, "lancer_cavalier_speed");
    }

    private static boolean holdsSpear(Player player) {
        return Path.Weapon.of(player.getInventory().getItemInMainHand().getType()) == Path.Weapon.SPEAR;
    }

    private static Vector facing(Player player) {
        double yaw = Math.toRadians(player.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    // ============================================================ Always-on

    void refresh(Player player) {
        boolean spear = holdsSpear(player);
        int reach = spear && plugin.has(player, Ability.LONG_REACH) ? core.power(player, Path.LANCER) : -1;
        double reachBonus = reach < 0 ? 0 : (0.5 + 0.02 * reach + (reach >= 15 ? 0.5 : 0)) * plugin.passivePower(player, Path.LANCER);
        PathEffects.setModifier(player, Attribute.ENTITY_INTERACTION_RANGE, reachKey, reachBonus, AttributeModifier.Operation.ADD_NUMBER);
        boolean cavalier = spear && plugin.has(player, Ability.CAVALIER);
        PathEffects.setModifier(player, Attribute.MOVEMENT_SPEED, cavalierSpeedKey,
                cavalier ? 0.08 * plugin.passivePower(player, Path.LANCER) : 0, AttributeModifier.Operation.ADD_SCALAR);
    }

    void removeModifiers(Player player) {
        PathEffects.setModifier(player, Attribute.ENTITY_INTERACTION_RANGE, reachKey, 0, AttributeModifier.Operation.ADD_NUMBER);
        PathEffects.setModifier(player, Attribute.MOVEMENT_SPEED, cavalierSpeedKey, 0, AttributeModifier.Operation.ADD_SCALAR);
    }

    // ============================================================ Hits

    void hit(EntityDamageByEntityEvent event, Player player, LivingEntity target, boolean charged) {
        int level = core.power(player, Path.LANCER);
        double power = plugin.hitPower(player, Path.LANCER);
        if (plugin.has(player, Ability.SKEWER) && charged) {
            // The enemy right behind the target, along the line from the player.
            Vector line = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (line.lengthSquared() > 0.01) {
                line.normalize();
                double share = (level >= 10 ? 0.6 : 0.4) * power;
                for (LivingEntity behind : core.around(player, target.getLocation().add(line.clone().multiply(1.5)), 1.6)) {
                    if (!behind.equals(target)) {
                        core.splash(player, behind, event.getDamage() * share);
                        behind.getWorld().spawnParticle(Particle.CRIT, behind.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.1);
                        break;
                    }
                }
            }
        }
        if (plugin.has(player, Ability.MOMENTUM_STRIKE) && (player.isSprinting() || player.isInsideVehicle())) {
            event.setDamage(event.getDamage() * (1 + (0.25 + 0.01 * level) * power));
            target.setVelocity(target.getVelocity().add(facing(player).multiply(0.8)).setY(0.3));
            if (level >= 15) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_SPEAR_HIT, 1f, 0.8f);
        }
    }

    /** Cavalier: less damage taken while mounted with a spear in hand. */
    void onHurt(EntityDamageEvent event, Player player) {
        if (plugin.has(player, Ability.CAVALIER) && player.isInsideVehicle() && holdsSpear(player)) {
            int level = core.power(player, Path.LANCER);
            event.setDamage(event.getDamage() * (1 - (level >= 10 ? 0.25 : 0.15) * plugin.passivePower(player, Path.LANCER)));
        }
    }

    // ============================================================ M2

    boolean skill(Player player, Ability skill) {
        return switch (skill) {
            case CHARGE_LINE -> {
                chargeLine(player);
                yield true;
            }
            case WAR_BANNER -> {
                warBanner(player);
                yield true;
            }
            default -> false;
        };
    }

    private void chargeLine(Player player) {
        int level = core.power(player, Path.LANCER);
        double skillPower = plugin.skillPower(player, Path.LANCER);
        double damage = (4 + 0.2 * level) * skillPower;
        int ticks = level >= 10 ? 10 : 7;
        Vector direction = facing(player);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_SPEAR_LUNGE_2, 1.2f, 1f);
        core.protectFromFall(player, 2000);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++tick > ticks) {
                    cancel();
                    return;
                }
                player.setVelocity(direction.clone().multiply(1.15).setY(Math.min(0, player.getVelocity().getY())));
                Location front = player.getLocation().add(direction.clone().multiply(1.2)).add(0, 1, 0);
                world.spawnParticle(Particle.CLOUD, player.getLocation(), 2, 0.2, 0.1, 0.2, 0);
                world.spawnParticle(Particle.CRIT, front, 3, 0.2, 0.2, 0.2, 0.05);
                for (LivingEntity target : core.around(player, front, 1.6)) {
                    if (hit.add(target.getUniqueId())) {
                        core.splash(player, target, damage);
                        target.setVelocity(direction.clone().multiply(0.6).setY(0.3));
                        if (level >= 10) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
        player.swingMainHand();
    }

    private void warBanner(Player player) {
        int level = core.power(player, Path.LANCER);
        int seconds = (int) Math.round(8 * plugin.skillPower(player, Path.LANCER));
        Location at = player.getLocation();
        World world = player.getWorld();
        ArmorStand banner = world.spawn(at, ArmorStand.class, stand -> {
            stand.setPersistent(false);
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.getEquipment().setHelmet(new ItemStack(Material.RED_BANNER));
        });
        banners.add(banner);
        world.playSound(at, Sound.EVENT_RAID_HORN, 0.6f, 1.4f);
        world.playSound(at, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
        new BukkitRunnable() {
            int second;

            @Override
            public void run() {
                if (!banner.isValid() || ++second > seconds) {
                    cancel();
                    banner.remove();
                    banners.remove(banner);
                    return;
                }
                Location center = banner.getLocation();
                world.spawnParticle(Particle.DUST, center.clone().add(0, 0.2, 0), 20, 3, 0.1, 3, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(0xC0392B), 1.2f));
                for (Entity entity : world.getNearbyEntities(center, 6, 3, 6)) {
                    if (entity instanceof Player ally && !ally.isDead()) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30, 0, false, false, true));
                        if (level >= 10) {
                            ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, false, false, true));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    void shutdown() {
        banners.forEach(Entity::remove);
        banners.clear();
        plugin.getServer().getOnlinePlayers().forEach(this::removeModifiers);
    }
}
