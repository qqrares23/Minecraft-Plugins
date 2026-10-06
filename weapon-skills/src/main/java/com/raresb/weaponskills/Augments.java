package com.raresb.weaponskills;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** The augments each player picked per skill (saved in their data) and what they do. */
final class Augments implements Listener {
    /** Who last hit a mob with which skill, for Reaping. */
    private record LastHit(UUID player, Skill skill, long time) {
    }

    private final WeaponSkillsPlugin plugin;
    private final Map<UUID, LastHit> lastHits = new HashMap<>();
    /** In-memory copy of each online player's picks: [skill ordinal][tier], loaded once from their data. */
    private final Map<UUID, Augment[][]> picks = new HashMap<>();

    Augments(WeaponSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(Skill skill, int tier) {
        return new NamespacedKey(plugin, "aug_" + skill.id() + "_" + tier);
    }

    /** Whether the tier's choice is open: the skill reached that tier's level. */
    boolean tierOpen(Player player, Skill skill, int tier) {
        return plugin.progress().level(player, skill) >= Augment.TIER_LEVELS[tier];
    }

    private Augment[][] picksOf(Player player) {
        return picks.computeIfAbsent(player.getUniqueId(), id -> {
            Augment[][] loaded = new Augment[Skill.values().length][Augment.TIER_LEVELS.length];
            for (Skill skill : Skill.values()) {
                for (int tier = 0; tier < Augment.TIER_LEVELS.length; tier++) {
                    String stored = player.getPersistentDataContainer().get(key(skill, tier), PersistentDataType.STRING);
                    Augment augment = stored == null ? null : Augment.fromId(stored);
                    loaded[skill.ordinal()][tier] = augment != null && Augment.options(skill, tier).contains(augment) ? augment : null;
                }
            }
            return loaded;
        });
    }

    /** The augment picked at a tier, or null (none picked yet, or the tier isn't open). */
    Augment chosen(Player player, Skill skill, int tier) {
        if (!plugin.augmentsEnabled()) {
            return null;
        }
        return tierOpen(player, skill, tier) ? picksOf(player)[skill.ordinal()][tier] : null;
    }

    void forget(Player player) {
        picks.remove(player.getUniqueId());
    }

    boolean has(Player player, Skill skill, Augment augment) {
        if (!plugin.augmentsEnabled()) {
            return false;
        }
        for (int tier = 0; tier < Augment.TIER_LEVELS.length; tier++) {
            if (chosen(player, skill, tier) == augment) {
                return true;
            }
        }
        return false;
    }

    /** Picks an augment. Returns false if it isn't offered at that tier or the tier isn't open yet. */
    boolean choose(Player player, Skill skill, int tier, Augment augment) {
        if (!tierOpen(player, skill, tier) || !Augment.options(skill, tier).contains(augment)) {
            return false;
        }
        player.getPersistentDataContainer().set(key(skill, tier), PersistentDataType.STRING, augment.id());
        picksOf(player)[skill.ordinal()][tier] = augment;
        return true;
    }

    /** Tiers open but not chosen yet (shown as a hint in the menu). */
    int pendingChoices(Player player, Skill skill) {
        int pending = 0;
        for (int tier = 0; tier < Augment.TIER_LEVELS.length; tier++) {
            if (tierOpen(player, skill, tier) && chosen(player, skill, tier) == null) {
                pending++;
            }
        }
        return pending;
    }

    // --- Effects ---

    /** MagicEnchants Resonance on the held weapon: augment effects +15% per level. */
    private double resonance(Player player) {
        return 1 + 0.15 * Math.min(2, plugin.magicLevel(player.getInventory().getItemInMainHand(), "resonance"));
    }

    private static int ticks(int base, double factor) {
        return (int) Math.round(base * factor);
    }

    double damageMultiplier(Player player, Skill skill) {
        double multiplier = 1;
        if (has(player, skill, Augment.BRUTAL)) {
            multiplier *= 1 + 0.25 * resonance(player);
        }
        if (has(player, skill, Augment.SWIFT)) {
            multiplier *= 0.9;
        }
        return multiplier;
    }

    double cooldownMultiplier(Player player, Skill skill) {
        double multiplier = 1;
        if (has(player, skill, Augment.BRUTAL)) {
            multiplier *= 1.15;
        }
        if (has(player, skill, Augment.SWIFT)) {
            multiplier *= 1 - 0.2 * resonance(player);
        }
        return multiplier;
    }

    /** Execute: more damage to targets under 30% health. Called before the hit lands. */
    double modifyHit(Player player, LivingEntity target, Skill skill, double damage) {
        if (has(player, skill, Augment.EXECUTE)) {
            var max = target.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && target.getHealth() < max.getValue() * 0.3) {
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, target.getHeight(), 0), 4, 0.2, 0.2, 0.2, 0);
                return damage * (1 + 0.4 * resonance(player));
            }
        }
        return damage;
    }

    /** On-hit augments, after the hit landed. */
    void afterHit(Player player, LivingEntity target, Skill skill, double damage) {
        lastHits.put(target.getUniqueId(), new LastHit(player.getUniqueId(), skill, System.currentTimeMillis()));
        double r = resonance(player);
        if (has(player, skill, Augment.SEARING)) {
            target.setFireTicks(Math.max(target.getFireTicks(), ticks(60, r)));
        }
        if (has(player, skill, Augment.FROST)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks(40, r), 1));
        }
        if (has(player, skill, Augment.VENOM)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks(60, r), 0));
        }
        if (has(player, skill, Augment.SUNDER)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks(80, r), 0));
        }
        if (has(player, skill, Augment.VAMPIRIC) && !player.isDead()) {
            var max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                player.setHealth(Math.min(max.getValue(), player.getHealth() + damage * 0.06 * r));
            }
        }
    }

    /** On-use augments, after the skill was used. Returns true if Echo kept it off cooldown. */
    boolean afterUse(Player player, Skill skill) {
        List<PotionEffect> effects = new java.util.ArrayList<>();
        double r = resonance(player);
        if (has(player, skill, Augment.GUARDED)) {
            effects.add(new PotionEffect(PotionEffectType.RESISTANCE, ticks(80, r), 0));
        }
        if (has(player, skill, Augment.FLEET)) {
            effects.add(new PotionEffect(PotionEffectType.SPEED, ticks(80, r), 1));
        }
        if (has(player, skill, Augment.VIGOR)) {
            effects.add(new PotionEffect(PotionEffectType.STRENGTH, ticks(100, r), 0));
        }
        effects.forEach(player::addPotionEffect);
        if (has(player, skill, Augment.MENDING)) {
            var max = player.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && !player.isDead()) {
                player.setHealth(Math.min(max.getValue(), player.getHealth() + 6 * r));
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2.2, 0), 3, 0.3, 0.2, 0.3, 0);
            }
        }
        return has(player, skill, Augment.ECHO) && ThreadLocalRandom.current().nextDouble() < 0.2 * r;
    }

    /** Reaping: a kill by a skill hit in the last second takes 30% off that skill's remaining cooldown. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LastHit hit = lastHits.remove(event.getEntity().getUniqueId());
        if (hit == null || System.currentTimeMillis() - hit.time() > 1000) {
            return;
        }
        Player player = plugin.getServer().getPlayer(hit.player());
        if (player != null && has(player, hit.skill(), Augment.REAPING)) {
            plugin.reduceCooldown(player, hit.skill(), Math.min(0.6, 0.3 * resonance(player)));
        }
    }

    /** Old entries for mobs that didn't die (called now and then). */
    void cleanUp() {
        long now = System.currentTimeMillis();
        lastHits.values().removeIf(hit -> now - hit.time() > 5000);
    }
}
