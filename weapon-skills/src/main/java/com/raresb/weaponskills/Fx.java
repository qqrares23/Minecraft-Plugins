package com.raresb.weaponskills;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Particle shapes and short animations used by the skills. */
final class Fx {
    private Fx() {
    }

    static Particle.DustOptions dust(int rgb, float size) {
        return new Particle.DustOptions(Color.fromRGB(rgb), size);
    }

    /** A flat ring of particles. */
    static <T> void ring(Location center, double radius, int points, Particle particle, T data) {
        World world = center.getWorld();
        if (particle == Particle.SWEEP_ATTACK) {
            // Sweep particles are big textures: a dense ring of them drops players' FPS. Keep it sparse.
            points = Math.min(points, Math.max(6, (int) (radius * 3)));
        }
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            world.spawnParticle(particle, center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 1, 0, 0, 0, 0, data);
        }
    }

    /** A straight line of particles. */
    static <T> void line(Location from, Location to, double spacing, Particle particle, T data) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 1.0e-3) {
            return;
        }
        step.normalize().multiply(spacing);
        Location point = from.clone();
        World world = from.getWorld();
        for (double d = 0; d <= length; d += spacing) {
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
            point.add(step);
        }
    }

    /** A ring that grows from the center out to {@code maxRadius} over {@code ticks} ticks. */
    static <T> void shockwave(Plugin plugin, Location center, double maxRadius, int ticks, Particle particle, T data) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick++;
                double radius = maxRadius * tick / ticks;
                ring(center, radius, (int) Math.max(12, radius * 10), particle, data);
                if (tick >= ticks) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /** Stars circling above the head of a stunned mob. */
    static void stunStars(Plugin plugin, LivingEntity target, int ticks) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 2;
                if (tick > ticks || !target.isValid()) {
                    cancel();
                    return;
                }
                Location head = target.getEyeLocation().add(0, 0.45, 0);
                for (int i = 0; i < 3; i++) {
                    double angle = tick * 0.35 + Math.PI * 2 * i / 3;
                    head.getWorld().spawnParticle(Particle.WAX_OFF, head.clone().add(Math.cos(angle) * 0.45, 0, Math.sin(angle) * 0.45), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0, 2);
    }
}
