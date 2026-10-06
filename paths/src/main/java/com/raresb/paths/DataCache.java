package com.raresb.paths;

import java.util.HashMap;
import java.util.Map;
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

    void forget(Player player) {
        values.remove(player.getUniqueId());
    }
}
