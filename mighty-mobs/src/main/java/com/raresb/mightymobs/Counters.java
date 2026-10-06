package com.raresb.mightymobs;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * MagicEnchants counters to mob abilities (1.10.0): Anchor stops Blinker teleports, Purifier stops
 * Regenerating and Vampire healing. MagicEnchants marks hit mobs with a timestamp; both are soft
 * (nothing happens without MagicEnchants).
 */
final class Counters {
    private static final NamespacedKey ANCHORED = new NamespacedKey("magicenchants", "anchored_until");
    private static final NamespacedKey PURIFIED = new NamespacedKey("magicenchants", "purified_until");
    private static final Map<String, Optional<Enchantment>> ENCHANTS = new HashMap<>();

    private Counters() {
    }

    private static boolean marked(Entity mob, NamespacedKey key) {
        return mob.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L) > System.currentTimeMillis();
    }

    /** Whether the player's held weapon has a MagicEnchants enchantment (looked up once, by key). */
    private static boolean holds(Player player, String id) {
        Enchantment enchantment = ENCHANTS.computeIfAbsent(id, key -> Optional.ofNullable(RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT).get(net.kyori.adventure.key.Key.key("magicenchants", key)))).orElse(null);
        return enchantment != null && player.getInventory().getItemInMainHand().getEnchantmentLevel(enchantment) > 0;
    }

    /** Blinker can't teleport: anchored recently, or hit right now with an Anchor weapon. */
    static boolean anchored(Entity mob, Player hitter) {
        return marked(mob, ANCHORED) || (hitter != null && holds(hitter, "anchor"));
    }

    /** Regenerating and Vampire can't heal. */
    static boolean purified(Entity mob) {
        return marked(mob, PURIFIED);
    }
}
