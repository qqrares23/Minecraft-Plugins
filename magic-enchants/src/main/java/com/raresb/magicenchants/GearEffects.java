package com.raresb.magicenchants;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
import org.bukkit.entity.Trident;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Trident, mace, spear, wolf armor, shield and elytra enchantments. */
final class GearEffects implements Listener {
    private static final long BASTION_COOLDOWN = 10_000;
    private static final long TAILWIND_COOLDOWN = 5_000;
    private static final long RALLY_COOLDOWN = 8_000;
    /** Featherfall: a swing this recent before landing counts as a (missed) smash. */
    private static final long FEATHERFALL_WINDOW = 1_000;

    private final MagicEnchantsPlugin plugin;
    /** Set on a thrown trident once its whirlpool has started (it can hit a mob and then the ground). */
    private final NamespacedKey maelstromUsed;
    private final Map<UUID, Long> bastionReady = new HashMap<>();
    private final Map<UUID, Long> tailwindReady = new HashMap<>();
    private final Map<UUID, Long> lastMaceSwing = new HashMap<>();
    private final Map<UUID, Long> rallyReady = new HashMap<>();

    GearEffects(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
        this.maelstromUsed = new NamespacedKey(plugin, "maelstrom_used");
    }

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private boolean chainable(Player player) {
        return !player.getScoreboardTags().contains(MagicEnchantsPlugin.NO_CHAIN);
    }

    // --- Tridents ---

    /** Undertow: more damage to wet targets, from a held or a thrown trident. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTridentDamage(EntityDamageByEntityEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld()) || plugin.isDealingBonusDamage()
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack trident = null;
        if (event.getDamager() instanceof Trident thrown && thrown.getShooter() instanceof Player) {
            trident = thrown.getItemStack();
        } else if (event.getDamager() instanceof Player player) {
            trident = player.getInventory().getItemInMainHand();
        }
        int undertow = plugin.level(trident, MagicEnchant.UNDERTOW);
        if (undertow > 0 && (target.isInWater() || target.isInRain())) {
            event.setDamage(event.getDamage() * (1 + 0.15 * undertow));
            target.getWorld().spawnParticle(Particle.SPLASH, target.getLocation().add(0, target.getHeight() / 2, 0), 20, 0.3, 0.4, 0.3, 0.1);
        }
    }

    /** Maelstrom and Stormcaller: where a thrown trident lands. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTridentHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident) || !(trident.getShooter() instanceof Player player)
                || !plugin.enabledIn(trident.getWorld())) {
            return;
        }
        ItemStack item = trident.getItemStack();
        LivingEntity target = event.getHitEntity() instanceof LivingEntity living && !living.equals(player) ? living : null;

        int storm = plugin.level(item, MagicEnchant.STORMCALLER);
        if (storm > 0 && target != null && trident.getWorld().hasStorm()
                && target.getLocation().getBlock().getLightFromSky() >= 14) {
            // Only the look and sound of lightning: no fire, no charged creepers.
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.3);
            plugin.bonusDamage(target, 6, player);
        }

        int maelstrom = plugin.level(item, MagicEnchant.MAELSTROM);
        if (maelstrom > 0 && chainable(player) && !trident.getPersistentDataContainer().has(maelstromUsed)) {
            trident.getPersistentDataContainer().set(maelstromUsed, PersistentDataType.BYTE, (byte) 1);
            Location center = target != null ? target.getLocation()
                    : event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0.5, 1, 0.5) : trident.getLocation();
            whirlpool(player, center, 40 + 20 * maelstrom);
        }
    }

    /** A whirlpool that pulls nearby enemies to its centre for a few seconds. */
    private void whirlpool(Player player, Location center, int ticks) {
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1f, 0.7f);
        new BukkitRunnable() {
            int age;

            @Override
            public void run() {
                if (age >= ticks || !player.isOnline()) {
                    cancel();
                    return;
                }
                double spin = age * 0.5;
                for (int i = 0; i < 3; i++) {
                    double angle = spin + i * Math.PI * 2 / 3;
                    double radius = 3 - (age % 20) * 0.12;
                    world.spawnParticle(Particle.SPLASH, center.clone().add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius), 4, 0.1, 0.1, 0.1, 0);
                }
                world.spawnParticle(Particle.BUBBLE_POP, center.clone().add(0, 0.3, 0), 4, 0.6, 0.2, 0.6, 0.02);
                for (LivingEntity near : center.getNearbyLivingEntities(4.5)) {
                    if (!WeaponEffects.validSecondaryTarget(player, near, null)) {
                        continue;
                    }
                    Vector pull = center.toVector().subtract(near.getLocation().toVector()).setY(0);
                    if (pull.lengthSquared() > 0.5) {
                        near.setVelocity(near.getVelocity().multiply(0.5).add(pull.normalize().multiply(0.28)));
                    }
                }
                if (age % 20 == 0) {
                    world.playSound(center, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.6f, 1f);
                }
                age += 4;
            }
        }.runTaskTimer(plugin, 0, 4);
    }

    // --- Maces ---

    /** Aftershock and Gravity Well: on a smash attack (a mace hit while falling). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmash(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)
                || plugin.isDealingBonusDamage() || !plugin.enabledIn(target.getWorld())) {
            return;
        }
        ItemStack mace = player.getInventory().getItemInMainHand();
        if (mace.getType() != Material.MACE || player.getFallDistance() <= 1.5 || !chainable(player)) {
            return;
        }
        Location center = target.getLocation();

        int well = plugin.level(mace, MagicEnchant.GRAVITY_WELL);
        if (well > 0) {
            for (LivingEntity near : center.getNearbyLivingEntities(4 + well)) {
                if (WeaponEffects.validSecondaryTarget(player, near, target)) {
                    Vector pull = center.toVector().subtract(near.getLocation().toVector()).setY(0);
                    if (pull.lengthSquared() > 0.5) {
                        near.setVelocity(pull.normalize().multiply(0.7).setY(0.2));
                    }
                }
            }
            center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 0.5, 0), 60, 2, 0.3, 2, 0.2);
            center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.7f);
        }

        if (plugin.level(mace, MagicEnchant.SHOCKWAVE) > 0) {
            // Shockwave: shields near the impact are knocked out for 3 seconds.
            for (Player near : center.getNearbyPlayers(3.5)) {
                if (!near.equals(player) && (near.getInventory().getItemInOffHand().getType() == Material.SHIELD
                        || near.getInventory().getItemInMainHand().getType() == Material.SHIELD)) {
                    near.setCooldown(Material.SHIELD, 60);
                    near.clearActiveItem();
                    near.getWorld().playSound(near.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.8f);
                }
            }
        }

        int aftershock = plugin.level(mace, MagicEnchant.AFTERSHOCK);
        if (aftershock > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || !player.getWorld().equals(center.getWorld())) {
                    return;
                }
                center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.3, 0), 3, 1.2, 0.1, 1.2, 0);
                center.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, 0.2, 0), 30, 2, 0.1, 2, 0.05);
                center.getWorld().playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.6f);
                for (LivingEntity near : center.getNearbyLivingEntities(3.5)) {
                    if (WeaponEffects.validSecondaryTarget(player, near, null)) {
                        plugin.bonusDamage(near, 2 + 2 * aftershock, player);
                        near.setVelocity(near.getVelocity().setY(0.45));
                    }
                }
            }, 20);
        }
    }

    /** Featherfall: remembers swings with the mace (a missed smash is a swing before landing). */
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() == Material.MACE && player.getFallDistance() > 1.5) {
            lastMaceSwing.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** Featherfall halves fall damage with the mace in hand (none after a swing just before landing); Cushion softens wall crashes. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.enabledIn(player.getWorld())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && plugin.level(player.getInventory().getItemInMainHand(), MagicEnchant.FEATHERFALL) > 0) {
            Long swing = lastMaceSwing.remove(player.getUniqueId());
            if (swing != null && System.currentTimeMillis() - swing <= FEATHERFALL_WINDOW) {
                event.setCancelled(true);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 12, 0.4, 0.1, 0.4, 0.02);
            } else {
                event.setDamage(event.getDamage() * 0.5);
            }
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FLY_INTO_WALL) {
            int cushion = plugin.level(player.getInventory().getChestplate(), MagicEnchant.CUSHION);
            if (cushion > 0) {
                event.setDamage(event.getDamage() * (1 - 0.25 * cushion));
            }
        }
    }

    // --- Spears ---

    /** Rally: a charged spear hit gives nearby players Speed (8 s cooldown). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRally(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity)
                || plugin.isDealingBonusDamage() || !plugin.enabledIn(player.getWorld()) || player.getAttackCooldown() < 0.9f) {
            return;
        }
        int rally = plugin.level(player.getInventory().getItemInMainHand(), MagicEnchant.RALLY);
        long now = System.currentTimeMillis();
        if (rally <= 0 || rallyReady.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        rallyReady.put(player.getUniqueId(), now + RALLY_COOLDOWN);
        for (Player ally : player.getLocation().getNearbyPlayers(6)) {
            ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40 + 40 * rally, 0));
            ally.getWorld().spawnParticle(Particle.WAX_OFF, ally.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.5);
        }
        player.getWorld().playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.4f, 1.6f);
    }

    // --- Wolves ---

    private int packleader(Entity entity) {
        if (!(entity instanceof Wolf wolf) || !wolf.isTamed() || wolf.getEquipment() == null) {
            return 0;
        }
        return plugin.level(wolf.getEquipment().getItem(EquipmentSlot.BODY), MagicEnchant.PACKLEADER);
    }

    /** Packleader: a wolf wearing it hits harder. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWolfHit(EntityDamageByEntityEvent event) {
        int level = packleader(event.getDamager());
        if (level > 0 && plugin.enabledIn(event.getDamager().getWorld())) {
            event.setDamage(event.getDamage() * (1 + 0.15 * level));
        }
    }

    /** Packleader: the wolf heals 2 hearts per level when it kills. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWolfKill(EntityDeathEvent event) {
        Entity killer = event.getDamageSource().getCausingEntity();
        int level = packleader(killer);
        if (level > 0 && killer instanceof Wolf wolf && !wolf.isDead()) {
            wolf.setHealth(Math.min(WeaponEffects.maxHealth(wolf), wolf.getHealth() + 4 * level));
            wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, 1, 0), level);
        }
    }

    // --- Shields ---

    /** Bastion, Spiked and Reflection: when a player blocks an attack with a shield. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlocked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isBlocking() || !plugin.enabledIn(player.getWorld())) {
            return;
        }
        ItemStack shield = player.getActiveItem();
        if (shield.getType() != Material.SHIELD) {
            return;
        }
        Entity damager = event.getDamager();
        LivingEntity attacker = damager instanceof LivingEntity living ? living
                : damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter ? shooter : null;
        // A shield only covers what is in front of the player.
        Vector toDamager = damager.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        Vector facing = player.getLocation().getDirection().setY(0);
        if (toDamager.lengthSquared() < 1e-4 || facing.lengthSquared() < 1e-4 || toDamager.normalize().dot(facing.normalize()) <= 0) {
            return;
        }

        int bastion = plugin.level(shield, MagicEnchant.BASTION);
        long now = System.currentTimeMillis();
        if (bastion > 0 && bastionReady.getOrDefault(player.getUniqueId(), 0L) <= now) {
            bastionReady.put(player.getUniqueId(), now + BASTION_COOLDOWN);
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, bastion - 1));
            player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
        }

        int spiked = plugin.level(shield, MagicEnchant.SPIKED);
        // Only real melee hits (not skill/bonus damage from range, see Thorns of Frost).
        if (spiked > 0 && damager instanceof LivingEntity melee && !melee.equals(player)
                && !melee.getScoreboardTags().contains(MagicEnchantsPlugin.NO_CHAIN)
                && melee.getLocation().distanceSquared(player.getLocation()) <= 36) {
            plugin.bonusDamage(melee, 1 + spiked, player);
            melee.getWorld().spawnParticle(Particle.CRIT, melee.getLocation().add(0, melee.getHeight() / 2, 0), 10, 0.3, 0.3, 0.3, 0.1);
        }

        int reflection = plugin.level(shield, MagicEnchant.REFLECTION);
        if (reflection > 0 && damager instanceof AbstractArrow arrow && attacker != null && !attacker.equals(player)
                && roll(0.15 * reflection)) {
            Location eye = player.getEyeLocation();
            Vector aim = attacker.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
            Arrow back = player.launchProjectile(Arrow.class, aim.multiply(2.2));
            back.setDamage(arrow.getDamage());
            back.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.remove();
            player.getWorld().playSound(eye, Sound.ITEM_SHIELD_BLOCK, 1f, 1.6f);
            player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, eye.add(aim), 10, 0.2, 0.2, 0.2, 0.1);
        }
    }

    // --- Elytra ---

    /** Tailwind: a push forward when the player starts gliding. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || !(event.getEntity() instanceof Player player) || !plugin.enabledIn(player.getWorld())) {
            return;
        }
        int tailwind = plugin.level(player.getInventory().getChestplate(), MagicEnchant.TAILWIND);
        long now = System.currentTimeMillis();
        if (tailwind <= 0 || tailwindReady.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        tailwindReady.put(player.getUniqueId(), now + TAILWIND_COOLDOWN);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isGliding()) {
                player.setVelocity(player.getLocation().getDirection().multiply(0.9 + 0.4 * tailwind));
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.3, 0.3, 0.3, 0.05);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 1.3f);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        bastionReady.remove(id);
        tailwindReady.remove(id);
        lastMaceSwing.remove(id);
        rallyReady.remove(id);
    }
}
