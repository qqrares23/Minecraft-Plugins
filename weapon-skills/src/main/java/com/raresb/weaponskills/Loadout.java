package com.raresb.weaponskills;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Which skill a player has in each trigger slot of each weapon, and which skills they have
 * unlocked. Saved in the player's own data.
 */
final class Loadout {
    private final WeaponSkillsPlugin plugin;
    /** In-memory copy of each online player's slot choices (key "weapon_trigger"; "" = nothing stored). */
    private final java.util.Map<java.util.UUID, java.util.Map<String, String>> stored = new java.util.HashMap<>();

    Loadout(WeaponSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey key(Skill.Weapon weapon, Skill.Trigger slot) {
        return new NamespacedKey(plugin, "slot_" + weapon.id() + "_" + slot.id());
    }

    /**
     * Mastery of a weapon: the levels gained across all of its skills (every level above 1
     * is a point). New skills unlock at mastery thresholds.
     */
    int mastery(Player player, Skill.Weapon weapon) {
        int points = 0;
        for (Skill skill : Skill.values()) {
            if (skill.weapon() == weapon) {
                points += plugin.progress().level(player, skill) - 1;
            }
        }
        return points;
    }

    boolean unlocked(Player player, Skill skill) {
        return skill.unlockMastery() <= 0 || mastery(player, skill.weapon()) >= skill.unlockMastery();
    }

    /** The skill in a slot: the player's choice if it is still valid, otherwise the default. */
    Skill get(Player player, Skill.Weapon weapon, Skill.Trigger slot) {
        String raw = stored.computeIfAbsent(player.getUniqueId(), id -> new java.util.HashMap<>())
                .computeIfAbsent(weapon.id() + "_" + slot.id(),
                        k -> player.getPersistentDataContainer().getOrDefault(key(weapon, slot), PersistentDataType.STRING, ""));
        Skill chosen = raw.isEmpty() ? null : Skill.fromId(raw);
        if (chosen != null && chosen.weapon() == weapon && chosen.fits(slot) && unlocked(player, chosen)) {
            return chosen;
        }
        Skill fallback = Skill.defaultFor(weapon, slot);
        return fallback != null && unlocked(player, fallback) ? fallback : null;
    }

    /** The slot a skill is currently equipped in, or null. */
    Skill.Trigger slotOf(Player player, Skill skill) {
        for (Skill.Trigger slot : skill.weapon().slots()) {
            if (get(player, skill.weapon(), slot) == skill) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Puts a skill in a slot. If it was already in another slot, the two slots swap, so a
     * skill is never equipped twice. Returns false if the skill can't go there.
     */
    boolean assign(Player player, Skill skill, Skill.Trigger slot) {
        if (!skill.fits(slot) || !unlocked(player, skill)) {
            return false;
        }
        Skill.Weapon weapon = skill.weapon();
        Skill previous = get(player, weapon, slot);
        Skill.Trigger oldSlot = slotOf(player, skill);
        set(player, weapon, slot, skill);
        if (oldSlot != null && oldSlot != slot && previous != null && previous.fits(oldSlot)) {
            set(player, weapon, oldSlot, previous);
        }
        return true;
    }

    private void set(Player player, Skill.Weapon weapon, Skill.Trigger slot, Skill skill) {
        player.getPersistentDataContainer().set(key(weapon, slot), PersistentDataType.STRING, skill.id());
        stored.computeIfAbsent(player.getUniqueId(), id -> new java.util.HashMap<>()).put(weapon.id() + "_" + slot.id(), skill.id());
    }

    void forget(Player player) {
        stored.remove(player.getUniqueId());
    }
}
