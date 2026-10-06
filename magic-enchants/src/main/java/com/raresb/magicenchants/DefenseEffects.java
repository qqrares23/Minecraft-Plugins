package com.raresb.magicenchants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Defensive and "while worn/held" enchantments: Warding, Steadfast, Last Stand, Clarity, Antidote, Sure-footed, Vigor
 * and Impaler's Reach (the last four are transient attribute modifiers, refreshed twice a second).
 */
final class DefenseEffects implements Listener {
    private static final long LAST_STAND_COOLDOWN = 180_000;
    /** Projectiles that are skill or path-magic shots (WeaponSkills skill arrows, Paths bolts and fireballs). */
    private static final NamespacedKey[] MAGIC_PROJECTILES = {new NamespacedKey("weaponskills", "skill_arrow"),
            new NamespacedKey("paths", "bolt"), new NamespacedKey("paths", "fireball")};

    private final MagicEnchantsPlugin plugin;
    private final NamespacedKey vigorKey;
    private final NamespacedKey sureFootedKey;
    private final NamespacedKey steadfastKey;
    private final NamespacedKey reachKey;
    /** Kept across relogs on purpose (a relog must not reset the cooldown); one entry per player who used it. */
    private final Map<UUID, Long> lastStandReady = new HashMap<>();
    /** Set while this class re-applies a shortened potion effect, so it doesn't shorten it again. */
    private boolean reapplying;

    DefenseEffects(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
        this.vigorKey = new NamespacedKey(plugin, "vigor");
        this.sureFootedKey = new NamespacedKey(plugin, "sure_footed");
        this.steadfastKey = new NamespacedKey(plugin, "steadfast");
        this.reachKey = new NamespacedKey(plugin, "impalers_reach");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickWorn, 10, 10);
    }

    // --- Worn / held attribute bonuses ---

    private void tickWorn() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean here = plugin.enabledIn(player.getWorld());
            var inventory = player.getInventory();
            int vigor = here ? plugin.level(inventory.getLeggings(), MagicEnchant.VIGOR) : 0;
            int steadfast = here ? plugin.level(inventory.getLeggings(), MagicEnchant.STEADFAST) : 0;
            int sure = here ? plugin.level(inventory.getBoots(), MagicEnchant.SURE_FOOTED) : 0;
            int reach = here ? plugin.level(inventory.getItemInMainHand(), MagicEnchant.IMPALERS_REACH) : 0;
            setModifier(player, Attribute.MAX_HEALTH, vigorKey, 2.0 * vigor);
            setModifier(player, Attribute.KNOCKBACK_RESISTANCE, steadfastKey, 0.15 * steadfast);
            setModifier(player, Attribute.MOVEMENT_EFFICIENCY, sureFootedKey, sure > 0 ? 1.0 : 0);
            setModifier(player, Attribute.ENTITY_INTERACTION_RANGE, reachKey, 0.5 * reach);
        }
    }

    /** Keeps a transient modifier at {@code amount} (0 = removed). Transient modifiers are never saved with the player. */
    private static void setModifier(Player player, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(key);
        if (current != null && current.getAmount() == amount) {
            return;
        }
        if (current != null) {
            instance.removeModifier(key);
        }
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    // --- Warding and Last Stand ---

    /** Whether a hit is skill, path or enchantment damage rather than a plain weapon hit. */
    private static boolean magicHit(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager.getScoreboardTags().contains(MagicEnchantsPlugin.NO_CHAIN)) {
            return true; // skill hits, path splashes, enchantment bonus damage
        }
        if (damager instanceof Projectile projectile) {
            PersistentDataContainer data = projectile.getPersistentDataContainer();
            for (NamespacedKey key : MAGIC_PROJECTILES) {
                if (data.has(key)) {
                    return true;
                }
            }
            return false;
        }
        return event.getCause() == EntityDamageEvent.DamageCause.MAGIC; // bleeding ticks
    }

    /** Warding: less damage from skills, path abilities and enchantment effects. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMagicHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || !plugin.enabledIn(victim.getWorld())) {
            return;
        }
        int warding = plugin.level(victim.getEquipment() == null ? null : victim.getEquipment().getChestplate(), MagicEnchant.WARDING);
        if (warding > 0 && magicHit(event)) {
            event.setDamage(event.getDamage() * (1 - 0.06 * warding));
        }
    }

    /** Last Stand: once every 3 minutes, a killing blow leaves the player at one heart instead. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.enabledIn(player.getWorld())
                || player.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        switch (event.getCause()) {
            case VOID, KILL, SUICIDE, WORLD_BORDER -> {
                return;
            }
            default -> {
            }
        }
        long now = System.currentTimeMillis();
        if (plugin.armorLevel(player, MagicEnchant.LAST_STAND) <= 0 || lastStandReady.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        lastStandReady.put(player.getUniqueId(), now + LAST_STAND_COOLDOWN);
        event.setCancelled(true);
        player.setHealth(Math.min(2.0, WeaponEffects.maxHealth(player)));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.3);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.3f);
        player.sendActionBar(plugin.lang().get("last-stand"));
    }

    // --- Clarity and Steadfast: shorter effects ---

    /** Clarity shortens Blindness, Darkness and Nausea; Antidote Poison and Wither; Steadfast roots and pins (strong Slowness). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (reapplying || !(event.getEntity() instanceof Player player) || !plugin.enabledIn(player.getWorld())) {
            return;
        }
        PotionEffect effect = event.getNewEffect();
        if (effect == null || effect.isInfinite() || effect.getDuration() < 4
                || (event.getAction() != EntityPotionEffectEvent.Action.ADDED && event.getAction() != EntityPotionEffectEvent.Action.CHANGED)) {
            return;
        }
        PotionEffectType type = effect.getType();
        double factor = 1;
        if (type == PotionEffectType.BLINDNESS || type == PotionEffectType.DARKNESS || type == PotionEffectType.NAUSEA) {
            factor = 1 - 0.35 * plugin.level(player.getInventory().getHelmet(), MagicEnchant.CLARITY);
        } else if (type == PotionEffectType.POISON || type == PotionEffectType.WITHER) {
            factor = 1 - 0.35 * plugin.level(player.getInventory().getChestplate(), MagicEnchant.ANTIDOTE);
        } else if (type == PotionEffectType.SLOWNESS && effect.getAmplifier() >= 3) {
            factor = 1 - 0.25 * plugin.level(player.getInventory().getLeggings(), MagicEnchant.STEADFAST);
        }
        if (factor >= 1) {
            return;
        }
        event.setCancelled(true);
        reapplying = true;
        try {
            player.addPotionEffect(effect.withDuration(Math.max(1, (int) (effect.getDuration() * factor))));
        } finally {
            reapplying = false;
        }
    }
}
