package com.raresb.professions;

import java.util.Locale;
import org.bukkit.Material;

/**
 * The profession perks: at levels 10, 20, 30, 40 and 50 every profession offers two, and the player
 * picks one (and can switch later). Effects live in PerkListener; numbers are kept small on purpose.
 */
public enum Perk {
    // ------------------------------------------------------------ Woodcutting
    ARBORIST(Profession.WOODCUTTING, 10, "Arborist", Material.OAK_SAPLING,
            "20% șansă ca un buștean să dea și un puiet al copacului."),
    STURDY_AXE(Profession.WOODCUTTING, 10, "Sturdy Axe", Material.IRON_AXE,
            "20% șansă ca tăiatul unui buștean să nu consume din toporul tău."),
    WOODSMANS_PACE(Profession.WOODCUTTING, 20, "Woodsman's Pace", Material.LEATHER_BOOTS,
            "Tăiatul unui buștean îți dă Viteză I pentru 4 secunde."),
    KINDLING(Profession.WOODCUTTING, 20, "Kindling", Material.CHARCOAL,
            "6% șansă per buștean să primești un cărbune de lemn."),
    STEADY_SWING(Profession.WOODCUTTING, 30, "Steady Swing", Material.GOLDEN_AXE,
            "Tăiatul unui buștean îți dă Grabă I pentru 4 secunde."),
    FOREST_BOUNTY(Profession.WOODCUTTING, 30, "Forest Bounty", Material.APPLE,
            "Descoperirile din copaci apar cu 30% mai des."),
    SAWMILL(Profession.WOODCUTTING, 40, "Sawmill", Material.OAK_PLANKS,
            "5% șansă per buștean să primești 4 scânduri."),
    WOODLAND_WISDOM(Profession.WOODCUTTING, 40, "Woodland Wisdom", Material.EXPERIENCE_BOTTLE,
            "4% șansă per buștean de un glob de experiență."),
    HEARTWOOD(Profession.WOODCUTTING, 50, "Heartwood", Material.DARK_OAK_LOG,
            "Șansa de al treilea buștean crește cu 10%."),
    GOLDEN_GROVE(Profession.WOODCUTTING, 50, "Golden Grove", Material.GOLDEN_APPLE,
            "0.5% șansă per buștean de un măr de aur."),

    // ----------------------------------------------------------------- Mining
    PROSPECTOR(Profession.MINING, 10, "Prospector", Material.RAW_IRON,
            "Șansa de minereu dublu crește cu 5%."),
    STURDY_PICK(Profession.MINING, 10, "Sturdy Pick", Material.IRON_PICKAXE,
            "20% șansă ca minatul să nu consume din târnăcopul tău."),
    DEEP_SIGHT(Profession.MINING, 20, "Deep Sight", Material.ENDER_EYE,
            "Când minezi sub Y=0 primești Vedere nocturnă (15 secunde după fiecare bloc)."),
    SIFTER(Profession.MINING, 20, "Sifter", Material.IRON_NUGGET,
            "3% șansă ca piatra minată să dea o pepită de fier sau de aur."),
    RUSH(Profession.MINING, 30, "Rush", Material.SUGAR,
            "Impulsurile de Grabă de la minereuri vin de două ori mai des."),
    ORE_SENSE(Profession.MINING, 30, "Ore Sense", Material.SPYGLASS,
            "Când minezi un minereu, minereurile de același fel din 4 blocuri strălucesc 5 secunde (doar pentru tine)."),
    SMELTERS_TOUCH(Profession.MINING, 40, "Smelter's Touch", Material.IRON_INGOT,
            "8% șansă ca fierul, aurul sau cuprul brut să cadă direct ca lingou."),
    GEM_CUTTER(Profession.MINING, 40, "Gem Cutter", Material.DIAMOND,
            "Diamantele și smaraldele au cu 10% mai multă șansă de dublu."),
    MASTER_MINER(Profession.MINING, 50, "Master Miner", Material.DIAMOND_PICKAXE,
            "Descoperirile din minat apar cu 30% mai des."),
    DWARVEN_ENDURANCE(Profession.MINING, 50, "Dwarven Endurance", Material.COOKED_BEEF,
            "Minatul nu te mai face să flămânzești."),

    // ---------------------------------------------------------------- Digging
    QUICK_SHOVEL(Profession.DIGGING, 10, "Quick Shovel", Material.IRON_SHOVEL,
            "Săpatul îți dă Grabă I pentru 3 secunde."),
    STURDY_SPADE(Profession.DIGGING, 10, "Sturdy Spade", Material.STONE_SHOVEL,
            "20% șansă ca săpatul să nu consume din lopata ta."),
    ARCHAEOLOGIST(Profession.DIGGING, 20, "Archaeologist", Material.BRUSH,
            "Descoperirile din săpat (cioburi, oase, pepite...) apar cu 30% mai des."),
    CLAY_WORKER(Profession.DIGGING, 20, "Clay Worker", Material.CLAY_BALL,
            "Lutul dă mereu o bilă de lut în plus; noroiul are 10% șansă de o bilă de lut."),
    GLASSMAKER(Profession.DIGGING, 30, "Glassmaker", Material.GLASS,
            "5% șansă ca nisipul să dea direct un bloc de sticlă."),
    FLINT_SIFTER(Profession.DIGGING, 30, "Flint Sifter", Material.FLINT,
            "Pietrișul are încă 10% șansă să dea cremene."),
    EXCAVATORS_PACE(Profession.DIGGING, 40, "Excavator's Pace", Material.LEATHER_BOOTS,
            "Săpatul îți dă Viteză I pentru 4 secunde."),
    BONE_COLLECTOR(Profession.DIGGING, 40, "Bone Collector", Material.BONE,
            "3% șansă de un os sau de făină de oase din pământ."),
    DEEP_DIGGER(Profession.DIGGING, 50, "Deep Digger", Material.DIAMOND_SHOVEL,
            "Șansa de recoltă dublă crește cu 5%."),
    EARTHS_EMBRACE(Profession.DIGGING, 50, "Earth's Embrace", Material.BREAD,
            "Săpatul nu te mai face să flămânzești."),

    // ---------------------------------------------------------------- Farming
    SEED_SAVER(Profession.FARMING, 10, "Seed Saver", Material.WHEAT_SEEDS,
            "Grâul și sfecla dau mereu o sămânță în plus."),
    COMPOST(Profession.FARMING, 10, "Compost", Material.BONE_MEAL,
            "5% șansă de făină de oase la fiecare recoltă."),
    FERTILE_TOUCH(Profession.FARMING, 20, "Fertile Touch", Material.GRASS_BLOCK,
            "La fiecare 10 secunde, o cultură din jurul tău (5 blocuri) crește un stadiu."),
    HARVESTERS_STRIDE(Profession.FARMING, 20, "Harvester's Stride", Material.LEATHER_BOOTS,
            "Recoltatul îți dă Viteză I pentru 4 secunde."),
    BUMPER_CROP(Profession.FARMING, 30, "Bumper Crop", Material.HAY_BLOCK,
            "Șansa de recoltă dublă crește cu 5%."),
    GOLDEN_TOUCH(Profession.FARMING, 30, "Golden Touch", Material.GOLDEN_CARROT,
            "Descoperirile din recolte apar cu 30% mai des."),
    GENTLE_STEP(Profession.FARMING, 40, "Gentle Step", Material.FARMLAND,
            "Nu mai strici pământul arat când sari pe el."),
    BAKER(Profession.FARMING, 40, "Baker", Material.BREAD,
            "4% șansă ca grâul să dea o pâine și cartofii un cartof copt."),
    ABUNDANCE(Profession.FARMING, 50, "Abundance", Material.PUMPKIN,
            "O recoltă dublă are 25% șansă să fie triplă."),
    HEARTY_MEALS(Profession.FARMING, 50, "Hearty Meals", Material.PUMPKIN_PIE,
            "Pâinea, cartofii copți, plăcintele, supele și prăjiturile te satură cu 2 puncte în plus."),

    // --------------------------------------------------------------- Foraging
    BERRY_PICKER(Profession.FORAGING, 10, "Berry Picker", Material.SWEET_BERRIES,
            "Culesul fructelor de pădure (și al celor strălucitoare) dă un fruct în plus."),
    STURDY_SHEARS(Profession.FORAGING, 10, "Sturdy Shears", Material.SHEARS,
            "20% șansă ca culesul cu foarfeca să nu o consume."),
    HERBALIST(Profession.FORAGING, 20, "Herbalist", Material.POPPY,
            "10% șansă ca o floare culeasă să dea o floare în plus."),
    LEAF_SIFTER(Profession.FORAGING, 20, "Leaf Sifter", Material.OAK_LEAVES,
            "Frunzele au încă 4% șansă să dea un puiet."),
    NATURES_STEP(Profession.FORAGING, 30, "Nature's Step", Material.LEATHER_BOOTS,
            "Culesul îți dă Viteză I pentru 4 secunde."),
    FORAGERS_SNACK(Profession.FORAGING, 30, "Forager's Snack", Material.SWEET_BERRIES,
            "4% șansă de un măr sau de fructe de pădure când culegi."),
    KEEN_EYE(Profession.FORAGING, 40, "Keen Eye", Material.SPYGLASS,
            "Descoperirile din cules apar cu 30% mai des."),
    WILD_HONEY(Profession.FORAGING, 40, "Wild Honey", Material.HONEYCOMB,
            "1.5% șansă de un fagure din flori și frunze."),
    RARE_SEEDS(Profession.FORAGING, 50, "Rare Seeds", Material.TORCHFLOWER_SEEDS,
            "0.5% șansă de o sămânță rară (torchflower sau pitcher pod) când culegi."),
    VERDANT(Profession.FORAGING, 50, "Verdant", Material.MOSS_BLOCK,
            "5% șansă de Regenerare I (3 secunde) când culegi."),

    // ---------------------------------------------------------------- Fishing
    STURDY_ROD(Profession.FISHING, 10, "Sturdy Rod", Material.FISHING_ROD,
            "25% șansă ca undița să nu se consume la o captură."),
    QUICK_BITE(Profession.FISHING, 10, "Quick Bite", Material.COD,
            "Peștii mușcă cu încă 5% mai repede."),
    TREASURE_SENSE(Profession.FISHING, 20, "Treasure Sense", Material.NAUTILUS_SHELL,
            "Comorile din apă apar cu 30% mai des."),
    FRESH_CATCH(Profession.FISHING, 20, "Fresh Catch", Material.COOKED_SALMON,
            "20% șansă ca peștele prins să fie deja gătit."),
    DOUBLE_HOOK(Profession.FISHING, 30, "Double Hook", Material.TROPICAL_FISH,
            "Șansa de pește în plus crește cu 5%."),
    RAIN_DANCER(Profession.FISHING, 30, "Rain Dancer", Material.WATER_BUCKET,
            "Pe ploaie peștii mușcă cu 15% mai repede."),
    SEA_LEGS(Profession.FISHING, 40, "Sea Legs", Material.HEART_OF_THE_SEA,
            "După fiecare captură primești Grația delfinului 5 secunde."),
    ANGLERS_WISDOM(Profession.FISHING, 40, "Angler's Wisdom", Material.EXPERIENCE_BOTTLE,
            "Capturile dau cu 50% mai multă experiență."),
    BOOK_ANGLER(Profession.FISHING, 50, "Book Angler", Material.ENCHANTED_BOOK,
            "1% șansă de o carte cu Luck of the Sea sau Lure la o captură."),
    LUCKY_CATCH(Profession.FISHING, 50, "Lucky Catch", Material.RABBIT_FOOT,
            "Fiecare captură îți dă Noroc (comori mai bune) pentru 30 de secunde."),

    // ---------------------------------------------------------------- Hunting
    BUTCHER(Profession.HUNTING, 10, "Butcher", Material.BEEF,
            "20% șansă ca animalele să dea o bucată de carne în plus."),
    LEATHERWORKER(Profession.HUNTING, 10, "Leatherworker", Material.LEATHER,
            "25% șansă de piele în plus de la vaci, cai, lame și iepuri."),
    PREDATORS_PACE(Profession.HUNTING, 20, "Predator's Pace", Material.LEATHER_BOOTS,
            "Uciderea unei creaturi ostile îți dă Viteză I pentru 4 secunde."),
    SOUL_COLLECTOR(Profession.HUNTING, 20, "Soul Collector", Material.EXPERIENCE_BOTTLE,
            "Creaturile dau cu 10% mai multă experiență."),
    TROPHY_HUNTER(Profession.HUNTING, 30, "Trophy Hunter", Material.SKELETON_SKULL,
            "Capetele de creaturi cad de două ori mai des."),
    BONE_PICKER(Profession.HUNTING, 30, "Bone Picker", Material.BONE,
            "30% șansă ca scheleții să dea un os în plus."),
    BLOODTHIRST(Profession.HUNTING, 40, "Bloodthirst", Material.REDSTONE,
            "Uciderea unei creaturi ostile te vindecă o inimă (cel mult o dată la 5 secunde)."),
    FIELD_COOK(Profession.HUNTING, 40, "Field Cook", Material.COOKED_BEEF,
            "25% șansă ca toată carnea căzută să fie deja gătită."),
    APEX(Profession.HUNTING, 50, "Apex", Material.DIAMOND_SWORD,
            "Șansa de pradă dublă crește cu 5%."),
    SPOILS(Profession.HUNTING, 50, "Spoils", Material.EMERALD,
            "2% șansă ca o creatură ostilă să dea un smarald."),

    // -------------------------------------------------------------- Husbandry
    GENTLE_SHEARS(Profession.HUSBANDRY, 10, "Gentle Shears", Material.SHEARS,
            "25% șansă ca tunsul să nu consume foarfeca."),
    FEEDER(Profession.HUSBANDRY, 10, "Feeder", Material.WHEAT,
            "20% șansă să primești înapoi mâncarea folosită la înmulțire."),
    BIG_LITTER(Profession.HUSBANDRY, 20, "Big Litter", Material.EGG,
            "Șansa de pui gemeni crește cu 3%."),
    FLEECE(Profession.HUSBANDRY, 20, "Fleece", Material.WHITE_WOOL,
            "Șansa de lână dublă la tuns crește cu 10%."),
    QUICK_GROWTH(Profession.HUSBANDRY, 30, "Quick Growth", Material.GOLDEN_CARROT,
            "Puii înmulțiți de tine cresc cu 25% mai repede."),
    RANCHERS_PACE(Profession.HUSBANDRY, 30, "Rancher's Pace", Material.LEATHER_BOOTS,
            "Înmulțitul și tunsul îți dau Viteză I pentru 4 secunde."),
    ANIMAL_WHISPERER(Profession.HUSBANDRY, 40, "Animal Whisperer", Material.EXPERIENCE_BOTTLE,
            "Înmulțitul dă experiență dublă."),
    HEARTY_STOCK(Profession.HUSBANDRY, 40, "Hearty Stock", Material.GOLDEN_APPLE,
            "Puii înmulțiți de tine au cu 20% mai multă viață."),
    MASTER_BREEDER(Profession.HUSBANDRY, 50, "Master Breeder", Material.HAY_BLOCK,
            "Pauza de înmulțire a părinților scade cu încă 15%."),
    PRIZE_STOCK(Profession.HUSBANDRY, 50, "Prize Stock", Material.FEATHER,
            "5% șansă ca înmulțitul să dea și piele, lână, pene sau un ou."),

    // --------------------------------------------------------------- Smelting
    BELLOWS(Profession.SMELTING, 10, "Bellows", Material.FURNACE,
            "Cuptoarele pe care le folosești topesc cu 10% mai repede."),
    THRIFTY_FUEL(Profession.SMELTING, 10, "Thrifty Fuel", Material.COAL,
            "Combustibilul din cuptoarele tale arde cu 10% mai mult."),
    BULK_SMELTER(Profession.SMELTING, 20, "Bulk Smelter", Material.IRON_INGOT,
            "Șansa de produs în plus crește cu 2% per obiect."),
    EMBER_HANDS(Profession.SMELTING, 20, "Ember Hands", Material.EXPERIENCE_BOTTLE,
            "Scoaterea din cuptor dă cu 25% mai multă experiență."),
    KILN_MASTER(Profession.SMELTING, 30, "Kiln Master", Material.BLAST_FURNACE,
            "Furnalele și afumătoarele tale lucrează cu 15% mai repede."),
    CHARCOAL_KILN(Profession.SMELTING, 30, "Charcoal Kiln", Material.CHARCOAL,
            "10% șansă per bucată de cărbune de lemn în plus."),
    GLASSBLOWER(Profession.SMELTING, 40, "Glassblower", Material.GLASS,
            "10% șansă per bloc de sticlă în plus."),
    CHEF(Profession.SMELTING, 40, "Chef", Material.COOKED_PORKCHOP,
            "10% șansă per porție de mâncare gătită în plus."),
    MASTER_SMELTER(Profession.SMELTING, 50, "Master Smelter", Material.GOLD_INGOT,
            "Toate cuptoarele tale lucrează cu încă 15% mai repede."),
    ETERNAL_FLAME(Profession.SMELTING, 50, "Eternal Flame", Material.BLAZE_POWDER,
            "5% șansă ca o bucată de combustibil să nu se consume."),

    // ------------------------------------------------------------- Enchanting
    LAPIS_SAVER(Profession.ENCHANTING, 10, "Lapis Saver", Material.LAPIS_LAZULI,
            "15% șansă să primești înapoi un lapis când vrăjești."),
    STUDIOUS(Profession.ENCHANTING, 10, "Studious", Material.BOOK,
            "Câștigi cu 20% mai mult XP de Enchanting."),
    ARCANE_REFUND(Profession.ENCHANTING, 20, "Arcane Refund", Material.EXPERIENCE_BOTTLE,
            "Șansa de a primi nivelurile înapoi crește cu 5%."),
    ANVIL_ADEPT(Profession.ENCHANTING, 20, "Anvil Adept", Material.ANVIL,
            "Lucrul la nicovală costă cu un nivel mai puțin."),
    BONUS_RUNE(Profession.ENCHANTING, 30, "Bonus Rune", Material.ENCHANTED_BOOK,
            "5% șansă ca o vrajă primită la masă să aibă un nivel în plus (până la maxim)."),
    ARCANE_FOCUS(Profession.ENCHANTING, 30, "Arcane Focus", Material.AMETHYST_SHARD,
            "20% șansă să primești înapoi un nivel când vrăjești."),
    TREASURE_SCHOLAR(Profession.ENCHANTING, 40, "Treasure Scholar", Material.CHISELED_BOOKSHELF,
            "2% șansă ca obiectul vrăjit să primească o vrajă în plus (nivel I)."),
    EXPERIENCE_WELL(Profession.ENCHANTING, 40, "Experience Well", Material.SCULK_CATALYST,
            "Primești cu 10% mai multă experiență din orice sursă."),
    ARCHMAGE(Profession.ENCHANTING, 50, "Archmage", Material.ENCHANTING_TABLE,
            "Șansa de a primi nivelurile înapoi crește cu încă 5%."),
    ANVIL_SAGE(Profession.ENCHANTING, 50, "Anvil Sage", Material.CHIPPED_ANVIL,
            "Lucrul la nicovală costă cu încă un nivel mai puțin."),

    // ---------------------------------------------------------------- Alchemy
    INGREDIENT_SAVER(Profession.ALCHEMY, 10, "Ingredient Saver", Material.NETHER_WART,
            "Șansa de a păstra ingredientul crește cu 5%."),
    QUICK_BREW(Profession.ALCHEMY, 10, "Quick Brew", Material.BREWING_STAND,
            "Standurile tale prepară cu 15% mai repede."),
    LONG_LASTING(Profession.ALCHEMY, 20, "Long Lasting", Material.CLOCK,
            "Poțiunile pe care le bei durează cu 15% mai mult."),
    FUEL_EFFICIENT(Profession.ALCHEMY, 20, "Fuel Efficient", Material.BLAZE_POWDER,
            "Praful de blaze ține 25 de preparări în loc de 20."),
    POTENT(Profession.ALCHEMY, 30, "Potent", Material.GLASS_BOTTLE,
            "4% șansă de o poțiune în plus la fiecare preparare."),
    IRON_STOMACH(Profession.ALCHEMY, 30, "Iron Stomach", Material.ROTTEN_FLESH,
            "Efectele negative (otravă, foame, slăbiciune...) durează cu 25% mai puțin pe tine."),
    ALCHEMISTS_THIRST(Profession.ALCHEMY, 40, "Alchemist's Thirst", Material.GHAST_TEAR,
            "Când bei o poțiune primești și Regenerare I pentru 3 secunde."),
    WART_GROWER(Profession.ALCHEMY, 40, "Wart Grower", Material.SOUL_SAND,
            "Negii de Nether pe care îi recoltezi dau unul în plus."),
    MASTER_ALCHEMIST(Profession.ALCHEMY, 50, "Master Alchemist", Material.DRAGON_BREATH,
            "Poțiunile pe care le bei durează cu încă 15% mai mult."),
    PHILOSOPHER(Profession.ALCHEMY, 50, "Philosopher", Material.GLOWSTONE_DUST,
            "Șansa de a păstra ingredientul crește cu încă 5%."),

    // ---------------------------------------------------------------- Smithing
    TEMPERING(Profession.SMITHING, 10, "Tempering", Material.IRON_INGOT,
            "Uneltele, armele și armurile pe care le fabrici au +15% durabilitate maximă."),
    TEMPLATE_SAVER(Profession.SMITHING, 10, "Template Saver", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
            "20% șansă să-ți primești înapoi șablonul de la masa de forjă."),
    MENDERS_TOUCH(Profession.SMITHING, 20, "Mender's Touch", Material.ANVIL,
            "Reparațiile la nicovală refac cu încă 25% mai multă durabilitate."),
    FORGE_ZEAL(Profession.SMITHING, 20, "Forge Zeal", Material.BLAST_FURNACE,
            "+20% XP de Smithing."),
    ANVIL_MASTER(Profession.SMITHING, 30, "Anvil Master", Material.CHIPPED_ANVIL,
            "Lucrul la nicovală costă cu un nivel mai puțin."),
    MASTER_SMITH(Profession.SMITHING, 30, "Master Smith", Material.DAMAGED_ANVIL,
            "Lucrul la nicovală nu costă niciodată peste 39 de niveluri: fără „Prea scump”."),
    REINFORCED(Profession.SMITHING, 40, "Reinforced", Material.IRON_CHESTPLATE,
            "Armura pe care o porți se uzează cu 20% mai încet."),
    FIELD_REPAIR(Profession.SMITHING, 40, "Field Repair", Material.GRINDSTONE,
            "Unealta sau arma din mână își reface 1% din durabilitate la fiecare 30 de secunde."),
    NETHERITE_SAVANT(Profession.SMITHING, 50, "Netherite Savant", Material.NETHERITE_INGOT,
            "25% șansă să-ți primești înapoi lingoul de netherite la o îmbunătățire."),
    MASTERWORK(Profession.SMITHING, 50, "Masterwork", Material.NETHER_STAR,
            "10% șansă ca echipamentul fabricat să fie o capodoperă: Unbreaking I și numele „Masterwork”.");

    /** Levels at which a perk is chosen. */
    public static final int[] TIERS = {10, 20, 30, 40, 50};

    private final Profession profession;
    private final int tier;
    private final String displayName;
    private final Material icon;
    private final String description;

    Perk(Profession profession, int tier, String displayName, Material icon, String description) {
        this.profession = profession;
        this.tier = tier;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public Profession profession() {
        return profession;
    }

    public int tier() {
        return tier;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    /** What the perk does (lang key perk.<id>). */
    public String description() {
        return ProfessionsPlugin.text("perk." + id(), description);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The two perks of a profession at a tier. */
    public static Perk[] of(Profession profession, int tier) {
        Perk[] pair = new Perk[2];
        int i = 0;
        for (Perk perk : values()) {
            if (perk.profession == profession && perk.tier == tier) {
                pair[i++] = perk;
            }
        }
        return pair;
    }
}
