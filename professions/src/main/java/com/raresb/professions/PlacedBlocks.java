package com.raresb.professions;

import java.util.Arrays;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Remembers which blocks players placed, so placing and breaking blocks can't be farmed
 * for XP or finds. Stored in the chunk's own data, so it survives restarts.
 */
final class PlacedBlocks {
    private final NamespacedKey key;

    PlacedBlocks(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "placed");
    }

    private static long pack(Block block) {
        return ((long) (block.getY() + 4096) << 8) | ((block.getX() & 15) << 4) | (block.getZ() & 15);
    }

    private long[] read(Chunk chunk) {
        long[] positions = chunk.getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
        return positions == null ? new long[0] : positions;
    }

    boolean isPlaced(Block block) {
        long packed = pack(block);
        for (long position : read(block.getChunk())) {
            if (position == packed) {
                return true;
            }
        }
        return false;
    }

    void markPlaced(Block block) {
        long packed = pack(block);
        long[] positions = read(block.getChunk());
        for (long position : positions) {
            if (position == packed) {
                return;
            }
        }
        long[] grown = Arrays.copyOf(positions, positions.length + 1);
        grown[positions.length] = packed;
        block.getChunk().getPersistentDataContainer().set(key, PersistentDataType.LONG_ARRAY, grown);
    }

    void clear(Block block) {
        long packed = pack(block);
        long[] positions = read(block.getChunk());
        long[] kept = Arrays.stream(positions).filter(p -> p != packed).toArray();
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
        if (kept.length == 0) {
            data.remove(key);
        } else if (kept.length != positions.length) {
            data.set(key, PersistentDataType.LONG_ARRAY, kept);
        }
    }
}
