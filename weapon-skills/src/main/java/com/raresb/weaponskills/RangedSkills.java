package com.raresb.weaponskills;

import com.raresb.weaponskills.common.Root;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Bow and crossbow skills. Each use needs (and uses up) one arrow. */
final class RangedSkills implements Listener {
    private final WeaponSkillsPlugin plugin;
    private final Combat combat;
    private final Skills skills;
    /** Marks arrows fired by a skill with the skill's id, so their impact can be handled. */
    private final NamespacedKey skillKey;
    /** Grapple arrows in flight, per player. */

    RangedSkills(WeaponSkillsPlugin plugin, Combat combat, Skills skills) {
        this.plugin = plugin;
        this.combat = combat;
        this.skills = skills;
        this.skillKey = new NamespacedKey(plugin, "skill_arrow");
    }

    // --- Arrows ---

    /**
     * Takes one arrow from the player's inventory. Creative players and Infinity bows don't use
     * arrows up (Infinity still needs one to be carried). Returns false if there is no arrow.
     */
    boolean takeArrow(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        boolean infinity = weapon.getType() == Material.BOW && weapon.getEnchantmentLevel(Enchantment.INFINITY) > 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && (item.getType() == Material.ARROW || item.getType() == Material.SPECTRAL_ARROW
                    || item.getType() == Material.TIPPED_ARROW)) {
                if (!(infinity && item.getType() == Material.ARROW)) {
                    item.setAmount(item.getAmount() - 1);
                    player.getInventory().setItem(i, item.getAmount() > 0 ? item : null);
                }
                return true;
            }
        }
        plugin.flash(player, plugin.lang().get("need-arrow"));
        return false;
    }

    /** Fires a skill arrow from the player. */
    private Arrow shoot(Player player, Vector velocity, double damage, Skill skill) {
        Arrow arrow = player.launchProjectile(Arrow.class, velocity);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setDamage(damage);
        arrow.setCritical(true);
        arrow.getPersistentDataContainer().set(skillKey, PersistentDataType.STRING, skill.id());
        return arrow;
    }

    /** A particle trail behind an arrow while it flies. */
    private void trail(Arrow arrow, Particle particle, Object data, int maxTicks) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isInBlock() || ++tick > maxTicks) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(particle, arrow.getLocation(), 2, 0.05, 0.05, 0.05, 0, data);
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** Where shots fly: the look direction, or straight ahead when cast with a look key ({@link Aim}). */
    private static Vector aim(Player player) {
        return Aim.direction(player);
    }

    // -------------------------------------------------------------------- Bow

    void arrowRain(Player player) {
        World world = player.getWorld();
        Location target = Aim.groundSpot(player, combat, 40, 25);
        double damage = skills.damage(player, Skill.ARROW_RAIN);
        boolean evolved = skills.evolved(player, Skill.ARROW_RAIN);
        double spread = evolved ? 4.5 : 3;
        int count = evolved ? 30 : 16;
        world.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);
        Fx.ring(target.clone().add(0, 0.1, 0), spread, 30, Particle.DUST, Fx.dust(0xFF4040, 1.3f));
        new BukkitRunnable() {
            int arrows;

            @Override
            public void run() {
                if (!player.isOnline() || ++arrows > count) {
                    cancel();
                    return;
                }
                Location from = target.clone().add(ThreadLocalRandom.current().nextDouble(-spread, spread), 14,
                        ThreadLocalRandom.current().nextDouble(-spread, spread));
                Arrow arrow = world.spawnArrow(from, new Vector(0, -1, 0), 1.8f, 2f);
                arrow.setShooter(player);
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setDamage(damage);
                arrow.getPersistentDataContainer().set(skillKey, PersistentDataType.STRING, Skill.ARROW_RAIN.id());
                world.spawnParticle(Particle.CLOUD, from, 2, 0.1, 0.1, 0.1, 0);
                if (arrows % 3 == 0) {
                    world.playSound(from, Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.4f);
                }
            }
        }.runTaskTimer(plugin, 10, 2);
    }

    void piercingShot(Player player) {
        if (skills.evolved(player, Skill.PIERCING_SHOT)) {
            // Evolved: two more piercing arrows to the sides.
            for (int side : new int[] {-1, 1}) {
                Arrow extra = shoot(player, aim(player).rotateAroundY(Math.toRadians(side * 6)).multiply(3.4),
                        skills.damage(player, Skill.PIERCING_SHOT), Skill.PIERCING_SHOT);
                extra.setPierceLevel(5);
                extra.setGravity(false);
                trail(extra, Particle.END_ROD, null, 40);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> extra.setGravity(true), 30);
            }
        }
        Arrow arrow = shoot(player, aim(player).multiply(3.4), skills.damage(player, Skill.PIERCING_SHOT), Skill.PIERCING_SHOT);
        arrow.setPierceLevel(5);
        arrow.setGravity(false);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.2f);
        trail(arrow, Particle.END_ROD, null, 40);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> arrow.setGravity(true), 30);
    }

    void volley(Player player) {
        double damage = skills.damage(player, Skill.VOLLEY);
        int half = skills.evolved(player, Skill.VOLLEY) ? 4 : 2;
        for (int i = -half; i <= half; i++) {
            Arrow arrow = shoot(player, aim(player).rotateAroundY(Math.toRadians(i * (half > 2 ? 6 : 9))).multiply(2.6), damage, Skill.VOLLEY);
            trail(arrow, Particle.CRIT, null, 15);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.8f);
    }

    /** An arrow carrying a gust of wind: it bursts where it lands, throwing everything near it back and up. */
    void windArrow(Player player) {
        Arrow arrow = shoot(player, aim(player).multiply(2.8), skills.damage(player, Skill.WIND_ARROW), Skill.WIND_ARROW);
        trail(arrow, Particle.CLOUD, null, 40);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.2f);
    }

    /**
     * The Wind Arrow's burst: knocks enemies back and up (evolved: wider, and they take 40% of the arrow's damage).
     * The shooter is thrown too when close, safe from fall damage - shooting a wall next to you is a quick escape.
     */
    private void windBurst(Player player, Location at, LivingEntity directHit) {
        boolean evolved = skills.evolved(player, Skill.WIND_ARROW);
        double radius = evolved ? 5 : 3.5;
        World world = at.getWorld();
        world.spawnParticle(Particle.GUST_EMITTER_SMALL, at, 1);
        world.spawnParticle(Particle.CLOUD, at, 30, radius / 3, 0.3, radius / 3, 0.08);
        world.playSound(at, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 0.9f);
        double splash = skills.damage(player, Skill.WIND_ARROW) * 0.4;
        for (LivingEntity target : combat.around(player, at, radius)) {
            Combat.knockBack(target, at, evolved ? 1.4 : 1.1, evolved ? 0.75 : 0.6);
            if (evolved && !target.equals(directHit)) {
                combat.hit(player, target, splash, Skill.WIND_ARROW);
            }
        }
        if (player.getWorld().equals(world) && player.getLocation().distanceSquared(at) <= radius * radius) {
            Combat.knockBack(player, at, 1.2, 0.8);
            combat.protectFromFall(player, 4000);
        }
    }

    void hoverShot(Player player) {
        player.setVelocity(new Vector(0, 0.15, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 12, 0, false, false, false));
        boolean evolved = skills.evolved(player, Skill.HOVER_SHOT);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, evolved ? 100 : 60, 0, false, false, true));
        combat.protectFromFall(player, 5000);
        double damage = skills.damage(player, Skill.HOVER_SHOT);
        World world = player.getWorld();
        Fx.ring(player.getLocation(), 1, 16, Particle.CLOUD, null);
        world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.2f);
        new BukkitRunnable() {
            int shot;

            @Override
            public void run() {
                if (!player.isOnline() || ++shot > (evolved ? 6 : 3)) {
                    cancel();
                    return;
                }
                Arrow arrow = shoot(player, aim(player).multiply(2.8), damage, Skill.HOVER_SHOT);
                trail(arrow, Particle.CRIT, null, 15);
                world.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1.1f + shot * 0.1f);
            }
        }.runTaskTimer(plugin, 4, 4);
    }

    // --------------------------------------------------------------- Crossbow

    void explosiveBolt(Player player) {
        Arrow bolt = shoot(player, aim(player).multiply(3.2), 2, Skill.EXPLOSIVE_BOLT);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.8f);
        trail(bolt, Particle.SMOKE, null, 60);
    }

    void netShot(Player player) {
        Arrow bolt = shoot(player, aim(player).multiply(2.8), 1, Skill.NET_SHOT);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.2f);
        trail(bolt, Particle.WHITE_ASH, null, 60);
    }

    void harpoonBolt(Player player) {
        Arrow bolt = shoot(player, aim(player).multiply(3.0), skills.damage(player, Skill.HARPOON_BOLT), Skill.HARPOON_BOLT);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.6f);
        trail(bolt, Particle.DUST, Fx.dust(0x9A9A9A, 0.9f), 60);
    }

    void burstFire(Player player) {
        double damage = skills.damage(player, Skill.BURST_FIRE);
        boolean evolved = skills.evolved(player, Skill.BURST_FIRE);
        new BukkitRunnable() {
            int shot;

            @Override
            public void run() {
                if (!player.isOnline() || ++shot > (evolved ? 5 : 3)) {
                    cancel();
                    return;
                }
                Vector spread = aim(player).add(new Vector(ThreadLocalRandom.current().nextDouble(-0.03, 0.03),
                        ThreadLocalRandom.current().nextDouble(-0.03, 0.03), ThreadLocalRandom.current().nextDouble(-0.03, 0.03)));
                Arrow bolt = shoot(player, spread.normalize().multiply(3.2), damage, Skill.BURST_FIRE);
                bolt.setPierceLevel(1);
                trail(bolt, Particle.CRIT, null, 20);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 0.9f, 1.2f + shot * 0.15f);
            }
        }.runTaskTimer(plugin, 0, 3);
    }

    void recoilShot(Player player) {
        Vector direction = aim(player);
        Arrow bolt = shoot(player, direction.clone().multiply(3.6), skills.damage(player, Skill.RECOIL_SHOT), Skill.RECOIL_SHOT);
        trail(bolt, Particle.CRIT, null, 30);
        if (skills.evolved(player, Skill.RECOIL_SHOT)) {
            for (int side : new int[] {-1, 1}) {
                Arrow extra = shoot(player, direction.clone().rotateAroundY(Math.toRadians(side * 8)).multiply(3.6),
                        skills.damage(player, Skill.RECOIL_SHOT), Skill.RECOIL_SHOT);
                trail(extra, Particle.CRIT, null, 30);
            }
        }
        player.setVelocity(direction.clone().multiply(-1.5).setY(Math.max(0.5, -direction.getY() * 1.5)));
        combat.protectFromFall(player, 4000);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.8f);
        player.getWorld().spawnParticle(Particle.GUST, player.getLocation().add(direction), 1);
    }

    // ------------------------------------------------ Bow and crossbow, unlockable

    /** Arrows falling on {@code spot} from above, tagged with the skill. */
    private Arrow skyArrow(Player player, Location spot, double damage, Skill skill, boolean fire) {
        World world = spot.getWorld();
        Location from = spot.clone().add(0, 14, 0);
        Arrow arrow = world.spawnArrow(from, new Vector(0, -1, 0), 1.8f, 2f);
        arrow.setShooter(player);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setDamage(damage);
        arrow.getPersistentDataContainer().set(skillKey, PersistentDataType.STRING, skill.id());
        if (fire) {
            arrow.setFireTicks(400);
        }
        world.spawnParticle(fire ? Particle.FLAME : Particle.CLOUD, from, 3, 0.1, 0.1, 0.1, 0.01);
        return arrow;
    }

    void rainOfFire(Player player) {
        Location target = Aim.groundSpot(player, combat, 40, 25);
        double damage = skills.damage(player, Skill.RAIN_OF_FIRE);
        boolean evolved = skills.evolved(player, Skill.RAIN_OF_FIRE);
        double spread = evolved ? 4.5 : 3;
        int count = evolved ? 26 : 14;
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.7f);
        Fx.ring(target.clone().add(0, 0.1, 0), spread, 30, Particle.FLAME, null);
        new BukkitRunnable() {
            int arrows;

            @Override
            public void run() {
                if (!player.isOnline() || ++arrows > count) {
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                skyArrow(player, target.clone().add(random.nextDouble(-spread, spread), 0, random.nextDouble(-spread, spread)),
                        damage, Skill.RAIN_OF_FIRE, true);
            }
        }.runTaskTimer(plugin, 10, 2);
        if (evolved) {
            // Evolved: the ground keeps burning for 3 seconds.
            new BukkitRunnable() {
                int second;

                @Override
                public void run() {
                    if (!player.isOnline() || ++second > 3) {
                        cancel();
                        return;
                    }
                    world.spawnParticle(Particle.FLAME, target, 40, spread / 2, 0.1, spread / 2, 0.02);
                    for (LivingEntity victim : combat.around(player, target, spread)) {
                        combat.hit(player, victim, 1.5, Skill.RAIN_OF_FIRE);
                        victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
                    }
                }
            }.runTaskTimer(plugin, 40, 20);
        }
    }

    /** Zoom in for a moment (slowness narrows the view), then one very heavy, very fast arrow. */
    void sniperMode(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 32, 5, false, false, false));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 1f, 0.8f);
        plugin.flash(player, plugin.lang().get("aiming"));
        boolean evolved = skills.evolved(player, Skill.SNIPER_MODE);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            double damage = skills.damage(player, Skill.SNIPER_MODE) * (evolved ? 1.5 : 1);
            Arrow arrow = shoot(player, aim(player).multiply(4.5), damage, Skill.SNIPER_MODE);
            arrow.setGravity(false);
            arrow.setPierceLevel(evolved ? 5 : 2);
            trail(arrow, Particle.END_ROD, null, 60);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1.2f, 0.5f);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.6f);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> arrow.setGravity(true), 40);
        }, 30);
    }

    void trapArrow(Player player) {
        Arrow arrow = shoot(player, aim(player).multiply(2.6), skills.damage(player, Skill.TRAP_ARROW), Skill.TRAP_ARROW);
        trail(arrow, Particle.WHITE_ASH, null, 40);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.8f);
    }

    /** A snare on the ground: roots the first enemy that steps in (evolved: all of them, wider). */
    private void plantTrap(Player player, Location spot) {
        boolean evolved = skills.evolved(player, Skill.TRAP_ARROW);
        double radius = evolved ? 3 : 1.6;
        int rootTicks = 80 + 4 * plugin.progress().bonusLevel(player, Skill.TRAP_ARROW);
        double damage = skills.damage(player, Skill.TRAP_ARROW);
        World world = spot.getWorld();
        world.playSound(spot, Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 0.8f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 5;
                if (!player.isOnline() || tick > (evolved ? 400 : 300)) {
                    cancel();
                    return;
                }
                Fx.ring(spot.clone().add(0, 0.1, 0), radius, 16, Particle.DUST, Fx.dust(0xB9A27A, 0.9f));
                List<LivingEntity> caught = combat.around(player, spot, radius);
                if (caught.isEmpty()) {
                    return;
                }
                for (LivingEntity victim : evolved ? caught : caught.subList(0, 1)) {
                    combat.hit(player, victim, damage, Skill.TRAP_ARROW);
                    Root.apply(plugin, victim, rootTicks, 6);
                }
                world.spawnParticle(Particle.BLOCK, spot, 40, radius / 2, 0.3, radius / 2, 0, Material.COBWEB.createBlockData());
                world.playSound(spot, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.2f, 0.6f);
                cancel();
            }
        }.runTaskTimer(plugin, 5, 5);
    }

    /** An arrow that splits into a fan of five (evolved: eight) after a short flight. */
    void splitShot(Player player) {
        double damage = skills.damage(player, Skill.SPLIT_SHOT);
        int pieces = skills.evolved(player, Skill.SPLIT_SHOT) ? 8 : 5;
        Arrow arrow = shoot(player, aim(player).multiply(2.8), damage, Skill.SPLIT_SHOT);
        trail(arrow, Particle.ENCHANTED_HIT, null, 8);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!arrow.isValid() || arrow.isInBlock() || !player.isOnline()) {
                return;
            }
            Vector velocity = arrow.getVelocity();
            Location at = arrow.getLocation();
            arrow.remove();
            at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.4f);
            at.getWorld().spawnParticle(Particle.ENCHANTED_HIT, at, 15, 0.2, 0.2, 0.2, 0.2);
            for (int i = 0; i < pieces; i++) {
                double angle = Math.toRadians((i - (pieces - 1) / 2.0) * 7);
                Arrow piece = at.getWorld().spawnArrow(at, velocity.clone().rotateAroundY(angle), (float) velocity.length(), 0f);
                piece.setShooter(player);
                piece.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                piece.setDamage(damage);
                piece.setCritical(true);
                piece.getPersistentDataContainer().set(skillKey, PersistentDataType.STRING, Skill.SPLIT_SHOT.id());
            }
        }, 8);
    }

    /** Ultimate: arrows keep falling on every enemy around the player. */
    void stormOfArrows(Player player) {
        boolean evolved = skills.evolved(player, Skill.STORM_OF_ARROWS);
        double damage = skills.damage(player, Skill.STORM_OF_ARROWS);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);
        world.spawnParticle(Particle.CLOUD, player.getLocation().add(0, 12, 0), 80, 6, 0.5, 6, 0.02);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 2;
                if (!player.isOnline() || player.isDead() || tick > (evolved ? 160 : 100)) {
                    cancel();
                    return;
                }
                List<LivingEntity> enemies = combat.around(player, player.getLocation(), 14);
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < (evolved ? 2 : 1); i++) {
                    Location spot = enemies.isEmpty()
                            ? player.getLocation().add(random.nextDouble(-8, 8), 0, random.nextDouble(-8, 8))
                            : enemies.get(random.nextInt(enemies.size())).getLocation().add(random.nextDouble(-0.5, 0.5), 0,
                            random.nextDouble(-0.5, 0.5));
                    skyArrow(player, spot, damage, Skill.STORM_OF_ARROWS, false);
                }
                if (tick % 10 == 0) {
                    world.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.3f);
                }
            }
        }.runTaskTimer(plugin, 10, 2);
    }

    /** Ten (evolved: sixteen) bolts in quick succession, for one arrow. */
    void gatling(Player player) {
        double damage = skills.damage(player, Skill.GATLING);
        int bolts = skills.evolved(player, Skill.GATLING) ? 16 : 10;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, bolts * 2 + 4, 0, false, false, false));
        new BukkitRunnable() {
            int shot;

            @Override
            public void run() {
                if (!player.isOnline() || ++shot > bolts) {
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                Vector spread = aim(player).add(new Vector(random.nextDouble(-0.05, 0.05), random.nextDouble(-0.04, 0.04),
                        random.nextDouble(-0.05, 0.05)));
                Arrow bolt = shoot(player, spread.normalize().multiply(3.2), damage, Skill.GATLING);
                trail(bolt, Particle.CRIT, null, 10);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 0.7f, 1.6f + random.nextFloat() * 0.3f);
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    void smokeBolt(Player player) {
        Arrow bolt = shoot(player, aim(player).multiply(2.8), 1, Skill.SMOKE_BOLT);
        trail(bolt, Particle.SMOKE, null, 60);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.9f);
    }

    /** A smoke cloud: blinds and slows enemies inside, and makes mobs lose track of their target. */
    private void smokeCloud(Player player, Location at) {
        boolean evolved = skills.evolved(player, Skill.SMOKE_BOLT);
        double radius = evolved ? 6 : 4;
        int seconds = evolved ? 8 : 6;
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.6f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 10;
                if (!player.isOnline() || tick > seconds * 20) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 25, radius / 2, 1, radius / 2, 0.01);
                world.spawnParticle(Particle.LARGE_SMOKE, at, 20, radius / 2, 0.8, radius / 2, 0.01);
                for (LivingEntity victim : combat.around(player, at, radius)) {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
                    if (evolved) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 0));
                    }
                    if (victim instanceof org.bukkit.entity.Mob mob && mob.getTarget() instanceof Player) {
                        mob.setTarget(null);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 10);
    }

    void chainBolt(Player player) {
        Arrow bolt = shoot(player, aim(player).multiply(3.0), skills.damage(player, Skill.CHAIN_BOLT), Skill.CHAIN_BOLT);
        trail(bolt, Particle.DUST, Fx.dust(0x707070, 0.9f), 60);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.7f);
    }

    /** Chains the hit target to nearby enemies: they are pulled together for 4 seconds. */
    private void chain(Player player, LivingEntity first) {
        boolean evolved = skills.evolved(player, Skill.CHAIN_BOLT);
        List<LivingEntity> group = new java.util.ArrayList<>();
        group.add(first);
        combat.around(player, first.getLocation(), 6).stream()
                .filter(e -> !e.equals(first))
                .sorted(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(first.getLocation())))
                .limit(evolved ? 4 : 2)
                .forEach(group::add);
        if (group.size() < 2) {
            return;
        }
        double shock = skills.damage(player, Skill.CHAIN_BOLT) * 0.3;
        World world = first.getWorld();
        world.playSound(first.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.7f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick += 5;
                group.removeIf(e -> !e.isValid() || e.isDead());
                if (!player.isOnline() || tick > 80 || group.size() < 2) {
                    cancel();
                    return;
                }
                Vector center = new Vector();
                group.forEach(e -> center.add(e.getLocation().toVector()));
                center.multiply(1.0 / group.size());
                for (LivingEntity member : group) {
                    Vector pull = center.clone().subtract(member.getLocation().toVector()).setY(0);
                    if (pull.lengthSquared() > 1) {
                        member.setVelocity(member.getVelocity().add(pull.normalize().multiply(0.35)));
                    }
                    member.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 1));
                    Fx.line(group.getFirst().getLocation().add(0, 1, 0), member.getLocation().add(0, 1, 0), 0.4, Particle.DUST,
                            Fx.dust(0x707070, 0.8f));
                    if (evolved && tick % 20 == 0) {
                        combat.hit(player, member, shock, Skill.CHAIN_BOLT);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    /** Ultimate: shells rain on the area the player looks at. They never break blocks. */
    void artilleryBarrage(Player player) {
        Location center = Aim.groundSpot(player, combat, 48, 25);
        boolean evolved = skills.evolved(player, Skill.ARTILLERY_BARRAGE);
        double radius = evolved ? 8 : 6;
        int shells = evolved ? 20 : 12;
        double damage = skills.damage(player, Skill.ARTILLERY_BARRAGE);
        World world = center.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1.2f, 0.5f);
        world.playSound(center, Sound.EVENT_RAID_HORN, 0.6f, 1.4f);
        Fx.ring(center.clone().add(0, 0.1, 0), radius, 50, Particle.DUST, Fx.dust(0xFF3030, 1.5f));
        new BukkitRunnable() {
            int shell;

            @Override
            public void run() {
                if (!player.isOnline() || ++shell > shells) {
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                Location spot = center.clone().add(random.nextDouble(-radius, radius), 0, random.nextDouble(-radius, radius));
                spot = world.getHighestBlockAt(spot).getLocation().add(0.5, 1, 0.5);
                if (Math.abs(spot.getY() - center.getY()) > 6) {
                    spot.setY(center.getY());
                }
                Location impact = spot;
                Fx.ring(impact.clone().add(0, 0.1, 0), 1.2, 12, Particle.DUST, Fx.dust(0xFF8C00, 1.0f));
                world.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 0.5f);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    Fx.line(impact.clone().add(0, 12, 0), impact, 0.6, Particle.SMOKE, null);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 1);
                    world.spawnParticle(Particle.FLAME, impact, 20, 1, 0.3, 1, 0.05);
                    world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
                    for (LivingEntity victim : combat.around(player, impact, 3)) {
                        double closeness = 1 - Math.min(1, victim.getLocation().distance(impact) / 3);
                        combat.hit(player, victim, damage * (0.4 + 0.6 * closeness), Skill.ARTILLERY_BARRAGE);
                        Combat.knockBack(victim, impact, 0.5 + 0.5 * closeness, 0.4);
                    }
                }, 10);
            }
        }.runTaskTimer(plugin, 20, 4);
    }

    // --- Impacts ---

    @EventHandler
    public void onImpact(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String id = projectile.getPersistentDataContainer().get(skillKey, PersistentDataType.STRING);
        if (id == null || !(projectile.getShooter() instanceof Player player)) {
            return;
        }
        Skill skill = Skill.fromId(id);
        if (skill == null) {
            return;
        }
        // Direct hits by skill arrows count toward the skill's XP.
        if (event.getHitEntity() instanceof LivingEntity) {
            plugin.progress().onHit(player, skill);
        }
        Location at = event.getHitEntity() != null ? event.getHitEntity().getLocation().add(0, 0.5, 0) : projectile.getLocation();
        World world = at.getWorld();
        switch (skill) {
            case EXPLOSIVE_BOLT -> {
                world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
                world.spawnParticle(Particle.FLAME, at, 30, 1, 0.5, 1, 0.05);
                world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                double damage = skills.damage(player, Skill.EXPLOSIVE_BOLT);
                for (LivingEntity target : combat.around(player, at, 4)) {
                    double closeness = 1 - Math.min(1, target.getLocation().distance(at) / 4);
                    combat.hit(player, target, damage * (0.4 + 0.6 * closeness), Skill.EXPLOSIVE_BOLT);
                    Combat.knockBack(target, at, 0.6 + 0.6 * closeness, 0.4);
                }
                if (skills.evolved(player, Skill.EXPLOSIVE_BOLT)) {
                    // Evolved: three smaller blasts go off around the first one.
                    for (int i = 0; i < 3; i++) {
                        Location cluster = at.clone().add(new Vector(2.5, 0, 0).rotateAroundY(Math.PI * 2 * i / 3));
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            world.spawnParticle(Particle.EXPLOSION, cluster, 1);
                            world.playSound(cluster, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                            for (LivingEntity target : combat.around(player, cluster, 2.5)) {
                                combat.hit(player, target, damage * 0.4, Skill.EXPLOSIVE_BOLT);
                            }
                        }, 6L + i * 3);
                    }
                }
                projectile.remove();
            }
            case NET_SHOT -> {
                world.spawnParticle(Particle.BLOCK, at, 60, 1.5, 0.5, 1.5, 0, Material.COBWEB.createBlockData());
                double netRadius = skills.evolved(player, Skill.NET_SHOT) ? 5 : 3;
                Fx.ring(at.clone().add(0, 0.1, 0), netRadius, 30, Particle.WHITE_ASH, null);
                world.playSound(at, Sound.BLOCK_WOOL_PLACE, 1.2f, 0.6f);
                int ticks = 60 + 4 * plugin.progress().bonusLevel(player, Skill.NET_SHOT);
                double damage = skills.damage(player, Skill.NET_SHOT);
                for (LivingEntity target : combat.around(player, at, netRadius)) {
                    combat.hit(player, target, damage, Skill.NET_SHOT);
                    Root.apply(plugin, target, ticks, 6);
                }
                projectile.remove();
            }
            case HARPOON_BOLT -> {
                if (event.getHitEntity() instanceof LivingEntity target && !target.equals(player)
                        && skills.evolved(player, Skill.HARPOON_BOLT)) {
                    // Evolved: everything near the target gets dragged in too.
                    for (LivingEntity near : combat.around(player, target.getLocation(), 3)) {
                        if (!near.equals(target)) {
                            Vector pullNear = player.getLocation().toVector().subtract(near.getLocation().toVector());
                            double d = pullNear.length();
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                    near.setVelocity(pullNear.normalize().multiply(Math.min(2.2, 0.4 + d * 0.11)).setY(0.4)));
                        }
                    }
                }
                if (event.getHitEntity() instanceof LivingEntity target && !target.equals(player)) {
                    Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
                    double distance = pull.length();
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            target.setVelocity(pull.normalize().multiply(Math.min(2.4, 0.4 + distance * 0.12)).setY(0.45)));
                    Fx.line(player.getEyeLocation(), target.getLocation().add(0, 1, 0), 0.4, Particle.DUST, Fx.dust(0x9A9A9A, 1.0f));
                    world.playSound(target.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
                }
            }
            case WIND_ARROW -> {
                windBurst(player, at, event.getHitEntity() instanceof LivingEntity hit ? hit : null);
                if (event.getHitEntity() == null) {
                    projectile.remove();
                }
            }
            case TRAP_ARROW -> {
                plantTrap(player, event.getHitEntity() != null ? event.getHitEntity().getLocation() : projectile.getLocation());
                projectile.remove();
            }
            case SMOKE_BOLT -> {
                smokeCloud(player, at);
                projectile.remove();
            }
            case CHAIN_BOLT -> {
                if (event.getHitEntity() instanceof LivingEntity target && !target.equals(player)) {
                    chain(player, target);
                }
            }
            default -> {
            }
        }
    }
}
