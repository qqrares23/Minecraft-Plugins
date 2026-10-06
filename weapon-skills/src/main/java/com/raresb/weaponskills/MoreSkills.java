package com.raresb.weaponskills;

import com.raresb.weaponskills.common.Root;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The later sword, axe, trident, mace and shield skills (unlocked with more mastery) and the
 * tool skills for hoes, shovels and fishing rods.
 */
final class MoreSkills implements Listener {
    private final WeaponSkillsPlugin plugin;
    private final Combat combat;
    private final Skills skills;
    /** Display entities in flight (axes, anvils, harpoons, shields), removed if the plugin is disabled. */
    private final List<Entity> temporaryEntities = new ArrayList<>();

    /** Counter Stance: active until (ms), and the damage soaked up so far. */
    private final Map<UUID, Long> counterUntil = new HashMap<>();
    private final Map<UUID, Double> counterStored = new HashMap<>();
    /** Lumberjack's Fury: cleaving hits left and until when (ms). */
    private final Map<UUID, Integer> furyHits = new HashMap<>();
    private final Map<UUID, Long> furyUntil = new HashMap<>();
    /** Burrow: soft blocks break instantly until (ms). */
    private final Map<UUID, Long> burrowUntil = new HashMap<>();
    /** Set while dealing Fury's splash damage, so it doesn't trigger itself. */
    private boolean cleaving;

    MoreSkills(WeaponSkillsPlugin plugin, Combat combat, Skills skills) {
        this.plugin = plugin;
        this.combat = combat;
        this.skills = skills;
    }

    private static boolean active(Map<UUID, Long> until, Player player) {
        Long time = until.get(player.getUniqueId());
        return time != null && time > System.currentTimeMillis();
    }

    /** Heavy slowness and weakness for a moment, with stars around the head. */
    private void stun(LivingEntity target, int ticks) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 1));
        Fx.stunStars(plugin, target, ticks);
    }

    // ------------------------------------------------------------------ Sword

    /** A blade wave that flies forward and passes through everything in its way. */
    void crescentWave(Player player) {
        Vector aim = Aim.flat(player);
        boolean evolved = skills.evolved(player, Skill.CRESCENT_WAVE);
        double damage = skills.damage(player, Skill.CRESCENT_WAVE);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.6f);
        wave(player, aim, damage);
        if (evolved) {
            wave(player, aim.clone().rotateAroundY(Math.toRadians(20)), damage);
            wave(player, aim.clone().rotateAroundY(Math.toRadians(-20)), damage);
        }
        player.swingMainHand();
    }

    private void wave(Player player, Vector direction, double damage) {
        World world = player.getWorld();
        Vector side = direction.clone().rotateAroundY(Math.PI / 2);
        Set<UUID> alreadyHit = new HashSet<>();
        new BukkitRunnable() {
            final Location position = player.getLocation().add(0, 1.1, 0);
            int step;

            @Override
            public void run() {
                if (!player.isOnline() || ++step > 16 || !position.getBlock().isPassable()) {
                    cancel();
                    return;
                }
                position.add(direction);
                for (double w = -1.3; w <= 1.3; w += 0.26) {
                    // A crescent: the tips trail behind the middle.
                    Location point = position.clone().add(side.clone().multiply(w)).subtract(direction.clone().multiply(w * w * 0.35));
                    world.spawnParticle(Particle.DUST, point, 1, 0, 0.05, 0, 0, Fx.dust(0xDDEEFF, 1.3f));
                }
                world.spawnParticle(Particle.SWEEP_ATTACK, position, 1);
                for (LivingEntity target : combat.around(player, position, 1.6)) {
                    if (alreadyHit.add(target.getUniqueId())) {
                        combat.hit(player, target, damage, Skill.CRESCENT_WAVE);
                        Combat.knockBack(target, position.clone().subtract(direction), 0.5, 0.2);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /** Soak up damage for 2 seconds, then release it in one hit. */
    void counterStance(Player player) {
        UUID id = player.getUniqueId();
        counterUntil.put(id, System.currentTimeMillis() + 2000);
        counterStored.put(id, 0.0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false, true));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.6f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    counterUntil.remove(id);
                    counterStored.remove(id);
                    return;
                }
                if (++tick < 40) {
                    if (tick % 4 == 0) {
                        Fx.ring(player.getLocation().add(0, 0.1, 0), 1.1, 16, Particle.DUST, Fx.dust(0xB0B0B0, 1.1f));
                    }
                    return;
                }
                cancel();
                counterUntil.remove(id);
                double stored = counterStored.getOrDefault(id, 0.0);
                counterStored.remove(id);
                releaseCounter(player, stored);
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private void releaseCounter(Player player, double stored) {
        boolean evolved = skills.evolved(player, Skill.COUNTER_STANCE);
        double damage = skills.damage(player, Skill.COUNTER_STANCE) + Math.min(40, stored) * 1.5;
        World world = player.getWorld();
        Location body = player.getLocation().add(0, 1, 0);
        world.playSound(body, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.6f);
        world.playSound(body, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        world.spawnParticle(Particle.SWEEP_ATTACK, body, 6, 1.2, 0.3, 1.2);
        world.spawnParticle(Particle.CRIT, body, 40, 1.5, 0.5, 1.5, 0.3);
        List<LivingEntity> targets = evolved ? combat.around(player, player.getLocation(), 4.5) : combat.inFront(player, 4, 120);
        for (LivingEntity target : targets) {
            combat.hit(player, target, damage, Skill.COUNTER_STANCE);
            Combat.knockBack(target, player.getLocation(), 0.9, 0.35);
        }
        if (stored > 0) {
            plugin.flash(player, plugin.lang().get("counter-released", "damage", Math.round(Math.min(40, stored) * 1.5)));
        }
        player.swingMainHand();
    }

    /** Counter Stance halves (evolved: -70%) the damage the player takes and stores it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCounterHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !active(counterUntil, player)) {
            return;
        }
        double cut = skills.evolved(player, Skill.COUNTER_STANCE) ? 0.7 : 0.5;
        counterStored.merge(player.getUniqueId(), event.getDamage() * cut, Double::sum);
        event.setDamage(event.getDamage() * (1 - cut));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.4f);
    }

    /** Lightning-fast cuts into one target. */
    boolean thousandCuts(Player player) {
        LivingEntity target = Aim.lookedAt(player, combat, 5);
        if (target == null) {
            plugin.flash(player, plugin.lang().get("no-target"));
            return false;
        }
        boolean evolved = skills.evolved(player, Skill.THOUSAND_CUTS);
        double damage = skills.damage(player, Skill.THOUSAND_CUTS);
        int cuts = evolved ? 20 : 12;
        World world = player.getWorld();
        new BukkitRunnable() {
            int cut;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || target.isDead() || ++cut > cuts
                        || target.getLocation().distanceSquared(player.getLocation()) > 49) {
                    cancel();
                    return;
                }
                boolean last = cut == cuts;
                combat.hit(player, target, last && evolved ? damage * 3 : damage, Skill.THOUSAND_CUTS);
                Location body = target.getLocation().add(0, target.getHeight() / 2, 0);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                world.spawnParticle(Particle.SWEEP_ATTACK, body.clone().add(random.nextDouble(-0.4, 0.4), random.nextDouble(-0.4, 0.4),
                        random.nextDouble(-0.4, 0.4)), 1);
                world.playSound(body, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.2f + cut * 0.04f);
                if (last && evolved) {
                    world.spawnParticle(Particle.CRIT, body, 30, 0.3, 0.4, 0.3, 0.4);
                    world.playSound(body, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
                }
                if (cut % 2 == 0) {
                    player.swingMainHand();
                }
            }
        }.runTaskTimer(plugin, 0, 2);
        return true;
    }

    // -------------------------------------------------------------------- Axe

    /** Axes rain down on the spot the player looks at. */
    void tomahawkRain(Player player) {
        Location center = Aim.groundSpot(player, combat, 30, 20);
        boolean evolved = skills.evolved(player, Skill.TOMAHAWK_RAIN);
        double spread = evolved ? 5 : 3.5;
        int count = evolved ? 18 : 10;
        double damage = skills.damage(player, Skill.TOMAHAWK_RAIN);
        ItemStack axe = player.getInventory().getItemInMainHand().clone();
        World world = center.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.6f);
        Fx.ring(center.clone().add(0, 0.1, 0), spread, 30, Particle.DUST, Fx.dust(0xC0392B, 1.2f));
        new BukkitRunnable() {
            int dropped;

            @Override
            public void run() {
                if (!player.isOnline() || ++dropped > count) {
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                Location impact = center.clone().add(random.nextDouble(-spread, spread), 0, random.nextDouble(-spread, spread));
                fallingAxe(player, axe, impact, damage);
            }
        }.runTaskTimer(plugin, 8, 3);
        player.swingMainHand();
    }

    private void fallingAxe(Player player, ItemStack axe, Location impact, double damage) {
        World world = impact.getWorld();
        ItemDisplay display = world.spawn(impact.clone().add(0, 8, 0), ItemDisplay.class, d -> {
            d.setItemStack(axe);
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
        });
        temporaryEntities.add(display);
        new BukkitRunnable() {
            int tick;
            float spin;

            @Override
            public void run() {
                tick++;
                if (!display.isValid() || tick > 10) {
                    cancel();
                    display.remove();
                    temporaryEntities.remove(display);
                    if (tick > 10 && player.isOnline()) {
                        world.spawnParticle(Particle.CRIT, impact, 15, 0.4, 0.2, 0.4, 0.2);
                        world.playSound(impact, Sound.ITEM_TRIDENT_HIT_GROUND, 0.8f, 0.8f);
                        for (LivingEntity target : combat.around(player, impact, 1.7)) {
                            combat.hit(player, target, damage, Skill.TOMAHAWK_RAIN);
                        }
                    }
                    return;
                }
                display.teleport(impact.clone().add(0, 8 - tick * 0.8, 0));
                spin += 1.1f;
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(spin, 1, 0, 0)),
                        new Vector3f(1, 1, 1), new Quaternionf()));
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    void lumberjacksFury(Player player) {
        furyHits.put(player.getUniqueId(), skills.evolved(player, Skill.LUMBERJACKS_FURY) ? 8 : 5);
        furyUntil.put(player.getUniqueId(), System.currentTimeMillis() + 10_000);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.3f);
        player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 2, 0), 4, 0.4, 0.2, 0.4);
    }

    /** Lumberjack's Fury: melee hits with an axe also hit everything around the target. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFuryHit(EntityDamageByEntityEvent event) {
        if (cleaving || !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)
                || !active(furyUntil, player) || Skill.Weapon.of(player.getInventory().getItemInMainHand().getType()) != Skill.Weapon.AXE) {
            return;
        }
        int left = furyHits.getOrDefault(player.getUniqueId(), 0);
        if (left <= 0) {
            return;
        }
        furyHits.put(player.getUniqueId(), left - 1);
        boolean evolved = skills.evolved(player, Skill.LUMBERJACKS_FURY);
        double damage = skills.damage(player, Skill.LUMBERJACKS_FURY);
        World world = target.getWorld();
        world.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3, 0.8, 0.2, 0.8);
        world.playSound(target.getLocation(), Sound.ITEM_AXE_STRIP, 1f, 0.7f);
        cleaving = true;
        try {
            for (LivingEntity near : combat.around(player, target.getLocation(), evolved ? 4 : 3)) {
                if (!near.equals(target)) {
                    combat.hit(player, near, damage, Skill.LUMBERJACKS_FURY);
                    if (evolved) {
                        Combat.knockBack(near, target.getLocation(), 0.6, 0.25);
                    }
                }
            }
        } finally {
            cleaving = false;
        }
        plugin.progress().onHit(player, Skill.LUMBERJACKS_FURY);
        if (left - 1 <= 0) {
            furyUntil.remove(player.getUniqueId());
            plugin.flash(player, plugin.lang().get("fury-ended"));
        }
    }

    boolean boneBreaker(Player player) {
        LivingEntity target = Aim.lookedAt(player, combat, 4.5);
        if (target == null) {
            plugin.flash(player, plugin.lang().get("no-target"));
            return false;
        }
        combat.hit(player, target, skills.damage(player, Skill.BONE_BREAKER), Skill.BONE_BREAKER);
        int ticks = 100 + 4 * plugin.progress().bonusLevel(player, Skill.BONE_BREAKER);
        Root.apply(plugin, target, ticks, 2);
        if (skills.evolved(player, Skill.BONE_BREAKER)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 1));
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0));
        }
        World world = target.getWorld();
        world.playSound(target.getLocation(), Sound.BLOCK_BONE_BLOCK_BREAK, 1.3f, 0.6f);
        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
        world.spawnParticle(Particle.BLOCK, target.getLocation().add(0, 0.5, 0), 25, 0.3, 0.3, 0.3, 0, Material.BONE_BLOCK.createBlockData());
        player.swingMainHand();
        return true;
    }

    // ---------------------------------------------------------------- Trident

    /** A wave carries the player forward and sweeps enemies aside. */
    void poseidonsCall(Player player) {
        boolean evolved = skills.evolved(player, Skill.POSEIDONS_CALL);
        double damage = skills.damage(player, Skill.POSEIDONS_CALL);
        World world = player.getWorld();
        Set<UUID> alreadyHit = new HashSet<>();
        combat.protectFromFall(player, 4000);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.7f);
        world.playSound(player.getLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 1f, 1f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++tick > (evolved ? 40 : 26)) {
                    cancel();
                    if (evolved && player.isOnline()) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 200, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 200, 0));
                    }
                    return;
                }
                Vector direction = Aim.flat(player);
                boolean blocked = !player.getLocation().add(direction).add(0, 0.5, 0).getBlock().isPassable();
                player.setVelocity(direction.clone().multiply(0.9).setY(blocked ? 0.5 : Math.max(-0.1, player.getVelocity().getY())));
                Location feet = player.getLocation();
                world.spawnParticle(Particle.SPLASH, feet.clone().add(0, 0.3, 0), 20, 0.7, 0.2, 0.7, 0.2);
                world.spawnParticle(Particle.FALLING_WATER, feet.clone().add(0, 1.2, 0), 8, 0.8, 0.5, 0.8, 0);
                world.spawnParticle(Particle.DUST, feet.clone().add(0, 0.6, 0), 6, 0.8, 0.3, 0.8, 0, Fx.dust(0x1E90FF, 1.4f));
                for (LivingEntity target : combat.around(player, feet, 2.2)) {
                    if (alreadyHit.add(target.getUniqueId())) {
                        combat.hit(player, target, damage, Skill.POSEIDONS_CALL);
                        target.setFireTicks(0);
                        Combat.knockBack(target, feet, 1.0, 0.45);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /** Throws a harpoon that drags what it hits back to the player (evolved: through up to 3). */
    void harpoonThrow(Player player) {
        World world = player.getWorld();
        Vector direction = Aim.direction(player);
        Location start = player.getEyeLocation().add(direction.clone().multiply(0.8));
        boolean evolved = skills.evolved(player, Skill.HARPOON_THROW);
        double damage = skills.damage(player, Skill.HARPOON_THROW);
        Location spawn = start.clone();
        spawn.setDirection(direction);
        ItemDisplay display = world.spawn(spawn, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.TRIDENT));
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f((float) Math.toRadians(-90), 1, 0, 0)),
                    new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
        });
        temporaryEntities.add(display);
        world.playSound(start, Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
        List<LivingEntity> hooked = new ArrayList<>();
        new BukkitRunnable() {
            final Location position = start.clone();
            double travelled;
            boolean returning;
            int ticks;

            @Override
            public void run() {
                if (!display.isValid() || !player.isOnline() || ++ticks > 100 || !player.getWorld().equals(world)) {
                    finish();
                    return;
                }
                if (!returning) {
                    RayTraceResult hit = world.rayTrace(position, direction, 1.4, FluidCollisionMode.NEVER, true, 0.5,
                            e -> e instanceof LivingEntity l && combat.canHit(player, l) && !hooked.contains(l));
                    if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                        combat.hit(player, target, damage, Skill.HARPOON_THROW);
                        hooked.add(target);
                        world.playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 0.8f);
                        returning = !evolved || hooked.size() >= 3;
                    } else if (hit != null && hit.getHitBlock() != null) {
                        world.playSound(hit.getHitPosition().toLocation(world), Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 1f);
                        returning = true;
                    }
                    travelled += 1.4;
                    if (travelled > 22) {
                        returning = true;
                    }
                    if (!returning) {
                        position.add(direction.clone().multiply(1.4));
                    }
                } else {
                    Vector back = player.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(position.toVector());
                    if (back.length() < 1.5) {
                        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1f);
                        finish();
                        return;
                    }
                    position.add(back.normalize().multiply(1.1));
                    for (LivingEntity target : hooked) {
                        if (target.isValid() && target.getLocation().distanceSquared(player.getLocation()) > 4) {
                            target.setVelocity(position.toVector().subtract(target.getLocation().toVector()).multiply(0.45).setY(0.15));
                        }
                    }
                }
                Location next = position.clone();
                next.setDirection(direction);
                display.teleport(next);
                Fx.line(player.getLocation().add(0, 1.2, 0), position, 0.7, Particle.DUST, Fx.dust(0x9A9A9A, 0.7f));
            }

            private void finish() {
                cancel();
                temporaryEntities.remove(display);
                display.remove();
            }
        }.runTaskTimer(plugin, 1, 1);
        player.swingMainHand();
    }

    /** Ultimate: three (evolved: five) huge waves sweep forward. */
    void tsunami(Player player) {
        boolean evolved = skills.evolved(player, Skill.TSUNAMI);
        int waves = evolved ? 5 : 3;
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
        world.spawnParticle(Particle.FALLING_WATER, player.getLocation().add(0, 4, 0), 200, 6, 1, 6, 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0));
        for (int i = 0; i < waves; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    bigWave(player, evolved);
                }
            }, i * 14L);
        }
    }

    private void bigWave(Player player, boolean evolved) {
        World world = player.getWorld();
        Vector direction = Aim.flat(player);
        Vector side = direction.clone().rotateAroundY(Math.PI / 2);
        Location start = player.getLocation();
        double damage = skills.damage(player, Skill.TSUNAMI);
        double halfWidth = evolved ? 5 : 3.5;
        Set<UUID> alreadyHit = new HashSet<>();
        world.playSound(start, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.5f);
        world.playSound(start, Sound.WEATHER_RAIN_ABOVE, 1.5f, 0.6f);
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                if (!player.isOnline() || ++step > 20) {
                    cancel();
                    return;
                }
                Location front = start.clone().add(direction.clone().multiply(step));
                for (double w = -halfWidth; w <= halfWidth; w += 0.6) {
                    Location spot = front.clone().add(side.clone().multiply(w));
                    for (double h = 0.3; h <= 3; h += 0.9) {
                        world.spawnParticle(Particle.DUST, spot.clone().add(0, h, 0), 1, 0.1, 0.2, 0.1, 0, Fx.dust(0x1565C0, 1.8f));
                    }
                    world.spawnParticle(Particle.SPLASH, spot.clone().add(0, 3.2, 0), 3, 0.2, 0.1, 0.2, 0.1);
                }
                for (LivingEntity target : combat.around(player, front.clone().add(0, 1, 0), halfWidth + 0.5)) {
                    target.setFireTicks(0);
                    if (alreadyHit.add(target.getUniqueId())) {
                        combat.hit(player, target, damage, Skill.TSUNAMI);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        if (evolved) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
                        }
                    }
                    target.setVelocity(direction.clone().multiply(1.3).setY(0.4));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ------------------------------------------------------------------- Mace

    /** A gust launches the player straight up and pushes everyone nearby away. */
    void windBurst(Player player) {
        boolean evolved = skills.evolved(player, Skill.WIND_BURST);
        World world = player.getWorld();
        Location feet = player.getLocation();
        world.playSound(feet, Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.8f);
        world.spawnParticle(Particle.GUST_EMITTER_SMALL, feet, 1);
        Fx.shockwave(plugin, feet.clone().add(0, 0.2, 0), evolved ? 4 : 3, 6, Particle.CLOUD, null);
        double damage = skills.damage(player, Skill.WIND_BURST);
        for (LivingEntity target : combat.around(player, feet, evolved ? 4 : 3)) {
            combat.hit(player, target, damage, Skill.WIND_BURST);
            Combat.knockBack(target, feet, evolved ? 1.6 : 1.1, 0.4);
        }
        player.setVelocity(new Vector(0, evolved ? 2.2 : 1.7, 0));
        combat.protectFromFall(player, 7000);
    }

    /** An anvil drops on the enemy the player looks at (evolved: three anvils). */
    void anvilDrop(Player player) {
        LivingEntity target = Aim.lookedAt(player, combat, 24);
        Location spot = target != null ? target.getLocation() : Aim.groundSpot(player, combat, 24, 12);
        double damage = skills.damage(player, Skill.ANVIL_DROP);
        dropAnvil(player, spot, target, damage);
        if (skills.evolved(player, Skill.ANVIL_DROP)) {
            for (int i = 0; i < 2; i++) {
                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                Location extra = spot.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> dropAnvil(player, extra, null, damage), 5L + i * 4);
            }
        }
        player.swingMainHand();
    }

    private void dropAnvil(Player player, Location spot, LivingEntity follow, double damage) {
        World world = spot.getWorld();
        Location top = spot.clone().add(0, 10, 0);
        BlockDisplay anvil = world.spawn(top, BlockDisplay.class, d -> {
            d.setBlock(Material.ANVIL.createBlockData());
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(-0.5f, 0, -0.5f), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
        });
        temporaryEntities.add(anvil);
        world.playSound(top, Sound.BLOCK_ANVIL_PLACE, 0.6f, 0.6f);
        new BukkitRunnable() {
            double height = 10;
            double speed = 0.4;

            @Override
            public void run() {
                if (!anvil.isValid() || !player.isOnline()) {
                    finish();
                    return;
                }
                Location ground = follow != null && follow.isValid() ? follow.getLocation() : spot;
                height -= speed;
                speed = Math.min(1.6, speed + 0.15);
                if (height <= 0) {
                    finish();
                    world.playSound(ground, Sound.BLOCK_ANVIL_LAND, 1.4f, 0.8f);
                    world.spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.5, 0), 40, 0.6, 0.3, 0.6, 0, Material.ANVIL.createBlockData());
                    world.spawnParticle(Particle.EXPLOSION, ground.clone().add(0, 0.5, 0), 1);
                    for (LivingEntity target : combat.around(player, ground, 1.9)) {
                        combat.hit(player, target, damage, Skill.ANVIL_DROP);
                        stun(target, 40);
                    }
                    return;
                }
                anvil.teleport(ground.clone().add(0, height, 0));
            }

            private void finish() {
                cancel();
                temporaryEntities.remove(anvil);
                anvil.remove();
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** Ultimate: leap, then crash down with three (evolved: four) widening shockwaves. */
    void cataclysm(Player player) {
        boolean evolved = skills.evolved(player, Skill.CATACLYSM);
        World world = player.getWorld();
        player.setVelocity(player.getLocation().getDirection().setY(0).multiply(0.4).setY(1.5));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
        combat.protectFromFall(player, 8000);
        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 0.6f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++tick > 100) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.02);
                if (tick > 6 && (Combat.onGround(player) || player.isInWater())) {
                    cancel();
                    combat.protectFromFall(player, 500);
                    Location center = player.getLocation();
                    double damage = skills.damage(player, Skill.CATACLYSM);
                    int waves = evolved ? 4 : 3;
                    for (int w = 0; w < waves; w++) {
                        int wave = w;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> shockwave(player, center, 5 + wave * 3,
                                damage * (1 - 0.15 * wave), evolved), wave * 8L);
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private void shockwave(Player player, Location center, double radius, double damage, boolean evolved) {
        if (!player.isOnline()) {
            return;
        }
        World world = center.getWorld();
        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5f, 0.5f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        Block ground = center.clone().subtract(0, 0.2, 0).getBlock();
        if (!ground.getType().isAir()) {
            Fx.shockwave(plugin, center.clone().add(0, 0.15, 0), radius, 8, Particle.BLOCK, ground.getBlockData());
        }
        Fx.shockwave(plugin, center.clone().add(0, 0.4, 0), radius, 8, Particle.FLAME, null);
        for (LivingEntity target : combat.around(player, center, radius)) {
            if (evolved) {
                Vector pull = center.toVector().subtract(target.getLocation().toVector()).setY(0);
                if (pull.lengthSquared() > 1) {
                    target.setVelocity(pull.normalize().multiply(0.6).setY(0.6));
                }
            } else {
                target.setVelocity(target.getVelocity().setY(0.6));
            }
            combat.hit(player, target, damage, Skill.CATACLYSM);
            stun(target, 30);
        }
    }

    // ----------------------------------------------------------------- Shield

    /** The shield flies from enemy to enemy, then returns. */
    void shieldThrow(Player player, EquipmentSlot hand) {
        World world = player.getWorld();
        boolean evolved = skills.evolved(player, Skill.SHIELD_THROW);
        int bounces = evolved ? 5 : 3;
        double damage = skills.damage(player, Skill.SHIELD_THROW);
        ItemStack shield = player.getInventory().getItem(hand).clone();
        Location start = player.getEyeLocation().subtract(0, 0.3, 0);
        ItemDisplay display = world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(shield);
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
        });
        temporaryEntities.add(display);
        world.playSound(start, Sound.ITEM_TRIDENT_THROW, 1f, 1.2f);
        LivingEntity first = combat.inFront(player, 12, 60).stream()
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation()))).orElse(null);
        Set<UUID> alreadyHit = new HashSet<>();
        new BukkitRunnable() {
            final Location position = start.clone();
            final Vector straight = player.getEyeLocation().getDirection().normalize();
            LivingEntity target = first;
            int hits;
            int ticks;
            double travelled;
            boolean returning;
            float spin;

            @Override
            public void run() {
                if (!display.isValid() || !player.isOnline() || ++ticks > 140 || !player.getWorld().equals(world)) {
                    finish();
                    return;
                }
                Vector step;
                if (!returning && target != null) {
                    Location aim = target.getLocation().add(0, target.getHeight() / 2, 0);
                    Vector to = aim.toVector().subtract(position.toVector());
                    if (!target.isValid() || target.isDead()) {
                        target = next();
                        return;
                    }
                    if (to.length() < 1.2) {
                        combat.hit(player, target, damage, Skill.SHIELD_THROW);
                        stun(target, 20);
                        alreadyHit.add(target.getUniqueId());
                        world.playSound(aim, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
                        world.spawnParticle(Particle.CRIT, aim, 12, 0.3, 0.3, 0.3, 0.2);
                        hits++;
                        target = hits < bounces ? next() : null;
                        returning = target == null;
                        return;
                    }
                    step = to.normalize().multiply(1.3);
                } else if (!returning) {
                    step = straight.clone().multiply(1.3);
                    travelled += 1.3;
                    if (travelled > 14 || !position.getBlock().isPassable()) {
                        returning = true;
                    }
                } else {
                    Vector back = player.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(position.toVector());
                    if (back.length() < 1.3) {
                        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.2f);
                        finish();
                        return;
                    }
                    step = back.normalize().multiply(1.5);
                }
                position.add(step);
                display.teleport(position);
                spin += 0.8f;
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(spin, 0, 1, 0)),
                        new Vector3f(1, 1, 1), new Quaternionf()));
                world.spawnParticle(Particle.CRIT, position, 1, 0.05, 0.05, 0.05, 0);
            }

            /** The next enemy within 8 blocks of the last one that hasn't been hit yet. */
            private LivingEntity next() {
                return combat.around(player, position, 8).stream()
                        .filter(e -> !alreadyHit.contains(e.getUniqueId()))
                        .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(position)))
                        .orElse(null);
            }

            private void finish() {
                cancel();
                temporaryEntities.remove(display);
                display.remove();
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** Resistance for the player and nearby players for 6 seconds (evolved: 8, wider, plus Absorption). */
    void phalanx(Player player) {
        boolean evolved = skills.evolved(player, Skill.PHALANX);
        double radius = evolved ? 10 : 6;
        int seconds = (evolved ? 8 : 6) + plugin.progress().bonusLevel(player, Skill.PHALANX) / 5;
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 0.8f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
        if (evolved) {
            for (Player ally : player.getLocation().getNearbyPlayers(radius)) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * seconds, 1));
            }
        }
        new BukkitRunnable() {
            int second;

            @Override
            public void run() {
                if (!player.isOnline() || ++second > seconds) {
                    cancel();
                    return;
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, false, false, true));
                for (Player ally : player.getLocation().getNearbyPlayers(radius)) {
                    if (!ally.equals(player)) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1, false, false, true));
                        world.spawnParticle(Particle.DUST, ally.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0, Fx.dust(0xAEB6BF, 1.2f));
                    }
                }
                Fx.ring(player.getLocation().add(0, 0.2, 0), radius, 40, Particle.DUST, Fx.dust(0xAEB6BF, 1.1f));
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    // ------------------------------------------------------------------ Tools

    private static boolean isCrop(Material type) {
        return type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES
                || type == Material.BEETROOTS || type == Material.NETHER_WART;
    }

    /** Harvests and replants every ripe crop in a 5x5 area (evolved: 9x9). */
    boolean harvestWave(Player player) {
        Block aimed = player.getTargetBlockExact(5);
        Location center = aimed != null ? aimed.getLocation() : player.getLocation();
        int radius = skills.evolved(player, Skill.HARVEST_WAVE) ? 4 : 2;
        List<Block> ripe = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -1; y <= 1; y++) {
                    Block block = center.getBlock().getRelative(x, y, z);
                    if (isCrop(block.getType()) && block.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()) {
                        ripe.add(block);
                    }
                }
            }
        }
        if (ripe.isEmpty()) {
            plugin.flash(player, plugin.lang().get("no-ripe-crops"));
            return false;
        }
        World world = player.getWorld();
        for (Block block : ripe) {
            Material crop = block.getType();
            if (player.breakBlock(block)) {
                world.spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 3, 0.3, 0.3, 0.3);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Material soil = block.getRelative(0, -1, 0).getType();
                    boolean fits = crop == Material.NETHER_WART ? soil == Material.SOUL_SAND : soil == Material.FARMLAND;
                    if (block.getType().isAir() && fits) {
                        block.setType(crop);
                    }
                });
            }
        }
        world.playSound(player.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 0.8f);
        world.playSound(player.getLocation(), Sound.BLOCK_CROP_BREAK, 1f, 1f);
        for (int i = 0; i < Math.min(5, ripe.size()); i++) {
            plugin.progress().onHit(player, Skill.HARVEST_WAVE);
        }
        plugin.flash(player, plugin.lang().get("crops-harvested", "count", ripe.size()));
        return true;
    }

    void burrow(Player player) {
        boolean evolved = skills.evolved(player, Skill.BURROW);
        int ticks = (evolved ? 160 : 100) + 5 * plugin.progress().bonusLevel(player, Skill.BURROW);
        burrowUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, ticks, 1, false, false, true));
        if (evolved) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1, false, false, true));
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 1.2f, 0.7f);
        player.getWorld().spawnParticle(Particle.BLOCK, player.getLocation(), 30, 0.5, 0.2, 0.5, 0, Material.DIRT.createBlockData());
    }

    /** Burrow: soft blocks break instantly with a shovel. */
    @EventHandler(ignoreCancelled = true)
    public void onDig(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (active(burrowUntil, player) && Skill.Weapon.of(event.getItemInHand().getType()) == Skill.Weapon.SHOVEL
                && Tag.MINEABLE_SHOVEL.isTagged(event.getBlock().getType())) {
            event.setInstaBreak(true);
        }
    }

    /** Pulls the player to the block they look at (evolved: further, and hooked mobs are pulled in instead). */
    boolean grappleHook(Player player) {
        boolean evolved = skills.evolved(player, Skill.GRAPPLE_HOOK);
        World world = player.getWorld();
        double range = evolved ? 45 : 30;
        RayTraceResult hit = world.rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(), range,
                FluidCollisionMode.NEVER, true, 0.4, e -> evolved && e instanceof LivingEntity l && combat.canHit(player, l));
        if (hit == null) {
            plugin.flash(player, plugin.lang().get("nothing-to-hook", "range", (int) range));
            return false;
        }
        Location point = hit.getHitPosition().toLocation(world);
        Fx.line(player.getLocation().add(0, 1.2, 0), point, 0.5, Particle.DUST, Fx.dust(0xC8A165, 0.8f));
        world.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
        if (hit.getHitEntity() instanceof LivingEntity target) {
            Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
            double distance = pull.length();
            target.setVelocity(pull.normalize().multiply(Math.min(2.4, 0.4 + distance * 0.1)).setY(0.45));
            plugin.progress().onHit(player, Skill.GRAPPLE_HOOK);
            return true;
        }
        Vector pull = point.toVector().subtract(player.getLocation().toVector());
        double distance = pull.length();
        Vector velocity = pull.normalize().multiply(Math.min(3.0, 0.5 + distance * 0.12));
        velocity.setY(velocity.getY() + 0.45);
        player.setVelocity(velocity);
        combat.protectFromFall(player, 4000);
        world.spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.3, 0.1, 0.3, 0.05);
        return true;
    }

    // --- Cleanup ---

    void forget(Player player) {
        UUID id = player.getUniqueId();
        counterUntil.remove(id);
        counterStored.remove(id);
        furyHits.remove(id);
        furyUntil.remove(id);
        burrowUntil.remove(id);
    }

    void shutdown() {
        temporaryEntities.forEach(Entity::remove);
        temporaryEntities.clear();
    }
}
