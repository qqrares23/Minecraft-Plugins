package com.raresb.weaponskills;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Trident, mace and pickaxe skills. */
final class OtherSkills {
    private final WeaponSkillsPlugin plugin;
    private final Combat combat;
    private final Skills skills;
    private final List<Entity> temporaryEntities = new ArrayList<>();

    OtherSkills(WeaponSkillsPlugin plugin, Combat combat, Skills skills) {
        this.plugin = plugin;
        this.combat = combat;
        this.skills = skills;
    }

    // ---------------------------------------------------------------- Trident

    void riptideSurge(Player player) {
        World world = player.getWorld();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        boolean evolved = skills.evolved(player, Skill.RIPTIDE_SURGE);
        player.setVelocity(direction.clone().multiply(evolved ? 2.8 : 2.1).add(new Vector(0, 0.25, 0)));
        combat.protectFromFall(player, 3000);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1f);
        double damage = skills.damage(player, Skill.RIPTIDE_SURGE);
        Set<UUID> alreadyHit = new HashSet<>();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || ++tick > (evolved ? 18 : 12)) {
                    cancel();
                    return;
                }
                Location body = player.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.SPLASH, body, 12, 0.4, 0.4, 0.4, 0.1);
                world.spawnParticle(Particle.BUBBLE_POP, body, 6, 0.3, 0.3, 0.3, 0.05);
                Fx.ring(body, 0.8, 10, Particle.DUST, Fx.dust(0x3FA9F5, 1.1f));
                for (LivingEntity target : combat.around(player, player.getLocation(), 2)) {
                    if (alreadyHit.add(target.getUniqueId())) {
                        combat.hit(player, target, damage, Skill.RIPTIDE_SURGE);
                        Combat.knockBack(target, player.getLocation(), 0.8, evolved ? 0.9 : 0.3);
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    void tidalWave(Player player) {
        World world = player.getWorld();
        Vector direction = Aim.flat(player);
        Vector side = direction.clone().rotateAroundY(Math.PI / 2);
        Location start = player.getLocation();
        double damage = skills.damage(player, Skill.TIDAL_WAVE);
        boolean evolved = skills.evolved(player, Skill.TIDAL_WAVE);
        double halfWidth = evolved ? 3.6 : 1.8;
        Set<UUID> alreadyHit = new HashSet<>();
        world.playSound(start, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.8f);
        world.playSound(start, Sound.BLOCK_WATER_AMBIENT, 1.5f, 0.6f);
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                if (!player.isOnline() || ++step > (evolved ? 18 : 12)) {
                    cancel();
                    return;
                }
                Location front = start.clone().add(direction.clone().multiply(step));
                for (double w = -halfWidth; w <= halfWidth; w += 0.45) {
                    Location spot = front.clone().add(side.clone().multiply(w));
                    world.spawnParticle(Particle.SPLASH, spot.clone().add(0, 0.4, 0), 4, 0.1, 0.3, 0.1, 0.1);
                    world.spawnParticle(Particle.FALLING_WATER, spot.clone().add(0, 1.4, 0), 2, 0.1, 0.1, 0.1, 0);
                    world.spawnParticle(Particle.DUST, spot.clone().add(0, 0.8, 0), 1, 0.1, 0.3, 0.1, 0, Fx.dust(0x1E90FF, 1.4f));
                }
                for (LivingEntity target : combat.around(player, front.clone().add(0, 0.5, 0), halfWidth + 0.4)) {
                    target.setFireTicks(0);
                    if (alreadyHit.add(target.getUniqueId())) {
                        combat.hit(player, target, damage, Skill.TIDAL_WAVE);
                    }
                    target.setVelocity(direction.clone().multiply(1.0).setY(0.3));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    void lightningCall(Player player) {
        World world = player.getWorld();
        LivingEntity aimed = Aim.lookedAt(player, combat, 32);
        Location strike = aimed != null ? aimed.getLocation() : Aim.groundSpot(player, combat, 32, 20);
        // Only the visual and sound of lightning: no fire.
        world.strikeLightningEffect(strike);
        world.spawnParticle(Particle.ELECTRIC_SPARK, strike.clone().add(0, 1, 0), 60, 1, 1, 1, 0.4);
        double damage = skills.damage(player, Skill.LIGHTNING_CALL);
        for (LivingEntity target : combat.around(player, strike, 2.5)) {
            combat.hit(player, target, damage, Skill.LIGHTNING_CALL);
        }
        if (skills.evolved(player, Skill.LIGHTNING_CALL)) {
            // Evolved: three more bolts strike the nearest enemies around the first.
            combat.around(player, strike, 10).stream()
                    .filter(e -> e.getLocation().distanceSquared(strike) > 6.25)
                    .sorted(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(strike)))
                    .limit(3)
                    .forEach(extra -> {
                        world.strikeLightningEffect(extra.getLocation());
                        combat.hit(player, extra, damage * 0.7, Skill.LIGHTNING_CALL);
                    });
        }
        player.swingMainHand();
    }

    // ------------------------------------------------------------------- Mace

    void gravityWell(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation();
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.5f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.4f);
        // A shrinking spiral of particles.
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (++tick > 10) {
                    cancel();
                    return;
                }
                double radius = 8 - tick * 0.7;
                for (int i = 0; i < 16; i++) {
                    double a = tick * 0.6 + Math.PI * 2 * i / 16;
                    world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(Math.cos(a) * radius, 1, Math.sin(a) * radius), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0, 1);
        double damage = skills.damage(player, Skill.GRAVITY_WELL);
        boolean evolved = skills.evolved(player, Skill.GRAVITY_WELL);
        if (evolved) {
            // Evolved: once everything is pulled in, it gets crushed.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                world.spawnParticle(Particle.EXPLOSION_EMITTER, player.getLocation(), 1);
                world.playSound(player.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.6f);
                for (LivingEntity target : combat.around(player, player.getLocation(), 4)) {
                    combat.hit(player, target, damage * 1.5, Skill.GRAVITY_WELL);
                }
            }, 20);
        }
        for (LivingEntity target : combat.around(player, center, evolved ? 14 : 9)) {
            Vector pull = center.toVector().subtract(target.getLocation().toVector());
            double distance = pull.length();
            if (distance > 0.5) {
                target.setVelocity(pull.normalize().multiply(Math.min(1.6, 0.3 + distance * 0.15)).setY(0.35));
            }
            combat.hit(player, target, damage, Skill.GRAVITY_WELL);
        }
        player.swingMainHand();
    }

    void tremor(Player player) {
        quake(player);
        if (skills.evolved(player, Skill.TREMOR)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    quake(player);
                }
            }, 20);
        }
    }

    private void quake(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation();
        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.5f);
        world.playSound(center, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 0.6f);
        Block ground = center.clone().subtract(0, 0.2, 0).getBlock();
        if (!ground.getType().isAir()) {
            Fx.shockwave(plugin, center.clone().add(0, 0.15, 0), 5, 6, Particle.BLOCK, ground.getBlockData());
        }
        world.spawnParticle(Particle.DUST_PLUME, center.clone().add(0, 0.2, 0), 40, 2, 0.1, 2, 0.05);
        double damage = skills.damage(player, Skill.TREMOR);
        for (LivingEntity target : combat.around(player, center, 5)) {
            combat.hit(player, target, damage, Skill.TREMOR);
            target.setVelocity(target.getVelocity().setY(0.35));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 3));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 1));
            Fx.stunStars(plugin, target, 50);
        }
        player.swingMainHand();
    }

    /** Meteor: dive down; the impact grows a lot with the height you fall from. */
    void meteor(Player player) {
        double startY = player.getLocation().getY();
        World world = player.getWorld();
        player.setVelocity(player.getLocation().getDirection().setY(0).multiply(0.3).setY(-2.6));
        combat.protectFromFall(player, 6000);
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++ticks > 120) {
                    cancel();
                    return;
                }
                Location body = player.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.FLAME, body, 10, 0.3, 0.5, 0.3, 0.03);
                world.spawnParticle(Particle.LARGE_SMOKE, body, 3, 0.3, 0.5, 0.3, 0.01);
                world.spawnParticle(Particle.LAVA, body, 1, 0.2, 0.2, 0.2, 0);
                if (Combat.onGround(player) || player.isInWater()) {
                    cancel();
                    combat.protectFromFall(player, 500);
                    meteorImpact(player, Math.max(0, startY - player.getLocation().getY()));
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private void meteorImpact(Player player, double height) {
        Location center = player.getLocation();
        World world = player.getWorld();
        double radius = 3 + Math.min(5, height / 2);
        double damage = skills.damage(player, Skill.METEOR) * (1 + Math.min(3, height * 0.15));
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, 0.5, 0.2, 0.5);
        world.spawnParticle(Particle.FLAME, center.clone().add(0, 0.3, 0), 80, radius / 2, 0.3, radius / 2, 0.1);
        world.spawnParticle(Particle.LAVA, center, 20, radius / 3, 0.2, radius / 3, 0);
        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.5f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        Fx.shockwave(plugin, center.clone().add(0, 0.2, 0), radius, 8, Particle.FLAME, null);
        Fx.shockwave(plugin, center.clone().add(0, 0.4, 0), radius, 8, Particle.DUST, Fx.dust(0xFF6A00, 1.8f));
        for (LivingEntity target : combat.around(player, center, radius)) {
            double closeness = 1 - Math.min(1, target.getLocation().distance(center) / radius);
            combat.hit(player, target, damage * (0.4 + 0.6 * closeness), Skill.METEOR);
            Combat.knockBack(target, center, 0.5 + 0.8 * closeness, 0.5 + 0.5 * closeness);
            target.setFireTicks(Math.max(target.getFireTicks(), 60));
        }
        if (skills.evolved(player, Skill.METEOR)) {
            // Evolved: the crater keeps burning for 3 seconds.
            new BukkitRunnable() {
                int second;

                @Override
                public void run() {
                    if (++second > 3 || !player.isOnline()) {
                        cancel();
                        return;
                    }
                    Fx.ring(center.clone().add(0, 0.2, 0), radius * 0.7, 30, Particle.FLAME, null);
                    world.spawnParticle(Particle.LAVA, center, 6, radius / 3, 0.1, radius / 3, 0);
                    for (LivingEntity target : combat.around(player, center, radius * 0.7)) {
                        combat.hit(player, target, 2, Skill.METEOR);
                        target.setFireTicks(Math.max(target.getFireTicks(), 40));
                    }
                }
            }.runTaskTimer(plugin, 20, 20);
        }
    }

    // ---------------------------------------------------------------- Pickaxe

    /** Outlines nearby ores through walls for 10 seconds. Only the player can see the outlines. */
    void tremorSense(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation();
        boolean evolved = skills.evolved(player, Skill.TREMOR_SENSE);
        int radius = evolved ? 16 : 10;
        int cap = evolved ? 160 : 80;
        List<BlockDisplay> outlines = new ArrayList<>();
        world.playSound(center, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.5f, 0.8f);
        Fx.shockwave(plugin, center.clone().add(0, 0.2, 0), radius, 10, Particle.DUST, Fx.dust(0x40E0D0, 1.2f));
        for (int x = -radius; x <= radius && outlines.size() < cap; x++) {
            for (int y = -radius; y <= radius && outlines.size() < cap; y++) {
                for (int z = -radius; z <= radius && outlines.size() < cap; z++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    Color color = oreColor(block.getType());
                    if (color == null) {
                        continue;
                    }
                    BlockDisplay outline = world.spawn(block.getLocation(), BlockDisplay.class, d -> {
                        d.setBlock(block.getBlockData());
                        d.setPersistent(false);
                        d.setVisibleByDefault(false);
                        d.setGlowing(true);
                        d.setGlowColorOverride(color);
                        d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                        // Slightly smaller than the block so it doesn't flicker against it.
                        d.setTransformation(new Transformation(new Vector3f(0.02f, 0.02f, 0.02f), new Quaternionf(),
                                new Vector3f(0.96f, 0.96f, 0.96f), new Quaternionf()));
                    });
                    player.showEntity(plugin, outline);
                    outlines.add(outline);
                    temporaryEntities.add(outline);
                }
            }
        }
        plugin.flash(player, plugin.lang().get("ores-sensed", "count", outlines.size()));
        int ticks = (200 + 10 * plugin.progress().bonusLevel(player, Skill.TREMOR_SENSE)) * (evolved ? 3 : 2) / 2;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            outlines.forEach(Entity::remove);
            temporaryEntities.removeAll(outlines);
        }, ticks);
        for (int i = 0; i < Math.min(outlines.size(), 5); i++) {
            plugin.progress().onHit(player, Skill.TREMOR_SENSE);
        }
    }

    private static Color oreColor(Material type) {
        String name = type.name();
        if (type == Material.ANCIENT_DEBRIS) {
            return Color.fromRGB(0x8B4513);
        }
        if (!name.endsWith("_ORE")) {
            return null;
        }
        if (name.contains("DIAMOND")) {
            return Color.AQUA;
        }
        if (name.contains("EMERALD")) {
            return Color.LIME;
        }
        if (name.contains("GOLD")) {
            return Color.YELLOW;
        }
        if (name.contains("IRON")) {
            return Color.fromRGB(0xD8AF93);
        }
        if (name.contains("REDSTONE")) {
            return Color.RED;
        }
        if (name.contains("LAPIS")) {
            return Color.BLUE;
        }
        if (name.contains("COPPER")) {
            return Color.ORANGE;
        }
        if (name.contains("QUARTZ")) {
            return Color.WHITE;
        }
        return Color.GRAY; // coal and anything else
    }

    void shutdown() {
        temporaryEntities.forEach(Entity::remove);
        temporaryEntities.clear();
    }
}
