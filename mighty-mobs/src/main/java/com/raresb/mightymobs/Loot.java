package com.raresb.mightymobs;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * What mighty mobs and special spawns drop when a player kills them: usually something that fits
 * the mob (its "themed" drop), and now and then a rare drop such as an enchanted book.
 */
final class Loot implements Listener {
    private final MightyMobsPlugin plugin;
    private final MightyMobs mobs;
    /** Marks a special spawn with its type, so its drops can match it. */
    private final NamespacedKey specialKey;

    Loot(MightyMobsPlugin plugin, MightyMobs mobs) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.specialKey = new NamespacedKey(plugin, "special");
    }

    void markSpecial(LivingEntity entity, SpecialSpawns.Type type) {
        entity.getPersistentDataContainer().set(specialKey, PersistentDataType.STRING, type.id());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !plugin.getConfig().getBoolean("drops.enabled", true)) {
            return; // only kills by players count: no loot from mobs dying to the sun or to falls
        }
        Set<Ability> abilities = mobs.abilities(entity);
        String specialId = entity.getPersistentDataContainer().get(specialKey, PersistentDataType.STRING);
        SpecialSpawns.Type special = specialId == null ? null : SpecialSpawns.Type.fromId(specialId);
        if (abilities.isEmpty() && special == null) {
            return;
        }
        Location at = entity.getLocation().add(0, 0.5, 0);
        double themedChance = plugin.getConfig().getDouble("drops.themed-chance", 0.5);
        double rareChance = 0;

        for (Ability ability : abilities) {
            if (roll(themedChance)) {
                at.getWorld().dropItemNaturally(at, themed(ability));
            }
        }
        if (!abilities.isEmpty()) {
            rareChance += plugin.getConfig().getDouble("drops.rare-chance", 0.10)
                    + (abilities.size() > 1 ? plugin.getConfig().getDouble("drops.second-ability-bonus", 0.08) : 0);
        }
        if (special != null) {
            if (roll(themedChance) || special == SpecialSpawns.Type.KILLER_BUNNY) {
                at.getWorld().dropItemNaturally(at, themed(special));
            }
            rareChance += specialRareChance(special) * plugin.getConfig().getDouble("drops.special-rare-multiplier", 1.0);
        }
        rareChance += plugin.bloodMoon().rareBonus(entity.getWorld());
        int looting = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        rareChance += looting * plugin.getConfig().getDouble("drops.looting-bonus", 0.02);
        // Bounty Hunter (a MagicEnchants enchantment on the weapon): +3% rare drop chance per level.
        rareChance += 0.03 * magicLevel(killer.getInventory().getItemInMainHand(), "bounty_hunter");

        if (roll(rareChance)) {
            // Special spawns give their own signature drop half the time.
            ItemStack signature = special != null && roll(0.5) ? signature(special) : null;
            dropRare(killer, signature != null ? signature : rare(), at);
        }
    }

    private void dropRare(Player killer, ItemStack item, Location at) {
        Item dropped = at.getWorld().dropItemNaturally(at, item);
        dropped.setGlowing(true);
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 25, 0.3, 0.4, 0.3, 0.2);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        killer.sendMessage(plugin.lang().get("rare-drop", "item", item.effectiveName()));
    }

    // --- Tables ---

    /** Something that fits the ability. */
    private static ItemStack themed(Ability ability) {
        return switch (ability) {
            case TANK -> stack(Material.IRON_INGOT, 1, 3);
            case SWIFT -> stack(Material.SUGAR, 1, 3);
            case BERSERKER -> stack(Material.BLAZE_POWDER, 1, 2);
            case VENOMOUS -> stack(Material.SPIDER_EYE, 1, 2);
            case FROST -> stack(Material.PACKED_ICE, 1, 2);
            case VAMPIRE -> stack(Material.REDSTONE, 2, 4);
            case EXPLOSIVE -> stack(Material.GUNPOWDER, 2, 4);
            case LEAPER -> stack(Material.SLIME_BALL, 1, 2);
            case REGENERATING -> stack(Material.GLISTERING_MELON_SLICE, 1, 2);
            case BLINKER -> stack(Material.ENDER_PEARL, 1, 2);
            case NECROMANCER -> stack(Material.BONE, 2, 5);
        };
    }

    /** Something that fits the special spawn. */
    private static ItemStack themed(SpecialSpawns.Type type) {
        return switch (type) {
            case CHICKEN_JOCKEY -> roll(0.5) ? stack(Material.FEATHER, 2, 4) : stack(Material.EGG, 1, 2);
            case SPIDER_JOCKEY -> roll(0.5) ? stack(Material.STRING, 2, 4) : stack(Material.ARROW, 3, 6);
            case CHARGED_CREEPER -> stack(Material.GUNPOWDER, 3, 6);
            case SKELETON_HORSEMAN -> roll(0.5) ? stack(Material.ARROW, 4, 8) : stack(Material.BONE, 2, 4);
            case ZOMBIE_HORSEMAN -> stack(Material.LEATHER, 2, 4);
            case ARMORED_BRUTE -> stack(Material.IRON_INGOT, 2, 5);
            case BABY_ZOMBIE_PACK -> roll(0.5) ? stack(Material.CARROT, 1, 3) : stack(Material.POTATO, 1, 3);
            case SLIME_TOWER -> stack(Material.SLIME_BALL, 3, 6);
            case BAT_BOMBER -> stack(Material.GUNPOWDER, 2, 4);
            case WITCH_COVEN -> roll(0.5) ? stack(Material.NETHER_WART, 2, 4) : stack(Material.GLOWSTONE_DUST, 2, 4);
            case KILLER_BUNNY -> stack(Material.RABBIT_FOOT, 1, 1);
        };
    }

    /** How often a special spawn gives a rare drop: the tougher the fight, the better the odds. */
    private static double specialRareChance(SpecialSpawns.Type type) {
        return switch (type) {
            case ARMORED_BRUTE -> 0.20;
            case SKELETON_HORSEMAN, ZOMBIE_HORSEMAN -> 0.15;
            case CHARGED_CREEPER, BAT_BOMBER, KILLER_BUNNY -> 0.10;
            case CHICKEN_JOCKEY, SPIDER_JOCKEY, WITCH_COVEN -> 0.06;
            case SLIME_TOWER -> 0.05;
            case BABY_ZOMBIE_PACK -> 0.04;
        };
    }

    /** The special spawn's own rare drop, or null if it has none. */
    private static ItemStack signature(SpecialSpawns.Type type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (type) {
            case SKELETON_HORSEMAN, SPIDER_JOCKEY -> {
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addEnchantment(Enchantment.POWER, type == SpecialSpawns.Type.SKELETON_HORSEMAN ? random.nextInt(2, 5) : random.nextInt(1, 3));
                return bow;
            }
            case ZOMBIE_HORSEMAN -> {
                return new ItemStack(Material.SADDLE);
            }
            case ARMORED_BRUTE -> {
                Material[] pieces = {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                        Material.DIAMOND_HELMET, Material.DIAMOND_BOOTS};
                ItemStack piece = new ItemStack(pieces[random.nextInt(pieces.length)]);
                piece.addEnchantment(Enchantment.PROTECTION, random.nextInt(1, 4));
                return piece;
            }
            case CHARGED_CREEPER -> {
                return new ItemStack(Material.CREEPER_HEAD);
            }
            case BAT_BOMBER -> {
                return stack(Material.TNT, 2, 4);
            }
            case SLIME_TOWER -> {
                return stack(Material.SLIME_BLOCK, 1, 2);
            }
            case WITCH_COVEN -> {
                return stack(Material.EXPERIENCE_BOTTLE, 4, 8);
            }
            default -> {
                return null;
            }
        }
    }

    /** MagicEnchants enchantments looked up by id (empty when that plugin isn't installed). */
    private final java.util.Map<String, java.util.Optional<Enchantment>> magicEnchants = new java.util.HashMap<>();

    /** The level of a MagicEnchants enchantment on an item (0 when that plugin isn't installed). */
    private int magicLevel(ItemStack item, String id) {
        var enchantment = magicEnchants.computeIfAbsent(id, key -> java.util.Optional.ofNullable(RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT).get(net.kyori.adventure.key.Key.key("magicenchants", key)))).orElse(null);
        return enchantment == null || item == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    /** The shared rare table. Enchanted books are about a third of it. */
    private static ItemStack rare() {
        int pick = ThreadLocalRandom.current().nextInt(100);
        if (pick < 30) {
            return enchantedBook();
        }
        if (pick < 55) {
            return stack(Material.EXPERIENCE_BOTTLE, 2, 5);
        }
        if (pick < 73) {
            return stack(Material.EMERALD, 1, 3);
        }
        if (pick < 83) {
            return new ItemStack(Material.GOLDEN_APPLE);
        }
        if (pick < 93) {
            return stack(Material.DIAMOND, 1, 2);
        }
        if (pick < 97) {
            return new ItemStack(Material.NAME_TAG);
        }
        if (pick < 99) {
            return new ItemStack(Material.NETHERITE_SCRAP);
        }
        return new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
    }

    /**
     * A book with one enchantment an enchanting table could give (so the MagicEnchants ones too,
     * but no treasure such as Mending). The level leans low: the lower of two rolls.
     */
    private static ItemStack enchantedBook() {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        List<Enchantment> options = new ArrayList<>();
        for (var key : registry.getTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE).values()) {
            Enchantment enchantment = registry.get(key);
            if (enchantment != null) {
                options.add(enchantment);
            }
        }
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (!options.isEmpty()) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Enchantment enchantment = options.get(random.nextInt(options.size()));
            int level = Math.min(random.nextInt(1, enchantment.getMaxLevel() + 1), random.nextInt(1, enchantment.getMaxLevel() + 1));
            book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(enchantment, level, true));
        }
        return book;
    }

    private static ItemStack stack(Material material, int min, int max) {
        return new ItemStack(material, ThreadLocalRandom.current().nextInt(min, max + 1));
    }

    private static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }
}
