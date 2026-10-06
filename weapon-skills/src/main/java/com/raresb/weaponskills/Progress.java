package com.raresb.weaponskills;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Skill levels: every use (and every enemy hit) gives the skill XP. Higher levels deal more
 * damage and have shorter cooldowns. Saved in the player's own data, so it survives restarts.
 */
final class Progress {
    private final WeaponSkillsPlugin plugin;
    /** Hit XP already granted for the current use of a skill (capped per use). */
    private final Map<UUID, Map<Skill, Integer>> hitXpThisUse = new HashMap<>();
    /**
     * In-memory copy of each online player's skill XP (index = skill ordinal) and the levels worked out
     * from it (-1 = not computed). Skills read levels many times per hit (damage, augments, mastery);
     * reading saved data every time was the main per-hit cost. All writes go through this class.
     */
    private final Map<UUID, int[]> xpCache = new HashMap<>();
    private final Map<UUID, int[]> levelCache = new HashMap<>();
    private final NamespacedKey[] keys = new NamespacedKey[Skill.values().length];

    Progress(WeaponSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(Skill skill) {
        NamespacedKey key = keys[skill.ordinal()];
        if (key == null) {
            key = new NamespacedKey(plugin, "xp_" + skill.id());
            keys[skill.ordinal()] = key;
        }
        return key;
    }

    private int[] xpOf(Player player) {
        return xpCache.computeIfAbsent(player.getUniqueId(), id -> {
            migrate(player);
            int[] xp = new int[Skill.values().length];
            for (Skill skill : Skill.values()) {
                xp[skill.ordinal()] = player.getPersistentDataContainer().getOrDefault(key(skill), PersistentDataType.INTEGER, 0);
            }
            return xp;
        });
    }

    /**
     * Grapple Arrow was replaced by Wind Arrow (1.14.0): its XP moves over, so bow mastery stays the same.
     * Its augment picks are dropped (Wind Arrow is a damage skill with other augment choices).
     */
    private void migrate(Player player) {
        var data = player.getPersistentDataContainer();
        NamespacedKey grapple = new NamespacedKey(plugin, "xp_grapple_arrow");
        Integer old = data.get(grapple, PersistentDataType.INTEGER);
        if (old != null) {
            data.set(key(Skill.WIND_ARROW), PersistentDataType.INTEGER,
                    data.getOrDefault(key(Skill.WIND_ARROW), PersistentDataType.INTEGER, 0) + old);
            data.remove(grapple);
            for (int tier : Augment.TIER_LEVELS) {
                data.remove(new NamespacedKey(plugin, "aug_grapple_arrow_" + tier));
            }
        }
    }

    private void writeXp(Player player, Skill skill, int xp) {
        player.getPersistentDataContainer().set(key(skill), PersistentDataType.INTEGER, xp);
        xpOf(player)[skill.ordinal()] = xp;
        int[] levels = levelCache.get(player.getUniqueId());
        if (levels != null) {
            levels[skill.ordinal()] = -1;
        }
    }

    /** Levels depend on the config (xp-per-level, max-level): forget them after a reload. */
    void clearLevelCache() {
        levelCache.clear();
    }

    int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("progression.max-level", 10));
    }

    /** XP needed to go from {@code level} to the next level. */
    int xpForNext(int level) {
        return plugin.getConfig().getInt("progression.xp-per-level", 15) * level;
    }

    int xp(Player player, Skill skill) {
        return xpOf(player)[skill.ordinal()];
    }

    int level(Player player, Skill skill) {
        int[] levels = levelCache.computeIfAbsent(player.getUniqueId(), id -> {
            int[] fresh = new int[Skill.values().length];
            java.util.Arrays.fill(fresh, -1);
            return fresh;
        });
        int cached = levels[skill.ordinal()];
        if (cached < 0) {
            cached = computeLevel(xp(player, skill));
            levels[skill.ordinal()] = cached;
        }
        return cached;
    }

    private int computeLevel(int xp) {
        int level = 1;
        while (level < maxLevel() && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
        }
        return level;
    }

    /**
     * The level that counts for a skill's cooldown reduction and effect length. Levels above
     * progression.bonus-cap-level don't shorten cooldowns further (damage keeps growing, see damageMultiplier).
     */
    int bonusLevel(Player player, Skill skill) {
        return Math.min(level(player, skill), plugin.getConfig().getInt("progression.bonus-cap-level", 20));
    }

    /** XP gained inside the current level, and XP needed for the next one (0 at max level). */
    int[] levelProgress(Player player, Skill skill) {
        int xp = xp(player, skill);
        int level = 1;
        while (level < maxLevel() && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
        }
        return level >= maxLevel() ? new int[] {0, 0} : new int[] {xp, xpForNext(level)};
    }

    /**
     * Skill damage grows over every level up to max-level, back-loaded: low levels stay well below the old
     * strength and only a max-level skill hits as hard as a level-20 skill used to (x1.95).
     */
    double damageMultiplier(Player player, Skill skill) {
        var config = plugin.getConfig();
        double start = config.getDouble("progression.damage-at-level-1", 0.6);
        double end = config.getDouble("progression.damage-at-max-level", 1.95);
        double curve = Math.max(0.1, config.getDouble("progression.damage-curve", 1.5));
        double progress = maxLevel() <= 1 ? 1 : Math.min(1, (level(player, skill) - 1) / (double) (maxLevel() - 1));
        return start + (end - start) * Math.pow(progress, curve);
    }

    double cooldownMultiplier(Player player, Skill skill) {
        return Math.max(0.3, 1 - plugin.getConfig().getDouble("progression.cooldown-reduction-per-level", 0.02) * (bonusLevel(player, skill) - 1));
    }

    /** Called when the skill is used. */
    void onUse(Player player, Skill skill) {
        hitXpThisUse.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Skill.class)).put(skill, 0);
        addXp(player, skill, plugin.getConfig().getInt("progression.xp-per-use", 5));
    }

    /** Called for every enemy the skill hits; capped per use so crowds don't give endless XP. */
    void onHit(Player player, Skill skill) {
        Map<Skill, Integer> granted = hitXpThisUse.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Skill.class));
        int already = granted.getOrDefault(skill, 0);
        int cap = plugin.getConfig().getInt("progression.max-hit-xp-per-use", 5);
        if (already < cap) {
            granted.put(skill, already + 1);
            addXp(player, skill, plugin.getConfig().getInt("progression.xp-per-hit", 1));
        }
    }

    /** Adds XP to a skill (level-ups and new unlocks are announced). Returns false at max level. */
    boolean addXp(Player player, Skill skill, int amount) {
        if (amount <= 0) {
            return false;
        }
        int before = level(player, skill);
        if (before >= maxLevel()) {
            return false;
        }
        writeXp(player, skill, xp(player, skill) + amount);
        int after = level(player, skill);
        if (after > before) {
            levelUp(player, skill, after);
            // Mastery is only needed on a level-up (it reads every skill of the weapon); only this skill changed.
            int masteryAfter = plugin.loadout().mastery(player, skill.weapon());
            int masteryBefore = masteryAfter - (after - before);
            for (Skill other : Skill.values()) {
                if (other.weapon() == skill.weapon() && other.unlockMastery() > masteryBefore
                        && other.unlockMastery() <= masteryAfter) {
                    announceUnlock(player, other);
                }
            }
        }
        return true;
    }

    private void announceUnlock(Player player, Skill skill) {
        player.sendMessage(plugin.lang().get("skill-unlocked", "skill", skill.icon() + " " + skill.displayName()));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
    }

    private void levelUp(Player player, Skill skill, int level) {
        player.showTitle(Title.title(
                Component.text(skill.icon() + " " + skill.displayName(), NamedTextColor.GOLD, TextDecoration.BOLD),
                plugin.lang().get(level >= maxLevel() ? "level-up.subtitle-max" : "level-up.subtitle", "level", level),
                Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1800), java.time.Duration.ofMillis(500))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
        player.sendMessage(plugin.lang().get("level-up.chat", "skill", skill.displayName(), "level", level));
        if (level == plugin.getConfig().getInt("progression.evolve-level", 10)) {
            player.sendMessage(plugin.lang().get("level-up.evolved", "skill", skill.displayName(), "evolution", Evolutions.of(skill)));
            player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.4f, 1.6f);
        }
    }

    /** Sets a skill straight to a level (admin command). */
    void setLevel(Player player, Skill skill, int level) {
        int xp = 0;
        for (int n = 1; n < level; n++) {
            xp += xpForNext(n);
        }
        writeXp(player, skill, xp);
    }

    void forget(Player player) {
        hitXpThisUse.remove(player.getUniqueId());
        xpCache.remove(player.getUniqueId());
        levelCache.remove(player.getUniqueId());
    }
}
