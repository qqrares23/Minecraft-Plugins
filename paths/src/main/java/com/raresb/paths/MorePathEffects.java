package com.raresb.paths;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Tidecaller (trident), Juggernaut (mace), Sentinel (shield) and Shadow (sword). */
final class MorePathEffects implements Listener {
    private final PathsPlugin plugin;
    private final PathEffects core;
    private final NamespacedKey juggernautKnockbackKey;
    private final NamespacedKey ironSkinHealthKey;
    private final NamespacedKey ironSkinToughnessKey;

    /** Tidecaller: soaked targets, until (ms). */
    private final Map<UUID, Long> soakedUntil = new HashMap<>();
    /** Juggernaut: fall damage saved up for the next mace hit, and until when (ms). */
    private final Map<UUID, Double> storedFall = new HashMap<>();
    private final Map<UUID, Long> storedFallUntil = new HashMap<>();
    /** Sentinel: Retaliation stacks and until when (ms); Bulwark until (ms). */
    private final Map<UUID, Integer> retaliation = new HashMap<>();
    private final Map<UUID, Long> retaliationUntil = new HashMap<>();
    private final Map<UUID, Long> bulwarkUntil = new HashMap<>();
    /** Shadow: Vanish until (ms) and Elusive's critical hit until (ms). */
    private final Map<UUID, Long> vanishUntil = new HashMap<>();
    private final Map<UUID, Long> elusiveCritUntil = new HashMap<>();

    MorePathEffects(PathsPlugin plugin, PathEffects core) {
        this.plugin = plugin;
        this.core = core;
        this.juggernautKnockbackKey = new NamespacedKey(plugin, "juggernaut_knockback");
        this.ironSkinHealthKey = new NamespacedKey(plugin, "iron_skin_health");
        this.ironSkinToughnessKey = new NamespacedKey(plugin, "iron_skin_toughness");
    }

    private static boolean active(Map<UUID, Long> until, UUID id) {
        return until.getOrDefault(id, 0L) > System.currentTimeMillis();
    }

    private static boolean wet(Player player) {
        return player.isInWater() || (player.getWorld().hasStorm() && player.getLocation().getBlock().getLightFromSky() >= 15);
    }

    // ============================================================ Always-on

    void refresh(Player player) {
        int sea = plugin.has(player, Ability.SEA_BLESSED) ? core.power(player, Path.TIDECALLER) : -1;
        if (sea >= 0 && player.isInWater()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, false, false, true));
            if (sea >= 10) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 40, 0, false, false, true));
            }
        }
        int unstoppable = plugin.has(player, Ability.UNSTOPPABLE) ? core.power(player, Path.JUGGERNAUT) : -1;
        PathEffects.setModifier(player, Attribute.KNOCKBACK_RESISTANCE, juggernautKnockbackKey,
                unstoppable < 0 ? 0 : Math.min(1, (unstoppable >= 10 ? 1 : 0.5) * plugin.passivePower(player, Path.JUGGERNAUT)),
                AttributeModifier.Operation.ADD_NUMBER);
        int skin = plugin.has(player, Ability.IRON_SKIN) ? core.power(player, Path.JUGGERNAUT) : -1;
        double skinPower = plugin.passivePower(player, Path.JUGGERNAUT);
        PathEffects.setModifier(player, Attribute.MAX_HEALTH, ironSkinHealthKey, skin < 0 ? 0 : Math.round((skin >= 10 ? 6 : 4) * skinPower),
                AttributeModifier.Operation.ADD_NUMBER);
        PathEffects.setModifier(player, Attribute.ARMOR_TOUGHNESS, ironSkinToughnessKey, skin < 0 ? 0 : (1 + 0.1 * skin) * skinPower,
                AttributeModifier.Operation.ADD_NUMBER);
    }

    void removeModifiers(Player player) {
        PathEffects.setModifier(player, Attribute.KNOCKBACK_RESISTANCE, juggernautKnockbackKey, 0, AttributeModifier.Operation.ADD_NUMBER);
        PathEffects.setModifier(player, Attribute.MAX_HEALTH, ironSkinHealthKey, 0, AttributeModifier.Operation.ADD_NUMBER);
        PathEffects.setModifier(player, Attribute.ARMOR_TOUGHNESS, ironSkinToughnessKey, 0, AttributeModifier.Operation.ADD_NUMBER);
    }

    // ============================================================ Hits

    void hit(Path path, EntityDamageByEntityEvent event, PathEffects.Source source, LivingEntity target, boolean charged) {
        Player player = source.player();
        int level = core.power(player, path);
        double power = plugin.hitPower(player, path);
        World world = target.getWorld();
        Location body = target.getLocation().add(0, target.getHeight() / 2, 0);
        UUID id = player.getUniqueId();
        switch (path) {
            case TIDECALLER -> {
                if (plugin.has(player, Ability.SEA_BLESSED) && wet(player)) {
                    event.setDamage(event.getDamage() * (1 + (0.1 + 0.005 * level) * plugin.passivePower(player, path)));
                }
                if (plugin.has(player, Ability.SOAKED)) {
                    if (active(soakedUntil, target.getUniqueId())) {
                        event.setDamage(event.getDamage() * (1 + (0.1 + 0.005 * level) * (wet(player) ? 2 : 1) * power));
                    }
                    soakedUntil.put(target.getUniqueId(), System.currentTimeMillis() + 5000);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, level >= 10 ? 1 : 0));
                    world.spawnParticle(Particle.FALLING_WATER, body.clone().add(0, 0.6, 0), 12, 0.3, 0.2, 0.3, 0);
                }
                if (plugin.has(player, Ability.UNDERTOW)) {
                    Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
                    if (pull.lengthSquared() > 4) {
                        Vector velocity = pull.normalize().multiply(0.6 * power).setY(0.2);
                        plugin.getServer().getScheduler().runTask(plugin, () -> target.setVelocity(velocity));
                    }
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int) (40 * power), level >= 10 ? 1 : 0));
                    world.spawnParticle(Particle.BUBBLE_POP, body, 15, 0.3, 0.4, 0.3, 0.05);
                }
            }
            case JUGGERNAUT -> {
                if (active(storedFallUntil, id) && storedFall.containsKey(id)) {
                    event.setDamage(event.getDamage() + storedFall.remove(id) * (1 + 0.05 * level));
                    storedFallUntil.remove(id);
                    world.playSound(body, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1.2f);
                }
                if (plugin.has(player, Ability.AFTERSHOCK) && charged) {
                    double splash = event.getDamage() * (0.25 + 0.005 * level) * power;
                    double radius = level >= 10 ? 3.5 : 2.5;
                    for (LivingEntity near : core.around(player, target.getLocation(), radius)) {
                        if (!near.equals(target)) {
                            core.splash(player, near, splash);
                        }
                    }
                    Block ground = target.getLocation().subtract(0, 0.2, 0).getBlock();
                    if (!ground.getType().isAir()) {
                        world.spawnParticle(Particle.BLOCK, target.getLocation().add(0, 0.1, 0), 25, radius / 2, 0.1, radius / 2, 0,
                                ground.getBlockData());
                    }
                }
                if (plugin.has(player, Ability.SHATTERING)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
                    if (player.getFallDistance() > 1.5) {
                        PathEffects.stun(target, (int) ((level >= 10 ? 60 : 40) * power));
                        world.playSound(body, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.6f);
                    }
                }
            }
            case SENTINEL -> {
                int stacks = active(retaliationUntil, id) ? retaliation.getOrDefault(id, 0) : 0;
                if (plugin.has(player, Ability.RETALIATION) && stacks > 0) {
                    event.setDamage(event.getDamage() * (1 + 0.3 * stacks * power));
                    retaliation.remove(id);
                    retaliationUntil.remove(id);
                    world.spawnParticle(Particle.ENCHANTED_HIT, body, 10 * stacks, 0.3, 0.4, 0.3, 0.2);
                    world.playSound(body, Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
                }
            }
            case SHADOW -> {
                if (plugin.has(player, Ability.BACKSTAB)) {
                    Vector facing = target.getLocation().getDirection().setY(0);
                    Vector toAttacker = player.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
                    if (facing.lengthSquared() > 1.0e-4 && toAttacker.lengthSquared() > 1.0e-4
                            && facing.normalize().dot(toAttacker.normalize()) < -0.3) {
                        event.setDamage(event.getDamage() * (1 + (0.4 + 0.02 * level) * power));
                        world.spawnParticle(Particle.DAMAGE_INDICATOR, body, 6, 0.2, 0.3, 0.2, 0.1);
                        world.playSound(body, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
                        if (level >= 10) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                        }
                    }
                }
                if (plugin.has(player, Ability.NIGHTBLADE)) {
                    long time = world.getTime();
                    boolean dark = target.getLocation().getBlock().getLightLevel() <= 7 || (time >= 13000 && time < 23000);
                    if (dark) {
                        event.setDamage(event.getDamage() * (1 + (0.15 + 0.0075 * level) * power));
                        world.spawnParticle(Particle.SQUID_INK, body, 4, 0.2, 0.3, 0.2, 0.01);
                        if (level >= 10) {
                            PathEffects.heal(player, event.getDamage() * 0.05);
                        }
                    }
                }
                if (active(vanishUntil, id)) {
                    vanishUntil.remove(id);
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    event.setDamage(event.getDamage() * (1 + 0.8 * plugin.skillPower(player, path)));
                    world.spawnParticle(Particle.CRIT, body, 30, 0.3, 0.4, 0.3, 0.4);
                    world.playSound(body, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
                }
                if (active(elusiveCritUntil, id)) {
                    elusiveCritUntil.remove(id);
                    event.setDamage(event.getDamage() * 1.5);
                    world.spawnParticle(Particle.CRIT, body, 15, 0.3, 0.4, 0.3, 0.3);
                }
            }
            default -> {
            }
        }
    }

    /** Stormborn: thrown tridents call lightning (always in a storm, otherwise sometimes). */
    @EventHandler
    public void onTridentImpact(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident) || !(trident.getShooter() instanceof Player player)
                || !plugin.has(player, Ability.STORMBORN)) {
            return;
        }
        int level = core.power(player, Path.TIDECALLER);
        double passive = plugin.passivePower(player, Path.TIDECALLER);
        if (!player.getWorld().hasStorm() && !PathEffects.roll(0.2 * passive)) {
            return;
        }
        Location at = event.getHitEntity() != null ? event.getHitEntity().getLocation() : trident.getLocation();
        at.getWorld().strikeLightningEffect(at);
        double damage = (4 + 0.2 * level) * passive;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getHitEntity() instanceof LivingEntity target && PathEffects.validTarget(player, target)) {
                core.splash(player, target, damage);
            }
            if (level >= 10) {
                for (LivingEntity near : core.around(player, at, 2.5)) {
                    if (!near.equals(event.getHitEntity())) {
                        core.splash(player, near, damage * 0.6);
                    }
                }
            }
        });
    }

    // ============================================================ Getting hurt

    void onHurt(EntityDamageEvent event, Player player, Entity attacker) {
        UUID id = player.getUniqueId();
        if (active(bulwarkUntil, id)) {
            event.setCancelled(true);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            if (attacker instanceof LivingEntity living && !(attacker instanceof Player other && other.equals(player))
                    && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                core.splash(player, living, event.getDamage());
            }
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && plugin.has(player, Ability.UNSTOPPABLE)) {
            int level = core.power(player, Path.JUGGERNAUT);
            double cut = Math.min(0.9, (0.3 + 0.01 * level) * plugin.passivePower(player, Path.JUGGERNAUT));
            double saved = event.getDamage() * cut;
            event.setDamage(event.getDamage() - saved);
            storedFall.merge(id, saved, Double::sum);
            storedFallUntil.put(id, System.currentTimeMillis() + 5000);
        }
        // Sentinel: hits caught on the shield.
        if (attacker != null && player.isBlocking() && plugin.isActive(player, Path.SENTINEL) && inFront(player, attacker)) {
            blocked(player, attacker);
        }
        if (plugin.has(player, Ability.STALWART)) {
            int level = core.power(player, Path.SENTINEL);
            double passive = plugin.passivePower(player, Path.SENTINEL);
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.PROJECTILE || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                event.setDamage(event.getDamage() * (1 - Math.min(0.6, (0.15 + 0.0075 * level) * passive)));
            } else if (level >= 10) {
                event.setDamage(event.getDamage() * 0.9);
            }
        }
        guardian(event, player);
        if (attacker != null && plugin.has(player, Ability.ELUSIVE)) {
            int level = core.power(player, Path.SHADOW);
            if (PathEffects.roll(Math.min(0.4, (0.08 + 0.004 * level) * plugin.passivePower(player, Path.SHADOW)))) {
                event.setCancelled(true);
                player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.6f);
                if (level >= 10) {
                    elusiveCritUntil.put(id, System.currentTimeMillis() + 4000);
                }
            }
        }
    }

    private static boolean inFront(Player player, Entity attacker) {
        Entity from = attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter ? shooter : attacker;
        Vector to = from.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        return to.lengthSquared() < 1.0e-4 || player.getLocation().getDirection().setY(0).normalize().dot(to.normalize()) > 0;
    }

    private void blocked(Player player, Entity attacker) {
        int level = core.power(player, Path.SENTINEL);
        double power = plugin.hitPower(player, Path.SENTINEL);
        plugin.addXp(player, Path.SENTINEL, 1.5);
        if (plugin.has(player, Ability.RETALIATION)) {
            int max = level >= 10 ? 5 : 3;
            UUID id = player.getUniqueId();
            int stacks = Math.min(max, (active(retaliationUntil, id) ? retaliation.getOrDefault(id, 0) : 0) + 1);
            retaliation.put(id, stacks);
            retaliationUntil.put(id, System.currentTimeMillis() + 5000);
            player.sendActionBar(plugin.lang().get("retaliation", "stacks", stacks));
        }
        if (plugin.has(player, Ability.SHIELD_SPIKES) && attacker instanceof LivingEntity living && !(attacker instanceof Projectile)) {
            core.splash(player, living, (1.5 + 0.1 * level) * power);
            Vector push = living.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (push.lengthSquared() > 1.0e-4) {
                living.setVelocity(push.normalize().multiply(0.8).setY(0.3));
            }
            if (level >= 10) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
            living.getWorld().spawnParticle(Particle.CRIT, living.getEyeLocation(), 10, 0.3, 0.3, 0.3, 0.2);
        }
    }

    /** Guardian: players near a Sentinel with Guardian take less damage (the best nearby Sentinel counts). */
    private void guardian(EntityDamageEvent event, Player victim) {
        double best = 0;
        for (Player sentinel : victim.getLocation().getNearbyPlayers(10)) {
            if (sentinel.equals(victim) || !plugin.has(sentinel, Ability.GUARDIAN)) {
                continue;
            }
            int level = core.power(sentinel, Path.SENTINEL);
            double radius = level >= 10 ? 10 : 6;
            if (sentinel.getLocation().distanceSquared(victim.getLocation()) <= radius * radius) {
                best = Math.max(best, Math.min(0.3, (0.08 + 0.004 * level) * plugin.passivePower(sentinel, Path.SENTINEL)));
            }
        }
        if (best > 0) {
            event.setDamage(event.getDamage() * (1 - best));
        }
    }

    /** Vanish: mobs can't pick the vanished player as a target. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && active(vanishUntil, player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ============================================================ Kills

    void onKill(PathEffects.Source source, LivingEntity entity) {
        Player player = source.player();
        if (plugin.has(player, Ability.SHADOW_VEIL) && core.paths(source).contains(Path.SHADOW)) {
            int ticks = (int) ((core.power(player, Path.SHADOW) >= 10 ? 80 : 40) * plugin.passivePower(player, Path.SHADOW));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, false, false, true));
            player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
        }
    }

    // ============================================================ M2 skills

    boolean skill(Player player, Ability skill) {
        Path path = skill.path();
        int level = core.power(player, path);
        double power = plugin.skillPower(player, path);
        switch (skill) {
            case MAELSTROM -> maelstrom(player, level, power);
            case GEYSER -> geyser(player, level, power);
            case SEISMIC_LEAP -> seismicLeap(player, level, power);
            case GRAVITY_CRUSH -> gravityCrush(player, level, power);
            case BULWARK -> bulwark(player, level, power);
            case SHIELD_CHARGE -> shieldCharge(player, level, power);
            case VANISH -> vanish(player, level, power);
            case SMOKE_BOMB -> smokeBomb(player, level, power);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static Location aimed(Player player, int range, double fallback) {
        Block block = player.getTargetBlockExact(range);
        return block != null ? block.getLocation().add(0.5, 1, 0.5)
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(fallback));
    }

    private void maelstrom(Player player, int level, double power) {
        Location center = aimed(player, 20, 10);
        World world = center.getWorld();
        int duration = level >= 10 ? 100 : 60;
        double damage = (2 + 0.1 * level) * power;
        world.playSound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.5f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 5;
                if (!player.isOnline() || tick > duration) {
                    cancel();
                    return;
                }
                for (int i = 0; i < 12; i++) {
                    double angle = tick * 0.4 + Math.PI * 2 * i / 12;
                    double radius = 1 + (i % 4);
                    world.spawnParticle(Particle.SPLASH, center.clone().add(Math.cos(angle) * radius, 0.3 + (i % 3) * 0.5,
                            Math.sin(angle) * radius), 3, 0.1, 0.1, 0.1, 0);
                }
                world.spawnParticle(Particle.BUBBLE_COLUMN_UP, center, 10, 0.5, 0.5, 0.5, 0.1);
                for (LivingEntity victim : core.around(player, center, 6)) {
                    Vector pull = center.toVector().subtract(victim.getLocation().toVector());
                    if (pull.lengthSquared() > 1) {
                        victim.setVelocity(pull.normalize().multiply(0.45).setY(0.1));
                    }
                    if (tick % 20 == 0) {
                        core.splash(player, victim, damage);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    private void geyser(Player player, int level, double power) {
        World world = player.getWorld();
        Vector facing = player.getLocation().getDirection().setY(0).normalize();
        double damage = (3 + 0.15 * level) * power;
        for (int step = 1; step <= 6; step++) {
            Location spot = player.getLocation().add(facing.clone().multiply(step));
            world.spawnParticle(Particle.SPLASH, spot, 20, 0.6, 1.5, 0.6, 0.2);
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP, spot, 10, 0.3, 1, 0.3, 0.2);
        }
        world.playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1.5f, 0.8f);
        for (LivingEntity victim : core.around(player, player.getLocation(), 6.5)) {
            Vector to = victim.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (to.lengthSquared() > 1 && to.normalize().dot(facing) < 0.5) {
                continue;
            }
            core.splash(player, victim, damage);
            victim.setVelocity(new Vector(0, 1.2, 0));
            victim.setFireTicks(0);
            if (level >= 10) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            }
        }
    }

    private void seismicLeap(Player player, int level, double power) {
        World world = player.getWorld();
        player.setVelocity(player.getLocation().getDirection().setY(0).normalize().multiply(0.5).setY(1.3));
        core.protectFromFall(player, 6000);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.2f);
        new BukkitRunnable() {
            int tick;
            double peak = player.getLocation().getY();

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++tick > 100) {
                    cancel();
                    return;
                }
                peak = Math.max(peak, player.getLocation().getY());
                world.spawnParticle(Particle.DUST_PLUME, player.getLocation(), 3, 0.2, 0.2, 0.2, 0.01);
                if (tick > 5 && (PathEffects.onGround(player) || player.isInWater())) {
                    cancel();
                    Location center = player.getLocation();
                    double height = Math.max(0, peak - center.getY());
                    double radius = (level >= 10 ? 6 : 4) + Math.min(3, height / 3);
                    double damage = PathEffects.attackDamage(player) * (1 + 0.03 * level) * (1 + Math.min(2, height * 0.1)) * power;
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
                    world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.6f);
                    Block ground = center.clone().subtract(0, 0.2, 0).getBlock();
                    if (!ground.getType().isAir()) {
                        world.spawnParticle(Particle.BLOCK, center, 60, radius / 2, 0.2, radius / 2, 0, ground.getBlockData());
                    }
                    for (LivingEntity victim : core.around(player, center, radius)) {
                        core.splash(player, victim, damage);
                        victim.setVelocity(victim.getVelocity().setY(0.7));
                        if (level >= 10) {
                            PathEffects.stun(victim, 30);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private void gravityCrush(Player player, int level, double power) {
        World world = player.getWorld();
        double radius = level >= 10 ? 12 : 8;
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.5f);
        world.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 80, radius / 2, 1, radius / 2, 0.05);
        for (LivingEntity victim : core.around(player, player.getLocation(), radius)) {
            Vector pull = player.getLocation().toVector().subtract(victim.getLocation().toVector());
            double distance = pull.length();
            if (distance > 1) {
                victim.setVelocity(pull.normalize().multiply(Math.min(1.8, 0.3 + distance * 0.15)).setY(0.3));
            }
        }
        double damage = PathEffects.attackDamage(player) * (0.8 + 0.03 * level) * power;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location center = player.getLocation();
            world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.4f, 0.7f);
            world.spawnParticle(Particle.EXPLOSION, center, 3, 1, 0.2, 1);
            for (LivingEntity victim : core.around(player, center, 3.5)) {
                core.splash(player, victim, damage);
                victim.setVelocity(new Vector(0, -1, 0));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3));
            }
        }, 10);
    }

    private void bulwark(Player player, int level, double power) {
        long millis = (long) ((level >= 10 ? 5000 : 3000) * power);
        bulwarkUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (millis / 50), 0, false, false, true));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 0.6f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !active(bulwarkUntil, player.getUniqueId())) {
                    cancel();
                    return;
                }
                Location c = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 16; i++) {
                    double angle = Math.PI * 2 * i / 16;
                    world.spawnParticle(Particle.DUST, c.clone().add(Math.cos(angle), 0, Math.sin(angle)), 1, 0, 0.4, 0, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(0x2E86C1), 1.2f));
                }
            }
        }.runTaskTimer(plugin, 0, 4);
    }

    private void shieldCharge(Player player, int level, double power) {
        World world = player.getWorld();
        int ticks = level >= 10 ? 15 : 10;
        double damage = (3 + 0.15 * level) * (level >= 10 ? 1.3 : 1) * power;
        Set<UUID> alreadyHit = new HashSet<>();
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1f, 1.2f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++tick > ticks) {
                    cancel();
                    return;
                }
                Vector direction = player.getLocation().getDirection().setY(0).normalize();
                player.setVelocity(direction.clone().multiply(1.0).setY(Math.min(0, player.getVelocity().getY())));
                world.spawnParticle(Particle.CLOUD, player.getLocation(), 4, 0.3, 0.1, 0.3, 0.02);
                for (LivingEntity victim : core.around(player, player.getLocation().add(direction), 1.8)) {
                    if (alreadyHit.add(victim.getUniqueId())) {
                        core.splash(player, victim, damage);
                        victim.setVelocity(direction.clone().multiply(1.2).setY(0.4));
                        PathEffects.stun(victim, 30);
                        world.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.7f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void vanish(Player player, int level, double power) {
        int ticks = (int) ((level >= 10 ? 120 : 80) * power);
        vanishUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false, true));
        if (level >= 10) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, false, false, true));
        }
        for (LivingEntity near : player.getLocation().getNearbyLivingEntities(24)) {
            if (near instanceof Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 30, 0.4, 0.7, 0.4, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
    }

    private void smokeBomb(Player player, int level, double power) {
        double radius = level >= 10 ? 7 : 5;
        int ticks = (int) (60 * power);
        World world = player.getWorld();
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add(0, 1, 0), 60, radius / 2, 1, radius / 2, 0.02);
        world.spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 40, radius / 2, 1, radius / 2, 0.02);
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.2f, 0.6f);
        for (LivingEntity victim : core.around(player, player.getLocation(), radius)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1));
            if (level >= 10) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0));
            }
            if (victim instanceof Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, false, true));
    }

    // ============================================================ Cleanup

    void forget(Player player) {
        UUID id = player.getUniqueId();
        for (Map<UUID, ?> map : java.util.List.of(storedFall, storedFallUntil, retaliation, retaliationUntil, bulwarkUntil,
                vanishUntil, elusiveCritUntil)) {
            map.remove(id);
        }
        removeModifiers(player);
    }

    void shutdown() {
        soakedUntil.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathCleanup(org.bukkit.event.entity.EntityDeathEvent event) {
        soakedUntil.remove(event.getEntity().getUniqueId());
    }
}
