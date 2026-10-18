package com.raresb.hud;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

/** Floating numbers on hit (gold for critical hits), seen only by the player who dealt the damage. */
final class DamageNumbers {
    private static final int RISE_TICKS = 16;
    /** At most this many numbers per player per tick: an area skill on a crowd would spawn dozens of entities at once. */
    private static final int MAX_PER_TICK = 8;

    private final HudPlugin plugin;
    private final Set<TextDisplay> live = new HashSet<>();
    /** Per attacker: the tick of their last number and how many were shown in it. */
    private final Map<UUID, long[]> perTick = new HashMap<>();

    DamageNumbers(HudPlugin plugin) {
        this.plugin = plugin;
    }

    void forget(Player player) {
        perTick.remove(player.getUniqueId());
    }

    void show(Player attacker, LivingEntity target, double damage, boolean critical) {
        long tick = plugin.getServer().getCurrentTick();
        long[] counter = perTick.computeIfAbsent(attacker.getUniqueId(), id -> new long[2]);
        if (counter[0] != tick) {
            counter[0] = tick;
            counter[1] = 0;
        }
        if (++counter[1] > MAX_PER_TICK) {
            return;
        }
        boolean hearts = !plugin.getConfig().getString("damage-numbers.unit", "hearts").equalsIgnoreCase("hp");
        String amount = String.format(Locale.ROOT, "%.1f", hearts ? damage / 2 : damage);
        Component text = critical
                ? Component.text("✦ " + amount, NamedTextColor.GOLD, TextDecoration.BOLD)
                : Component.text(amount, NamedTextColor.WHITE);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = target.getLocation().add(random.nextDouble(-0.5, 0.5), target.getHeight() * 0.75, random.nextDouble(-0.5, 0.5));
        TextDisplay number = target.getWorld().spawn(at, TextDisplay.class, display -> {
            display.setPersistent(false); // never saved with the chunk
            display.setVisibleByDefault(false);
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(true);
            display.setSeeThrough(true);
            display.setTeleportDuration(RISE_TICKS);
        });
        attacker.showEntity(plugin, number);
        live.add(number);
        // Float up (the client smooths the teleport), then disappear.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (number.isValid()) {
                number.teleport(at.clone().add(0, 0.9, 0));
            }
        }, 2);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            live.remove(number);
            number.remove();
        }, RISE_TICKS + 4);
    }

    void shutdown() {
        live.forEach(TextDisplay::remove);
        live.clear();
    }
}
