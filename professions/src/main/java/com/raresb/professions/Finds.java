package com.raresb.professions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/**
 * The random finds each profession can turn up, gated by level and weighted by rarity. Every find
 * belongs to the activity: what turns up depends on the block that was broken.
 */
final class Finds {
    /** How special a find is: rare ones get a bigger effect and a chat message. */
    enum Rarity {
        COMMON, UNCOMMON, RARE, VERY_RARE
    }

    /**
     * One possible find. {@code item} gets the block that was broken (so a find can match it, e.g.
     * a sapling of the tree being cut) and {@code from} limits the find to the blocks it belongs to.
     */
    record Find(Function<Material, ItemStack> item, int weight, int minLevel, Rarity rarity, Predicate<Material> from) {
    }

    private Finds() {
    }

    private static final Predicate<Material> ANYWHERE = source -> true;

    private static Find find(Material material, int min, int max, int weight, int minLevel, Rarity rarity, Predicate<Material> from) {
        return new Find(source -> new ItemStack(material, ThreadLocalRandom.current().nextInt(min, max + 1)), weight, minLevel, rarity, from);
    }

    private static Find find(Material material, int min, int max, int weight, int minLevel, Rarity rarity) {
        return find(material, min, max, weight, minLevel, rarity, ANYWHERE);
    }

    private static Find find(Function<Material, ItemStack> item, int weight, int minLevel, Rarity rarity, Predicate<Material> from) {
        return new Find(item, weight, minLevel, rarity, from);
    }

    private static ItemStack stack(Material material, int min, int max) {
        return new ItemStack(material, ThreadLocalRandom.current().nextInt(min, max + 1));
    }

    private static Predicate<Material> named(String... parts) {
        return source -> {
            for (String part : parts) {
                if (source.name().contains(part)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static Predicate<Material> of(Material... materials) {
        Set<Material> set = Set.of(materials);
        return set::contains;
    }

    /** An enchanted book with a random enchantment that can come from an enchanting table. */
    static ItemStack randomEnchantedBook() {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        List<Enchantment> options = new ArrayList<>();
        var tableTag = registry.getTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE);
        for (var key : tableTag.values()) {
            Enchantment enchantment = registry.get(key);
            if (enchantment != null) {
                options.add(enchantment);
            }
        }
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (!options.isEmpty()) {
            Enchantment enchantment = options.get(ThreadLocalRandom.current().nextInt(options.size()));
            int level = ThreadLocalRandom.current().nextInt(1, enchantment.getMaxLevel() + 1);
            book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(enchantment, level, true));
        }
        return book;
    }

    private static final Material[] SHERDS = {Material.ANGLER_POTTERY_SHERD, Material.ARCHER_POTTERY_SHERD, Material.ARMS_UP_POTTERY_SHERD,
            Material.BLADE_POTTERY_SHERD, Material.BREWER_POTTERY_SHERD, Material.BURN_POTTERY_SHERD, Material.DANGER_POTTERY_SHERD,
            Material.EXPLORER_POTTERY_SHERD, Material.FRIEND_POTTERY_SHERD, Material.HEART_POTTERY_SHERD, Material.HEARTBREAK_POTTERY_SHERD,
            Material.HOWL_POTTERY_SHERD, Material.MINER_POTTERY_SHERD, Material.MOURNER_POTTERY_SHERD, Material.PLENTY_POTTERY_SHERD,
            Material.PRIZE_POTTERY_SHERD, Material.SHEAF_POTTERY_SHERD, Material.SHELTER_POTTERY_SHERD, Material.SKULL_POTTERY_SHERD,
            Material.SNORT_POTTERY_SHERD};

    private static ItemStack randomOf(Material... materials) {
        return new ItemStack(materials[ThreadLocalRandom.current().nextInt(materials.length)]);
    }

    // ------------------------------------------------------------ Woodcutting

    private static final Predicate<Material> NETHER_STEM = named("CRIMSON", "WARPED");
    private static final Predicate<Material> OVERWORLD_TREE = NETHER_STEM.negate();

    /** The sapling (or propagule, or fungus) of the tree a log or leaf block belongs to. */
    static Material saplingOf(Material tree) {
        String name = tree.name().replace("STRIPPED_", "");
        for (String suffix : new String[] {"_LOG", "_WOOD", "_STEM", "_HYPHAE", "_LEAVES"}) {
            if (name.endsWith(suffix)) {
                name = name.substring(0, name.length() - suffix.length());
            }
        }
        name = name.replace("FLOWERING_", "");
        for (String sapling : new String[] {name + "_SAPLING", name + "_PROPAGULE", name + "_FUNGUS", name}) {
            Material match = Material.getMaterial(sapling);
            if (match != null && match.isItem() && !match.name().endsWith("_LEAVES")) {
                return match; // AZALEA_LEAVES -> AZALEA
            }
        }
        return Material.STICK;
    }

    static final List<Find> WOODCUTTING = List.of(
            find(Material.STICK, 2, 4, 30, 1, Rarity.COMMON),
            find(source -> stack(source, 1, 2), 25, 1, Rarity.COMMON, ANYWHERE), // a thick branch: more of the same wood
            find(source -> new ItemStack(saplingOf(source)), 20, 1, Rarity.COMMON, ANYWHERE),
            find(Material.APPLE, 1, 2, 20, 1, Rarity.COMMON, named("OAK")),
            find(Material.COCOA_BEANS, 1, 3, 20, 3, Rarity.COMMON, named("JUNGLE")),
            find(Material.RESIN_CLUMP, 1, 3, 20, 3, Rarity.COMMON, named("PALE_OAK")),
            find(source -> randomOf(Material.EGG, Material.FEATHER), 10, 5, Rarity.UNCOMMON, OVERWORLD_TREE), // a bird's nest
            find(Material.HONEYCOMB, 1, 2, 8, 8, Rarity.UNCOMMON, OVERWORLD_TREE),
            find(Material.SHROOMLIGHT, 1, 1, 10, 8, Rarity.UNCOMMON, NETHER_STEM),
            find(Material.GOLDEN_APPLE, 1, 1, 3, 20, Rarity.RARE, OVERWORLD_TREE),
            find(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 1, 45, Rarity.VERY_RARE, OVERWORLD_TREE));

    // ----------------------------------------------------------------- Mining

    private static final Predicate<Material> NETHER_ROCK = named("NETHER", "BLACKSTONE", "BASALT", "MAGMA", "ANCIENT_DEBRIS")
            .and(of(Material.SMOOTH_BASALT).negate());
    private static final Predicate<Material> END_ROCK = of(Material.END_STONE, Material.OBSIDIAN);
    private static final Predicate<Material> OVERWORLD_ROCK = NETHER_ROCK.or(END_ROCK).negate();
    private static final Predicate<Material> DEEP_ROCK = named("DEEPSLATE").or(of(Material.TUFF));
    private static final Predicate<Material> GEODE = of(Material.CALCITE, Material.SMOOTH_BASALT, Material.AMETHYST_CLUSTER);

    static final List<Find> MINING = List.of(
            find(Material.COAL, 1, 3, 30, 1, Rarity.COMMON, OVERWORLD_ROCK),
            find(Material.RAW_COPPER, 1, 3, 20, 3, Rarity.COMMON, OVERWORLD_ROCK),
            find(Material.RAW_IRON, 1, 2, 14, 6, Rarity.UNCOMMON, OVERWORLD_ROCK),
            find(Material.REDSTONE, 2, 5, 14, 8, Rarity.UNCOMMON, DEEP_ROCK),
            find(Material.LAPIS_LAZULI, 2, 5, 12, 8, Rarity.UNCOMMON, DEEP_ROCK),
            find(Material.AMETHYST_SHARD, 1, 3, 40, 1, Rarity.UNCOMMON, GEODE),
            find(Material.RAW_GOLD, 1, 2, 6, 15, Rarity.RARE, OVERWORLD_ROCK),
            find(Material.EMERALD, 1, 1, 3, 20, Rarity.RARE, OVERWORLD_ROCK),
            find(Material.DIAMOND, 1, 1, 3, 30, Rarity.RARE, DEEP_ROCK),
            find(Material.QUARTZ, 1, 3, 30, 1, Rarity.COMMON, NETHER_ROCK),
            find(Material.GOLD_NUGGET, 2, 6, 25, 1, Rarity.COMMON, NETHER_ROCK),
            find(Material.MAGMA_CREAM, 1, 2, 30, 5, Rarity.UNCOMMON, of(Material.MAGMA_BLOCK)),
            find(Material.GLOWSTONE_DUST, 1, 3, 10, 8, Rarity.UNCOMMON, NETHER_ROCK),
            find(Material.NETHERITE_SCRAP, 1, 1, 1, 40, Rarity.VERY_RARE, NETHER_ROCK));

    // ---------------------------------------------------------------- Digging

    private static final Predicate<Material> SNOW = named("SNOW");
    private static final Predicate<Material> SOUL = named("SOUL_");
    private static final Predicate<Material> EARTH = SNOW.or(SOUL).negate();

    static final List<Find> DIGGING = List.of(
            find(Material.FLINT, 1, 3, 40, 1, Rarity.COMMON, named("GRAVEL")),
            find(Material.BONE, 1, 3, 25, 1, Rarity.COMMON, SNOW.negate()),
            find(Material.CLAY_BALL, 2, 4, 20, 1, Rarity.COMMON, of(Material.CLAY, Material.MUD, Material.DIRT, Material.GRASS_BLOCK,
                    Material.COARSE_DIRT, Material.ROOTED_DIRT)),
            find(Material.SNOWBALL, 2, 4, 30, 1, Rarity.COMMON, SNOW),
            find(Material.IRON_NUGGET, 2, 5, 12, 3, Rarity.UNCOMMON, EARTH),
            find(Material.GOLD_NUGGET, 1, 4, 10, 5, Rarity.UNCOMMON, SNOW.negate()),
            find(Material.NETHER_WART, 1, 2, 15, 5, Rarity.UNCOMMON, SOUL),
            find(source -> randomOf(SHERDS), 5, 15, Rarity.RARE, EARTH),
            find(Material.EMERALD, 1, 2, 3, 20, Rarity.RARE, EARTH),
            find(Material.DIAMOND, 1, 1, 1, 35, Rarity.VERY_RARE, EARTH));

    // ---------------------------------------------------------------- Farming

    /** What a crop block yields when harvested. */
    static Material produceOf(Material crop) {
        return switch (crop) {
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case COCOA -> Material.COCOA_BEANS;
            case MELON -> Material.MELON_SLICE;
            case TORCHFLOWER_CROP -> Material.TORCHFLOWER;
            case PITCHER_CROP -> Material.PITCHER_PLANT;
            default -> crop.isItem() ? crop : Material.WHEAT;
        };
    }

    static final List<Find> FARMING = List.of(
            find(source -> stack(produceOf(source), 2, 4), 30, 1, Rarity.COMMON, ANYWHERE), // a bumper crop
            find(Material.BONE_MEAL, 1, 3, 25, 1, Rarity.COMMON),
            find(source -> randomOf(Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS), 15, 1,
                    Rarity.COMMON, of(Material.WHEAT, Material.BEETROOTS, Material.MELON, Material.PUMPKIN)),
            find(Material.GOLDEN_CARROT, 1, 2, 8, 10, Rarity.RARE, of(Material.CARROTS)),
            find(Material.GLISTERING_MELON_SLICE, 1, 2, 8, 10, Rarity.RARE, of(Material.MELON)),
            find(Material.TORCHFLOWER_SEEDS, 1, 1, 3, 20, Rarity.RARE),
            find(Material.PITCHER_POD, 1, 1, 3, 20, Rarity.RARE));

    // --------------------------------------------------------------- Foraging

    private static final Predicate<Material> LEAVES = named("_LEAVES");
    private static final Predicate<Material> GRASS = named("GRASS", "FERN").and(named("SEAGRASS").negate());

    static final List<Find> FORAGING = List.of(
            // Leaves: what falls out of a tree.
            find(Material.STICK, 1, 2, 30, 1, Rarity.COMMON, LEAVES.or(of(Material.DEAD_BUSH))),
            find(source -> new ItemStack(saplingOf(source)), 20, 1, Rarity.COMMON, LEAVES),
            find(Material.APPLE, 1, 1, 15, 1, Rarity.COMMON, named("OAK_LEAVES")),
            find(Material.SPORE_BLOSSOM, 1, 1, 2, 30, Rarity.RARE, named("AZALEA")),
            // Grass and ferns: seeds.
            find(source -> randomOf(Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS), 30, 1,
                    Rarity.COMMON, GRASS),
            find(Material.TORCHFLOWER_SEEDS, 1, 1, 3, 20, Rarity.RARE, GRASS),
            find(Material.PITCHER_POD, 1, 1, 3, 25, Rarity.RARE, GRASS),
            // Everything else (flowers, mushrooms, vines, berries...): more of what was picked.
            find(source -> stack(pickedFrom(source), 1, 2), 30, 1, Rarity.COMMON, LEAVES.or(GRASS).or(of(Material.DEAD_BUSH)).negate()));

    /** The item a foraged plant gives. */
    private static Material pickedFrom(Material plant) {
        return switch (plant) {
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            case CAVE_VINES, CAVE_VINES_PLANT -> Material.GLOW_BERRIES;
            case KELP_PLANT -> Material.KELP;
            case TALL_SEAGRASS -> Material.SEAGRASS;
            default -> plant.isItem() ? plant : Material.WHEAT_SEEDS;
        };
    }

    // ---------------------------------------------------------------- Fishing

    static final List<Find> FISHING = List.of(
            find(Material.KELP, 1, 3, 20, 1, Rarity.COMMON),
            find(Material.INK_SAC, 1, 2, 20, 1, Rarity.COMMON),
            find(Material.TROPICAL_FISH, 1, 2, 20, 1, Rarity.COMMON),
            find(Material.PRISMARINE_SHARD, 1, 3, 15, 1, Rarity.COMMON),
            find(Material.PRISMARINE_CRYSTALS, 1, 3, 12, 1, Rarity.COMMON),
            find(Material.PUFFERFISH, 1, 1, 8, 5, Rarity.UNCOMMON),
            find(Material.NAUTILUS_SHELL, 1, 1, 10, 5, Rarity.UNCOMMON),
            find(Material.TURTLE_SCUTE, 1, 1, 4, 15, Rarity.RARE),
            find(source -> randomEnchantedBook(), 3, 20, Rarity.RARE, ANYWHERE), // like vanilla fishing treasure
            find(Material.WET_SPONGE, 1, 1, 2, 30, Rarity.RARE),
            find(Material.HEART_OF_THE_SEA, 1, 1, 1, 40, Rarity.VERY_RARE),
            find(Material.TRIDENT, 1, 1, 1, 45, Rarity.VERY_RARE));

    /** Picks a random find that belongs to the {@code source} block and the player's level allows, or null if none. */
    static Find roll(List<Find> table, int level, Material source) {
        int total = 0;
        for (Find find : table) {
            if (find.minLevel() <= level && find.from().test(source)) {
                total += find.weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        int pick = ThreadLocalRandom.current().nextInt(total);
        for (Find find : table) {
            if (find.minLevel() <= level && find.from().test(source)) {
                pick -= find.weight();
                if (pick < 0) {
                    return find;
                }
            }
        }
        return null;
    }
}
