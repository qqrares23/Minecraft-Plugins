package com.raresb.magicenchants;

import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Bow and crossbow enchantments. The bow's levels are copied onto each arrow when it is shot. */
final class RangedEffects implements Listener {
    private final MagicEnchantsPlugin plugin;
    private final Map<MagicEnchant, NamespacedKey> keys = new HashMap<>();
    /** Hunter's Mark: marked entity -> (mark ends at, damage bonus). */
    private final Map<UUID, double[]> marked = new HashMap<>();

    private static final MagicEnchant[] CARRIED = {MagicEnchant.HOMING, MagicEnchant.EXPLOSIVE_TIPS, MagicEnchant.HUNTERS_MARK,
            MagicEnchant.RICOCHET, MagicEnchant.HARPOON, MagicEnchant.SCATTER_SHOT, MagicEnchant.PIERCING_BOLT};
    /** Overcharge: when each player last loaded a crossbow. */
    private final Map<UUID, Long> loadedAt = new HashMap<>();
    private final NamespacedKey overchargeKey;
    /** Set once a projectile's Explosive Tips / Scatter Shot burst has gone off (Piercing bolts hit several mobs). */
    private final NamespacedKey burstKey;
    /** Paths' tag on arrows (which weapon shot them), copied onto Barrage's extra arrows so they count for Ranger. */
    private static final NamespacedKey PATHS_WEAPON = new NamespacedKey("paths", "weapon");
    /** Area/pull effects a Ricochet bounce does not take along (each bounce would explode or pull again). */
    private static final MagicEnchant[] NOT_BOUNCED = {MagicEnchant.EXPLOSIVE_TIPS, MagicEnchant.SCATTER_SHOT, MagicEnchant.HARPOON};

    RangedEffects(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
        for (MagicEnchant enchant : CARRIED) {
            keys.put(enchant, new NamespacedKey(plugin, "arrow_" + enchant.name().toLowerCase(java.util.Locale.ROOT)));
        }
        this.overchargeKey = new NamespacedKey(plugin, "arrow_overcharge");
        this.burstKey = new NamespacedKey(plugin, "arrow_burst_done");
    }

    private int carried(Projectile projectile, MagicEnchant enchant) {
        return projectile.getPersistentDataContainer().getOrDefault(keys.get(enchant), PersistentDataType.INTEGER, 0);
    }

    private void copyLevels(ItemStack bow, Projectile projectile) {
        PersistentDataContainer data = projectile.getPersistentDataContainer();
        for (MagicEnchant enchant : CARRIED) {
            int level = plugin.level(bow, enchant);
            if (level > 0) {
                data.set(keys.get(enchant), PersistentDataType.INTEGER, level);
            }
        }
    }

    private void copyLevels(Projectile from, Projectile to) {
        for (MagicEnchant enchant : CARRIED) {
            int level = carried(from, enchant);
            if (level > 0) {
                to.getPersistentDataContainer().set(keys.get(enchant), PersistentDataType.INTEGER, level);
            }
        }
    }

    // --- Shooting ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoad(EntityLoadCrossbowEvent event) {
        if (event.getEntity() instanceof Player player) {
            loadedAt.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        loadedAt.remove(event.getPlayer().getUniqueId());
    }

    /** Piercing Bolt (more damage the more armor the target wears) and Overcharge (bolts held loaded 3 s). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoltHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile) || !(event.getEntity() instanceof LivingEntity target)
                || !plugin.enabledIn(target.getWorld())) {
            return;
        }
        int piercing = carried(projectile, MagicEnchant.PIERCING_BOLT);
        if (piercing > 0) {
            var armor = target.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
            double points = armor != null ? armor.getValue() : 0;
            event.setDamage(event.getDamage() * (1 + 0.008 * points * piercing)); // +16%/level on full diamond
        }
        int overcharge = projectile.getPersistentDataContainer().getOrDefault(overchargeKey, PersistentDataType.INTEGER, 0);
        if (overcharge > 0) {
            event.setDamage(event.getDamage() * (1 + 0.15 * overcharge));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        ItemStack bow = event.getBow();
        copyLevels(bow, projectile);
        int overcharge = plugin.level(bow, MagicEnchant.OVERCHARGE);
        if (overcharge > 0 && bow != null && bow.getType() == Material.CROSSBOW
                && System.currentTimeMillis() - loadedAt.getOrDefault(player.getUniqueId(), Long.MAX_VALUE) >= 3000) {
            projectile.getPersistentDataContainer().set(overchargeKey, PersistentDataType.INTEGER, overcharge);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getEyeLocation(), 15, 0.2, 0.2, 0.2, 0.2);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_QUICK_CHARGE_3, 1f, 0.6f);
        }
        if (carried(projectile, MagicEnchant.HOMING) > 0) {
            home(player, projectile, carried(projectile, MagicEnchant.HOMING));
        }

        // Barrage (bows): extra arrows in a small fan.
        int barrage = plugin.level(bow, MagicEnchant.BARRAGE);
        if (barrage > 0 && bow != null && bow.getType() == Material.BOW && projectile instanceof AbstractArrow original) {
            // Barrage I: one extra arrow on each side; Barrage II: two on each side.
            for (int i = 1; i <= barrage; i++) {
                for (int side : new int[] {-1, 1}) {
                    Vector velocity = original.getVelocity().clone().rotateAroundY(Math.toRadians(side * 7 * i));
                    Arrow extra = player.launchProjectile(Arrow.class, velocity);
                    extra.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
                    extra.setDamage(original.getDamage());
                    extra.setCritical(original.isCritical());
                    extra.setFireTicks(original.getFireTicks());
                    // The bow itself, so Power, Punch and other bow enchantments count for the extra arrows too.
                    extra.setWeapon(bow);
                    String pathsWeapon = original.getPersistentDataContainer().get(PATHS_WEAPON, PersistentDataType.STRING);
                    if (pathsWeapon != null) {
                        extra.getPersistentDataContainer().set(PATHS_WEAPON, PersistentDataType.STRING, pathsWeapon);
                    }
                    copyLevels(projectile, extra);
                    if (carried(extra, MagicEnchant.HOMING) > 0) {
                        home(player, extra, carried(extra, MagicEnchant.HOMING));
                    }
                }
            }
        }
    }

    /** Steers the arrow toward the nearest target in front of it. */
    private void home(Player shooter, Projectile projectile, int level) {
        double range = 8 + 6 * level;
        double turn = 0.12 * level;
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!projectile.isValid() || ++tick > 80 || (projectile instanceof AbstractArrow arrow && arrow.isInBlock())) {
                    cancel();
                    return;
                }
                Vector velocity = projectile.getVelocity();
                double speed = velocity.length();
                if (speed < 0.2) {
                    return;
                }
                Vector heading = velocity.clone().normalize();
                LivingEntity best = null;
                double bestDistance = Double.MAX_VALUE;
                for (LivingEntity candidate : projectile.getLocation().getNearbyLivingEntities(range)) {
                    if (!WeaponEffects.validSecondaryTarget(shooter, candidate, null)) {
                        continue;
                    }
                    Vector to = candidate.getLocation().add(0, candidate.getHeight() / 2, 0).toVector()
                            .subtract(projectile.getLocation().toVector());
                    double distance = to.length();
                    if (distance < bestDistance && to.normalize().dot(heading) > 0.64) { // within ~50 degrees
                        best = candidate;
                        bestDistance = distance;
                    }
                }
                if (best != null) {
                    Vector toTarget = best.getLocation().add(0, best.getHeight() / 2, 0).toVector()
                            .subtract(projectile.getLocation().toVector()).normalize();
                    projectile.setVelocity(heading.multiply(1 - turn).add(toTarget.multiply(turn)).normalize().multiply(speed));
                    if (tick % 2 == 0) {
                        projectile.getWorld().spawnParticle(Particle.DUST, projectile.getLocation(), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(0xF5B041), 0.8f));
                    }
                }
            }
        }.runTaskTimer(plugin, 2, 1);
    }

    // --- Impacts ---

    @EventHandler
    public void onImpact(ProjectileHitEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        Entity hit = event.getHitEntity();
        Location at = hit != null ? hit.getLocation().add(0, hit.getHeight() / 2, 0) : projectile.getLocation();
        World world = at.getWorld();

        int mark = carried(projectile, MagicEnchant.HUNTERS_MARK);
        if (mark > 0 && hit instanceof LivingEntity target && !target.equals(shooter)) {
            int seconds = mark == 1 ? 6 : 10;
            marked.put(target.getUniqueId(), new double[] {System.currentTimeMillis() + seconds * 1000L, mark == 1 ? 0.15 : 0.25});
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, seconds * 20, 0));
            world.spawnParticle(Particle.DUST, at, 15, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0xAF7AC5), 1.3f));
            world.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.6f);
        }

        int harpoon = carried(projectile, MagicEnchant.HARPOON);
        if (harpoon > 0 && hit instanceof LivingEntity target && !target.equals(shooter)) {
            Vector pull = shooter.getLocation().toVector().subtract(target.getLocation().toVector());
            double distance = pull.length();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    target.setVelocity(pull.normalize().multiply(Math.min(2.2, 0.4 + distance * 0.11)).setY(0.4)));
            world.playSound(at, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
        }

        int ricochet = carried(projectile, MagicEnchant.RICOCHET);
        if (ricochet > 0 && hit instanceof LivingEntity target && projectile instanceof AbstractArrow arrow) {
            ricochet(shooter, arrow, target, ricochet);
        }

        // One burst per projectile: a Piercing bolt passing through several mobs explodes only at the first.
        boolean burstDone = projectile.getPersistentDataContainer().has(burstKey);
        int explosive = burstDone ? 0 : carried(projectile, MagicEnchant.EXPLOSIVE_TIPS);
        int scatter = burstDone ? 0 : carried(projectile, MagicEnchant.SCATTER_SHOT);
        if (explosive > 0 || scatter > 0) {
            projectile.getPersistentDataContainer().set(burstKey, PersistentDataType.BYTE, (byte) 1);
        }
        if (explosive > 0) {
            world.spawnParticle(Particle.EXPLOSION, at, 1);
            world.spawnParticle(Particle.FLAME, at, 15, 0.6, 0.4, 0.6, 0.05);
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
            for (LivingEntity near : at.getNearbyLivingEntities(2.5)) {
                if (WeaponEffects.validSecondaryTarget(shooter, near, null)) {
                    plugin.bonusDamage(near, 4, shooter);
                    Vector push = near.getLocation().toVector().subtract(at.toVector()).setY(0);
                    if (push.lengthSquared() > 1.0e-4) {
                        near.setVelocity(near.getVelocity().add(push.normalize().multiply(0.5).setY(0.3)));
                    }
                }
            }
            if (hit == null) {
                projectile.remove();
            }
        }

        if (scatter > 0) {
            world.spawnParticle(Particle.CRIT, at, 30 + 10 * scatter, 1.2, 0.5, 1.2, 0.4);
            world.spawnParticle(Particle.SMALL_GUST, at, 3, 0.5, 0.3, 0.5, 0);
            world.playSound(at, Sound.BLOCK_DECORATED_POT_SHATTER, 1f, 1.2f);
            for (LivingEntity near : at.getNearbyLivingEntities(2.5)) {
                if (!near.equals(hit) && WeaponEffects.validSecondaryTarget(shooter, near, null)) {
                    plugin.bonusDamage(near, 1.5 + scatter, shooter);
                }
            }
        }
    }

    /** Fires a new bolt from the hit target to the next enemy nearby. */
    private void ricochet(Player shooter, AbstractArrow from, LivingEntity hit, int bouncesLeft) {
        LivingEntity next = hit.getLocation().getNearbyLivingEntities(8).stream()
                .filter(e -> WeaponEffects.validSecondaryTarget(shooter, e, hit))
                .min((a, b) -> Double.compare(a.getLocation().distanceSquared(hit.getLocation()), b.getLocation().distanceSquared(hit.getLocation())))
                .orElse(null);
        if (next == null) {
            return;
        }
        Location start = hit.getEyeLocation();
        Vector direction = next.getLocation().add(0, next.getHeight() / 2, 0).toVector().subtract(start.toVector()).normalize();
        double damage = from.getDamage() * 0.8;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Arrow bounce = start.getWorld().spawnArrow(start.clone().add(direction.clone().multiply(0.8)), direction, 2.2f, 0f);
            bounce.setShooter(shooter);
            bounce.setDamage(damage);
            bounce.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            copyLevels(from, bounce);
            for (MagicEnchant effect : NOT_BOUNCED) {
                bounce.getPersistentDataContainer().remove(keys.get(effect));
            }
            bounce.getPersistentDataContainer().set(keys.get(MagicEnchant.RICOCHET), PersistentDataType.INTEGER, bouncesLeft - 1);
            if (bouncesLeft - 1 <= 0) {
                bounce.getPersistentDataContainer().remove(keys.get(MagicEnchant.RICOCHET));
            }
            start.getWorld().playSound(start, Sound.ITEM_TRIDENT_HIT, 0.8f, 1.6f);
            start.getWorld().spawnParticle(Particle.ENCHANTED_HIT, start, 10, 0.2, 0.2, 0.2, 0.2);
        });
    }

    /** Hunter's Mark: marked targets take extra damage from players and their arrows. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMarkedHit(EntityDamageByEntityEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        double[] mark = marked.get(event.getEntity().getUniqueId());
        if (mark == null) {
            return;
        }
        if (mark[0] < System.currentTimeMillis()) {
            marked.remove(event.getEntity().getUniqueId());
            return;
        }
        boolean byPlayer = event.getDamager() instanceof Player
                || event.getDamager() instanceof Projectile p && p.getShooter() instanceof Player;
        if (byPlayer) {
            event.setDamage(event.getDamage() * (1 + mark[1]));
        }
    }
}
