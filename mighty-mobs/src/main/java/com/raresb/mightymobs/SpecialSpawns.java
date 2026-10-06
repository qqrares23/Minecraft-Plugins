package com.raresb.mightymobs;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Special spawns: when certain mobs spawn naturally, there is a chance a special variant appears
 * instead (chicken jockeys, horsemen, armored brutes...). Chances are in the config under
 * special-spawns (percent) and can be changed in-game with /mm special.
 */
final class SpecialSpawns implements Listener {
    /** Each special spawn and the natural spawn it can replace. */
    enum Type {
        CHICKEN_JOCKEY("Chicken Jockey", EntityType.ZOMBIE, 5, "A baby zombie riding a chicken."),
        SPIDER_JOCKEY("Spider Jockey", EntityType.SPIDER, 5, "A skeleton riding a spider."),
        CHARGED_CREEPER("Charged Creeper", EntityType.CREEPER, 3, "A creeper with a much bigger explosion."),
        SKELETON_HORSEMAN("Skeleton Horseman", EntityType.SKELETON, 1.5, "A skeleton with an enchanted bow riding a skeleton horse."),
        ZOMBIE_HORSEMAN("Zombie Horseman", EntityType.ZOMBIE, 1.5, "A zombie riding a zombie horse."),
        ARMORED_BRUTE("Armored Brute", EntityType.ZOMBIE, 2, "A zombie in iron or diamond armor with a sword."),
        BABY_ZOMBIE_PACK("Baby Zombie Pack", EntityType.ZOMBIE, 2, "Three extra fast baby zombies."),
        SLIME_TOWER("Slime Tower", EntityType.SLIME, 4, "Three slimes stacked on top of each other."),
        BAT_BOMBER("Bat Bomber", EntityType.CREEPER, 1, "A creeper riding a bat, dropping in from above."),
        WITCH_COVEN("Witch Coven", EntityType.WITCH, 5, "Two more witches come along."),
        KILLER_BUNNY("Killer Bunny", EntityType.RABBIT, 3, "A hostile rabbit that hunts players.");

        private final String displayName;
        private final EntityType trigger;
        private final double defaultChance;
        private final String description;

        Type(String displayName, EntityType trigger, double defaultChance, String description) {
            this.displayName = displayName;
            this.trigger = trigger;
            this.defaultChance = defaultChance;
            this.description = description;
        }

        String displayName() {
            return displayName;
        }

        EntityType trigger() {
            return trigger;
        }

        String description() {
            return description;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Type fromId(String id) {
            try {
                return valueOf(id.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final MightyMobsPlugin plugin;
    private final MightyMobs mobs;
    private final Loot loot;
    private final Mounts mounts;

    SpecialSpawns(MightyMobsPlugin plugin, MightyMobs mobs, Loot loot, Mounts mounts) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.loot = loot;
        this.mounts = mounts;
    }

    /** Chance in percent (0-100). */
    double chance(Type type) {
        return plugin.getConfig().getDouble("special-spawns." + type.id(), type.defaultChance);
    }

    void setChance(Type type, double percent) {
        plugin.getConfig().set("special-spawns." + type.id(), percent);
        plugin.saveConfig();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != SpawnReason.NATURAL || !plugin.worldAllowed(event.getEntity().getWorld())
                || mobs.isMighty(event.getEntity()) || !plugin.getConfig().getBoolean("special-spawns.enabled", true)
                || plugin.ignored(event.getEntityType())) {
            return;
        }
        LivingEntity entity = event.getEntity();
        double multiplier = plugin.bloodMoon().specialMultiplier(entity.getWorld());
        for (Type type : Type.values()) {
            if (type.trigger() == entity.getType() && ThreadLocalRandom.current().nextDouble() * 100 < chance(type) * multiplier) {
                // Change the mob once it is actually in the world.
                entity.getScheduler().run(plugin, task -> apply(type, entity), null);
                return; // at most one special per spawn
            }
        }
    }

    /** Turns a freshly spawned mob into the special spawn. Also used by /mm special spawn. */
    void apply(Type type, LivingEntity entity) {
        if (!entity.isValid()) {
            return;
        }
        World world = entity.getWorld();
        Location at = entity.getLocation();
        loot.markSpecial(entity, type); // its drops match the special spawn (see Loot)
        switch (type) {
            case CHICKEN_JOCKEY -> {
                if (entity instanceof Zombie zombie) {
                    zombie.setBaby();
                    Chicken chicken = world.spawn(at, Chicken.class, SpawnReason.CUSTOM, mounts::mark);
                    chicken.addPassenger(zombie);
                }
            }
            case SPIDER_JOCKEY -> {
                if (entity instanceof Spider spider) {
                    Skeleton skeleton = world.spawn(at, Skeleton.class, SpawnReason.CUSTOM, this::markBuffed);
                    spider.addPassenger(skeleton);
                }
            }
            case CHARGED_CREEPER -> {
                if (entity instanceof Creeper creeper) {
                    creeper.setPowered(true);
                }
            }
            case SKELETON_HORSEMAN -> {
                if (entity instanceof Skeleton skeleton) {
                    SkeletonHorse horse = world.spawn(at, SkeletonHorse.class, SpawnReason.CUSTOM, h -> {
                        h.setTamed(true);
                        mounts.mark(h);
                    });
                    ItemStack bow = new ItemStack(Material.BOW);
                    bow.addUnsafeEnchantment(Enchantment.POWER, 2);
                    equip(skeleton, bow, Material.IRON_HELMET);
                    horse.addPassenger(skeleton);
                }
            }
            case ZOMBIE_HORSEMAN -> {
                if (entity instanceof Zombie zombie) {
                    zombie.setAdult();
                    ZombieHorse horse = world.spawn(at, ZombieHorse.class, SpawnReason.CUSTOM, h -> {
                        h.setTamed(true);
                        mounts.mark(h);
                    });
                    equip(zombie, new ItemStack(Material.IRON_SWORD), Material.CHAINMAIL_HELMET);
                    horse.addPassenger(zombie);
                }
            }
            case ARMORED_BRUTE -> {
                if (entity instanceof Zombie zombie) {
                    zombie.setAdult();
                    boolean diamond = ThreadLocalRandom.current().nextDouble() < 0.25;
                    EntityEquipment gear = zombie.getEquipment();
                    gear.setHelmet(new ItemStack(diamond ? Material.DIAMOND_HELMET : Material.IRON_HELMET));
                    gear.setChestplate(new ItemStack(diamond ? Material.DIAMOND_CHESTPLATE : Material.IRON_CHESTPLATE));
                    gear.setLeggings(new ItemStack(diamond ? Material.DIAMOND_LEGGINGS : Material.IRON_LEGGINGS));
                    gear.setBoots(new ItemStack(diamond ? Material.DIAMOND_BOOTS : Material.IRON_BOOTS));
                    gear.setItemInMainHand(new ItemStack(diamond ? Material.DIAMOND_SWORD : Material.IRON_SWORD));
                    lowDropChances(gear);
                    // The health display shows this name in front of the hearts.
                    mobs.setBaseName(zombie, plugin.lang().get("armored-brute-name"));
                    mobs.updateName(zombie);
                }
            }
            case BABY_ZOMBIE_PACK -> {
                for (int i = 0; i < 3; i++) {
                    Location spot = at.clone().add(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 0, ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
                    if (!spot.getBlock().isPassable()) {
                        spot = at;
                    }
                    world.spawn(spot, Zombie.class, SpawnReason.CUSTOM, z -> {
                        z.setBaby();
                        markBuffed(z);
                    });
                }
            }
            case SLIME_TOWER -> {
                if (entity instanceof Slime base) {
                    Slime middle = world.spawn(at, Slime.class, SpawnReason.CUSTOM, s -> {
                        s.setSize(Math.max(1, base.getSize() - 1));
                        markBuffed(s);
                    });
                    Slime top = world.spawn(at, Slime.class, SpawnReason.CUSTOM, s -> {
                        s.setSize(Math.max(1, base.getSize() - 2));
                        markBuffed(s);
                    });
                    base.addPassenger(middle);
                    middle.addPassenger(top);
                }
            }
            case BAT_BOMBER -> {
                if (entity instanceof Creeper creeper) {
                    Bat bat = world.spawn(at.clone().add(0, 3, 0), Bat.class, SpawnReason.CUSTOM, b -> b.setAwake(true));
                    bat.addPassenger(creeper);
                }
            }
            case WITCH_COVEN -> {
                for (int i = 0; i < 2; i++) {
                    Location spot = at.clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2));
                    world.spawn(spot.getBlock().isPassable() ? spot : at, Witch.class, SpawnReason.CUSTOM, this::markBuffed);
                }
            }
            case KILLER_BUNNY -> {
                if (entity instanceof Rabbit rabbit) {
                    rabbit.setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
                }
            }
        }
    }

    /** Mobs that come with a special spawn (riders, packs, covens) count as buffed too. */
    private void markBuffed(LivingEntity entity) {
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin, "buffed"), PersistentDataType.BYTE, (byte) 1);
    }

    private static void equip(LivingEntity entity, ItemStack weapon, Material helmet) {
        EntityEquipment gear = entity.getEquipment();
        if (gear == null) {
            return;
        }
        gear.setItemInMainHand(weapon);
        gear.setHelmet(new ItemStack(helmet));
        lowDropChances(gear);
    }

    /** Special gear rarely drops, like vanilla mob gear. */
    private static void lowDropChances(EntityEquipment gear) {
        gear.setHelmetDropChance(0.05f);
        gear.setChestplateDropChance(0.05f);
        gear.setLeggingsDropChance(0.05f);
        gear.setBootsDropChance(0.05f);
        gear.setItemInMainHandDropChance(0.05f);
    }
}
