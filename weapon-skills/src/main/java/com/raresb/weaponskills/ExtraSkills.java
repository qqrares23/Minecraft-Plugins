package com.raresb.weaponskills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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

/** The unlockable sword, axe and shield skills, and the two ultimates. */
final class ExtraSkills implements Listener {
    private final WeaponSkillsPlugin plugin;
    private final Combat combat;
    private final Skills skills;
    private final Map<UUID, Long> bloodlustUntil = new HashMap<>();
    private final Map<UUID, Long> reflectUntil = new HashMap<>();
    private final Map<UUID, Long> guardianUntil = new HashMap<>();
    /** Guardian Aura casters whose aura is evolved (10 blocks, 40%). */
    private final java.util.Set<UUID> guardianEvolved = new java.util.HashSet<>();
    private final List<Entity> temporaryEntities = new ArrayList<>();
    /** Mirror Image decoys: damage to them is cancelled (they can't be invulnerable, or mobs ignore them). */
    private final java.util.Set<UUID> decoys = new java.util.HashSet<>();

    ExtraSkills(WeaponSkillsPlugin plugin, Combat combat, Skills skills) {
        this.plugin = plugin;
        this.combat = combat;
        this.skills = skills;
    }

    private static boolean active(Map<UUID, Long> until, Player player) {
        Long time = until.get(player.getUniqueId());
        return time != null && time > System.currentTimeMillis();
    }

    // --- Temporary attribute boosts (Blade Dance, Ragnarok) ---

    private NamespacedKey boostKey(String name) {
        return new NamespacedKey(plugin, "boost_" + name);
    }

    private void boost(Player player, String name, Attribute attribute, double amount, int ticks) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        NamespacedKey key = boostKey(name + "_" + attribute.getKey().getKey());
        instance.removeModifier(key);
        instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> instance.removeModifier(key), ticks);
    }

    /** If the server stopped during a boost, the modifier is still saved on the player: remove it. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (Attribute attribute : new Attribute[] {Attribute.ATTACK_SPEED, Attribute.MOVEMENT_SPEED, Attribute.SCALE,
                Attribute.ATTACK_DAMAGE, Attribute.KNOCKBACK_RESISTANCE}) {
            AttributeInstance instance = event.getPlayer().getAttribute(attribute);
            if (instance != null) {
                instance.getModifiers().stream()
                        .filter(m -> m.getKey().getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))
                                && m.getKey().getKey().startsWith("boost_"))
                        .toList()
                        .forEach(instance::removeModifier);
            }
        }
    }

    // ---------------------------------------------------------------- Sword

    boolean shadowStep(Player player) {
        LivingEntity target = Aim.lookedAt(player, combat, 20);
        World world = player.getWorld();
        if (target == null) {
            plugin.flash(player, plugin.lang().get("look-at-enemy-step"));
            return false;
        }
        Vector behind = target.getLocation().getDirection().setY(0);
        if (behind.lengthSquared() < 1.0e-4) {
            behind = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        }
        Location destination = target.getLocation().subtract(behind.normalize().multiply(1.5));
        if (!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()) {
            destination = target.getLocation();
        }
        destination.setDirection(target.getLocation().toVector().subtract(destination.toVector()));
        world.spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
        world.spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.1);
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        Location to = destination;
        player.teleportAsync(to).thenRun(() -> {
            world.spawnParticle(Particle.LARGE_SMOKE, to.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
            combat.hit(player, target, skills.damage(player, Skill.SHADOW_STEP), Skill.SHADOW_STEP);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
            if (skills.evolved(player, Skill.SHADOW_STEP)) {
                // Evolved: a shadow burst also hits and blinds everything near the target.
                world.spawnParticle(Particle.SQUID_INK, target.getLocation().add(0, 1, 0), 30, 1.2, 0.5, 1.2, 0.02);
                for (LivingEntity near : combat.around(player, target.getLocation(), 2.8)) {
                    if (!near.equals(target)) {
                        combat.hit(player, near, skills.damage(player, Skill.SHADOW_STEP) * 0.6, Skill.SHADOW_STEP);
                        near.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                    }
                }
            }
            world.playSound(to, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
            player.swingMainHand();
        });
        return true;
    }

    void bladeDance(Player player) {
        int ticks = (120 + 6 * plugin.progress().bonusLevel(player, Skill.BLADE_DANCE)) * (skills.evolved(player, Skill.BLADE_DANCE) ? 3 : 2) / 2;
        if (skills.evolved(player, Skill.BLADE_DANCE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0));
        }
        boost(player, "dance", Attribute.ATTACK_SPEED, 1.0, ticks);
        boost(player, "dance", Attribute.MOVEMENT_SPEED, 0.15, ticks);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.5f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 2;
                if (!player.isOnline() || player.isDead() || tick > ticks) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                for (int i = 0; i < 2; i++) {
                    double a = tick * 0.4 + Math.PI * i;
                    player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, base.clone().add(Math.cos(a) * 1.1, 1.0, Math.sin(a) * 1.1), 1);
                }
                player.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, 1, 0), 2, 0.4, 0.5, 0.4, 0, Fx.dust(0xFFD700, 1.0f));
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    void mirrorImage(Player player) {
        World world = player.getWorld();
        List<Mannequin> decoys = new ArrayList<>();
        boolean evolved = skills.evolved(player, Skill.MIRROR_IMAGE);
        for (int side : evolved ? new int[] {-1, 1, 0} : new int[] {-1, 1}) {
            Vector offset = side == 0 ? Aim.flat(player).multiply(-2)
                    : Aim.flat(player).rotateAroundY(Math.PI / 2).multiply(side * 2);
            Location spot = player.getLocation().add(offset);
            if (!spot.getBlock().isPassable()) {
                spot = player.getLocation();
            }
            Mannequin decoy = world.spawn(spot, Mannequin.class, m -> {
                m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
                m.setPersistent(false);
                m.setDescription(null);
                m.getEquipment().setArmorContents(player.getInventory().getArmorContents());
                m.getEquipment().setItemInMainHand(player.getInventory().getItemInMainHand().clone());
            });
            decoys.add(decoy);
            this.decoys.add(decoy.getUniqueId());
            temporaryEntities.add(decoy);
            world.spawnParticle(Particle.CLOUD, spot.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.05);
        }
        // Mobs chasing the player go after the decoys instead.
        for (Entity nearby : player.getNearbyEntities(16, 8, 16)) {
            if (nearby instanceof Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(decoys.get(ThreadLocalRandom.current().nextInt(decoys.size())));
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 50, 0, false, false, true));
        world.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
        int ticks = 120 + 6 * plugin.progress().bonusLevel(player, Skill.MIRROR_IMAGE);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Mannequin decoy : decoys) {
                if (decoy.isValid()) {
                    decoy.getWorld().spawnParticle(Particle.LARGE_SMOKE, decoy.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                    if (evolved && player.isOnline()) {
                        // Evolved: the decoys burst when they vanish.
                        decoy.getWorld().spawnParticle(Particle.EXPLOSION, decoy.getLocation().add(0, 1, 0), 1);
                        decoy.getWorld().playSound(decoy.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
                        for (LivingEntity near : combat.around(player, decoy.getLocation(), 3)) {
                            combat.hit(player, near, 5, Skill.MIRROR_IMAGE);
                        }
                    }
                    decoy.remove();
                }
                temporaryEntities.remove(decoy);
                this.decoys.remove(decoy.getUniqueId());
            }
        }, ticks);
    }

    void judgment(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation();
        double damage = skills.damage(player, Skill.JUDGMENT);
        boolean evolved = skills.evolved(player, Skill.JUDGMENT);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.6f);
        world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1f, 1.2f);
        Fx.shockwave(plugin, center.clone().add(0, 0.1, 0), 6, 20, Particle.DUST, Fx.dust(0xFFFFFF, 1.6f));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 1));
        new BukkitRunnable() {
            int blade;

            @Override
            public void run() {
                if (!player.isOnline() || ++blade > (evolved ? 26 : 16)) {
                    cancel();
                    return;
                }
                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                double distance = ThreadLocalRandom.current().nextDouble(1.5, evolved ? 9 : 6.5);
                Location impact = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                impact = world.getHighestBlockAt(impact).getLocation().add(0.5, 1, 0.5);
                if (Math.abs(impact.getY() - center.getY()) > 6) {
                    impact.setY(center.getY());
                }
                dropBlade(player, impact, damage);
            }
        }.runTaskTimer(plugin, 20, 3);
    }

    private void dropBlade(Player player, Location impact, double damage) {
        World world = impact.getWorld();
        Location start = impact.clone().add(0, 9, 0);
        ItemDisplay sword = world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.NETHERITE_SWORD));
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(1);
            d.setGlowing(true);
            // Point the blade down.
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f((float) (Math.PI * 0.75), 0, 0, 1)),
                    new Vector3f(2.2f, 2.2f, 2.2f), new Quaternionf()));
        });
        temporaryEntities.add(sword);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick++;
                Location at = start.clone().subtract(0, tick * 1.2, 0);
                world.spawnParticle(Particle.END_ROD, at, 2, 0.05, 0.2, 0.05, 0);
                if (at.getY() <= impact.getY() + 0.5 || tick > 12) {
                    cancel();
                    // Removal and damage first, so the blade always disappears and always hits,
                    // even if a visual effect below were to fail.
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        sword.remove();
                        temporaryEntities.remove(sword);
                    }, 10);
                    sword.teleport(impact);
                    if (player.isOnline()) {
                        for (LivingEntity target : combat.around(player, impact, 2.2)) {
                            combat.hit(player, target, damage, Skill.JUDGMENT);
                        }
                    }
                    world.spawnParticle(Particle.EXPLOSION, impact, 1);
                    world.spawnParticle(Particle.FLASH, impact, 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
                    world.playSound(impact, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 1.5f);
                    Fx.ring(impact.clone().add(0, 0.1, 0), 2, 16, Particle.ELECTRIC_SPARK, null);
                    return;
                }
                sword.teleport(at);
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ------------------------------------------------------------------ Axe

    void bloodlust(Player player) {
        int ticks = 160 + 8 * plugin.progress().bonusLevel(player, Skill.BLOODLUST);
        bloodlustUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.2f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 4;
                if (!player.isOnline() || tick > ticks) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 5, 0.4, 0.6, 0.4, 0,
                        Fx.dust(0x8B0000, 1.3f));
            }
        }.runTaskTimer(plugin, 0, 4);
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && active(bloodlustUntil, killer)) {
            double max = killer.getAttribute(Attribute.MAX_HEALTH).getValue();
            boolean evolved = skills.evolved(killer, Skill.BLOODLUST);
            killer.setHealth(Math.min(max, killer.getHealth() + (evolved ? 8 : 4)));
            if (evolved) {
                killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
            }
            killer.getWorld().spawnParticle(Particle.HEART, killer.getLocation().add(0, 2.1, 0), 3, 0.3, 0.1, 0.3);
            event.getEntity().getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, event.getEntity().getLocation().add(0, 1, 0),
                    10, 0.3, 0.4, 0.3, 0.1);
        }
    }

    void groundPound(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation();
        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.6f);
        world.spawnParticle(Particle.DUST_PLUME, center.clone().add(0, 0.2, 0), 50, 2, 0.1, 2, 0.05);
        Fx.shockwave(plugin, center.clone().add(0, 0.15, 0), 6, 6, Particle.DUST, Fx.dust(0x5A4632, 1.8f));
        double damage = skills.damage(player, Skill.GROUND_POUND);
        boolean evolved = skills.evolved(player, Skill.GROUND_POUND);
        for (LivingEntity target : combat.around(player, center, evolved ? 8 : 6)) {
            combat.hit(player, target, damage, Skill.GROUND_POUND);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, evolved ? 3 : 2));
        }
        player.swingMainHand();
    }

    boolean chainHook(Player player) {
        LivingEntity target = Aim.lookedAt(player, combat, 16);
        World world = player.getWorld();
        if (target != null && skills.evolved(player, Skill.CHAIN_HOOK)) {
            // Evolved: two more chains grab the nearest enemies in front.
            combat.inFront(player, 16, 50).stream()
                    .filter(e -> !e.equals(target))
                    .sorted(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())))
                    .limit(2)
                    .forEach(extra -> hook(player, extra));
        }
        if (target == null) {
            plugin.flash(player, plugin.lang().get("look-at-enemy-hook"));
            return false;
        }
        hook(player, target);
        return true;
    }

    private void hook(Player player, LivingEntity target) {
        World world = player.getWorld();
        Location from = player.getEyeLocation().subtract(0, 0.3, 0);
        Location to = target.getLocation().add(0, target.getHeight() / 2, 0);
        Fx.line(from, to, 0.3, Particle.DUST, Fx.dust(0x9A9A9A, 1.2f));
        Fx.line(from, to, 0.6, Particle.CRIT, null);
        world.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.7f);
        world.playSound(target.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
        Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
        double distance = pull.length();
        target.setVelocity(pull.normalize().multiply(Math.min(2.2, 0.35 + distance * 0.13)).setY(0.45));
        combat.hit(player, target, skills.damage(player, Skill.CHAIN_HOOK), Skill.CHAIN_HOOK);
        player.swingMainHand();
    }

    void ragnarok(Player player) {
        boolean evolved = skills.evolved(player, Skill.RAGNAROK);
        int ticks = evolved ? 300 : 200;
        World world = player.getWorld();
        boost(player, "ragnarok", Attribute.SCALE, 0.6, ticks);
        boost(player, "ragnarok", Attribute.ATTACK_DAMAGE, 0.5, ticks);
        boost(player, "ragnarok", Attribute.KNOCKBACK_RESISTANCE, 0.8, ticks);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 1));
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, player.getLocation(), 1);
        Fx.shockwave(plugin, player.getLocation().add(0, 0.2, 0), 7, 8, Particle.FLAME, null);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 3;
                if (!player.isOnline() || player.isDead() || tick > ticks) {
                    cancel();
                    return;
                }
                Location base = player.getLocation();
                world.spawnParticle(Particle.FLAME, base.clone().add(0, 1.2, 0), 8, 0.6, 1, 0.6, 0.02);
                world.spawnParticle(Particle.DUST, base.clone().add(0, 1.2, 0), 6, 0.6, 1, 0.6, 0, Fx.dust(0xFF4500, 1.8f));
                world.spawnParticle(Particle.LAVA, base, 1, 0.5, 0, 0.5, 0);
                if (evolved && tick % 21 == 0) {
                    // Evolved: a burning aura scorches everything close by once a second.
                    for (LivingEntity near : combat.around(player, base, 3.5)) {
                        combat.hit(player, near, 3, Skill.RAGNAROK);
                        near.setFireTicks(Math.max(near.getFireTicks(), 40));
                    }
                    Fx.ring(base.clone().add(0, 0.3, 0), 3.5, 24, Particle.FLAME, null);
                }
            }
        }.runTaskTimer(plugin, 0, 3);
    }

    // --------------------------------------------------------------- Shield

    void reflect(Player player) {
        int ticks = 80 + 4 * plugin.progress().bonusLevel(player, Skill.REFLECT) + (skills.evolved(player, Skill.REFLECT) ? 40 : 0);
        reflectUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 1.2f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 2;
                if (!player.isOnline() || tick > ticks) {
                    cancel();
                    return;
                }
                Location front = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.9)).subtract(0, 0.4, 0);
                player.getWorld().spawnParticle(Particle.DUST, front, 6, 0.35, 0.45, 0.35, 0, Fx.dust(0xB19CD9, 1.1f));
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    /** Called when a projectile hits the player. Returns true if it was reflected. */
    boolean tryReflect(Player player, Projectile projectile) {
        if (!active(reflectUntil, player)) {
            return false;
        }
        World world = player.getWorld();
        Vector back;
        if (projectile.getShooter() instanceof LivingEntity shooter && shooter.getWorld().equals(world)) {
            back = shooter.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
        } else {
            back = projectile.getVelocity().multiply(-1).normalize();
        }
        projectile.remove();
        Arrow arrow = player.launchProjectile(Arrow.class, back.multiply(2.6));
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setDamage(skills.evolved(player, Skill.REFLECT) ? 12 : 6);
        arrow.setCritical(true);
        world.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.8f);
        world.spawnParticle(Particle.ENCHANTED_HIT, player.getEyeLocation(), 12, 0.3, 0.3, 0.3, 0.2);
        plugin.progress().onHit(player, Skill.REFLECT);
        return true;
    }

    void guardianAura(Player player) {
        int ticks = 160 + 8 * plugin.progress().bonusLevel(player, Skill.GUARDIAN_AURA);
        guardianUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        boolean evolved = skills.evolved(player, Skill.GUARDIAN_AURA);
        if (evolved) {
            guardianEvolved.add(player.getUniqueId());
        } else {
            guardianEvolved.remove(player.getUniqueId());
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.2f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 5;
                if (!player.isOnline() || tick > ticks) {
                    cancel();
                    return;
                }
                Fx.ring(player.getLocation().add(0, 0.2, 0), evolved ? 10 : 6, evolved ? 56 : 36, Particle.DUST, Fx.dust(0x87CEFA, 1.2f));
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 2.2, 0), 1, 0.1, 0.1, 0.1, 0);
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecoyHurt(EntityDamageEvent event) {
        if (decoys.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Players within 6 blocks of an active Guardian Aura take 30% less damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || guardianUntil.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        guardianUntil.values().removeIf(t -> t < now);
        for (UUID id : guardianUntil.keySet()) {
            Player guardian = plugin.getServer().getPlayer(id);
            boolean evolved = guardianEvolved.contains(id);
            double radius = evolved ? 10 : 6;
            if (guardian != null && guardian.getWorld().equals(victim.getWorld())
                    && guardian.getLocation().distanceSquared(victim.getLocation()) <= radius * radius) {
                event.setDamage(event.getDamage() * (evolved ? 0.6 : 0.7));
                victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.1);
                return;
            }
        }
    }

    void taunt(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 0.8f);
        world.playSound(player.getLocation(), Sound.ENTITY_PILLAGER_CELEBRATE, 1f, 1f);
        Fx.shockwave(plugin, player.getLocation().add(0, 0.2, 0), 12, 8, Particle.DUST, Fx.dust(0xFF0000, 1.4f));
        int taunted = 0;
        for (Entity nearby : player.getNearbyEntities(12, 6, 12)) {
            if (nearby instanceof Mob mob && !(mob instanceof org.bukkit.entity.Tameable tame && tame.isTamed())) {
                mob.setTarget(player);
                if (skills.evolved(player, Skill.TAUNT)) {
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1));
                }
                world.spawnParticle(Particle.ANGRY_VILLAGER, mob.getLocation().add(0, mob.getHeight() + 0.3, 0), 1);
                taunted++;
            }
        }
        for (int i = 0; i < Math.min(taunted, 5); i++) {
            plugin.progress().onHit(player, Skill.TAUNT);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80 + 4 * plugin.progress().bonusLevel(player, Skill.TAUNT), 0));
    }

    void shutdown() {
        temporaryEntities.forEach(Entity::remove);
        temporaryEntities.clear();
    }

    void forget(Player player) {
        bloodlustUntil.remove(player.getUniqueId());
        reflectUntil.remove(player.getUniqueId());
        guardianUntil.remove(player.getUniqueId());
    }
}
