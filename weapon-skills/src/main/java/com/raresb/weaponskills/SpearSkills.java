package com.raresb.weaponskills;

import com.raresb.weaponskills.common.Root;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Spear skills: long reach, piercing lines, charges, throws and leaps. */
final class SpearSkills {
    private final WeaponSkillsPlugin plugin;
    private final Combat combat;
    private final Skills skills;
    /** Thrown and falling spears, removed if the plugin is disabled. */
    private final List<Entity> temporaryEntities = new ArrayList<>();

    SpearSkills(WeaponSkillsPlugin plugin, Combat combat, Skills skills) {
        this.plugin = plugin;
        this.combat = combat;
        this.skills = skills;
    }

    /** Can't walk or jump for a moment (same as Trap Arrow's snare). */
    private void pin(LivingEntity target, int ticks) {
        Root.apply(plugin, target, ticks, 6);
    }

    /** Enemies in a straight line in front of the player, up to {@code reach} blocks, {@code width} wide. */
    private List<LivingEntity> inLine(Player player, Vector direction, double reach, double width) {
        Location eye = player.getEyeLocation();
        List<LivingEntity> hit = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (double d = 0.5; d <= reach; d += 0.5) {
            Location point = eye.clone().add(direction.clone().multiply(d));
            if (!point.getBlock().isPassable()) {
                break; // the thrust stops at walls
            }
            for (LivingEntity target : combat.around(player, point.clone().subtract(0, 0.6, 0), width)) {
                if (seen.add(target.getUniqueId())) {
                    hit.add(target);
                }
            }
        }
        return hit;
    }

    private static void thrustLine(Player player, Vector direction, double reach, int color) {
        Location eye = player.getEyeLocation().subtract(0, 0.3, 0);
        Fx.line(eye, eye.clone().add(direction.clone().multiply(reach)), 0.3, Particle.DUST, Fx.dust(color, 1.0f));
    }

    private ItemDisplay spearDisplay(Player player, Location at, float scale) {
        ItemStack spear = player.getInventory().getItemInMainHand().clone();
        ItemStack shown = spear.getType().name().endsWith("_SPEAR") ? spear : new ItemStack(Material.IRON_SPEAR);
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(shown);
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
        });
        temporaryEntities.add(display);
        return display;
    }

    private void remove(Entity entity) {
        entity.remove();
        temporaryEntities.remove(entity);
    }

    // ------------------------------------------------------------------ Defaults

    /** A long thrust that pierces everything in a line; the first target is pinned. */
    void impale(Player player) {
        boolean evolved = skills.evolved(player, Skill.IMPALE);
        double reach = evolved ? 9 : 6;
        Vector direction = Aim.direction(player);
        double damage = skills.damage(player, Skill.IMPALE);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_SPEAR_ATTACK, 1.2f, 0.8f);
        thrustLine(player, direction, reach, 0xD0D8E0);
        List<LivingEntity> targets = inLine(player, direction, reach, 1.0);
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            combat.hit(player, target, damage, Skill.IMPALE);
            world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.2, 0.3, 0.2, 0.2);
            if (i == 0 || evolved) {
                pin(target, 20);
            }
        }
        player.swingMainHand();
    }

    /** Dive spear-first onto the spot in front; the higher the fall, the harder the hit. */
    void dragoonDive(Player player) {
        double startY = player.getLocation().getY();
        World world = player.getWorld();
        Location spot = Aim.groundSpot(player, combat, 12, 6);
        Vector toSpot = spot.toVector().subtract(player.getLocation().toVector()).setY(0);
        Vector horizontal = toSpot.lengthSquared() > 0.01 ? toSpot.normalize().multiply(Math.min(1.2, toSpot.length() * 0.15)) : new Vector();
        player.setVelocity(horizontal.setY(-2.0));
        world.playSound(player.getLocation(), Sound.ITEM_SPEAR_LUNGE_1, 1f, 0.7f);
        combat.protectFromFall(player, 5000);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++ticks > 100) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 4, 0.2, 0.5, 0.2, 0, Fx.dust(0xC8D6E5, 1.3f));
                if (Combat.onGround(player) || player.isInWater()) {
                    cancel();
                    combat.protectFromFall(player, 500);
                    double height = Math.max(0, startY - player.getLocation().getY());
                    boolean evolved = skills.evolved(player, Skill.DRAGOON_DIVE);
                    double radius = (evolved ? 3.5 : 2.5) + Math.min(2, height / 5);
                    double damage = skills.damage(player, Skill.DRAGOON_DIVE) * (1 + Math.min(1.5, height * 0.1));
                    Location center = player.getLocation();
                    world.playSound(center, Sound.ITEM_SPEAR_HIT, 1.2f, 0.6f);
                    world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND, 1f, 1.1f);
                    world.spawnParticle(Particle.DUST_PLUME, center.clone().add(0, 0.2, 0), 30, radius / 3, 0.1, radius / 3, 0.05);
                    Fx.ring(center.clone().add(0, 0.1, 0), radius, 30, Particle.CRIT, null);
                    for (LivingEntity target : combat.around(player, center, radius)) {
                        combat.hit(player, target, damage, Skill.DRAGOON_DIVE);
                        if (evolved) {
                            pin(target, 30);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** Charge forward spear-first; the first enemy is carried along and takes a big hit (evolved: everyone in the way). */
    void lanceCharge(Player player) {
        boolean evolved = skills.evolved(player, Skill.LANCE_CHARGE);
        Vector direction = Aim.flat(player);
        double damage = skills.damage(player, Skill.LANCE_CHARGE);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_SPEAR_LUNGE_2, 1.2f, 0.9f);
        combat.protectFromFall(player, 2000);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++tick > (evolved ? 12 : 9)) {
                    cancel();
                    return;
                }
                player.setVelocity(direction.clone().multiply(1.1).setY(Math.min(0, player.getVelocity().getY())));
                Location front = player.getLocation().add(direction.clone().multiply(1.2)).add(0, 1, 0);
                world.spawnParticle(Particle.SWEEP_ATTACK, front, 1);
                world.spawnParticle(Particle.CLOUD, player.getLocation(), 2, 0.2, 0.1, 0.2, 0);
                for (LivingEntity target : combat.around(player, front, 1.5)) {
                    if (!hit.add(target.getUniqueId())) {
                        continue;
                    }
                    combat.hit(player, target, damage, Skill.LANCE_CHARGE);
                    target.setVelocity(direction.clone().multiply(1.4).setY(0.35));
                    world.playSound(target.getLocation(), Sound.ITEM_SPEAR_HIT, 1f, 0.8f);
                    if (!evolved) {
                        player.setVelocity(new Vector());
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
        player.swingMainHand();
    }

    /** Throw a spear that flies far and pierces several enemies (evolved: more, and the last is pinned). */
    void javelin(Player player) {
        boolean evolved = skills.evolved(player, Skill.JAVELIN);
        int pierce = evolved ? 5 : 3;
        double damage = skills.damage(player, Skill.JAVELIN);
        World world = player.getWorld();
        Vector velocity = Aim.direction(player).multiply(1.6);
        Location position = player.getEyeLocation().add(velocity.clone().normalize().multiply(0.8));
        ItemDisplay display = spearDisplay(player, position, 1.2f);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.2f);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            int tick;
            LivingEntity last;

            @Override
            public void run() {
                if (!player.isOnline() || !display.isValid() || ++tick > 30 || !position.getBlock().isPassable() || hit.size() >= pierce) {
                    cancel();
                    if (evolved && last != null && last.isValid()) {
                        pin(last, 40);
                    }
                    remove(display);
                    return;
                }
                velocity.setY(velocity.getY() - 0.02); // a little drop over distance
                position.add(velocity);
                Location at = position.clone().setDirection(velocity);
                display.teleport(at);
                world.spawnParticle(Particle.CRIT, position, 1, 0, 0, 0, 0);
                for (LivingEntity target : combat.around(player, position.clone().subtract(0, 0.6, 0), 1.0)) {
                    if (hit.size() < pierce && hit.add(target.getUniqueId())) {
                        combat.hit(player, target, damage, Skill.JAVELIN);
                        world.playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 1f);
                        last = target;
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
        player.swingMainHand();
    }

    /** Vault forward and up on the spear, then float down (evolved: further, and Speed II after). */
    void poleVault(Player player) {
        boolean evolved = skills.evolved(player, Skill.POLE_VAULT);
        Vector direction = Aim.flat(player).multiply(evolved ? 1.5 : 1.1).setY(evolved ? 1.1 : 0.95);
        player.setVelocity(direction);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false, true));
        if (evolved) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, false, true));
        }
        combat.protectFromFall(player, 4000);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_SPEAR_USE, 1f, 1.3f);
        world.spawnParticle(Particle.GUST, player.getLocation(), 1);
    }

    /** Sweep the spear in a full circle: damage and push away everything in reach. */
    void sweepingArc(Player player) {
        boolean evolved = skills.evolved(player, Skill.SWEEPING_ARC);
        double radius = evolved ? 6 : 4.5;
        double damage = skills.damage(player, Skill.SWEEPING_ARC);
        Location center = player.getLocation();
        World world = player.getWorld();
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        world.playSound(center, Sound.ITEM_SPEAR_ATTACK, 1f, 0.6f);
        for (double r = 1.5; r <= radius; r += 1.5) {
            Fx.ring(center.clone().add(0, 1, 0), r, (int) (r * 8), Particle.SWEEP_ATTACK, null);
        }
        for (LivingEntity target : combat.around(player, center, radius)) {
            combat.hit(player, target, damage, Skill.SWEEPING_ARC);
            Combat.knockBack(target, center, 0.9, 0.3);
            if (evolved) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            }
        }
        player.swingMainHand();
    }

    // ------------------------------------------------------------------ Unlocked with mastery

    /** Brace the spear: for a few seconds enemies in front are poked and pushed back (evolved: all around, longer). */
    void spearWall(Player player) {
        boolean evolved = skills.evolved(player, Skill.SPEAR_WALL);
        int duration = evolved ? 120 : 80;
        double damage = skills.damage(player, Skill.SPEAR_WALL);
        World world = player.getWorld();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 2, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, false, true));
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.7f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || (tick += 10) > duration) {
                    cancel();
                    return;
                }
                List<LivingEntity> targets = evolved ? combat.around(player, player.getLocation(), 3.5)
                        : combat.inFront(player, 3.5, 140);
                Vector facing = Aim.flat(player);
                for (int i = -2; i <= 2; i++) {
                    Location tip = player.getLocation().add(0, 1, 0)
                            .add(facing.clone().rotateAroundY(Math.toRadians(i * 25)).multiply(2.2));
                    world.spawnParticle(Particle.CRIT, tip, 2, 0.05, 0.05, 0.05, 0);
                }
                for (LivingEntity target : targets) {
                    combat.hit(player, target, damage, Skill.SPEAR_WALL);
                    Combat.knockBack(target, player.getLocation(), 0.8, 0.25);
                    world.playSound(target.getLocation(), Sound.ITEM_SPEAR_HIT, 0.8f, 1.2f);
                }
            }
        }.runTaskTimer(plugin, 0, 10);
        player.swingMainHand();
    }

    /** Eight quick thrusts in a line, each piercing everything in the way (evolved: twelve). */
    void thrustFlurry(Player player) {
        int thrusts = skills.evolved(player, Skill.THRUST_FLURRY) ? 12 : 8;
        double damage = skills.damage(player, Skill.THRUST_FLURRY);
        World world = player.getWorld();
        new BukkitRunnable() {
            int done;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++done > thrusts) {
                    cancel();
                    return;
                }
                Vector direction = Aim.direction(player);
                world.playSound(player.getLocation(), Sound.ITEM_SPEAR_ATTACK, 0.8f, 1.2f + done * 0.05f);
                thrustLine(player, direction, 5, 0xE8E8E8);
                for (LivingEntity target : inLine(player, direction, 5, 0.9)) {
                    combat.hit(player, target, damage, Skill.THRUST_FLURRY);
                }
                player.swingMainHand();
            }
        }.runTaskTimer(plugin, 0, 3);
    }

    /** Throw the spear at the enemy you look at: it is pinned to the ground and glows (evolved: so are those around it). */
    boolean pinningThrow(Player player) {
        LivingEntity target = Aim.lookedAt(player, combat, 24);
        if (target == null) {
            plugin.flash(player, plugin.lang().get("no-target"));
            return false;
        }
        boolean evolved = skills.evolved(player, Skill.PINNING_THROW);
        double damage = skills.damage(player, Skill.PINNING_THROW);
        World world = player.getWorld();
        Location from = player.getEyeLocation();
        Location to = target.getLocation().add(0, target.getHeight() / 2, 0);
        Fx.line(from, to, 0.4, Particle.CRIT, null);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.9f);
        world.playSound(to, Sound.ITEM_SPEAR_HIT, 1.2f, 0.7f);
        combat.hit(player, target, damage, Skill.PINNING_THROW);
        List<LivingEntity> pinned = new ArrayList<>(List.of(target));
        if (evolved) {
            pinned.addAll(combat.around(player, target.getLocation(), 3));
        }
        for (LivingEntity victim : pinned) {
            pin(victim, 60);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
        }
        // The spear stays stuck in the ground next to the target for the duration.
        ItemDisplay stuck = spearDisplay(player, target.getLocation().add(0.4, 0.6, 0.4), 1.2f);
        stuck.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f((float) (Math.PI * 0.75), 0, 0, 1)),
                new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> remove(stuck), 60);
        player.swingMainHand();
        return true;
    }

    /** Leap to the spot you aim at and land spear-first: damage and knock-up around (evolved: wider, stuns). */
    void dragonLeap(Player player) {
        Location spot = Aim.groundSpot(player, combat, 16, 10);
        Vector offset = spot.toVector().subtract(player.getLocation().toVector());
        double horizontal = Math.min(16, Math.hypot(offset.getX(), offset.getZ()));
        // About 1 second in the air: horizontal speed covers the distance, vertical gives the arc.
        Vector flat = offset.clone().setY(0);
        flat = flat.lengthSquared() > 0.01 ? flat.normalize().multiply(horizontal / 18.0) : new Vector();
        player.setVelocity(flat.setY(1.0 + Math.max(0, offset.getY()) * 0.06));
        combat.protectFromFall(player, 5000);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_SPEAR_LUNGE_3, 1f, 0.8f);
        world.spawnParticle(Particle.GUST_EMITTER_SMALL, player.getLocation(), 1);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++ticks > 80) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 3, 0.2, 0.4, 0.2, 0, Fx.dust(0x6A5ACD, 1.3f));
                if (ticks > 4 && (Combat.onGround(player) || player.isInWater())) {
                    cancel();
                    combat.protectFromFall(player, 500);
                    boolean evolved = skills.evolved(player, Skill.DRAGON_LEAP);
                    double radius = evolved ? 4.5 : 3.5;
                    double damage = skills.damage(player, Skill.DRAGON_LEAP);
                    Location center = player.getLocation();
                    world.playSound(center, Sound.ITEM_SPEAR_HIT, 1.2f, 0.5f);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
                    Fx.shockwave(plugin, center.clone().add(0, 0.2, 0), radius, 6, Particle.DUST, Fx.dust(0x6A5ACD, 1.5f));
                    for (LivingEntity target : combat.around(player, center, radius)) {
                        combat.hit(player, target, damage, Skill.DRAGON_LEAP);
                        Combat.knockBack(target, center, 0.3, 0.7);
                        if (evolved) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3));
                            Fx.stunStars(plugin, target, 40);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** ULTIMATE: a giant spear falls where you aim, then rings of light spikes erupt around it. */
    void gungnir(Player player) {
        Location spot = Aim.groundSpot(player, combat, 32, 16);
        boolean evolved = skills.evolved(player, Skill.GUNGNIR);
        double damage = skills.damage(player, Skill.GUNGNIR);
        World world = spot.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 1.4f);
        Fx.ring(spot.clone().add(0, 0.1, 0), 4, 40, Particle.END_ROD, null);
        ItemDisplay spear = spearDisplay(player, spot.clone().add(0, 20, 0), 5f);
        // Point down: the spear model points up and to the right, so turn it 135 degrees.
        spear.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f((float) (Math.PI * 0.75), 0, 0, 1)),
                new Vector3f(5f, 5f, 5f), new Quaternionf()));
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick++;
                if (!player.isOnline() || !spear.isValid()) {
                    cancel();
                    remove(spear);
                    return;
                }
                if (tick <= 10) {
                    spear.teleport(spot.clone().add(0, 20 - tick * 2, 0));
                    world.spawnParticle(Particle.END_ROD, spot.clone().add(0, 20 - tick * 2, 0), 6, 0.3, 0.8, 0.3, 0.02);
                    return;
                }
                cancel();
                world.playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
                world.playSound(spot, Sound.ITEM_SPEAR_HIT, 1.5f, 0.5f);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, spot, 1);
                world.spawnParticle(Particle.FLASH, spot, 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
                for (LivingEntity target : combat.around(player, spot, 4)) {
                    combat.hit(player, target, damage, Skill.GUNGNIR);
                    Combat.knockBack(target, spot, 0.8, 0.5);
                }
                spikes(player, spot, damage * 0.4, evolved ? 4 : 3);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> remove(spear), 30);
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void spikes(Player player, Location center, double damage, int rings) {
        World world = center.getWorld();
        new BukkitRunnable() {
            int ring;

            @Override
            public void run() {
                if (!player.isOnline() || ++ring > rings) {
                    cancel();
                    return;
                }
                double radius = 2 + ring * 2;
                int points = (int) (radius * 3);
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    Location base = Aim.toGround(center.clone().add(Math.cos(angle) * radius, 3, Math.sin(angle) * radius));
                    for (double y = 0; y < 2.2; y += 0.3) {
                        world.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 1, 0, 0, 0, 0);
                    }
                }
                world.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.6f + ring * 0.2f);
                for (LivingEntity target : combat.around(player, center, radius + 1)) {
                    double distance = target.getLocation().distance(center);
                    if (distance >= radius - 1.2) {
                        combat.hit(player, target, damage, Skill.GUNGNIR);
                        target.setVelocity(target.getVelocity().setY(0.8));
                    }
                }
            }
        }.runTaskTimer(plugin, 6, 6);
    }

    void shutdown() {
        temporaryEntities.forEach(Entity::remove);
        temporaryEntities.clear();
    }
}
