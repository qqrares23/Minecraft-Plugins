package com.raresb.mightymobs;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerNameEntityEvent;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

final class MobListener implements Listener {
    private final MightyMobsPlugin plugin;
    private final MightyMobs mobs;

    MobListener(MightyMobsPlugin plugin, MightyMobs mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
    }

    // --- Spawning ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (!canBeMighty(entity) || mobs.isSummoned(entity) || !plugin.spawnReasons().contains(event.getSpawnReason())) {
            return;
        }
        if (!plugin.worldAllowed(entity.getWorld()) || plugin.ignored(entity.getType())) {
            return;
        }
        World world = entity.getWorld();
        if (ThreadLocalRandom.current().nextDouble() >= plugin.bloodMoon().mightyChance(world)) {
            return;
        }
        mobs.makeMighty(entity, mobs.rollAbilities(plugin.bloodMoon().doubleAbilityChance(world)));
    }

    /** Hostile mobs only; bosses and the warden are strong enough already. */
    static boolean canBeMighty(Entity entity) {
        return canBeMighty(entity.getClass());
    }

    static boolean canBeMighty(Class<?> type) {
        return Enemy.class.isAssignableFrom(type)
                && LivingEntity.class.isAssignableFrom(type)
                && !Boss.class.isAssignableFrom(type)
                && !Warden.class.isAssignableFrom(type);
    }

    // Keep the set of loaded mighty mobs in sync as chunks load and unload.

    @EventHandler
    public void onAddToWorld(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living) || !MightyMobs.showsHealth(living)) {
            return;
        }
        if (mobs.isMighty(living)) {
            mobs.track(living);
        }
        // Covers freshly spawned mobs and mobs loaded with their chunk.
        refreshNextTick(living);
    }

    @EventHandler
    public void onRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        mobs.untrack(event.getEntity().getUniqueId());
    }

    // --- Health display ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        refreshNextTick(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHealed(EntityRegainHealthEvent event) {
        refreshNextTick(event.getEntity());
    }

    /** A player renamed the mob with a name tag: keep that name in front of the health. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNameTag(PlayerNameEntityEvent event) {
        if (MightyMobs.showsHealth(event.getEntity())) {
            mobs.setBaseName(event.getEntity(), event.getName());
            refreshNextTick(event.getEntity());
        }
    }

    /** Health changes after the event, so update the name on the next tick. */
    private void refreshNextTick(Entity entity) {
        if (entity instanceof LivingEntity living && MightyMobs.showsHealth(living)) {
            entity.getScheduler().run(plugin, task -> mobs.updateName(living), null);
        }
    }

    // --- Attacking abilities ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        LivingEntity attacker = attacker(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Set<Ability> abilities = mobs.abilities(attacker);
        if (buffed(attacker)) {
            // The world plays on Normal; mighty mobs and special spawns keep hitting like on Hard.
            event.setDamage(event.getDamage() * plugin.getConfig().getDouble("buffed-damage-multiplier", 1.5));
        }
        if (!abilities.isEmpty()) {
            if (abilities.contains(Ability.BERSERKER)) {
                event.setDamage(event.getDamage() * 1.8);
            }
            if (abilities.contains(Ability.VENOMOUS)) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
            }
            if (abilities.contains(Ability.FROST)) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), 160));
            }
            if (abilities.contains(Ability.VAMPIRE) && !Counters.purified(attacker)) {
                double healed = Math.min(mobs.maxHealth(attacker), attacker.getHealth() + event.getFinalDamage() * 0.5);
                attacker.setHealth(healed);
                attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, attacker.getHeight() + 0.3, 0), 2);
                mobs.updateName(attacker);
            }
        }

        onHitByPlayer(event, victim, attacker);
    }

    /** Mighty mobs, special spawns (and the mobs that come with them) and necromancer minions. */
    private boolean buffed(LivingEntity mob) {
        if (mob instanceof Player) {
            return false;
        }
        var data = mob.getPersistentDataContainer();
        return !mobs.abilities(mob).isEmpty() || mobs.isSummoned(mob)
                || data.has(new NamespacedKey(plugin, "special"), PersistentDataType.STRING)
                || data.has(new NamespacedKey(plugin, "buffed"), PersistentDataType.BYTE);
    }

    private void onHitByPlayer(EntityDamageByEntityEvent event, LivingEntity victim, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) {
            return;
        }
        Set<Ability> abilities = mobs.abilities(victim);
        if (abilities.contains(Ability.BLINKER) && ThreadLocalRandom.current().nextDouble() < 0.25 && !Counters.anchored(victim, player)) {
            blinkBehind(victim, player);
        }
        if (abilities.contains(Ability.NECROMANCER)
                && victim.getHealth() - event.getFinalDamage() < mobs.maxHealth(victim) / 2
                && victim.getHealth() - event.getFinalDamage() > 0
                && mobs.firstTime(victim, "necromancer")) {
            summonMinions(victim);
        }
    }

    /** The mob behind a hit: the entity itself, or whoever shot the projectile. */
    private static LivingEntity attacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }

    /** Teleports the mob 2 blocks behind the player, if there is room. */
    private void blinkBehind(LivingEntity mob, Player player) {
        Vector back = player.getLocation().getDirection().setY(0);
        if (back.lengthSquared() < 1.0e-4) {
            return;
        }
        Location target = player.getLocation().subtract(back.normalize().multiply(2));
        target.setY(player.getLocation().getY());
        Block feet = target.getBlock();
        if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable() || feet.getRelative(0, -1, 0).isPassable()) {
            return;
        }
        target.setDirection(player.getLocation().toVector().subtract(target.toVector()));
        World world = mob.getWorld();
        world.spawnParticle(Particle.PORTAL, mob.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3);
        mob.teleportAsync(target).thenRun(() -> {
            world.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            world.spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3);
        });
    }

    private void summonMinions(LivingEntity necromancer) {
        World world = necromancer.getWorld();
        world.playSound(necromancer.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1f);
        for (int i = 0; i < 2; i++) {
            Location spot = necromancer.getLocation().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2));
            if (!spot.getBlock().isPassable()) {
                spot = necromancer.getLocation();
            }
            world.spawnParticle(Particle.SOUL, spot.clone().add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.02);
            world.spawn(spot, Zombie.class, CreatureSpawnEvent.SpawnReason.CUSTOM, zombie -> {
                mobs.markSummoned(zombie);
                zombie.setShouldBurnInDay(false);
                zombie.setRemoveWhenFarAway(true);
                if (necromancer instanceof org.bukkit.entity.Mob mob && mob.getTarget() != null) {
                    zombie.setTarget(mob.getTarget());
                }
            });
        }
    }

    // --- Death ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Set<Ability> abilities = mobs.abilities(entity);
        if (abilities.isEmpty()) {
            return;
        }
        if (abilities.contains(Ability.EXPLOSIVE)) {
            deathBlast(entity);
        }
        mobs.untrack(entity.getUniqueId());
    }

    /**
     * Tougher mobs drop more XP: hostile mobs with more health or armor than a zombie (endermen,
     * blazes, ravagers, armored brutes...), mighty mobs (more with two abilities) and special spawns.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeathXp(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Enemy) || event.getDroppedExp() <= 0) {
            return;
        }
        double multiplier = toughness(entity);
        Set<Ability> abilities = mobs.abilities(entity);
        if (!abilities.isEmpty()) {
            multiplier *= plugin.getConfig().getDouble("xp-multiplier", 3.0);
            if (abilities.size() > 1) {
                multiplier *= plugin.getConfig().getDouble("xp.second-ability-multiplier", 1.5);
            }
        }
        if (entity.getPersistentDataContainer().has(new NamespacedKey(plugin, "special"), PersistentDataType.STRING)) {
            multiplier *= plugin.getConfig().getDouble("xp.special-spawn-multiplier", 2.0);
        }
        if (multiplier > 1) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * multiplier));
        }
    }

    /**
     * 1.0 for a zombie-like mob, more for every health point above 20 and every armor point, up to
     * xp.max-toughness-multiplier. Uses the mob's own base health, not what mighty mobs add.
     */
    private double toughness(LivingEntity entity) {
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        AttributeInstance armor = entity.getAttribute(Attribute.ARMOR);
        double extraHealth = Math.max(0, (health != null ? health.getBaseValue() : 20) - 20);
        double armorPoints = armor != null ? armor.getValue() : 0;
        double multiplier = 1 + extraHealth * plugin.getConfig().getDouble("xp.per-extra-health", 0.025)
                + armorPoints * plugin.getConfig().getDouble("xp.per-armor-point", 0.03);
        return Math.min(multiplier, plugin.getConfig().getDouble("xp.max-toughness-multiplier", 3.0));
    }

    /**
     * A blast that hurts and knocks back nearby players. It is not a real explosion,
     * so it never breaks blocks or destroys the mob's own drops.
     */
    private void deathBlast(LivingEntity entity) {
        Location center = entity.getLocation().add(0, 0.5, 0);
        World world = center.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        double radius = 3.5;
        for (Player player : center.getNearbyPlayers(radius)) {
            double distance = player.getLocation().distance(center);
            double strength = Math.max(0, 1 - distance / radius);
            player.damage(8 * strength, entity);
            Vector push = player.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() > 1.0e-4) {
                player.setVelocity(player.getVelocity().add(push.normalize().multiply(0.9 * strength).setY(0.4 * strength)));
            }
        }
    }
}
