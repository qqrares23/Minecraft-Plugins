package com.raresb.paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Transformation;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Beastmaster (pets), Pyromancer and Frost Warden. The two magic paths cast a bolt with a
 * left-click of their staff (Blaze Rod / Breeze Rod) and a spell with sneak + right-click; bolts
 * never set blocks on fire. Staff enchantments from MagicEnchants (Spellweaver, Wildfire, Inferno
 * Core, Deep Freeze, Brittle) are read here by key.
 */
final class MagicPathEffects implements Listener {
    private static final long BOLT_COOLDOWN = 400;
    /** Bolt speed in blocks per tick: what a vanilla small fireball settles at (both staffs fly the same). */
    private static final double BOLT_SPEED = 1.9;
    /** Bolts vanish after this many ticks (~114 blocks). */
    private static final int BOLT_LIFETIME = 60;
    /** Bolt auto-aim: enemies up to this far, and within this cone (cosine; ~14°) of where the player looks. */
    private static final double AIM_RANGE = 28;
    private static final double AIM_COS = Math.cos(Math.toRadians(14));

    private final PathsPlugin plugin;
    private final PathEffects core;
    /** Marks magic bolts with the staff that cast them (BLAZE_ROD / BREEZE_ROD). */
    private final NamespacedKey boltKey;
    /** Marks a Pyromancer's Fireball. */
    private final NamespacedKey fireballKey;
    /** Marks wolves summoned by Call of the Wild. */
    private final NamespacedKey summonedKey;
    private final NamespacedKey bondKey;

    private final Map<UUID, Long> lastBolt = new HashMap<>();
    private final Map<UUID, Integer> boltHits = new HashMap<>();
    /** Beastmaster: target -> {owner, marked until}; target -> last time an owner's pet hit it. */
    private final Map<UUID, UUID> markedBy = new HashMap<>();
    private final Map<UUID, Long> markedUntil = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> petHitAt = new HashMap<>();
    /** Frost Warden: target -> {Chill stacks, until ms}. */
    private final Map<UUID, double[]> chill = new HashMap<>();
    private final List<Wolf> summoned = new ArrayList<>();
    /** Kindled Soul: player -> {Heat stacks, until ms}. */
    private final Map<UUID, double[]> heat = new HashMap<>();
    /** Phoenix Ward / Ice Block running until (ms). */
    private final Map<UUID, Long> wardUntil = new HashMap<>();
    private final Map<UUID, Long> iceBlockUntil = new HashMap<>();
    /** Ice Block shells, removed when the spell ends or the plugin stops. */
    private final List<BlockDisplay> shells = new ArrayList<>();
    private long ticks;

    MagicPathEffects(PathsPlugin plugin, PathEffects core) {
        this.plugin = plugin;
        this.core = core;
        this.boltKey = new NamespacedKey(plugin, "bolt");
        this.fireballKey = new NamespacedKey(plugin, "fireball");
        this.summonedKey = new NamespacedKey(plugin, "summoned");
        this.bondKey = new NamespacedKey(plugin, "alpha_bond");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::frostWalk, 4, 4);
    }

    NamespacedKey boltKey() {
        return boltKey;
    }

    /** The player's tamed animals within {@code radius}. */
    private static List<Tameable> pets(Player player, double radius) {
        List<Tameable> pets = new ArrayList<>();
        for (LivingEntity entity : player.getLocation().getNearbyLivingEntities(radius)) {
            if (entity instanceof Tameable pet && pet.isTamed() && player.equals(pet.getOwner())) {
                pets.add(pet);
            }
        }
        return pets;
    }

    /** Level of a MagicEnchants enchantment on the held item, capped at {@code max}. */
    private int staffLevel(Player player, String id, int max) {
        return Math.min(max, plugin.magicLevel(player.getInventory().getItemInMainHand(), id));
    }

    /** Inferno Core (Blaze Rod): Pyromancer M2 and spell damage +10% per level. */
    private double infernoCore(Player player) {
        return 1 + 0.1 * staffLevel(player, "inferno_core", 3);
    }

    /** Kindled Soul Heat stacks (0 once they ran out). */
    private int heat(Player player) {
        double[] stacks = heat.get(player.getUniqueId());
        return stacks == null || stacks[1] < System.currentTimeMillis() ? 0 : (int) stacks[0];
    }

    /** Where an area skill lands: the block looked at within 24, or 12 blocks ahead. */
    private static Location aimSpot(Player player) {
        Block aimed = player.getTargetBlockExact(24);
        return aimed != null ? aimed.getLocation().add(0.5, 1, 0.5)
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(12));
    }

    /** Horizontal facing from the yaw (safe at ±90° pitch). */
    private static Vector flat(Player player) {
        double yaw = Math.toRadians(player.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    private static boolean slowed(LivingEntity target) {
        return target.hasPotionEffect(PotionEffectType.SLOWNESS) || target.getFreezeTicks() > 0;
    }

    // ============================================================ Always-on (every second)

    void refresh(Player player) {
        ticks++;
        if (plugin.has(player, Ability.FLAMEWARD)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, false, false, true));
            if (core.power(player, Path.PYROMANCER) >= 10 && player.getWorld().getEnvironment() == World.Environment.NETHER) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, true));
            }
        }
        if (plugin.has(player, Ability.WINTERS_GRACE) && core.power(player, Path.FROST_WARDEN) >= 10) {
            String below = player.getLocation().subtract(0, 0.2, 0).getBlock().getType().name();
            if (below.contains("ICE") || below.contains("SNOW")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, true));
            }
        }
        if (plugin.has(player, Ability.PERMAFROST)) {
            double radius = core.power(player, Path.FROST_WARDEN) >= 10 ? 6 : 4;
            for (LivingEntity near : core.around(player, player.getLocation(), radius)) {
                if (near instanceof Enemy) {
                    near.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, true));
                    near.getWorld().spawnParticle(Particle.SNOWFLAKE, near.getLocation().add(0, 0.2, 0), 3, 0.3, 0.1, 0.3, 0);
                }
            }
        }
        if (!plugin.isActive(player, Path.BEASTMASTER) || ticks % 5 != 0) {
            return;
        }
        int level = core.power(player, Path.BEASTMASTER);
        double passive = plugin.passivePower(player, Path.BEASTMASTER);
        boolean bond = plugin.has(player, Ability.ALPHA_BOND);
        for (Tameable pet : pets(player, 24)) {
            AttributeInstance health = pet.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                double amount = bond ? (0.2 + 0.01 * level) * passive : 0;
                AttributeModifier current = health.getModifier(bondKey);
                if (current == null || Math.abs(current.getAmount() - amount) > 1.0e-6) {
                    if (current != null) {
                        health.removeModifier(bondKey);
                    }
                    if (amount > 0) {
                        health.addTransientModifier(new AttributeModifier(bondKey, amount, AttributeModifier.Operation.ADD_SCALAR));
                    }
                }
            }
            if (bond) {
                pet.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 0, false, false, true));
            }
        }
        if (plugin.has(player, Ability.PACK_LEADER)) {
            List<Tameable> close = pets(player, 12);
            if (close.size() >= 2) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0, false, false, true));
                close.forEach(pet -> pet.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0, false, false, true)));
                if (level >= 10 && close.size() >= 3) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 0, false, false, true));
                }
            }
        }
    }

    /** Winter's Grace: water freezes under a Frost Warden's feet (like Frost Walker). */
    private void frostWalk() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.has(player, Ability.WINTERS_GRACE) || player.isInWater() || player.isFlying()) {
                continue;
            }
            int radius = core.power(player, Path.FROST_WARDEN) >= 10 ? 3 : 2;
            Block feet = player.getLocation().subtract(0, 1, 0).getBlock();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius) {
                        continue;
                    }
                    Block block = feet.getRelative(x, 0, z);
                    if (block.getType() == Material.WATER && block.getBlockData() instanceof Levelled water && water.getLevel() == 0
                            && block.getRelative(0, 1, 0).getType().isAir()) {
                        block.setType(Material.FROSTED_ICE);
                    }
                }
            }
        }
    }

    // ============================================================ Bolts

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK)) {
            return;
        }
        castBolt(event.getPlayer(), null);
    }

    /**
     * Casts the staff's bolt if the player holds the staff of an active magic path. Returns false otherwise.
     * {@code struck} is the mob the player clicked in melee (the staff fires at it instead), or null.
     */
    private boolean castBolt(Player player, LivingEntity struck) {
        Path.Weapon weapon = Path.Weapon.of(player.getInventory().getItemInMainHand().getType());
        Path path = plugin.activeFor(player, weapon);
        if (path == null || !path.magic()) {
            return false;
        }
        long now = System.currentTimeMillis();
        double cooldown = BOLT_COOLDOWN / (1 + 0.1 * staffLevel(player, "spellweaver", 3)); // MagicEnchants Spellweaver
        if (path == Path.PYROMANCER && plugin.has(player, Ability.KINDLED_SOUL) && core.power(player, path) >= 10 && heat(player) >= 5) {
            cooldown *= 0.7;
        }
        if (lastBolt.getOrDefault(player.getUniqueId(), 0L) + (long) cooldown > now) {
            return true;
        }
        lastBolt.put(player.getUniqueId(), now);
        LivingEntity aimed = struck != null && PathEffects.validTarget(player, struck) ? struck : aimTarget(player);
        Vector direction = aimed != null
                ? aimPoint(aimed).toVector().subtract(player.getEyeLocation().toVector()).normalize()
                : player.getEyeLocation().getDirection().normalize();
        Projectile bolt;
        if (path == Path.PYROMANCER) {
            SmallFireball fireball = player.launchProjectile(SmallFireball.class, direction.clone().multiply(BOLT_SPEED));
            fireball.setIsIncendiary(false);
            fireball.setAcceleration(new Vector()); // fly() keeps the speed
            bolt = fireball;
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.7f, 1.4f);
        } else {
            bolt = player.launchProjectile(Snowball.class, direction.clone().multiply(BOLT_SPEED));
            bolt.setGravity(false);
            core.trail(bolt, Particle.SNOWFLAKE, 30);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.7f, 1.6f);
        }
        bolt.getPersistentDataContainer().set(boltKey, PersistentDataType.STRING, weapon.name());
        fly(bolt, player, path, aimed);
        player.swingMainHand();
        return true;
    }

    /**
     * Bolt auto-aim: the hostile mob (or a mob attacking the player) closest to the crosshair,
     * within {@link #AIM_RANGE} blocks and the aim cone, in plain sight. Players are never auto-aimed.
     */
    private LivingEntity aimTarget(Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity entity : eye.getNearbyLivingEntities(AIM_RANGE)) {
            if (entity instanceof Player || !PathEffects.validTarget(player, entity)
                    || !(entity instanceof Enemy || (entity instanceof org.bukkit.entity.Mob mob && player.equals(mob.getTarget())))) {
                continue;
            }
            Vector to = aimPoint(entity).toVector().subtract(eye.toVector());
            double distance = to.length();
            if (distance < 0.5 || distance > AIM_RANGE) {
                continue;
            }
            double dot = to.multiply(1 / distance).dot(look);
            // Closest to the crosshair wins; a little bias toward nearer targets.
            double score = dot - distance * 0.001;
            if (dot >= AIM_COS && score > bestScore && player.hasLineOfSight(entity)) {
                best = entity;
                bestScore = score;
            }
        }
        return best;
    }

    /** Where bolts aim on a target: the middle of its body. */
    private static Location aimPoint(LivingEntity target) {
        return target.getLocation().add(0, target.getHeight() * 0.55, 0);
    }

    /**
     * Flies a bolt: a constant {@link #BOLT_SPEED} (snowballs used to slow down and drop, fireballs
     * sped up), steering toward its auto-aim target for the first 2 s. Every tick it also checks for
     * a mob overlapping the bolt: vanilla misses a mob the bolt starts inside (point-blank, e.g. a
     * staff click on an adjacent mob), so such bolts used to pass through without damage.
     */
    private void fly(Projectile bolt, Player player, Path path, LivingEntity target) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!bolt.isValid()) {
                    cancel();
                    return;
                }
                if (++tick > BOLT_LIFETIME || !player.isOnline()) {
                    bolt.remove();
                    cancel();
                    return;
                }
                LivingEntity inside = overlapping(bolt, player);
                if (inside != null) {
                    boltImpact(player, bolt, path, inside, inside.getLocation().add(0, 0.5, 0));
                    cancel();
                    return;
                }
                Vector heading = bolt.getVelocity().lengthSquared() > 1.0e-6 ? bolt.getVelocity().normalize() : player.getEyeLocation().getDirection();
                if (target != null && tick <= 40 && target.isValid() && !target.isDead() && target.getWorld().equals(bolt.getWorld())) {
                    Vector want = aimPoint(target).toVector().subtract(bolt.getLocation().toVector()).normalize();
                    heading = heading.multiply(0.5).add(want.multiply(0.5)).normalize();
                }
                bolt.setVelocity(heading.multiply(BOLT_SPEED));
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /** A valid mob whose hitbox (slightly enlarged, like vanilla's) contains the bolt, or null. */
    private static LivingEntity overlapping(Projectile bolt, Player player) {
        Vector at = bolt.getBoundingBox().getCenter();
        for (LivingEntity entity : bolt.getLocation().getNearbyLivingEntities(3)) {
            if (!entity.equals(player) && PathEffects.validTarget(player, entity) && entity.getBoundingBox().clone().expand(0.3).contains(at)) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Bolts and Fireballs: handle the hit ourselves (no vanilla fire or explosion) and deal the
     * damage with the projectile as the direct source, so the hit counts as a bolt hit.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }
        boolean fireball = projectile.getPersistentDataContainer().has(fireballKey, PersistentDataType.BYTE);
        String bolt = projectile.getPersistentDataContainer().get(boltKey, PersistentDataType.STRING);
        if (!fireball && bolt == null) {
            return;
        }
        event.setCancelled(true);
        Location at = event.getHitEntity() != null ? event.getHitEntity().getLocation().add(0, 0.5, 0) : projectile.getLocation();
        if (fireball) {
            projectile.remove();
            fireballBlast(player, at);
            return;
        }
        Path path = Path.Weapon.valueOf(bolt) == Path.Weapon.BLAZE_ROD ? Path.PYROMANCER : Path.FROST_WARDEN;
        boltImpact(player, projectile, path, event.getHitEntity() instanceof LivingEntity target ? target : null, at);
    }

    /** A bolt lands: particles, the bolt's damage to {@code target} (if any) with the bolt as the direct source, bolt removed. */
    private void boltImpact(Player player, Projectile projectile, Path path, LivingEntity target, Location at) {
        if (!projectile.isValid()) {
            return; // already landed this tick
        }
        at.getWorld().spawnParticle(path == Path.PYROMANCER ? Particle.FLAME : Particle.SNOWFLAKE, at, 10, 0.2, 0.2, 0.2, 0.05);
        if (target != null && PathEffects.validTarget(player, target)) {
            double damage = 3 + 0.15 * core.power(player, path);
            target.setNoDamageTicks(0);
            target.damage(damage, DamageSource.builder(DamageType.THROWN).withDirectEntity(projectile).withCausingEntity(player).build());
            target.setNoDamageTicks(0);
        }
        projectile.remove();
    }

    /** Small fireballs set what they hit on fire; only Ignite should. */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (event.getCombuster() instanceof Projectile projectile && projectile.getPersistentDataContainer().has(boltKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    // ============================================================ Hits

    /** Beastmaster marks and Predator: any hit by the player, with anything. */
    void onAnyHit(EntityDamageByEntityEvent event, PathEffects.Source source, LivingEntity target) {
        Player player = source.player();
        if (source.kind() == PathEffects.Kind.PET || !plugin.isActive(player, Path.BEASTMASTER)) {
            return;
        }
        int level = core.power(player, Path.BEASTMASTER);
        double power = plugin.hitPower(player, Path.BEASTMASTER);
        if (plugin.has(player, Ability.PACK_MARK)) {
            markedBy.put(target.getUniqueId(), player.getUniqueId());
            markedUntil.put(target.getUniqueId(), System.currentTimeMillis() + 5000);
            for (Tameable pet : pets(player, 24)) {
                if (pet instanceof Wolf wolf && !wolf.isSitting() && !target.equals(wolf.getTarget())) {
                    wolf.setTarget(target);
                }
            }
            if (level >= 10) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            }
        }
        if (plugin.has(player, Ability.PREDATOR)) {
            long last = petHitAt.getOrDefault(target.getUniqueId(), Map.of()).getOrDefault(player.getUniqueId(), 0L);
            if (last + 5000 > System.currentTimeMillis()) {
                event.setDamage(event.getDamage() * (1 + (0.1 + 0.005 * level) * power));
            }
        }
    }

    void hit(Path path, EntityDamageByEntityEvent event, PathEffects.Source source, LivingEntity target, boolean charged) {
        Player player = source.player();
        int level = core.power(player, path);
        double power = plugin.hitPower(player, path);
        World world = target.getWorld();
        Location body = target.getLocation().add(0, target.getHeight() / 2, 0);
        switch (path) {
            case BEASTMASTER -> {
                UUID owner = markedBy.get(target.getUniqueId());
                if (plugin.has(player, Ability.PACK_MARK) && player.getUniqueId().equals(owner)
                        && markedUntil.getOrDefault(target.getUniqueId(), 0L) > System.currentTimeMillis()) {
                    event.setDamage(event.getDamage() * (1 + (0.2 + 0.01 * level) * power));
                }
                if (plugin.has(player, Ability.ALPHA_BOND) && level >= 10) {
                    event.setDamage(event.getDamage() * 1.2);
                }
                petHitAt.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>()).put(player.getUniqueId(), System.currentTimeMillis());
            }
            case PYROMANCER, FROST_WARDEN -> {
                if (source.kind() == PathEffects.Kind.MELEE) {
                    // Hitting with the staff casts a bolt instead.
                    event.setCancelled(true);
                    castBolt(player, target);
                    return;
                }
                if (path == Path.PYROMANCER) {
                    pyromancerHit(event, player, target, level, power, world, body);
                } else {
                    frostHit(event, player, source.damager(), target, level, power, world, body);
                }
            }
            default -> {
            }
        }
    }

    private void pyromancerHit(EntityDamageByEntityEvent event, Player player, LivingEntity target, int level, double power,
                               World world, Location body) {
        if (plugin.has(player, Ability.KINDLED_SOUL)) {
            int stacks = heat(player);
            if (stacks > 0) {
                event.setDamage(event.getDamage() * (1 + 0.05 * stacks * plugin.passivePower(player, Path.PYROMANCER)));
            }
        }
        if (plugin.has(player, Ability.CINDER_CHAIN) && target.getFireTicks() > 0) {
            double jump = event.getDamage() * 0.5 * power;
            int jumps = level >= 10 ? 2 : 1;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                List<LivingEntity> near = core.around(player, target.getLocation(), 4);
                near.remove(target);
                near.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(target.getLocation())));
                for (LivingEntity next : near.subList(0, Math.min(jumps, near.size()))) {
                    Location from = body.clone();
                    Vector step = next.getLocation().add(0, next.getHeight() / 2, 0).toVector().subtract(from.toVector());
                    int points = (int) Math.ceil(step.length() * 3);
                    for (int i = 0; i <= points; i++) {
                        world.spawnParticle(Particle.FLAME, from.clone().add(step.clone().multiply((double) i / Math.max(1, points))), 1, 0, 0, 0, 0);
                    }
                    core.splash(player, next, jump);
                    next.setFireTicks(Math.max(next.getFireTicks(), 40));
                }
            });
        }
        if (plugin.has(player, Ability.IGNITE)) {
            if (target.getFireTicks() > 0) {
                event.setDamage(event.getDamage() * (1 + (0.1 + 0.005 * level) * power));
            }
            target.setFireTicks(Math.max(target.getFireTicks(), (int) (60 * power)));
            if (level >= 10) {
                for (LivingEntity near : core.around(player, target.getLocation(), 3)) {
                    if (!near.equals(target) && near.getFireTicks() <= 0) {
                        near.setFireTicks(60);
                        world.spawnParticle(Particle.FLAME, near.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.02);
                        break;
                    }
                }
            }
        }
        if (plugin.has(player, Ability.COMBUSTION)) {
            int hits = boltHits.merge(player.getUniqueId(), 1, Integer::sum);
            if (hits >= (level >= 10 ? 3 : 4)) {
                boltHits.remove(player.getUniqueId());
                world.spawnParticle(Particle.EXPLOSION, body, 1);
                world.spawnParticle(Particle.FLAME, body, 25, 1, 0.5, 1, 0.05);
                world.playSound(body, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                double blast = (4 + 0.2 * level) * power;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (LivingEntity near : core.around(player, body, 2.5)) {
                        core.splash(player, near, blast);
                    }
                });
            }
        }
    }

    private void frostHit(EntityDamageByEntityEvent event, Player player, Entity bolt, LivingEntity target, int level, double power,
                          World world, Location body) {
        int brittle = staffLevel(player, "brittle", 3); // MagicEnchants Brittle (Breeze Rod)
        if (brittle > 0 && slowed(target)) {
            event.setDamage(event.getDamage() * (1 + 0.06 * brittle));
        }
        if (plugin.has(player, Ability.PERMAFROST) && level >= 10 && target.getWorld().equals(player.getWorld())
                && target.getLocation().distanceSquared(player.getLocation()) <= 36) {
            event.setDamage(event.getDamage() * (1 + 0.1 * plugin.passivePower(player, Path.FROST_WARDEN)));
        }
        if (plugin.has(player, Ability.ICICLE_PIERCE)) {
            if (level >= 10 && target.getFreezeTicks() > 0) {
                event.setDamage(event.getDamage() + 4);
            }
            Vector direction = bolt.getVelocity().lengthSquared() > 1.0e-4 ? bolt.getVelocity().normalize() : player.getEyeLocation().getDirection();
            double pierce = event.getDamage() * 0.6 * power;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                LivingEntity behind = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity near : core.around(player, target.getLocation(), 6)) {
                    Vector to = near.getLocation().toVector().subtract(target.getLocation().toVector());
                    double distance = to.lengthSquared();
                    if (!near.equals(target) && distance > 0.01 && to.normalize().dot(direction) > 0.6 && distance < best) {
                        behind = near;
                        best = distance;
                    }
                }
                if (behind != null) {
                    Vector step = behind.getLocation().toVector().subtract(target.getLocation().toVector());
                    for (double d = 0; d <= 1; d += 0.15) {
                        world.spawnParticle(Particle.SNOWFLAKE, body.clone().add(step.clone().multiply(d)), 1, 0, 0, 0, 0);
                    }
                    core.splash(player, behind, pierce);
                }
            });
        }
        if (plugin.has(player, Ability.SHATTER_SHOT) && slowed(target)) {
            event.setDamage(event.getDamage() * (1 + (0.25 + 0.01 * level) * power));
            world.spawnParticle(Particle.BLOCK, body, 12, 0.3, 0.3, 0.3, 0, Material.ICE.createBlockData());
            if (level >= 10) {
                double splash = event.getDamage() * 0.3;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (LivingEntity near : core.around(player, target.getLocation(), 2.5)) {
                        if (!near.equals(target)) {
                            core.splash(player, near, splash);
                        }
                    }
                });
            }
        }
        if (plugin.has(player, Ability.CHILL)) {
            double[] stacks = chill.getOrDefault(target.getUniqueId(), new double[] {0, 0});
            if (stacks[1] < System.currentTimeMillis()) {
                stacks[0] = 0;
            }
            stacks[0]++;
            stacks[1] = System.currentTimeMillis() + 4000;
            if (stacks[0] >= 3) {
                chill.remove(target.getUniqueId());
                freeze(player, target, (int) ((level >= 10 ? 60 : 40) * power));
                if (level >= 10) {
                    event.setDamage(event.getDamage() + 3);
                }
            } else {
                chill.put(target.getUniqueId(), stacks);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, (int) stacks[0] - 1));
            }
        }
    }

    /** Freezes a target; MagicEnchants Deep Freeze on the caster's staff makes it last +20% per level. */
    private void freeze(Player caster, LivingEntity target, int ticks) {
        ticks = (int) (ticks * (1 + 0.2 * staffLevel(caster, "deep_freeze", 3)));
        PathEffects.root(target, ticks);
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), 140 + ticks));
        target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0,
                Material.ICE.createBlockData());
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f);
    }

    // ============================================================ Getting hurt

    void onHurt(EntityDamageEvent event, Player player, Entity attacker) {
        if (iceBlockUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()
                && event.getCause() != EntityDamageEvent.DamageCause.VOID && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE && plugin.has(player, Ability.WINTERS_GRACE)) {
            event.setCancelled(true);
            return;
        }
        if (!(attacker instanceof LivingEntity living) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        // Only real melee hits: skill/splash/enchantment damage (tagged NO_CHAIN) also counts as an attack,
        // even from a thrown Javelin far away, and must not burn or freeze its caster.
        if (living.getScoreboardTags().contains(PathEffects.NO_CHAIN)
                || !living.getWorld().equals(player.getWorld()) || living.getLocation().distanceSquared(player.getLocation()) > 36) {
            return;
        }
        if (wardUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) {
            living.setFireTicks(Math.max(living.getFireTicks(), 80));
        }
        if (plugin.has(player, Ability.EMBER_AURA)) {
            double passive = plugin.passivePower(player, Path.PYROMANCER);
            living.setFireTicks(Math.max(living.getFireTicks(), (int) (60 * passive)));
            if (core.power(player, Path.PYROMANCER) >= 10) {
                plugin.getServer().getScheduler().runTask(plugin, () -> core.splash(player, living, 2 * passive));
            }
        }
        if (plugin.has(player, Ability.FROST_ARMOR)) {
            int level = core.power(player, Path.FROST_WARDEN);
            double passive = plugin.passivePower(player, Path.FROST_WARDEN);
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            plugin.getServer().getScheduler().runTask(plugin, () -> core.splash(player, living, (1 + 0.05 * level) * passive));
            if (level >= 10) {
                freeze(player, living, 20);
            }
        }
    }

    // ============================================================ Kills

    void onKill(PathEffects.Source source, LivingEntity entity) {
        UUID id = entity.getUniqueId();
        markedBy.remove(id);
        markedUntil.remove(id);
        petHitAt.remove(id);
        chill.remove(id);
        Player player = source.player();
        if (source.weapon() == Path.Weapon.BLAZE_ROD && plugin.isActive(player, Path.PYROMANCER)) {
            if (plugin.has(player, Ability.KINDLED_SOUL)) {
                double[] stacks = heat.computeIfAbsent(player.getUniqueId(), k -> new double[] {0, 0});
                stacks[0] = Math.min(5, (stacks[1] < System.currentTimeMillis() ? 0 : stacks[0]) + 1);
                stacks[1] = System.currentTimeMillis() + 8000;
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 6 * (int) stacks[0], 0.3, 0.5, 0.3, 0.02);
            }
            int wildfire = staffLevel(player, "wildfire", 3); // MagicEnchants Wildfire (Blaze Rod)
            if (wildfire > 0 && source.kind() == PathEffects.Kind.BOLT && entity.getFireTicks() > 0) {
                Location at = entity.getLocation().add(0, 0.5, 0);
                at.getWorld().spawnParticle(Particle.FLAME, at, 40, 1, 0.5, 1, 0.08);
                at.getWorld().playSound(at, Sound.ITEM_FIRECHARGE_USE, 0.9f, 0.8f);
                for (LivingEntity near : core.around(player, at, 2 + wildfire)) {
                    near.setFireTicks(Math.max(near.getFireTicks(), 80));
                }
            }
        }
        if (source.kind() == PathEffects.Kind.PET && plugin.has(player, Ability.PREDATOR) && core.power(player, Path.BEASTMASTER) >= 10) {
            PathEffects.heal(player, 2);
        }
    }

    // ============================================================ M2 skills

    boolean skill(Player player, Ability skill) {
        Path path = skill.path();
        int level = core.power(player, path);
        double power = plugin.skillPower(player, path) * (path == Path.PYROMANCER ? infernoCore(player) : 1);
        return switch (skill) {
            case CALL_OF_THE_WILD -> callOfTheWild(player, level, power);
            case FIRE_PILLAR -> firePillar(player, level, power);
            case GLACIAL_LANCE -> glacialLance(player, level, power);
            case RALLY -> rally(player, level, power);
            case FIREBALL -> fireball(player);
            case FLAME_WAVE -> flameWave(player, level, power);
            case BLIZZARD -> blizzard(player, level, power);
            case FROST_NOVA -> frostNova(player, level, power);
            default -> false;
        };
    }

    private boolean callOfTheWild(Player player, int level, double power) {
        World world = player.getWorld();
        int count = level >= 10 ? 3 : 2;
        int lifetime = (int) (600 * power);
        world.playSound(player.getLocation(), Sound.ENTITY_WOLF_ANGRY_GROWL, 1.2f, 0.9f);
        for (int i = 0; i < count; i++) {
            Location spot = player.getLocation().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2));
            if (!spot.getBlock().isPassable()) {
                spot = player.getLocation();
            }
            Wolf wolf = world.spawn(spot, Wolf.class, w -> {
                w.setTamed(true);
                w.setOwner(player);
                w.setPersistent(false);
                w.customName(plugin.lang().get("wild-wolf"));
                w.getPersistentDataContainer().set(summonedKey, PersistentDataType.BYTE, (byte) 1);
            });
            summoned.add(wolf);
            world.spawnParticle(Particle.CLOUD, spot.clone().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.05);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (wolf.isValid()) {
                    wolf.getWorld().spawnParticle(Particle.CLOUD, wolf.getLocation().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.05);
                    wolf.remove();
                }
                summoned.remove(wolf);
            }, lifetime);
        }
        return true;
    }

    private boolean rally(Player player, int level, double power) {
        List<Tameable> pets = pets(player, 24);
        if (pets.isEmpty()) {
            player.sendActionBar(plugin.lang().get("no-pets"));
            return false;
        }
        int ticks = (int) (200 * power);
        for (Tameable pet : pets) {
            if (pet.getLocation().distanceSquared(player.getLocation()) > 36) {
                pet.teleport(player.getLocation());
            }
            pet.setHealth(PathEffects.maxHealth(pet));
            pet.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 1));
            pet.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0));
            if (level >= 10) {
                pet.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 0));
            }
            pet.getWorld().spawnParticle(Particle.HEART, pet.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3);
        }
        player.getWorld().playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1.4f);
        return true;
    }

    private boolean fireball(Player player) {
        LargeFireball fireball = player.launchProjectile(LargeFireball.class, player.getEyeLocation().getDirection().normalize().multiply(1.2));
        fireball.setYield(0);
        fireball.setIsIncendiary(false);
        fireball.getPersistentDataContainer().set(fireballKey, PersistentDataType.BYTE, (byte) 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1f, 1f);
        return true;
    }

    private void fireballBlast(Player player, Location at) {
        int level = core.power(player, Path.PYROMANCER);
        double damage = (6 + 0.3 * level) * plugin.skillPower(player, Path.PYROMANCER) * infernoCore(player);
        World world = at.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        world.spawnParticle(Particle.FLAME, at, 60, 1.5, 0.6, 1.5, 0.08);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1f);
        for (LivingEntity victim : core.around(player, at, 3)) {
            double closeness = 1 - Math.min(1, victim.getLocation().distance(at) / 3);
            core.splash(player, victim, damage * (0.5 + 0.5 * closeness));
            victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
        }
        if (level >= 10) {
            new BukkitRunnable() {
                int second;

                @Override
                public void run() {
                    if (!player.isOnline() || ++second > 3) {
                        cancel();
                        return;
                    }
                    for (int i = 0; i < 24; i++) {
                        double angle = Math.PI * 2 * i / 24;
                        world.spawnParticle(Particle.FLAME, at.clone().add(Math.cos(angle) * 3, 0.1, Math.sin(angle) * 3), 1, 0, 0.1, 0, 0);
                    }
                    for (LivingEntity victim : core.around(player, at, 3)) {
                        core.splash(player, victim, 1.5);
                        victim.setFireTicks(Math.max(victim.getFireTicks(), 40));
                    }
                }
            }.runTaskTimer(plugin, 20, 20);
        }
    }

    private boolean flameWave(Player player, int level, double power) {
        World world = player.getWorld();
        Vector facing = player.getLocation().getDirection().setY(0).normalize();
        double range = level >= 10 ? 8 : 6;
        double damage = (4 + 0.2 * level) * power;
        world.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.7f);
        for (double d = 1; d <= range; d += 0.8) {
            double width = d * 0.6;
            Location row = player.getLocation().add(facing.clone().multiply(d)).add(0, 0.8, 0);
            world.spawnParticle(Particle.FLAME, row, (int) (6 + d * 2), width / 2, 0.3, width / 2, 0.02);
        }
        for (LivingEntity victim : core.around(player, player.getLocation(), range)) {
            Vector to = victim.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            if (to.lengthSquared() > 1 && to.normalize().dot(facing) < 0.6) {
                continue;
            }
            core.splash(player, victim, damage);
            victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
            if (level >= 10) {
                victim.setVelocity(facing.clone().multiply(0.9).setY(0.3));
            }
        }
        return true;
    }

    private boolean blizzard(Player player, int level, double power) {
        Location center = aimSpot(player);
        double radius = level >= 10 ? 7 : 5;
        double damage = (2 + 0.1 * level) * power;
        World world = center.getWorld();
        world.playSound(center, Sound.ITEM_ELYTRA_FLYING, 0.8f, 1.6f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 10;
                if (!player.isOnline() || tick > 80) {
                    cancel();
                    if (player.isOnline()) {
                        for (LivingEntity victim : core.around(player, center, radius)) {
                            freeze(player, victim, 40);
                        }
                    }
                    return;
                }
                world.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 2, 0), 80, radius / 2, 1.5, radius / 2, 0.05);
                world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, 1, 0), 40, radius / 2, 1, radius / 2, 0.02);
                if (tick % 20 == 0) {
                    for (LivingEntity victim : core.around(player, center, radius)) {
                        core.splash(player, victim, damage);
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 10);
        return true;
    }

    private boolean frostNova(Player player, int level, double power) {
        double radius = level >= 10 ? 7 : 5;
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2 * i / 40;
            world.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius),
                    2, 0.1, 0.1, 0.1, 0.01);
        }
        for (LivingEntity victim : core.around(player, player.getLocation(), radius)) {
            freeze(player, victim, (int) (40 * power));
            if (level >= 10) {
                core.splash(player, victim, (4 + 0.2 * level) * power);
            }
        }
        return true;
    }

    private boolean firePillar(Player player, int level, double power) {
        Location center = aimSpot(player);
        double radius = level >= 10 ? 3 : 2;
        double damage = (3 + 0.15 * level) * power * (level >= 10 ? 1.25 : 1);
        World world = center.getWorld();
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.6f);
        world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.8f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || tick > 60) {
                    cancel();
                    return;
                }
                for (double y = 0; y < 6; y += 0.5) {
                    world.spawnParticle(Particle.FLAME, center.clone().add(0, y, 0), 3, radius / 3, 0.2, radius / 3, 0.01);
                }
                world.spawnParticle(Particle.LAVA, center, 2, radius / 2, 0.1, radius / 2, 0);
                if (tick % 20 == 0) {
                    for (LivingEntity victim : core.around(player, center, radius + 0.5)) {
                        if (Math.abs(victim.getLocation().getY() - center.getY()) > 6) {
                            continue;
                        }
                        core.splash(player, victim, damage);
                        victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
                        if (tick == 0) {
                            victim.setVelocity(victim.getVelocity().setY(0.9));
                        }
                    }
                }
                tick += 5;
            }
        }.runTaskTimer(plugin, 0, 5);
        return true;
    }

    private boolean glacialLance(Player player, int level, double power) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        double range = level >= 10 ? 24 : 16;
        double damage = (5 + 0.25 * level) * power;
        World world = eye.getWorld();
        world.playSound(eye, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f, 0.6f);
        world.playSound(eye, Sound.ITEM_TRIDENT_THROW, 1f, 1.4f);
        java.util.Set<UUID> pierced = new java.util.HashSet<>();
        for (double d = 1; d <= range; d += 0.5) {
            Location point = eye.clone().add(direction.clone().multiply(d));
            if (point.getBlock().isSolid()) {
                world.spawnParticle(Particle.BLOCK, point, 20, 0.3, 0.3, 0.3, 0, Material.ICE.createBlockData());
                break;
            }
            world.spawnParticle(Particle.SNOWFLAKE, point, 2, 0.05, 0.05, 0.05, 0);
            if (d % 1 == 0) {
                world.spawnParticle(Particle.BLOCK, point, 2, 0.1, 0.1, 0.1, 0, Material.ICE.createBlockData());
            }
            for (LivingEntity victim : point.getNearbyLivingEntities(1.2)) {
                if (PathEffects.validTarget(player, victim) && pierced.add(victim.getUniqueId())) {
                    core.splash(player, victim, damage);
                    freeze(player, victim, 40);
                }
            }
        }
        return true;
    }

    // ============================================================ Spells (sneak + right-click with the staff)

    boolean spell(Player player, Ability spell) {
        Path path = spell.path();
        int level = core.power(player, path);
        double power = plugin.skillPower(player, path) * (path == Path.PYROMANCER ? infernoCore(player) : 1);
        return switch (spell) {
            case BLAZING_LEAP -> blazingLeap(player, level, power);
            case PHOENIX_WARD -> phoenixWard(player, level, power);
            case FROST_STEP -> frostStep(player, level);
            case ICE_BLOCK -> iceBlock(player, level);
            default -> false;
        };
    }

    private void flameBurst(Player player, Location at, double damage) {
        World world = at.getWorld();
        world.spawnParticle(Particle.FLAME, at, 40, 1.2, 0.2, 1.2, 0.06);
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.7f);
        for (LivingEntity victim : core.around(player, at, 3)) {
            core.splash(player, victim, damage);
            victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
        }
    }

    private boolean blazingLeap(Player player, int level, double power) {
        double damage = (3 + 0.15 * level) * power;
        flameBurst(player, player.getLocation(), damage);
        player.setVelocity(flat(player).multiply(1.3).setY(0.9));
        core.protectFromFall(player, 5000);
        if (level >= 10) {
            new BukkitRunnable() {
                int tick;

                @Override
                public void run() {
                    tick += 2;
                    if (!player.isOnline() || tick > 80) {
                        cancel();
                    } else if (tick > 6 && PathEffects.onGround(player)) {
                        cancel();
                        flameBurst(player, player.getLocation(), damage);
                    }
                }
            }.runTaskTimer(plugin, 2, 2);
        }
        return true;
    }

    private boolean phoenixWard(Player player, int level, double power) {
        UUID id = player.getUniqueId();
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120, level >= 10 ? 2 : 1, false, true, true));
        wardUntil.put(id, System.currentTimeMillis() + 6000);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5f, 1.6f);
        double damage = (4 + 0.2 * level) * power;
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 5;
                if (!player.isOnline()) {
                    cancel();
                    wardUntil.remove(id);
                    return;
                }
                if (tick >= 120) {
                    cancel();
                    wardUntil.remove(id);
                    flameBurst(player, player.getLocation().add(0, 0.5, 0), damage);
                    return;
                }
                Location at = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI * 2 * i / 8 + tick * 0.15;
                    player.getWorld().spawnParticle(Particle.FLAME, at.clone().add(Math.cos(angle) * 0.9, Math.sin(tick * 0.2) * 0.6, Math.sin(angle) * 0.9), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 5, 5);
        return true;
    }

    private static boolean standable(Location feet) {
        return feet.getBlock().isPassable() && feet.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private boolean frostStep(Player player, int level) {
        double range = level >= 10 ? 9 : 6;
        Location start = player.getLocation();
        Vector direction = start.getDirection().normalize();
        Location end = null;
        for (double d = 0.5; d <= range; d += 0.5) {
            Location step = start.clone().add(direction.clone().multiply(d));
            if (!standable(step)) {
                break;
            }
            end = step;
        }
        if (end == null || end.distanceSquared(start) < 1) {
            return false; // a wall right in front: no blink, no cooldown
        }
        World world = start.getWorld();
        Vector path = end.toVector().subtract(start.toVector());
        java.util.Set<UUID> slowedNow = new java.util.HashSet<>();
        for (double d = 0; d <= 1; d += 0.1) {
            Location point = start.clone().add(path.clone().multiply(d)).add(0, 0.2, 0);
            world.spawnParticle(Particle.SNOWFLAKE, point, 4, 0.2, 0.1, 0.2, 0.01);
            world.spawnParticle(Particle.BLOCK, point, 2, 0.2, 0.05, 0.2, 0, Material.ICE.createBlockData());
            for (LivingEntity victim : core.around(player, point, 1.5)) {
                if (slowedNow.add(victim.getUniqueId())) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                }
            }
        }
        player.teleport(end);
        player.setFallDistance(0);
        core.protectFromFall(player, 2000);
        world.playSound(end, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1.4f);
        if (level >= 10) {
            world.spawnParticle(Particle.SNOWFLAKE, end.clone().add(0, 0.5, 0), 40, 1.5, 0.3, 1.5, 0.05);
            world.playSound(end, Sound.BLOCK_GLASS_BREAK, 1f, 1f);
            for (LivingEntity victim : core.around(player, end, 3)) {
                freeze(player, victim, 30);
            }
        }
        return true;
    }

    private static final List<PotionEffectType> HARMFUL = List.of(PotionEffectType.POISON, PotionEffectType.WITHER,
            PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS,
            PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE, PotionEffectType.LEVITATION);

    private boolean iceBlock(Player player, int level) {
        UUID id = player.getUniqueId();
        iceBlockUntil.put(id, System.currentTimeMillis() + 3000);
        player.setVelocity(new Vector());
        if (level >= 10) {
            HARMFUL.forEach(player::removePotionEffect);
            player.setFireTicks(0);
            player.setFreezeTicks(0);
        }
        World world = player.getWorld();
        BlockDisplay shell = world.spawn(player.getLocation(), BlockDisplay.class, display -> {
            display.setBlock(Material.ICE.createBlockData());
            display.setTransformation(new Transformation(new Vector3f(-0.65f, -0.05f, -0.65f), new AxisAngle4f(),
                    new Vector3f(1.3f, 2.2f, 1.3f), new AxisAngle4f()));
            display.setPersistent(false);
        });
        shells.add(shell);
        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_PLACE, 1.2f, 0.7f);
        new BukkitRunnable() {
            int second;

            @Override
            public void run() {
                if (!player.isOnline() || ++second > 3) {
                    cancel();
                    iceBlockUntil.remove(id);
                    shell.remove();
                    shells.remove(shell);
                    world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);
                    world.spawnParticle(Particle.BLOCK, player.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0, Material.ICE.createBlockData());
                    return;
                }
                PathEffects.heal(player, 2);
            }
        }.runTaskTimer(plugin, 20, 20);
        return true;
    }

    /** Ice Block: the encased player can look around but not move. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (iceBlockUntil.getOrDefault(event.getPlayer().getUniqueId(), 0L) > System.currentTimeMillis() && event.hasChangedPosition()) {
            Location stay = event.getFrom().clone();
            stay.setYaw(event.getTo().getYaw());
            stay.setPitch(event.getTo().getPitch());
            event.setTo(stay);
        }
    }

    // ============================================================ Cleanup

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathCleanup(org.bukkit.event.entity.EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        markedBy.remove(id);
        markedUntil.remove(id);
        petHitAt.remove(id);
        chill.remove(id);
        if (event.getEntity().getPersistentDataContainer().has(summonedKey, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    void forget(Player player) {
        lastBolt.remove(player.getUniqueId());
        boltHits.remove(player.getUniqueId());
        heat.remove(player.getUniqueId());
        wardUntil.remove(player.getUniqueId());
        iceBlockUntil.remove(player.getUniqueId());
        for (Wolf wolf : new ArrayList<>(summoned)) {
            if (player.equals(wolf.getOwner())) {
                wolf.remove();
                summoned.remove(wolf);
            }
        }
    }

    void shutdown() {
        summoned.forEach(Entity::remove);
        summoned.clear();
        shells.forEach(Entity::remove);
        shells.clear();
    }
}
