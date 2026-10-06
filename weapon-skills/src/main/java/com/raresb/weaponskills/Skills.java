package com.raresb.weaponskills;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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

/** The special attacks themselves. */
final class Skills {
    private final WeaponSkillsPlugin plugin;
    private final Combat combat;
    /** Players in a parry stance, until this time (ms). */
    private final Map<UUID, Long> parryUntil = new HashMap<>();
    /** Thrown axes still in flight, removed if the plugin is disabled. */
    private final Set<ItemDisplay> thrownAxes = new HashSet<>();

    private final ExtraSkills extra;
    private final RangedSkills ranged;
    private final OtherSkills other;
    private final GatherSkills gather;
    private final MoreSkills more;
    private final SpearSkills spear;

    Skills(WeaponSkillsPlugin plugin, Combat combat) {
        this.plugin = plugin;
        this.combat = combat;
        this.extra = new ExtraSkills(plugin, combat, this);
        this.ranged = new RangedSkills(plugin, combat, this);
        this.other = new OtherSkills(plugin, combat, this);
        this.gather = new GatherSkills(plugin, this);
        this.more = new MoreSkills(plugin, combat, this);
        this.spear = new SpearSkills(plugin, combat, this);
    }

    GatherSkills gather() {
        return gather;
    }

    MoreSkills more() {
        return more;
    }

    ExtraSkills extra() {
        return extra;
    }

    RangedSkills ranged() {
        return ranged;
    }

    /**
     * Skill damage x the player's level bonus. For most weapons the skill damage is a multiple of
     * the weapon's attack damage; shields, bows and crossbows use a flat number instead.
     */
    double damage(Player player, Skill skill) {
        double base = skill.weapon().flatDamage() ? plugin.skillDamage(skill) : Combat.weaponDamage(player) * plugin.skillDamage(skill);
        return base * plugin.progress().damageMultiplier(player, skill) * plugin.augments().damageMultiplier(player, skill);
    }

    /** Whether the player's level in the skill has reached its evolution (level 10 by default). */
    boolean evolved(Player player, Skill skill) {
        return plugin.progress().level(player, skill) >= plugin.getConfig().getInt("progression.evolve-level", 10);
    }

    /** Uses the skill. Returns false if it couldn't be used (no arrow, no target...): no cooldown then. */
    boolean use(Player player, Skill skill) {
        if ((skill.weapon() == Skill.Weapon.BOW || skill.weapon() == Skill.Weapon.CROSSBOW) && !ranged.takeArrow(player)) {
            return false;
        }
        switch (skill) {
            case WHIRLWIND -> whirlwind(player);
            case GROUND_SLAM -> dive(player, skill);
            case DASH_STRIKE -> dashStrike(player);
            case PARRY -> parry(player);
            case RISING_SLASH -> risingSlash(player);
            case BLADE_FLURRY -> bladeFlurry(player);
            case SHADOW_STEP -> {
                if (!extra.shadowStep(player)) {
                    return false;
                }
            }
            case BLADE_DANCE -> extra.bladeDance(player);
            case MIRROR_IMAGE -> extra.mirrorImage(player);
            case JUDGMENT -> extra.judgment(player);
            case CLEAVE -> cleave(player);
            case EXECUTIONERS_DROP -> dive(player, skill);
            case BERSERKER_CHARGE -> berserkerCharge(player);
            case AXE_THROW -> axeThrow(player);
            case WAR_CRY -> warCry(player);
            case EARTHSPLITTER -> earthsplitter(player);
            case BLOODLUST -> extra.bloodlust(player);
            case GROUND_POUND -> extra.groundPound(player);
            case CHAIN_HOOK -> {
                if (!extra.chainHook(player)) {
                    return false;
                }
            }
            case RAGNAROK -> extra.ragnarok(player);
            case SHIELD_BASH -> shieldBash(player);
            case FORTIFY -> fortify(player);
            case REFLECT -> extra.reflect(player);
            case GUARDIAN_AURA -> extra.guardianAura(player);
            case TAUNT -> extra.taunt(player);
            case ARROW_RAIN -> ranged.arrowRain(player);
            case PIERCING_SHOT -> ranged.piercingShot(player);
            case VOLLEY -> ranged.volley(player);
            case WIND_ARROW -> ranged.windArrow(player);
            case HOVER_SHOT -> ranged.hoverShot(player);
            case EXPLOSIVE_BOLT -> ranged.explosiveBolt(player);
            case NET_SHOT -> ranged.netShot(player);
            case HARPOON_BOLT -> ranged.harpoonBolt(player);
            case BURST_FIRE -> ranged.burstFire(player);
            case RECOIL_SHOT -> ranged.recoilShot(player);
            case RIPTIDE_SURGE -> other.riptideSurge(player);
            case TIDAL_WAVE -> other.tidalWave(player);
            case LIGHTNING_CALL -> other.lightningCall(player);
            case GRAVITY_WELL -> other.gravityWell(player);
            case TREMOR -> other.tremor(player);
            case METEOR -> other.meteor(player);
            case TREMOR_SENSE -> other.tremorSense(player);
            case TUNNEL_BORE -> {
                if (!gather.tunnelBore(player)) {
                    return false;
                }
            }
            case MINERS_RUSH -> gather.minersRush(player);
            case SHATTER -> {
                if (!gather.shatter(player)) {
                    return false;
                }
            }
            case CLEAR_CUT -> {
                if (!gather.clearCut(player)) {
                    return false;
                }
            }
            case CRESCENT_WAVE -> more.crescentWave(player);
            case COUNTER_STANCE -> more.counterStance(player);
            case THOUSAND_CUTS -> {
                if (!more.thousandCuts(player)) {
                    return false;
                }
            }
            case TOMAHAWK_RAIN -> more.tomahawkRain(player);
            case LUMBERJACKS_FURY -> more.lumberjacksFury(player);
            case BONE_BREAKER -> {
                if (!more.boneBreaker(player)) {
                    return false;
                }
            }
            case SHIELD_THROW -> more.shieldThrow(player, shieldHand(player));
            case PHALANX -> more.phalanx(player);
            case RAIN_OF_FIRE -> ranged.rainOfFire(player);
            case TRAP_ARROW -> ranged.trapArrow(player);
            case SPLIT_SHOT -> ranged.splitShot(player);
            case SNIPER_MODE -> ranged.sniperMode(player);
            case STORM_OF_ARROWS -> ranged.stormOfArrows(player);
            case GATLING -> ranged.gatling(player);
            case SMOKE_BOLT -> ranged.smokeBolt(player);
            case CHAIN_BOLT -> ranged.chainBolt(player);
            case ARTILLERY_BARRAGE -> ranged.artilleryBarrage(player);
            case POSEIDONS_CALL -> more.poseidonsCall(player);
            case HARPOON_THROW -> more.harpoonThrow(player);
            case TSUNAMI -> more.tsunami(player);
            case WIND_BURST -> more.windBurst(player);
            case ANVIL_DROP -> more.anvilDrop(player);
            case CATACLYSM -> more.cataclysm(player);
            case IMPALE -> spear.impale(player);
            case DRAGOON_DIVE -> spear.dragoonDive(player);
            case LANCE_CHARGE -> spear.lanceCharge(player);
            case JAVELIN -> spear.javelin(player);
            case POLE_VAULT -> spear.poleVault(player);
            case SWEEPING_ARC -> spear.sweepingArc(player);
            case SPEAR_WALL -> spear.spearWall(player);
            case THRUST_FLURRY -> spear.thrustFlurry(player);
            case PINNING_THROW -> {
                if (!spear.pinningThrow(player)) {
                    return false;
                }
            }
            case DRAGON_LEAP -> spear.dragonLeap(player);
            case GUNGNIR -> spear.gungnir(player);
            case HARVEST_WAVE -> {
                if (!more.harvestWave(player)) {
                    return false;
                }
            }
            case BURROW -> more.burrow(player);
            case GRAPPLE_HOOK -> {
                if (!more.grappleHook(player)) {
                    return false;
                }
            }
            case GROVE_GROWTH -> {
                if (!gather.groveGrowth(player)) {
                    return false;
                }
            }
            case LUMBER_FRENZY -> gather.lumberFrenzy(player);
        }
        combat.wear(player, skill.weapon() == Skill.Weapon.SHIELD ? shieldHand(player) : EquipmentSlot.HAND);
        return true;
    }

    // ------------------------------------------------------------------ Sword

    /** Spin in place and hit everything around you. */
    private void whirlwind(Player player) {
        Location center = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
        world.playSound(center, Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 1.4f);
        // Blades sweeping around the player over a few ticks.
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (++tick > 6 || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location c = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 3; i++) {
                    double angle = tick * 1.05 + Math.PI * 2 * i / 3;
                    world.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(Math.cos(angle) * 2.3, 0, Math.sin(angle) * 2.3), 1);
                }
                Fx.ring(c.clone().subtract(0, 0.6, 0), 1.2 + tick * 0.35, 20, Particle.DUST, Fx.dust(0xE8F4FF, 1.2f));
            }
        }.runTaskTimer(plugin, 0, 1);
        world.spawnParticle(Particle.CRIT, center, 25, 1.5, 0.3, 1.5, 0.2);
        double damage = damage(player, Skill.WHIRLWIND);
        boolean evolved = evolved(player, Skill.WHIRLWIND);
        double radius = evolved ? 5 : 3.5;
        Runnable spin = () -> {
            for (LivingEntity target : combat.around(player, player.getLocation(), radius)) {
                combat.hit(player, target, damage, Skill.WHIRLWIND);
                Combat.knockBack(target, player.getLocation(), 0.7, 0.3);
            }
            player.swingMainHand();
        };
        spin.run();
        if (evolved) {
            // Evolved: a second, wider spin half a second later.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.9f);
                    Fx.shockwave(plugin, player.getLocation().add(0, 0.4, 0), radius, 6, Particle.SWEEP_ATTACK, null);
                    spin.run();
                }
            }, 10);
        }
    }

    /**
     * Ground Slam (sword) and Executioner's Drop (axe): dive straight down, and hit on landing.
     * The higher you dive from, the harder the hit.
     */
    private void dive(Player player, Skill skill) {
        double startY = player.getLocation().getY();
        World world = player.getWorld();
        player.setVelocity(player.getLocation().getDirection().setY(0).multiply(0.3).setY(-2.2));
        world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 0.6f);
        combat.protectFromFall(player, 5000);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || player.isDead() || ticks > 100) {
                    cancel();
                    return;
                }
                Location body = player.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.CLOUD, body, 3, 0.2, 0.4, 0.2, 0);
                if (skill == Skill.GROUND_SLAM) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, body, 6, 0.3, 0.5, 0.3, 0.05);
                } else {
                    world.spawnParticle(Particle.FLAME, body, 5, 0.25, 0.5, 0.25, 0.02);
                    world.spawnParticle(Particle.DUST, body, 4, 0.3, 0.5, 0.3, 0, Fx.dust(0x8B0000, 1.4f));
                }
                if (Combat.onGround(player) || player.isInWater()) {
                    cancel();
                    double height = Math.max(0, startY - player.getLocation().getY());
                    combat.protectFromFall(player, 500);
                    if (skill == Skill.GROUND_SLAM) {
                        slamImpact(player, height);
                    } else {
                        executionImpact(player, height);
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private void slamImpact(Player player, double height) {
        Location center = player.getLocation();
        World world = player.getWorld();
        boolean evolved = evolved(player, Skill.GROUND_SLAM);
        double radius = 3 + Math.min(3, height / 3) + (evolved ? 1.5 : 0);
        double damage = damage(player, Skill.GROUND_SLAM) * (1 + Math.min(1.5, height * 0.1));

        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.8f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.spawnParticle(Particle.DUST_PLUME, center.clone().add(0, 0.2, 0), 40, radius / 3, 0.2, radius / 3, 0.05);
        Block ground = center.clone().subtract(0, 0.2, 0).getBlock();
        Location wave = center.clone().add(0, 0.15, 0);
        if (!ground.getType().isAir()) {
            // The ground block cracking outward in a growing ring.
            Fx.shockwave(plugin, wave, radius, 7, Particle.BLOCK, ground.getBlockData());
        }
        Fx.shockwave(plugin, wave.clone().add(0, 0.2, 0), radius, 7, Particle.DUST, Fx.dust(0xFFFFFF, 1.6f));
        for (LivingEntity target : combat.around(player, center, radius)) {
            double closeness = 1 - Math.min(1, target.getLocation().distance(center) / radius);
            combat.hit(player, target, damage * (0.5 + 0.5 * closeness), Skill.GROUND_SLAM);
            Combat.knockBack(target, center, 0.4 + 0.5 * closeness, 0.5 + 0.4 * closeness);
            if (evolved) {
                stun(target, 40);
            }
        }
    }

    /** Dash forward, hitting everything along the way. */
    private void dashStrike(Player player) {
        World world = player.getWorld();
        Vector direction = Aim.flat(player);
        boolean evolved = evolved(player, Skill.DASH_STRIKE);
        player.setVelocity(direction.clone().multiply(evolved ? 2.6 : 1.9).setY(0.15));
        world.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.3f);
        combat.protectFromFall(player, 2000);
        double damage = damage(player, Skill.DASH_STRIKE);
        Set<UUID> alreadyHit = new HashSet<>();
        world.spawnParticle(Particle.GUST, player.getLocation().add(0, 1, 0), 1);

        new BukkitRunnable() {
            int ticks;
            Location last = player.getLocation().add(0, 1, 0);

            @Override
            public void run() {
                if (!player.isOnline() || ++ticks > (evolved ? 11 : 8)) {
                    cancel();
                    return;
                }
                Location now = player.getLocation().add(0, 1, 0);
                Fx.line(last, now, 0.25, Particle.END_ROD, null);
                Fx.line(last, now, 0.4, Particle.DUST, Fx.dust(0xB0E0FF, 1.3f));
                world.spawnParticle(Particle.CRIT, now, 6, 0.3, 0.4, 0.3, 0.1);
                last = now;
                for (LivingEntity target : combat.around(player, player.getLocation(), 1.8)) {
                    if (alreadyHit.add(target.getUniqueId())) {
                        combat.hit(player, target, damage, Skill.DASH_STRIKE);
                        if (evolved) {
                            target.setVelocity(target.getVelocity().setY(0.7));
                        }
                        world.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
                        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1f);
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** A short stance: the next melee hit on the player is blocked and countered. */
    private void parry(Player player) {
        parryUntil.put(player.getUniqueId(), System.currentTimeMillis() + (evolved(player, Skill.PARRY) ? 2000 : 1250));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.4f);
        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, player.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 1, false, false, true));
        UUID id = player.getUniqueId();
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                Long until = parryUntil.get(id);
                if (!player.isOnline() || until == null || until < System.currentTimeMillis()) {
                    cancel();
                    return;
                }
                tick++;
                Location base = player.getLocation();
                // Two counter-rotating rings at chest height.
                for (int i = 0; i < 4; i++) {
                    double a = tick * 0.5 + Math.PI / 2 * i;
                    player.getWorld().spawnParticle(Particle.END_ROD, base.clone().add(Math.cos(a) * 0.9, 1.3, Math.sin(a) * 0.9), 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.DUST, base.clone().add(Math.cos(-a) * 0.9, 0.7, Math.sin(-a) * 0.9), 1, 0, 0, 0, 0,
                            Fx.dust(0xFFD700, 1.1f));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Called when the player is hit in melee. Returns true if the hit was parried
     * (the caller cancels the damage).
     */
    boolean tryParry(Player player, LivingEntity attacker) {
        Long until = parryUntil.get(player.getUniqueId());
        if (until == null || until < System.currentTimeMillis()) {
            return false;
        }
        parryUntil.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f);
        world.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 2f);
        world.spawnParticle(Particle.FLASH, player.getEyeLocation(), 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
        world.spawnParticle(Particle.ELECTRIC_SPARK, player.getEyeLocation(), 30, 0.5, 0.5, 0.5, 0.3);
        world.spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.3);
        if (combat.canHit(player, attacker) || attacker instanceof org.bukkit.entity.Mob) {
            combat.hit(player, attacker, damage(player, Skill.PARRY), Skill.PARRY);
            Combat.knockBack(attacker, player.getLocation(), 0.9, 0.35);
        }
        if (evolved(player, Skill.PARRY)) {
            // Evolved: the counter is a spin that hits everything around.
            for (LivingEntity target : combat.around(player, player.getLocation(), 3)) {
                if (!target.equals(attacker)) {
                    combat.hit(player, target, damage(player, Skill.PARRY) * 0.6, Skill.PARRY);
                    Combat.knockBack(target, player.getLocation(), 0.6, 0.3);
                }
            }
            Fx.ring(player.getLocation().add(0, 1, 0), 2.5, 16, Particle.SWEEP_ATTACK, null);
        }
        player.swingMainHand();
        plugin.flash(player, plugin.lang().get("parried"));
        return true;
    }

    // -------------------------------------------------------------------- Axe

    /** A heavy swing in front of the player that also disables shields. */
    private void cleave(Player player) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector facing = Aim.flat(player);
        world.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        world.playSound(eye, Sound.ITEM_AXE_SCRAPE, 1f, 0.6f);
        for (int i = -3; i <= 3; i++) {
            Vector arc = facing.clone().rotateAroundY(Math.toRadians(i * 18)).multiply(2.5);
            world.spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(arc).add(0, 1.1, 0), 1);
        }
        // A red slash that sweeps across the front over a few ticks.
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (++tick > 5) {
                    cancel();
                    return;
                }
                for (int j = 0; j < 4; j++) {
                    double deg = -60 + (tick - 1) * 24 + j * 6;
                    for (double r = 1.2; r <= 3.6; r += 0.4) {
                        Vector v = facing.clone().rotateAroundY(Math.toRadians(deg)).multiply(r);
                        world.spawnParticle(Particle.DUST, player.getLocation().add(v).add(0, 1.1 + (r - 2.4) * 0.15, 0), 1, 0, 0, 0, 0,
                                Fx.dust(0xB22222, 1.3f));
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
        double damage = damage(player, Skill.CLEAVE);
        boolean evolved = evolved(player, Skill.CLEAVE);
        if (evolved) {
            Fx.shockwave(plugin, player.getLocation().add(0, 1.1, 0), 4, 5, Particle.SWEEP_ATTACK, null);
        }
        for (LivingEntity target : evolved ? combat.around(player, player.getLocation(), 4.5) : combat.inFront(player, 4, 120)) {
            combat.hit(player, target, damage, Skill.CLEAVE);
            Combat.knockBack(target, player.getLocation(), 0.6, 0.25);
            if (target instanceof Player victim && victim.isBlocking()) {
                victim.setCooldown(Material.SHIELD, 100);
                world.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
            }
        }
        player.swingMainHand();
    }

    /** Landing of Executioner's Drop: a small area, heavy damage and a stun. */
    private void executionImpact(Player player, double height) {
        Location center = player.getLocation();
        World world = player.getWorld();
        double damage = damage(player, Skill.EXECUTIONERS_DROP) * (1 + Math.min(1.0, height * 0.08));
        world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND, 1.2f, 0.6f);
        world.playSound(center, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 0.5f);
        world.spawnParticle(Particle.CRIT, center.clone().add(0, 0.5, 0), 40, 1, 0.3, 1, 0.3);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, center.clone().add(0, 0.5, 0), 10, 1, 0.3, 1, 0.1);
        world.spawnParticle(Particle.LAVA, center, 8, 0.6, 0.1, 0.6, 0);
        for (double y = 0; y < 3; y += 0.25) {
            world.spawnParticle(Particle.DUST, center.clone().add(0, y, 0), 3, 0.25, 0, 0.25, 0, Fx.dust(0x8B0000, 1.8f));
        }
        Fx.shockwave(plugin, center.clone().add(0, 0.15, 0), 2.8, 5, Particle.DUST, Fx.dust(0xB22222, 1.5f));
        boolean evolved = evolved(player, Skill.EXECUTIONERS_DROP);
        for (LivingEntity target : combat.around(player, center, evolved ? 4.2 : 2.8)) {
            combat.hit(player, target, damage, Skill.EXECUTIONERS_DROP);
            stun(target, evolved ? 70 : 40);
        }
        player.swingMainHand();
    }

    /** Charge forward, knocking enemies aside, then gain Strength and Speed. */
    private void berserkerCharge(Player player) {
        World world = player.getWorld();
        Vector direction = Aim.flat(player);
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.1f);
        combat.protectFromFall(player, 2500);
        double damage = damage(player, Skill.BERSERKER_CHARGE);
        boolean evolved = evolved(player, Skill.BERSERKER_CHARGE);
        Set<UUID> alreadyHit = new HashSet<>();

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!player.isOnline() || ++ticks > (evolved ? 18 : 12)) {
                    cancel();
                    if (player.isOnline()) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, evolved ? 1 : 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 0));
                        world.spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3);
                        Fx.shockwave(plugin, player.getLocation().add(0, 0.2, 0), 2.5, 5, Particle.FLAME, null);
                    }
                    return;
                }
                Vector velocity = direction.clone().multiply(0.9);
                velocity.setY(Combat.onGround(player) ? 0.05 : player.getVelocity().getY());
                player.setVelocity(velocity);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation(), 1, 0.2, 0, 0.2, 0.01);
                world.spawnParticle(Particle.FLAME, player.getLocation().add(0, 0.3, 0), 6, 0.3, 0.2, 0.3, 0.02);
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0, Fx.dust(0xFF3300, 1.5f));
                for (LivingEntity target : combat.around(player, player.getLocation(), 1.8)) {
                    if (alreadyHit.add(target.getUniqueId())) {
                        combat.hit(player, target, damage, Skill.BERSERKER_CHARGE);
                        // Throw them to the side of the charge.
                        Vector side = direction.clone().rotateAroundY(Math.PI / 2);
                        Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector());
                        if (side.dot(toTarget) < 0) {
                            side.multiply(-1);
                        }
                        target.setVelocity(side.multiply(1.1).add(direction.clone().multiply(0.4)).setY(0.45));
                        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /** Throw a spinning copy of the axe that hits the first thing in its path and flies back. */
    private void axeThrow(Player player) {
        Vector aim = Aim.direction(player);
        throwAxe(player, aim);
        if (evolved(player, Skill.AXE_THROW)) {
            // Evolved: two more axes fanned out to the sides.
            throwAxe(player, aim.clone().rotateAroundY(Math.toRadians(15)));
            throwAxe(player, aim.clone().rotateAroundY(Math.toRadians(-15)));
        }
    }

    private void throwAxe(Player player, Vector direction) {
        World world = player.getWorld();
        Location start = player.getEyeLocation().add(direction.clone().multiply(0.8));
        ItemStack axe = player.getInventory().getItemInMainHand().clone();
        double damage = damage(player, Skill.AXE_THROW);
        world.playSound(start, Sound.ITEM_TRIDENT_THROW, 1f, 0.7f);

        Location spawn = start.clone();
        spawn.setDirection(direction);
        ItemDisplay display = world.spawn(spawn, ItemDisplay.class, d -> {
            d.setItemStack(axe);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
        });
        thrownAxes.add(display);

        new BukkitRunnable() {
            final Location position = start.clone();
            int ticks;
            double travelled;
            boolean returning;
            float spin;

            @Override
            public void run() {
                ticks++;
                if (!display.isValid() || !player.isOnline() || ticks > 120 || !player.getWorld().equals(world)) {
                    finish();
                    return;
                }
                Vector step;
                if (!returning) {
                    step = direction.clone().multiply(1.2);
                    RayTraceResult hit = world.rayTrace(position, direction, 1.2, org.bukkit.FluidCollisionMode.NEVER, true, 0.4,
                            e -> e instanceof LivingEntity living && combat.canHit(player, living));
                    if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                        combat.hit(player, target, damage, Skill.AXE_THROW);
                        world.playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 0.8f);
                        world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.2);
                        returning = true;
                    } else if (hit != null && hit.getHitBlock() != null) {
                        world.playSound(hit.getHitPosition().toLocation(world), Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 1f);
                        returning = true;
                    } else {
                        travelled += 1.2;
                        if (travelled >= 20) {
                            returning = true;
                        }
                    }
                    if (returning) {
                        return; // turn around next tick
                    }
                } else {
                    Vector toPlayer = player.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(position.toVector());
                    if (toPlayer.length() < 1.3) {
                        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1f);
                        finish();
                        return;
                    }
                    step = toPlayer.normalize().multiply(1.4);
                }
                position.add(step);
                Location next = position.clone();
                next.setDirection(step);
                display.teleport(next);
                // Spin end over end around the axis perpendicular to the flight path.
                spin += 0.9f;
                display.setInterpolationDelay(0);
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(new AxisAngle4f(spin, 1, 0, 0)),
                        new Vector3f(1, 1, 1), new Quaternionf()));
                world.spawnParticle(Particle.CRIT, position, 2, 0.05, 0.05, 0.05, 0);
                world.spawnParticle(Particle.DUST, position, 2, 0.1, 0.1, 0.1, 0, Fx.dust(returning ? 0x9BD4FF : 0xC0C0C0, 1.0f));
                if (ticks % 3 == 0) {
                    world.playSound(position, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.3f, 1.8f);
                }
            }

            private void finish() {
                cancel();
                thrownAxes.remove(display);
                display.remove();
            }
        }.runTaskTimer(plugin, 1, 1);
        player.swingMainHand();
    }

    // ------------------------------------------------------------- New skills

    /** An uppercut that launches everything in front of the player into the air. */
    private void risingSlash(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        Vector facing = Aim.flat(player);
        world.playSound(base, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
        world.playSound(base, Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
        // Vertical arcs of light in front of the player.
        for (int i = -2; i <= 2; i++) {
            Location foot = base.clone().add(facing.clone().rotateAroundY(Math.toRadians(i * 20)).multiply(2));
            Fx.line(foot.clone().add(0, 0.2, 0), foot.clone().add(0, 3, 0), 0.3, Particle.END_ROD, null);
            world.spawnParticle(Particle.SWEEP_ATTACK, foot.clone().add(0, 1.2, 0), 1);
        }
        world.spawnParticle(Particle.CLOUD, base, 15, 0.8, 0.1, 0.8, 0.05);
        double damage = damage(player, Skill.RISING_SLASH);
        boolean evolved = evolved(player, Skill.RISING_SLASH);
        if (evolved) {
            for (int i = 0; i < 8; i++) {
                Location foot = base.clone().add(new Vector(2.5, 0, 0).rotateAroundY(Math.PI * 2 * i / 8));
                Fx.line(foot.clone().add(0, 0.2, 0), foot.clone().add(0, 3.5, 0), 0.35, Particle.END_ROD, null);
            }
        }
        for (LivingEntity target : evolved ? combat.around(player, base, 4.5) : combat.inFront(player, 4, 110)) {
            combat.hit(player, target, damage, Skill.RISING_SLASH);
            target.setVelocity(target.getVelocity().setY(evolved ? 1.35 : 1.05).add(facing.clone().multiply(0.2)));
        }
        player.setVelocity(player.getVelocity().setY(0.55));
        combat.protectFromFall(player, 3000);
        player.swingMainHand();
    }

    /** Six rapid slashes at everything in front of the player. */
    private void bladeFlurry(Player player) {
        World world = player.getWorld();
        double damage = damage(player, Skill.BLADE_FLURRY);
        boolean evolved = evolved(player, Skill.BLADE_FLURRY);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 14, 2, false, false, false));
        new BukkitRunnable() {
            int slash;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++slash > 6) {
                    cancel();
                    return;
                }
                Location eye = player.getEyeLocation();
                Vector facing = Aim.flat(player);
                double side = (slash % 2 == 0 ? 1 : -1) * 0.6;
                Location spot = player.getLocation().add(facing.clone().multiply(1.8))
                        .add(facing.clone().rotateAroundY(Math.PI / 2).multiply(side)).add(0, 1.1, 0);
                world.spawnParticle(Particle.SWEEP_ATTACK, spot, 1);
                world.spawnParticle(Particle.CRIT, spot, 6, 0.4, 0.3, 0.4, 0.2);
                if (evolved) {
                    // Evolved: the slashes circle the player, hitting the sides and back too.
                    for (int i = 1; i < 4; i++) {
                        Vector around = facing.clone().rotateAroundY(Math.PI / 2 * i + slash * 0.5).multiply(2.4);
                        Location extra = player.getLocation().add(around).add(0, 1.1, 0);
                        world.spawnParticle(Particle.SWEEP_ATTACK, extra, 1);
                        world.spawnParticle(Particle.CRIT, extra, 4, 0.3, 0.3, 0.3, 0.2);
                    }
                }
                world.playSound(spot, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.2f + slash * 0.12f);
                for (LivingEntity target : evolved ? combat.around(player, player.getLocation(), 4.2) : combat.inFront(player, 3.2, 100)) {
                    combat.hit(player, target, damage, Skill.BLADE_FLURRY);
                }
                if (slash % 2 == 0) {
                    player.swingMainHand();
                } else {
                    player.swingOffHand();
                }
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    /** A roar that weakens and slows nearby enemies and gives the player Resistance. */
    private void warCry(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation();
        int level = plugin.progress().bonusLevel(player, Skill.WAR_CRY);
        int ticks = 100 + 10 * level;
        world.playSound(center, Sound.EVENT_RAID_HORN, 1.2f, 1f);
        world.playSound(center, Sound.ENTITY_RAVAGER_ROAR, 1f, 0.8f);
        world.spawnParticle(Particle.SONIC_BOOM, player.getEyeLocation().add(center.getDirection().multiply(1.5)), 1);
        Fx.shockwave(plugin, center.clone().add(0, 0.2, 0), 8, 8, Particle.DUST, Fx.dust(0xFF8C00, 1.6f));
        Fx.shockwave(plugin, center.clone().add(0, 1.2, 0), 8, 8, Particle.ANGRY_VILLAGER, null);
        for (LivingEntity target : combat.around(player, center, 8)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks / 2, 0));
            Combat.knockBack(target, center, 0.5, 0.2);
            plugin.progress().onHit(player, Skill.WAR_CRY);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 0));
        if (evolved(player, Skill.WAR_CRY)) {
            // Evolved: rally nearby players - heal them and give them Strength.
            for (Player ally : center.getNearbyPlayers(8)) {
                double max = ally.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                ally.setHealth(Math.min(max, ally.getHealth() + 4));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0));
                world.spawnParticle(Particle.HEART, ally.getLocation().add(0, 2.1, 0), 2, 0.3, 0.1, 0.3);
            }
        }
        player.swingMainHand();
    }

    /**
     * A fissure that races along the ground, launching and stunning whatever it passes under.
     * Evolved: three fissures - one forward and one to each side.
     */
    private void earthsplitter(Player player) {
        World world = player.getWorld();
        Vector forward = Aim.flat(player);
        Location start = player.getLocation();
        double damage = damage(player, Skill.EARTHSPLITTER);
        Set<UUID> alreadyHit = new HashSet<>();
        world.playSound(start, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.7f);
        player.swingMainHand();
        fissure(player, start, forward, damage, alreadyHit);
        if (evolved(player, Skill.EARTHSPLITTER)) {
            fissure(player, start, forward.clone().rotateAroundY(Math.PI / 2), damage, alreadyHit);
            fissure(player, start, forward.clone().rotateAroundY(-Math.PI / 2), damage, alreadyHit);
        }
    }

    private void fissure(Player player, Location start, Vector direction, double damage, Set<UUID> alreadyHit) {
        World world = start.getWorld();
        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                if (!player.isOnline() || ++step > 10) {
                    cancel();
                    return;
                }
                Location point = start.clone().add(direction.clone().multiply(step));
                // Follow the terrain: find the ground at this point.
                Block ground = world.getHighestBlockAt(point.getBlockX(), point.getBlockZ());
                if (Math.abs(ground.getY() - start.getY()) > 4) {
                    ground = point.clone().subtract(0, 1, 0).getBlock();
                }
                Location surface = ground.getLocation().add(0.5, 1.05, 0.5);
                if (!ground.getType().isAir()) {
                    world.spawnParticle(Particle.BLOCK, surface, 25, 0.35, 0.1, 0.35, 0.1, ground.getBlockData());
                }
                world.spawnParticle(Particle.DUST_PLUME, surface, 6, 0.3, 0.1, 0.3, 0.02);
                world.spawnParticle(Particle.DUST, surface, 6, 0.3, 0.2, 0.3, 0, Fx.dust(0x6B4226, 1.6f));
                world.playSound(surface, Sound.BLOCK_STONE_BREAK, 0.8f, 0.6f);
                for (LivingEntity target : combat.around(player, surface, 1.6)) {
                    if (alreadyHit.add(target.getUniqueId())) {
                        combat.hit(player, target, damage, Skill.EARTHSPLITTER);
                        target.setVelocity(target.getVelocity().setY(0.8));
                        stun(target, 30);
                    }
                }
            }
        }.runTaskTimer(plugin, 2, 1);
    }

    /** Brace behind the shield: Resistance II for a few seconds (longer at higher levels). */
    private void fortify(Player player) {
        World world = player.getWorld();
        int level = plugin.progress().bonusLevel(player, Skill.FORTIFY);
        int ticks = 80 + 6 * level;
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 1));
        if (evolved(player, Skill.FORTIFY)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks, 1));
        }
        world.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.8f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 2;
                if (!player.isOnline() || player.isDead() || tick > ticks) {
                    cancel();
                    return;
                }
                // A golden dome around the player.
                Location base = player.getLocation();
                for (int i = 0; i < 10; i++) {
                    double a = tick * 0.2 + Math.PI * 2 * i / 10;
                    double h = (i % 3) * 0.7 + 0.2;
                    double r = 1.1 - (i % 3) * 0.25;
                    world.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), 1, 0, 0, 0, 0,
                            Fx.dust(0xFFD700, 1.0f));
                }
                if (tick % 10 == 0) {
                    world.spawnParticle(Particle.ENCHANTED_HIT, base.clone().add(0, 1, 0), 6, 0.5, 0.6, 0.5, 0.05);
                }
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    // ----------------------------------------------------------------- Shield

    private static EquipmentSlot shieldHand(Player player) {
        return player.getInventory().getItemInOffHand().getType() == Material.SHIELD ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
    }

    /** Slam the shield into everything right in front: knockback, a stun, and shields disabled. */
    private void shieldBash(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
        world.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.8f, 1.2f);
        Location front = player.getLocation().add(Aim.flat(player).multiply(1.5)).add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION, front, 1);
        world.spawnParticle(Particle.CLOUD, front, 15, 0.4, 0.4, 0.4, 0.15);
        Vector facing = Aim.flat(player);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (++tick > 4) {
                    cancel();
                    return;
                }
                double r = 0.8 * tick;
                for (int deg = -45; deg <= 45; deg += 9) {
                    Vector v = facing.clone().rotateAroundY(Math.toRadians(deg)).multiply(r);
                    world.spawnParticle(Particle.DUST, player.getLocation().add(v).add(0, 1, 0), 1, 0, 0.3, 0, 0, Fx.dust(0xDDDDDD, 1.4f));
                }
            }
        }.runTaskTimer(plugin, 0, 1);
        double damage = plugin.skillDamage(Skill.SHIELD_BASH) * plugin.progress().damageMultiplier(player, Skill.SHIELD_BASH);
        boolean evolved = evolved(player, Skill.SHIELD_BASH);
        for (LivingEntity target : combat.inFront(player, evolved ? 4 : 3.2, evolved ? 180 : 90)) {
            combat.hit(player, target, damage, Skill.SHIELD_BASH);
            Combat.knockBack(target, player.getLocation(), evolved ? 1.9 : 1.3, 0.45);
            stun(target, 30);
            if (target instanceof Player victim) {
                victim.setCooldown(Material.SHIELD, 60);
            }
        }
        if (shieldHand(player) == EquipmentSlot.OFF_HAND) {
            player.swingOffHand();
        } else {
            player.swingMainHand();
        }
    }

    /** Heavy slowness and weakness for a moment, with stars around the head. */
    private void stun(LivingEntity target, int ticks) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 1));
        Fx.stunStars(plugin, target, ticks);
    }

    void forget(Player player) {
        parryUntil.remove(player.getUniqueId());
        extra.forget(player);
        more.forget(player);
        gather.forget(player);
    }

    void shutdown() {
        thrownAxes.forEach(ItemDisplay::remove);
        thrownAxes.clear();
        extra.shutdown();
        more.shutdown();
        spear.shutdown();
        other.shutdown();
        gather.shutdown();
    }
}
