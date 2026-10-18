package com.raresb.magicenchants.common;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Roots and pins: strong Slowness plus no jumping. Jump Boost 200 used to block jumps, but since
 * 1.20.5 it adds jump strength instead, so a rooted mob that tried to jump was launched into the air.
 * Jumping is now blocked with a transient {@code <plugin>:root} JUMP_STRENGTH ×0 modifier.
 */
public final class Root {
    /** Server tick at which each rooted entity may jump again (overlapping roots extend it). */
    private static final Map<UUID, Integer> UNTIL = new HashMap<>();

    private Root() {
    }

    /** Slowness at {@code slowness} (6 = can't walk) and no jumping for {@code ticks}; stops the target. */
    public static void apply(Plugin plugin, LivingEntity target, int ticks, int slowness) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, slowness));
        noJump(plugin, target, ticks);
        target.setVelocity(new Vector());
    }

    /** No jumping for {@code ticks}. */
    public static void noJump(Plugin plugin, LivingEntity target, int ticks) {
        AttributeInstance jump = target.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump == null || ticks <= 0) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "root");
        UUID id = target.getUniqueId();
        UNTIL.merge(id, Bukkit.getCurrentTick() + ticks, Math::max);
        if (jump.getModifier(key) == null) {
            jump.addTransientModifier(new AttributeModifier(key, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Integer end = UNTIL.get(id);
            if (end != null && end > Bukkit.getCurrentTick()) {
                return; // a longer root is still running; its own task releases it
            }
            UNTIL.remove(id);
            jump.removeModifier(key);
        }, ticks);
    }
}
