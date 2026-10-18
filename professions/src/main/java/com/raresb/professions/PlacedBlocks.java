package com.raresb.professions;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Remembers which blocks players placed, so placing and breaking blocks can't be farmed
 * for XP or finds. Stored in the chunk's own data as a packed long array, so it survives restarts;
 * an in-memory set per loaded chunk makes the lookups O(1) and avoids rewriting the array per block.
 */
final class PlacedBlocks implements Listener {
    private final NamespacedKey key;
    /** Packed positions per loaded chunk, loaded lazily from the chunk PDC; written back on unload. */
    private final Map<Chunk, Set<Long>> index = new ConcurrentHashMap<>();

    PlacedBlocks(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "placed");
    }

    private static long pack(Block block) {
        return ((long) (block.getY() + 4096) << 8) | ((block.getX() & 15) << 4) | (block.getZ() & 15);
    }

    /** The chunk's set, loaded from its PDC on first touch. */
    private Set<Long> load(Chunk chunk) {
        return index.computeIfAbsent(chunk, c -> {
            long[] positions = c.getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
            Set<Long> set = new HashSet<>(positions == null ? 16 : positions.length * 2);
            if (positions != null) {
                for (long position : positions) {
                    set.add(position);
                }
            }
            return set;
        });
    }

    boolean isPlaced(Block block) {
        return load(block.getChunk()).contains(pack(block));
    }

    void markPlaced(Block block) {
        load(block.getChunk()).add(pack(block));
    }

    void clear(Block block) {
        load(block.getChunk()).remove(pack(block));
    }

    /** Persists a chunk's set to its PDC in the packed-long format. */
    private void persist(Chunk chunk, Set<Long> set) {
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        if (set.isEmpty()) {
            data.remove(key);
            return;
        }
        long[] positions = new long[set.size()];
        int i = 0;
        for (long position : set) {
            positions[i++] = position;
        }
        data.set(key, PersistentDataType.LONG_ARRAY, positions);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(ChunkUnloadEvent event) {
        Set<Long> set = index.remove(event.getChunk());
        if (set != null) {
            persist(event.getChunk(), set);
        }
    }

    /** Writes every loaded chunk's set back on shutdown. */
    void shutdown() {
        index.forEach(this::persist);
        index.clear();
    }
}
