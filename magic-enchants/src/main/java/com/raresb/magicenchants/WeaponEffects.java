package com.raresb.magicenchants;

import com.raresb.magicenchants.common.Root;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Sword, axe and spear enchantments. */
final class WeaponEffects implements Listener {
    /** Chance-based effects only fire on hits with at least this much attack charge. */
    private static final float MIN_CHARGE = 0.8f;

    private final MagicEnchantsPlugin plugin;
    private final Map<UUID, Bleed> bleeding = new HashMap<>();
    private final Map<UUID, int[]> momentum = new HashMap<>(); // {stacks}
    private final Map<UUID, Long> momentumLastHit = new HashMap<>();

    private record Bleed(UUID attacker, int secondsLeft) {
    }

    WeaponEffects(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickBleeding, 20, 20);
    }

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max != null ? max.getValue() : 20;
    }

    /** Whether a chained / splash effect from the player should hit this entity. */
    static boolean validSecondaryTarget(Player player, LivingEntity entity, Entity primary) {
        if (entity.equals(player) || entity.equals(primary) || entity.isDead() || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Tameable tame && tame.isTamed() && player.equals(tame.getOwner())) {
            return false;
        }
        return !(entity instanceof Player other) || other.getGameMode() == GameMode.SURVIVAL || other.getGameMode() == GameMode.ADVENTURE;
    }

    // --- Damage bonuses (before armor, so other plugins see the final number) ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitDamage(EntityDamageByEntityEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        if (plugin.isDealingBonusDamage() || !(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        boolean charged = player.getAttackCooldown() >= MIN_CHARGE;

        int executioner = plugin.level(weapon, MagicEnchant.EXECUTIONER);
        if (executioner > 0 && target.getHealth() < maxHealth(target) * 0.3) {
            event.setDamage(event.getDamage() * (1 + 0.1 * executioner));
            target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() / 2, 0), 12,
                    0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0x8E44AD), 1.4f));
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.4f, 1.8f);
        }

        int thunder = plugin.level(weapon, MagicEnchant.THUNDERSTRIKE);
        if (thunder > 0 && charged && roll(thunder == 1 ? 0.05 : 0.08)) {
            // Only the visual and sound of lightning: no fire, and the attacker isn't hurt.
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.3);
            event.setDamage(event.getDamage() + (thunder == 1 ? 4 : 7));
        }

        int stackLevel = plugin.level(weapon, MagicEnchant.MOMENTUM);
        if (stackLevel > 0 && charged) {
            long now = System.currentTimeMillis();
            int[] stacks = momentum.computeIfAbsent(player.getUniqueId(), id -> new int[1]);
            long last = momentumLastHit.getOrDefault(player.getUniqueId(), 0L);
            stacks[0] = now - last <= 2000 ? Math.min(2 + stackLevel, stacks[0] + 1) : 0;
            momentumLastHit.put(player.getUniqueId(), now);
            if (stacks[0] > 0) {
                event.setDamage(event.getDamage() * (1 + 0.05 * stacks[0]));
                player.sendActionBar(plugin.lang().get("momentum", "stacks", "▮".repeat(stacks[0])));
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, target.getHeight() / 2, 0), 3 * stacks[0],
                        0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0xE67E22), 1.0f));
            }
        }

        int skyfall = plugin.level(weapon, MagicEnchant.SKYFALL);
        if (skyfall > 0 && player.getFallDistance() > 1.5) {
            event.setDamage(event.getDamage() * 1.35);
            target.setVelocity(target.getVelocity().setY(-0.8));
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, target.getHeight() / 2, 0), 20, 0.3, 0.4, 0.3, 0.2);
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_SPEAR_HIT, 1f, 0.6f);
        }
        int vanguard = plugin.level(weapon, MagicEnchant.VANGUARD);
        if (vanguard > 0 && player.getInventory().getItemInOffHand().getType() == Material.SHIELD) {
            event.setDamage(event.getDamage() * (1 + 0.08 * vanguard));
        }
    }

    // --- On-hit effects ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitEffects(EntityDamageByEntityEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        if (plugin.isDealingBonusDamage() || !(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        boolean charged = player.getAttackCooldown() >= MIN_CHARGE;
        Location body = target.getLocation().add(0, target.getHeight() / 2, 0);

        int bleed = plugin.level(weapon, MagicEnchant.BLEEDING);
        if (bleed > 0 && charged && roll(0.05 + 0.05 * bleed)) {
            bleeding.put(target.getUniqueId(), new Bleed(player.getUniqueId(), 2 + bleed));
            target.getWorld().playSound(body, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.8f);
            bleedParticles(target, 12);
            if (target instanceof Player victim) {
                victim.sendActionBar(plugin.lang().get("bleeding"));
            }
        }

        int venom = plugin.level(weapon, MagicEnchant.VENOM);
        if (venom > 0 && charged && roll(venom == 1 ? 0.15 : 0.25)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, venom - 1));
            target.getWorld().spawnParticle(Particle.ITEM_SLIME, body, 15, 0.3, 0.4, 0.3, 0);
            target.getWorld().playSound(body, Sound.ENTITY_SPIDER_HURT, 0.6f, 1.6f);
        }

        int frost = plugin.level(weapon, MagicEnchant.FROSTBITE);
        if (frost > 0 && charged && roll(frost == 1 ? 0.15 : 0.25)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            target.setFreezeTicks(Math.max(target.getFreezeTicks(), 160));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, body, 25, 0.4, 0.5, 0.4, 0.05);
            target.getWorld().playSound(body, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
        }

        int lifesteal = plugin.level(weapon, MagicEnchant.LIFESTEAL);
        if (lifesteal > 0 && event.getFinalDamage() > 0 && !player.isDead()) {
            double heal = event.getFinalDamage() * 0.06 * lifesteal;
            player.setHealth(Math.min(maxHealth(player), player.getHealth() + heal));
            player.getWorld().spawnParticle(Particle.DUST, body, 6, 0.3, 0.4, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(0x9B1B30), 1.2f));
            if (heal >= 1) {
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, player.getHeight() + 0.3, 0), 1);
            }
        }

        int cleaving = plugin.level(weapon, MagicEnchant.CLEAVING);
        // Area enchantments only fire off normal weapon hits, not off skill/splash damage (see NO_CHAIN).
        boolean chainable = !player.getScoreboardTags().contains(MagicEnchantsPlugin.NO_CHAIN);
        if (cleaving > 0 && charged && chainable && event.getFinalDamage() > 0) {
            double splash = event.getFinalDamage() * (0.1 + 0.1 * cleaving);
            for (LivingEntity near : target.getLocation().getNearbyLivingEntities(2.2)) {
                if (validSecondaryTarget(player, near, target)) {
                    plugin.bonusDamage(near, splash, player);
                    near.getWorld().spawnParticle(Particle.SWEEP_ATTACK, near.getLocation().add(0, 1, 0), 1);
                }
            }
        }

        int pinning = plugin.level(weapon, MagicEnchant.PINNING);
        if (pinning > 0 && charged && roll(pinning == 1 ? 0.08 : 0.12)) {
            Root.apply(plugin, target, 30, 6);
            target.getWorld().spawnParticle(Particle.CRIT, body, 15, 0.3, 0.2, 0.3, 0.1);
            target.getWorld().playSound(body, Sound.ITEM_SPEAR_HIT, 1f, 1.3f);
            if (target instanceof Player victim) {
                victim.sendActionBar(plugin.lang().get("pinned"));
            }
        }

        int chain = plugin.level(weapon, MagicEnchant.CHAIN_LIGHTNING);
        if (chain > 0 && charged && chainable && roll(chain == 1 ? 0.08 : 0.12)) {
            chainLightning(player, target, chain + 1, chain == 1 ? 3 : 5);
        }
    }

    /** Lightning that jumps from the target to up to {@code jumps} nearby enemies. */
    private void chainLightning(Player player, LivingEntity first, int jumps, double damage) {
        List<LivingEntity> chain = new ArrayList<>();
        LivingEntity current = first;
        for (int i = 0; i < jumps; i++) {
            LivingEntity from = current;
            LivingEntity next = from.getLocation().getNearbyLivingEntities(6).stream()
                    .filter(e -> validSecondaryTarget(player, e, first) && !chain.contains(e))
                    .min((a, b) -> Double.compare(a.getLocation().distanceSquared(from.getLocation()),
                            b.getLocation().distanceSquared(from.getLocation())))
                    .orElse(null);
            if (next == null) {
                break;
            }
            // A crackling line between the two.
            Location a = from.getLocation().add(0, from.getHeight() * 0.6, 0);
            Location b = next.getLocation().add(0, next.getHeight() * 0.6, 0);
            Vector step = b.toVector().subtract(a.toVector());
            double length = step.length();
            step.normalize().multiply(0.3);
            Location point = a.clone();
            for (double d = 0; d < length; d += 0.3) {
                point.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.05, 0.05, 0.05, 0);
                point.add(step);
            }
            plugin.bonusDamage(next, damage, player);
            next.getWorld().playSound(next.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
            chain.add(next);
            current = next;
        }
    }

    // --- Bleeding ---

    private void tickBleeding() {
        Iterator<Map.Entry<UUID, Bleed>> it = bleeding.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Bleed> entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            Bleed bleed = entry.getValue();
            if (!(entity instanceof LivingEntity target) || target.isDead() || !target.isValid() || bleed.secondsLeft() <= 0) {
                it.remove();
                continue;
            }
            Entity attacker = Bukkit.getEntity(bleed.attacker());
            DamageSource.Builder source = DamageSource.builder(DamageType.MAGIC);
            if (attacker != null) {
                // Credit the attacker, so bleeding kills still drop XP and loot like a player kill.
                source.withCausingEntity(attacker).withDirectEntity(attacker);
            }
            target.setNoDamageTicks(0);
            target.damage(1.0, source.build());
            target.setNoDamageTicks(0); // a bleed tick must not make the target ignore the next real hit
            bleedParticles(target, 6);
            entry.setValue(new Bleed(bleed.attacker(), bleed.secondsLeft() - 1));
        }
    }

    private static void bleedParticles(LivingEntity target, int count) {
        Location body = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        target.getWorld().spawnParticle(Particle.FALLING_DUST, body, count, 0.25, 0.3, 0.25, 0,
                Material.REDSTONE_BLOCK.createBlockData());
        target.getWorld().spawnParticle(Particle.DUST, body, count / 2, 0.25, 0.3, 0.25, 0,
                new Particle.DustOptions(Color.fromRGB(0x8B0000), 1.1f));
    }

    // --- Kills ---

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!plugin.enabledIn(event.getEntity().getWorld())) {
            return;
        }
        bleeding.remove(event.getEntity().getUniqueId());
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player) {
            return;
        }
        int harvest = plugin.level(killer.getInventory().getItemInMainHand(), MagicEnchant.SOUL_HARVEST);
        if (harvest > 0) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * (1 + 0.5 * harvest)));
            Location at = event.getEntity().getLocation().add(0, 0.5, 0);
            at.getWorld().spawnParticle(Particle.SOUL, at, 6, 0.3, 0.3, 0.3, 0.02);
            if (roll(0.05 * harvest)) {
                event.getDrops().add(plugin.soulFragment());
                at.getWorld().playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 1f);
                at.getWorld().spawnParticle(Particle.SCULK_SOUL, at, 12, 0.3, 0.4, 0.3, 0.05);
            }
        }
    }
}
