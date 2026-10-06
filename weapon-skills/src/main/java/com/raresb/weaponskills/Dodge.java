package com.raresb.weaponskills;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.util.Vector;

/**
 * Dodge roll: double-tap sneak while holding a combat weapon to roll in the direction you are
 * moving (backwards if standing still). The roll has invulnerability frames: for half a second
 * nothing hurts you.
 */
final class Dodge implements Listener {
    /** When the player last rolled (ms); read by MagicEnchants Afterimage. */
    private static final org.bukkit.NamespacedKey DODGED_AT = new org.bukkit.NamespacedKey("weaponskills", "dodged_at");
    private final WeaponSkillsPlugin plugin;
    /** When each player last pressed sneak (ms), to spot a double tap. */
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Map<UUID, Long> readyAt = new HashMap<>();
    private final Map<UUID, Long> untouchableUntil = new HashMap<>();

    Dodge(WeaponSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    /** When the player's dodge is ready again (ms), 0 if it is ready. */
    long readyAt(Player player) {
        return readyAt.getOrDefault(player.getUniqueId(), 0L);
    }

    void forget(Player player) {
        lastSneak.remove(player.getUniqueId());
        readyAt.remove(player.getUniqueId());
        untouchableUntil.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long before = lastSneak.put(player.getUniqueId(), now);
        if (before != null && now - before <= plugin.getConfig().getLong("dodge.double-tap-millis", 300)) {
            lastSneak.remove(player.getUniqueId());
            roll(player, now);
        }
    }

    private void roll(Player player, long now) {
        if (!plugin.getConfig().getBoolean("dodge.enabled", true) || !player.hasPermission("weaponskills.use")
                || !player.hasPermission("weaponskills.dodge") || !plugin.worldEnabled(player)
                || !holdsCombatWeapon(player) || !Combat.onGround(player) || player.isInsideVehicle()) {
            return;
        }
        long ready = readyAt(player);
        if (ready > now) {
            plugin.flash(player, plugin.lang().get("dodge-cooldown", "seconds", String.format(Locale.ROOT, "%.1f", (ready - now) / 1000.0)));
            return;
        }
        // Roll where the movement keys point, relative to where the player looks; backwards if none are held.
        Input input = player.getCurrentInput();
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1.0e-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Vector direction = new Vector();
        if (input.isForward()) {
            direction.add(forward);
        }
        if (input.isBackward()) {
            direction.subtract(forward);
        }
        if (input.isRight()) {
            direction.add(right);
        }
        if (input.isLeft()) {
            direction.subtract(right);
        }
        if (direction.lengthSquared() < 1.0e-4) {
            direction = forward.clone().multiply(-1);
        }
        player.setVelocity(direction.normalize().multiply(plugin.getConfig().getDouble("dodge.speed", 1.0)).setY(0.22));

        // Fleetfoot (a MagicEnchants enchantment on the boots): -20% cooldown per level.
        double fleetfoot = 1 - 0.2 * plugin.magicLevel(player.getInventory().getBoots(), "fleetfoot");
        readyAt.put(player.getUniqueId(), now + (long) (plugin.getConfig().getDouble("dodge.cooldown", 3) * 1000 * fleetfoot));
        // MagicEnchants Afterimage reads this: the first hit soon after a roll deals more damage.
        player.getPersistentDataContainer().set(DODGED_AT, org.bukkit.persistence.PersistentDataType.LONG, now);
        untouchableUntil.put(player.getUniqueId(), now + plugin.getConfig().getLong("dodge.untouchable-ticks", 10) * 50);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.2, 0), 10, 0.3, 0.1, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.7f);
        plugin.publishCooldowns(player);
    }

    /** Swords, axes, bows, crossbows, tridents and maces - or a shield in either hand. Not tools. */
    private static boolean holdsCombatWeapon(Player player) {
        Skill.Weapon weapon = Skill.Weapon.of(player.getInventory().getItemInMainHand().getType());
        return (weapon != null && !weapon.isTool())
                || player.getInventory().getItemInMainHand().getType() == Material.SHIELD
                || player.getInventory().getItemInOffHand().getType() == Material.SHIELD;
    }

    private boolean rolling(Player player) {
        return untouchableUntil.getOrDefault(player.getUniqueId(), 0L) >= System.currentTimeMillis();
    }

    /**
     * Invulnerability frames: while rolling, nothing hurts the player - attacks, arrows, explosions,
     * magic, fire, falls. Only the void, /kill, starving and the world border still do.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !rolling(player)) {
            return;
        }
        switch (event.getCause()) {
            case VOID, KILL, SUICIDE, STARVATION, WORLD_BORDER -> {
            }
            default -> {
                event.setCancelled(true);
                if (event instanceof EntityDamageByEntityEvent) {
                    plugin.flash(player, plugin.lang().get("dodged"));
                    player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_DEFLECT, 0.6f, 1.5f);
                }
            }
        }
    }

    /** Harmful effects from attacks, arrows and thrown or lingering potions miss a rolling player too. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && rolling(player) && event.getNewEffect() != null
                && event.getNewEffect().getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
            switch (event.getCause()) {
                case ATTACK, ARROW, POTION_SPLASH, AREA_EFFECT_CLOUD, WITHER_ROSE -> event.setCancelled(true);
                default -> {
                }
            }
        }
    }
}
