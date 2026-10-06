package com.raresb.weaponskills;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Where skills aim. The look-up, look-down and ultimate keys are pressed while looking steeply up
 * or down, so a skill cast with one of them aims as if the player looked straight ahead: a rain of
 * axes lands on the ground in front instead of in the sky, a bolt flies forward instead of into
 * the floor. Skills where the real look direction is the point (grapples, mining) opt out with
 * {@link Skill#freeAim()}.
 */
final class Aim {
    /** Players whose last skill was cast with a look-up/look-down/ultimate key. */
    private static final Set<UUID> LEVELLED = new HashSet<>();

    private Aim() {
    }

    /** Called before every cast: whether this cast aims straight ahead. */
    static void setLevelled(Player player, boolean levelled) {
        if (levelled) {
            LEVELLED.add(player.getUniqueId());
        } else {
            LEVELLED.remove(player.getUniqueId());
        }
    }

    static boolean levelled(Player player) {
        return LEVELLED.contains(player.getUniqueId());
    }

    static void forget(Player player) {
        LEVELLED.remove(player.getUniqueId());
    }

    /**
     * The horizontal direction the player faces, from their yaw. Unlike
     * {@code getDirection().setY(0).normalize()} this is never zero (or NaN) when looking straight up or down.
     */
    static Vector flat(Player player) {
        double yaw = Math.toRadians(player.getLocation().getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** The direction shots and throws fly: the look direction, or straight ahead for a look-key cast. */
    static Vector direction(Player player) {
        return levelled(player) ? flat(player) : player.getEyeLocation().getDirection().normalize();
    }

    /** The enemy the player looks at within {@code range}, or else the closest one in front of them. */
    static LivingEntity lookedAt(Player player, Combat combat, double range) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        // The real look direction first: looking down at a mob below still picks it.
        RayTraceResult hit = world.rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.5,
                e -> e instanceof LivingEntity l && combat.canHit(player, l));
        if (hit == null && levelled(player)) {
            hit = world.rayTrace(eye, flat(player), range, FluidCollisionMode.NEVER, true, 0.8,
                    e -> e instanceof LivingEntity l && combat.canHit(player, l));
        }
        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            return target;
        }
        double near = levelled(player) ? Math.min(range, 8) : Math.min(range, 4);
        return combat.inFront(player, near, levelled(player) ? 90 : 70).stream()
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                .orElse(null);
    }

    /**
     * The spot on the ground the player aims at, for skills that hit an area (rains, barrages, leaps).
     * Aiming at the top of a block uses that spot. Aiming at a wall, a ceiling or the sky uses the
     * ground under that point instead, never the top of the rock. A look-key cast picks the nearest
     * enemy in front, or the ground {@code fallback} blocks ahead (at most 8).
     */
    static Location groundSpot(Player player, Combat combat, double range, double fallback) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        RayTraceResult hit = world.rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitBlockFace() == BlockFace.UP) {
            return hit.getHitPosition().toLocation(world);
        }
        Vector direction = eye.getDirection();
        double distance = fallback;
        if (levelled(player)) {
            LivingEntity target = combat.inFront(player, Math.min(range, 16), 70).stream()
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                    .orElse(null);
            if (target != null) {
                return target.getLocation();
            }
            direction = flat(player);
            distance = Math.min(fallback, 8);
            hit = world.rayTraceBlocks(eye, direction, distance, FluidCollisionMode.NEVER, true);
        }
        Location point = hit != null
                // Step back out of the wall/ceiling that was hit.
                ? hit.getHitPosition().toLocation(world).subtract(direction.clone().normalize().multiply(0.5))
                : eye.clone().add(direction.clone().normalize().multiply(distance));
        return toGround(point);
    }

    /** The first ground below a point (up to 40 blocks down), or the point itself over a void. */
    static Location toGround(Location point) {
        RayTraceResult down = point.getWorld().rayTraceBlocks(point, new Vector(0, -1, 0), 40, FluidCollisionMode.NEVER, true);
        return down != null ? down.getHitPosition().toLocation(point.getWorld()) : point;
    }
}
