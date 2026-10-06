package com.raresb.mightymobs;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.ZombieHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Despawning for the mounts of special spawns (horseman horses, jockey chickens). They are animals
 * (and the horses are tamed), so vanilla never despawns them – and a mob riding something never
 * despawns either. Before 1.7.2 every Skeleton Horseman and Chicken Jockey stayed in the world for
 * good. Now a mount is removed (with its rider) when no player is within the despawn range, and a
 * riderless one when no player is near. A mount a player saddles, leashes or rides is left alone.
 */
final class Mounts implements Listener {
    /** Like vanilla's instant despawn distance for monsters. */
    private static final double DESPAWN_RANGE = 128;
    /** A riderless mount goes once nobody is this close. */
    private static final double RIDERLESS_RANGE = 24;

    private static final NamespacedKey BASE_NAME = new NamespacedKey("mightymobs", "base_name");

    private final NamespacedKey mountKey;
    private final Set<UUID> tracked = new HashSet<>();

    Mounts(MightyMobsPlugin plugin) {
        this.mountKey = new NamespacedKey(plugin, "mount");
    }

    void mark(LivingEntity mount) {
        mount.getPersistentDataContainer().set(mountKey, PersistentDataType.BYTE, (byte) 1);
        tracked.add(mount.getUniqueId());
    }

    /** Checks every mount already loaded (also removes the leftovers from before 1.7.2). */
    int sweepLoaded() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                removed += consider(entity) ? 1 : 0;
            }
        }
        return removed;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            consider(entity);
        }
    }

    /** Runs every 10 seconds. */
    void tick() {
        tracked.removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            // Unloaded or dead: forget it; it's checked again when its chunk loads.
            return !(entity instanceof LivingEntity living) || !living.isValid() || check(living);
        });
    }

    /** Starts tracking a mount; true if it was removed right away. */
    private boolean consider(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !isMount(living)) {
            return false;
        }
        if (check(living)) {
            return true;
        }
        tracked.add(living.getUniqueId());
        return false;
    }

    /** True when the mount is no longer ours to manage (removed, or claimed by a player). */
    private boolean check(LivingEntity mount) {
        if (claimed(mount)) {
            mount.getPersistentDataContainer().remove(mountKey);
            return true;
        }
        boolean riderless = mount.getPassengers().isEmpty();
        if (!playerWithin(mount.getLocation(), riderless ? RIDERLESS_RANGE : DESPAWN_RANGE)) {
            mount.getPassengers().forEach(Entity::remove);
            mount.remove();
            return true;
        }
        return false;
    }

    private boolean isMount(LivingEntity entity) {
        if (entity.getPersistentDataContainer().has(mountKey, PersistentDataType.BYTE)) {
            return true;
        }
        // Mounts spawned before 1.7.2 carry no tag: they are the only CUSTOM-spawned chickens and
        // ownerless tamed skeleton/zombie horses on this server.
        if (entity.getEntitySpawnReason() != SpawnReason.CUSTOM) {
            return false;
        }
        if (entity instanceof SkeletonHorse || entity instanceof ZombieHorse) {
            AbstractHorse horse = (AbstractHorse) entity;
            return horse.isTamed() && horse.getOwner() == null && !claimed(entity);
        }
        return entity instanceof Chicken && !claimed(entity);
    }

    private static boolean claimed(LivingEntity mount) {
        // base_name holds a name-tag name (the custom name itself is the HP display).
        if (mount.isLeashed() || mount.getPersistentDataContainer().has(BASE_NAME, PersistentDataType.STRING)) {
            return true;
        }
        for (Entity passenger : mount.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        if (mount instanceof AbstractHorse horse) {
            return horse.getOwner() != null || horse.getInventory().getSaddle() != null;
        }
        return false;
    }

    private static boolean playerWithin(Location at, double range) {
        double rangeSquared = range * range;
        for (Player player : at.getWorld().getPlayers()) {
            if (!player.isDead() && player.getLocation().distanceSquared(at) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }
}
