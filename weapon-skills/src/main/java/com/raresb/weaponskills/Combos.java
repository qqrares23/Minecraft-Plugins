package com.raresb.weaponskills;

import com.raresb.weaponskills.common.Root;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Cross-weapon combos. A skill hit from a combat weapon leaves that weapon's mark on the target for
 * a few seconds; a skill hit from a DIFFERENT weapon on a marked target triggers the mark's combo.
 */
final class Combos {
    private static final long MARK_MILLIS = 4000;

    /** The mark each combat weapon leaves, and what its combo does. Tools leave no mark. */
    enum Mark {
        EXPOSED(Skill.Weapon.SWORD, "Exposed", 0xDDEEFF, "Combo: +50% din lovitură ca daune bonus."),
        SUNDERED(Skill.Weapon.AXE, "Sundered", 0xC0392B, "Combo: armura țintei scade cu 6 timp de 5 secunde, +25% daune."),
        STAGGERED(Skill.Weapon.SHIELD, "Staggered", 0x8794A1, "Combo: ținta e amețită 1,5 secunde și împinsă."),
        MARKED(Skill.Weapon.BOW, "Marked", 0x3FA65B, "Combo: +35% daune bonus și ținta strălucește 5 secunde."),
        BOLTED(Skill.Weapon.CROSSBOW, "Bolted", 0xE08A2B, "Combo: o mică explozie (fără blocuri) rănește tot din jurul țintei."),
        SOAKED(Skill.Weapon.TRIDENT, "Soaked", 0x1FA5A0, "Combo: un fulger lovește ținta pentru +60% daune."),
        DAZED(Skill.Weapon.MACE, "Dazed", 0x8E5BBF, "Combo: ținta e amețită 2 secunde."),
        PINNED(Skill.Weapon.SPEAR, "Pinned", 0xB8860B, "Combo: ținta e țintuită 1,5 secunde, +25% daune.");

        private final Skill.Weapon weapon;
        private final String displayName;
        private final int color;
        private final String description;

        Mark(Skill.Weapon weapon, String displayName, int color, String description) {
            this.weapon = weapon;
            this.displayName = displayName;
            this.color = color;
            this.description = description;
        }

        String displayName() {
            return displayName;
        }

        String description() {
            return WeaponSkillsPlugin.text("combo." + name().toLowerCase(java.util.Locale.ROOT), description);
        }

        static Mark of(Skill.Weapon weapon) {
            for (Mark mark : values()) {
                if (mark.weapon == weapon) {
                    return mark;
                }
            }
            return null;
        }
    }

    private record Applied(Mark mark, UUID owner, long until) {
    }

    private final WeaponSkillsPlugin plugin;
    private final Map<UUID, Applied> marks = new HashMap<>();
    private final NamespacedKey sunderKey;

    Combos(WeaponSkillsPlugin plugin) {
        this.plugin = plugin;
        this.sunderKey = new NamespacedKey(plugin, "combo_sundered");
    }

    /** Called by Combat.hit after a skill hit landed (inside the no-chain tag). */
    void onSkillHit(Player player, LivingEntity target, Skill skill, double damage) {
        if (!plugin.getConfig().getBoolean("combos.enabled", true)) {
            return;
        }
        Mark incoming = Mark.of(skill.weapon());
        if (incoming == null || target.isDead()) {
            return;
        }
        long now = System.currentTimeMillis();
        Applied existing = marks.get(target.getUniqueId());
        if (existing != null && existing.until() > now && existing.owner().equals(player.getUniqueId())
                && existing.mark() != incoming) {
            marks.remove(target.getUniqueId());
            trigger(existing.mark(), player, target, damage);
            return; // the combo consumes the mark; the next skill hit marks again
        }
        marks.put(target.getUniqueId(), new Applied(incoming, player.getUniqueId(), now + MARK_MILLIS));
    }

    private void trigger(Mark mark, Player player, LivingEntity target, double damage) {
        World world = target.getWorld();
        var body = target.getLocation().add(0, target.getHeight() / 2, 0);
        world.spawnParticle(Particle.DUST, body, 25, 0.4, 0.5, 0.4, 0, Fx.dust(mark.color, 1.6f));
        world.playSound(body, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
        world.playSound(body, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
        player.sendActionBar(plugin.lang().get("combo-triggered", "mark", mark.displayName()));
        switch (mark) {
            case EXPOSED -> bonus(player, target, damage * 0.5);
            case SUNDERED -> {
                AttributeInstance armor = target.getAttribute(Attribute.ARMOR);
                if (armor != null && armor.getModifier(sunderKey) == null) {
                    armor.addTransientModifier(new AttributeModifier(sunderKey, -6, AttributeModifier.Operation.ADD_NUMBER));
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> armor.removeModifier(sunderKey), 100);
                }
                bonus(player, target, damage * 0.25);
            }
            case STAGGERED -> {
                stun(target, 30);
                Combat.knockBack(target, player.getLocation(), 0.8, 0.3);
            }
            case MARKED -> {
                bonus(player, target, damage * 0.35);
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            }
            case BOLTED -> {
                world.spawnParticle(Particle.EXPLOSION, body, 1);
                world.playSound(body, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                for (Entity near : target.getNearbyEntities(2.5, 2.5, 2.5)) {
                    if (near instanceof LivingEntity living && plugin.combat().canHit(player, living)) {
                        bonus(player, living, damage * 0.4);
                    }
                }
                bonus(player, target, damage * 0.4);
            }
            case SOAKED -> {
                world.strikeLightningEffect(target.getLocation());
                bonus(player, target, damage * 0.6);
            }
            case DAZED -> stun(target, 40);
            case PINNED -> {
                Root.apply(plugin, target, 30, 6);
                bonus(player, target, damage * 0.25);
            }
        }
    }

    /** Extra damage from a combo: a plain damage call, so it never triggers skills, combos or area effects. */
    private static void bonus(Player player, LivingEntity target, double amount) {
        if (amount <= 0 || target.isDead()) {
            return;
        }
        boolean marked = player.addScoreboardTag(Combat.NO_CHAIN);
        try {
            target.setNoDamageTicks(0);
            target.damage(amount, player);
            target.setNoDamageTicks(0);
        } finally {
            if (marked) {
                player.removeScoreboardTag(Combat.NO_CHAIN);
            }
        }
    }

    private void stun(LivingEntity target, int ticks) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 1));
        Fx.stunStars(plugin, target, ticks);
    }

    /** Twice a second: a small coloured puff over each marked target, and old marks dropped. */
    void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Applied>> it = marks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Applied> entry = it.next();
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (entry.getValue().until() <= now || !(entity instanceof LivingEntity living) || living.isDead()) {
                it.remove();
                continue;
            }
            living.getWorld().spawnParticle(Particle.DUST, living.getLocation().add(0, living.getHeight() + 0.4, 0), 3,
                    0.15, 0.05, 0.15, 0, Fx.dust(entry.getValue().mark().color, 1.0f));
        }
    }
}
