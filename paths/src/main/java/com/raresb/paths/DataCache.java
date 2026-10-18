package com.raresb.paths;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * In-memory copy of the player data this plugin reads on every hit (active paths, picked abilities and
 * talents, XP). Values are read from the player's data once, then served from memory; every write goes
 * to both. Dropped when the player leaves.
 */
final class DataCache {
    private static final Object NONE = new Object();
    private final Map<UUID, Map<NamespacedKey, Object>> values = new HashMap<>();
    /** The active-paths CSV parsed into an EnumSet once, re-parsed only when the string changes. */
    private final Map<UUID, Set<Path>> activePaths = new HashMap<>();

    private Map<NamespacedKey, Object> of(Player player) {
        return values.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
    }

    String getString(Player player, NamespacedKey key) {
        Object value = of(player).computeIfAbsent(key, k -> {
            String stored = player.getPersistentDataContainer().get(k, PersistentDataType.STRING);
            return stored == null ? NONE : stored;
        });
        return value == NONE ? null : (String) value;
    }

    void setString(Player player, NamespacedKey key, String value) {
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        of(player).put(key, value);
    }

    double getDouble(Player player, NamespacedKey key) {
        return (Double) of(player).computeIfAbsent(key,
                k -> player.getPersistentDataContainer().getOrDefault(k, PersistentDataType.DOUBLE, 0.0));
    }

    void setDouble(Player player, NamespacedKey key, double value) {
        player.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
        of(player).put(key, value);
    }

    /** The player's picked paths, parsed from the CSV at {@code key} once and served from memory after. */
    Set<Path> getActive(Player player, NamespacedKey key) {
        return activePaths.computeIfAbsent(player.getUniqueId(), id -> {
            Set<Path> paths = EnumSet.noneOf(Path.class);
            String raw = getString(player, key);
            if (raw != null) {
                for (String pathId : raw.split(",")) {
                    Path path = Path.fromId(pathId);
                    if (path != null) {
                        paths.add(path);
                    }
                }
            }
            return paths;
        });
    }

    /** Writes the picked paths as CSV and refreshes the parsed copy. */
    void setActive(Player player, NamespacedKey key, Set<Path> paths, String csv) {
        setString(player, key, csv);
        activePaths.put(player.getUniqueId(), EnumSet.copyOf(paths.isEmpty() ? EnumSet.noneOf(Path.class) : paths));
    }

    void forget(Player player) {
        values.remove(player.getUniqueId());
        activePaths.remove(player.getUniqueId());
    }
}
