package com.raresb.weaponskills;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Targeting, damage and movement helpers shared by all skills. */
final class Combat {
    private final WeaponSkillsPlugin plugin;
    /** Players protected from fall damage until this time (ms), e.g. after a slam. */
    private final Map<UUID, Long> noFallUntil = new HashMap<>();

    Combat(WeaponSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Whether an area skill used by {@code player} should hit {@code target}. */
    /** Entity tag (shared by our plugins) set while one of our plugins deals skill/splash/bonus damage: no area effects chain off it. */
    static final String NO_CHAIN = "raresb_no_chain";

    boolean canHit(Player player, LivingEntity target) {
        if (target.equals(player) || target.isDead() || !target.isValid() || target instanceof ArmorStand) {
            return false;
        }
        if (target instanceof Tameable tameable && tameable.isTamed() && player.equals(tameable.getOwner())) {
            return false;
        }
        if (target instanceof Player other) {
            return other.getGameMode() == GameMode.SURVIVAL || other.getGameMode() == GameMode.ADVENTURE;
        }
        if (target instanceof Enemy || plugin.getConfig().getBoolean("hit-passive-mobs", false)) {
            return true;
        }
        // A passive mob that is fighting the player (e.g. an angry wolf or iron golem).
        return target instanceof Mob mob && player.equals(mob.getTarget());
    }

    List<LivingEntity> around(Player player, Location center, double radius) {
        return center.getNearbyLivingEntities(radius).stream()
                .filter(e -> canHit(player, e))
                .toList();
    }

    /** Targets within {@code range} in front of the player, inside a cone of {@code angle} degrees. */
    List<LivingEntity> inFront(Player player, double range, double angle) {
        Location eye = player.getEyeLocation();
        Vector facing = Aim.flat(player);
        double minDot = Math.cos(Math.toRadians(angle / 2));
        return around(player, player.getLocation(), range).stream()
                .filter(e -> {
                    Vector to = e.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                    return to.lengthSquared() < 0.5 || to.normalize().dot(facing) >= minDot;
                })
                .toList();
    }

    /** The player's melee attack damage with the weapon they hold. */
    static double weaponDamage(Player player) {
        AttributeInstance attack = player.getAttribute(Attribute.ATTACK_DAMAGE);
        return attack != null ? attack.getValue() : 1;
    }

    /**
     * Damages the target as an attack by the player (PvP rules and armor apply) and gives
     * the skill XP for the hit.
     */
    void hit(Player player, LivingEntity target, double damage, Skill skill) {
        // Skills hit several times in quick succession; skip the usual half-second immunity.
        target.setNoDamageTicks(0);
        // While set, other plugins' area effects (Cleaving, Chain Lightning, path splashes) don't fire off this
        // hit: a skill hitting 10 mobs would otherwise set off 10 more splashes each (shared by all our plugins).
        damage = plugin.augments().modifyHit(player, target, skill, damage);
        boolean marked = !player.getScoreboardTags().contains(NO_CHAIN);
        if (marked) {
            player.addScoreboardTag(NO_CHAIN);
        }
        try {
            target.damage(damage, player);
            plugin.combos().onSkillHit(player, target, skill, damage);
            plugin.augments().afterHit(player, target, skill, damage);
        } finally {
            if (marked) {
                player.removeScoreboardTag(NO_CHAIN);
            }
        }
        // Vanilla would now ignore the player's next normal hit for half a second (it is weaker
        // than this one): clear the invulnerability again so M1 hits right after a skill land.
        target.setNoDamageTicks(0);
        plugin.progress().onHit(player, skill);
    }

    /** Pushes the target away from {@code from}. */
    static void knockBack(LivingEntity target, Location from, double horizontal, double vertical) {
        Vector push = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (push.lengthSquared() < 1.0e-4) {
            push = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
        }
        // Knockback resistance (netherite, Steadfast...) also softens skill knockback, like vanilla knockback.
        AttributeInstance resistance = target.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        double keep = 1 - Math.max(0, Math.min(1, resistance != null ? resistance.getValue() : 0));
        target.setVelocity(target.getVelocity().add(push.normalize().multiply(horizontal * keep).setY(vertical * keep)));
    }

    /**
     * Server-side ground check (the client-reported on-ground flag can be faked): is there a
     * solid block just below any corner of the player's feet?
     */
    static boolean onGround(Player player) {
        Location feet = player.getLocation();
        double below = feet.getY() - 0.05;
        for (double dx : new double[] {-0.29, 0.29}) {
            for (double dz : new double[] {-0.29, 0.29}) {
                if (feet.getWorld().getBlockAt((int) Math.floor(feet.getX() + dx), (int) Math.floor(below),
                        (int) Math.floor(feet.getZ() + dz)).isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    void protectFromFall(Player player, long millis) {
        noFallUntil.merge(player.getUniqueId(), System.currentTimeMillis() + millis, Math::max);
    }

    boolean isFallProtected(Player player) {
        Long until = noFallUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    void forget(Player player) {
        noFallUntil.remove(player.getUniqueId());
    }

    /** Wears the item in the given hand like a normal hit would. */
    void wear(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack item = player.getInventory().getItem(hand);
        int cost = plugin.getConfig().getInt("durability-cost", 1);
        double chance = plugin.getConfig().getDouble("durability-chance", 0.5);
        if (!item.isEmpty() && cost > 0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < chance) {
            player.getInventory().setItem(hand, item.damage(cost, player));
        }
    }
}
