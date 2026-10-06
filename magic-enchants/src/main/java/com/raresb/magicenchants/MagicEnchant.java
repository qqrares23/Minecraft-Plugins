package com.raresb.magicenchants;

import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

/** The magic enchantments: what they go on, how they appear in the enchanting table, what they do. */
public enum MagicEnchant {
    // weight: 10 = common ... 1 = very rare (vanilla scale). Costs follow vanilla's shape:
    // an enchanting-table level between min and max cost can roll this enchantment level.

    // --- Swords, axes and spears ---
    BLEEDING("Bleeding", 0xC0392B, Target.WEAPON, Category.MELEE, 3, 5, 5, 10, 35, 2,
            "Șansă ca ținta să sângereze, ignorând armura."),
    VENOM("Venom", 0x3CB043, Target.WEAPON, Category.MELEE, 2, 5, 5, 12, 35, 2,
            "Șansă de a otrăvi ținta."),
    FROSTBITE("Frostbite", 0x7FDBFF, Target.WEAPON, Category.MELEE, 2, 5, 5, 12, 35, 2,
            "Șansă de a încetini și îngheța ținta."),
    LIFESTEAL("Lifesteal", 0x9B1B30, Target.WEAPON, Category.MELEE, 3, 2, 15, 10, 45, 4,
            "Te vindecă cu o parte din daunele pe care le faci."),
    EXECUTIONER("Executioner", 0x8E44AD, Target.WEAPON, Category.MELEE, 3, 2, 15, 10, 45, 4,
            "Daune în plus împotriva țintelor sub 30% viață."),
    THUNDERSTRIKE("Thunderstrike", 0xF1C40F, Target.WEAPON, Category.MELEE, 2, 1, 20, 15, 55, 8,
            "Șansă de a lovi ținta cu un fulger."),
    CLEAVING("Cleaving", 0xD35400, Target.WEAPON, Category.MELEE, 3, 5, 5, 10, 35, 2,
            "Loviturile tale rănesc și inamicii de lângă țintă."),
    SOUL_HARVEST("Soul Harvest", 0x6C3483, Target.WEAPON, Category.MELEE, 3, 2, 15, 10, 45, 4,
            "Uciderile dau mai mult XP și uneori un Soul Fragment (dă XP abilităților)."),
    CHAIN_LIGHTNING("Chain Lightning", 0x5DADE2, Target.WEAPON, Category.MELEE, 2, 1, 20, 15, 55, 8,
            "Șansă ca fulgerul să sară între inamicii apropiați."),
    MOMENTUM("Momentum", 0xE67E22, Target.WEAPON, Category.MELEE, 3, 5, 5, 10, 35, 2,
            "Loviturile rapide, una după alta, fac tot mai multe daune."),
    SKILL_SURGE("Skill Surge", 0x48C9B0, Target.WEAPON, Category.MELEE, 3, 2, 15, 10, 45, 4,
            "Fiecare lovitură scurtează reîncărcarea abilităților de armă."),

    // --- Spears only (1.3.0; the sword/axe enchantments above also go on spears) ---
    PINNING("Pinning", 0xB8860B, Target.SPEAR, Category.SPEAR, 2, 5, 5, 12, 35, 2,
            "Loviturile încărcate pot țintui ținta în loc 1,5 secunde."),
    SKYFALL("Skyfall", 0x6A5ACD, Target.SPEAR, Category.SPEAR, 1, 2, 15, 0, 50, 4,
            "Lovituri din cădere: +35% daune și ținta e trântită la pământ."),
    VANGUARD("Vanguard", 0x839192, Target.SPEAR, Category.SPEAR, 2, 2, 15, 10, 45, 4,
            "Cu un scut în mâna stângă: lovituri mai puternice și mai puține daune primite."),

    // --- Armor ---
    THORNS_OF_FROST("Thorns of Frost", 0x85C1E9, Target.ARMOR, Category.ARMOR, 2, 5, 5, 10, 35, 2,
            "Șansă de a încetini și îngheța pe cine te lovește."),
    SECOND_WIND("Second Wind", 0x58D68D, Target.CHEST, Category.ARMOR, 1, 2, 15, 0, 50, 4,
            "Sub 20% viață: un val de Regenerare și Absorbție (reîncărcare 2 min)."),
    EVASION("Evasion", 0xBDC3C7, Target.LEGS, Category.ARMOR, 3, 2, 10, 10, 40, 4,
            "Șansă mică de a evita complet un atac."),
    SPRING_HEELS("Spring Heels", 0x82E0AA, Target.FEET, Category.ARMOR, 2, 5, 5, 10, 35, 2,
            "Sărituri mai înalte și mai puține daune din cădere."),
    NIGHT_VISION("Night Vision", 0xF7DC6F, Target.HEAD, Category.ARMOR, 1, 2, 15, 0, 50, 4,
            "Vezi în întuneric cât timp îl porți."),

    // --- Bows and crossbows ---
    HOMING("Homing", 0xF5B041, Target.RANGED, Category.RANGED, 2, 2, 15, 10, 45, 4,
            "Săgețile se curbează spre țintele apropiate."),
    BARRAGE("Barrage", 0xEB984E, Target.BOW, Category.RANGED, 2, 2, 15, 10, 45, 4,
            "Tragi săgeți în plus la fiecare tragere (doar arcuri)."),
    EXPLOSIVE_TIPS("Explosive Tips", 0xE74C3C, Target.RANGED, Category.RANGED, 1, 1, 25, 0, 60, 8,
            "Săgețile explodează la impact (nu sparg niciodată blocuri)."),
    HUNTERS_MARK("Hunter's Mark", 0xAF7AC5, Target.RANGED, Category.RANGED, 2, 5, 5, 10, 35, 2,
            "Țintele lovite strălucesc și primesc daune în plus o vreme."),
    RICOCHET("Ricochet", 0xA3E4D7, Target.CROSSBOW, Category.RANGED, 2, 2, 15, 10, 45, 4,
            "Săgețile sar la alt inamic apropiat după ce lovesc."),
    HARPOON("Harpoon", 0x909497, Target.CROSSBOW, Category.RANGED, 1, 2, 15, 0, 50, 4,
            "Săgețile trag spre tine ce lovesc."),
    SCATTER_SHOT("Scatter Shot", 0xF0B27A, Target.CROSSBOW, Category.RANGED, 2, 5, 5, 10, 35, 2,
            "Săgețile se sparg în schije la impact, rănind tot ce e în jur."),

    // --- Tools ---
    AUTO_SMELT("Auto-Smelt", 0xFF7F50, Target.PICKAXE, Category.TOOLS, 1, 2, 15, 0, 50, 4,
            "Minereurile cad gata topite: lingouri în loc de fier, aur și cupru brut, resturi de netherite din ancient debris."),
    MAGNET("Magnet", 0xC0392B, Target.MINING, Category.TOOLS, 1, 5, 5, 0, 40, 2,
            "Ce minezi ajunge direct în inventar."),
    EXCAVATOR("Excavator", 0x8D6E63, Target.DIGGER, Category.TOOLS, 1, 1, 20, 0, 55, 8,
            "Minezi 3x3 dintr-odată cu târnăcoape și lopeți (furișat ca să minezi un singur bloc)."),
    TREASURE_HUNTER("Treasure Hunter", 0xF4D03F, Target.FISHING, Category.TOOLS, 3, 2, 15, 10, 45, 4,
            "Șansă ca o captură să fie o comoară: cărți vrăjite, șei, arcuri și undițe vrăjite..."),
    SNAGGING("Snagging", 0x5499C7, Target.FISHING, Category.TOOLS, 2, 5, 5, 10, 35, 2,
            "Când tragi undița, creatura agățată e smulsă puternic spre tine."),
    REPLENISH("Replenish", 0x52BE80, Target.HOE, Category.TOOLS, 1, 2, 15, 0, 50, 4,
            "Culturile coapte recoltate cu sapa se replantează singure (folosesc o sămânță din recoltă)."),
    GREEN_THUMB("Green Thumb", 0x7DCEA0, Target.HOE, Category.TOOLS, 2, 2, 15, 10, 45, 4,
            "Cât ții sapa în mână, culturile din jurul tău cresc mai repede."),

    // --- Elytra (in the armor page) ---
    TAILWIND("Tailwind", 0xD6EAF8, Target.ELYTRA, Category.ARMOR, 2, 2, 15, 10, 45, 4,
            "Un impuls de viteză când începi să planezi (reîncărcare 5 s)."),
    CUSHION("Cushion", 0xF5CBA7, Target.ELYTRA, Category.ARMOR, 3, 5, 5, 10, 35, 2,
            "Mai puține daune când te izbești de un perete în zbor."),

    // --- Tridents ---
    MAELSTROM("Maelstrom", 0x2E86C1, Target.TRIDENT, Category.TRIDENT, 2, 2, 15, 10, 45, 4,
            "Tridentul aruncat lasă un vârtej care trage inamicii spre el."),
    STORMCALLER("Stormcaller", 0x5D6D7E, Target.TRIDENT, Category.TRIDENT, 1, 2, 15, 0, 50, 4,
            "Pe ploaie, tridentul aruncat cheamă un fulger asupra țintei (fără furtună)."),
    UNDERTOW("Undertow", 0x1ABC9C, Target.TRIDENT, Category.TRIDENT, 3, 5, 5, 10, 35, 2,
            "Daune în plus împotriva țintelor ude: în apă sau în ploaie."),

    // --- Maces ---
    AFTERSHOCK("Aftershock", 0xA04000, Target.MACE, Category.MACE, 2, 2, 15, 10, 45, 4,
            "Loviturile din cădere lasă o a doua undă de șoc o secundă mai târziu."),
    GRAVITY_WELL("Gravity Well", 0x6C3483, Target.MACE, Category.MACE, 2, 2, 15, 10, 45, 4,
            "Loviturile din cădere trag inamicii din jur spre locul impactului."),
    FEATHERFALL("Featherfall", 0xFDFEFE, Target.MACE, Category.MACE, 1, 5, 5, 0, 40, 2,
            "Cu buzduganul în mână: jumătate din daunele de cădere, deloc dacă ai lovit (sau ai ratat) chiar înainte de aterizare."),

    // --- Shields ---
    BASTION("Bastion", 0xD4AC0D, Target.SHIELD, Category.SHIELD, 2, 5, 5, 10, 35, 2,
            "Un atac blocat îți dă Absorbție câteva secunde (reîncărcare 10 s)."),
    SPIKED("Spiked", 0x909497, Target.SHIELD, Category.SHIELD, 3, 5, 5, 10, 35, 2,
            "Cine te lovește în scut se rănește în țepi."),
    REFLECTION("Reflection", 0xAED6F1, Target.SHIELD, Category.SHIELD, 2, 2, 15, 10, 45, 4,
            "Șansă ca o săgeată blocată să zboare înapoi spre cel care a tras-o."),

    // --- Magic: work together with the other plugins ---
    ARCANE_ECHO("Arcane Echo", 0xBB8FCE, Target.ANY_WEAPON, Category.MAGIC, 2, 1, 20, 15, 55, 8,
            "Șansă ca o abilitate de armă (WeaponSkills) să nu intre în reîncărcare."),
    COOLDOWN_SIPHON("Cooldown Siphon", 0x76D7C4, Target.ANY_WEAPON, Category.MAGIC, 2, 2, 15, 10, 45, 4,
            "Fiecare ucidere scurtează reîncărcarea abilităților de armă."),
    PATHBOUND("Pathbound", 0xE59866, Target.COMBAT, Category.MAGIC, 3, 5, 5, 10, 35, 2,
            "Mai mult XP pentru path-ul care folosește arma asta."),
    SCHOLAR("Scholar", 0x85929E, Target.GATHERING, Category.MAGIC, 3, 5, 5, 10, 35, 2,
            "Mai mult XP pentru profesia în care folosești unealta."),
    MIGHTSLAYER("Mightslayer", 0xCB4335, Target.ANY_WEAPON, Category.MAGIC, 3, 5, 5, 10, 35, 2,
            "Daune în plus împotriva creaturilor puternice și a aparițiilor speciale."),
    BLOOD_MOONS_GIFT("Blood Moon's Gift", 0x922B21, Target.ANY_WEAPON, Category.MAGIC, 2, 2, 15, 10, 45, 4,
            "În nopțile cu Lună Sângerie: daune în plus, iar uciderile te vindecă."),
    COMBO_KEEPER("Combo Keeper", 0xF39C12, Target.CHEST, Category.MAGIC, 2, 2, 15, 10, 45, 4,
            "Combo-ul ține mai mult între lovituri."),
    SOULBOUND("Soulbound", 0x8E44AD, Target.ANYTHING, Category.MAGIC, 1, 1, 25, 0, 60, 8, true,
            "Obiectul rămâne la tine când mori. Comoară: doar din pradă și de la bibliotecari."),
    PACKLEADER("Packleader", 0xA04000, Target.WOLF_ARMOR, Category.MOUNTS, 3, 5, 5, 10, 35, 2,
            "Lupul care poartă armura face mai multe daune și se vindecă atunci când ucide."),
    TIMBERSONG("Timbersong", 0x6E2C00, Target.AXE, Category.MAGIC, 2, 5, 5, 10, 35, 2,
            "Abilitățile de tăiat lemne se reîncarcă mai repede, iar buștenii dau mai mult XP de Woodcutting."),
    PROSPECTOR("Prospector", 0xD4AC0D, Target.PICKAXE, Category.MAGIC, 2, 2, 15, 10, 45, 4,
            "Mai multe descoperiri de Mining din minereuri (Professions)."),
    BOUNTY_HUNTER("Bounty Hunter", 0xB03A2E, Target.ANY_WEAPON, Category.MAGIC, 3, 5, 5, 10, 35, 2,
            "Creaturile puternice ucise cu arma au șanse mai mari de pradă rară."),

    // --- 1.6.0: armor ---
    WARDING("Warding", 0x5DADE2, Target.CHEST, Category.ARMOR, 4, 2, 15, 10, 45, 4,
            "Mai puține daune din abilități, path-uri și efecte de vrăji."),
    STEADFAST("Steadfast", 0x7E5109, Target.LEGS, Category.ARMOR, 3, 5, 5, 10, 35, 2,
            "Mai puțin recul și imobilizări mai scurte."),
    LAST_STAND("Last Stand", 0xF1C40F, Target.ARMOR, Category.ARMOR, 1, 1, 25, 0, 60, 8, true,
            "O dată la 3 minute, o lovitură mortală te lasă la o inimă. Comoară."),
    FLEETFOOT("Fleetfoot", 0x48C9B0, Target.FEET, Category.ARMOR, 2, 2, 15, 10, 45, 4,
            "Eschiva se reîncarcă mai repede."),
    SURE_FOOTED("Sure-footed", 0x935116, Target.FEET, Category.ARMOR, 1, 5, 5, 0, 40, 2,
            "Nisipul sufletelor și blocurile de miere nu te mai încetinesc."),
    CLARITY("Clarity", 0xD7BDE2, Target.HEAD, Category.ARMOR, 2, 5, 5, 10, 35, 2,
            "Orbirea, Întunericul și Greața țin mai puțin."),
    HEADHUNTERS_EYE("Headhunter's Eye", 0xE74C3C, Target.HEAD, Category.ARMOR, 1, 2, 15, 0, 50, 4,
            "Vezi în sidebar viața jucătorului sau a creaturii la care te uiți."),
    VIGOR("Vigor", 0xE6B0AA, Target.LEGS, Category.ARMOR, 2, 1, 20, 15, 55, 8,
            "+1 inimă maximă pe nivel."),

    // --- 1.6.0: crossbows, spears, maces ---
    PIERCING_BOLT("Piercing Bolt", 0xAAB7B8, Target.CROSSBOW, Category.RANGED, 3, 2, 15, 10, 45, 4,
            "Săgețile de arbaletă trec mai ușor prin armură."),
    OVERCHARGE("Overcharge", 0xF5B041, Target.CROSSBOW, Category.RANGED, 2, 2, 15, 10, 45, 4,
            "O săgeată ținută încărcată cel puțin 3 secunde face mai multe daune."),
    IMPALERS_REACH("Impaler's Reach", 0xB7950B, Target.SPEAR, Category.SPEAR, 2, 2, 15, 10, 45, 4,
            "Ajungi mai departe cu sulița."),
    RALLY("Rally", 0xF7DC6F, Target.SPEAR, Category.SPEAR, 2, 5, 5, 10, 35, 2,
            "Loviturile încărcate dau Viteză aliaților din jur."),
    SHOCKWAVE("Shockwave", 0x7D3C98, Target.MACE, Category.MACE, 1, 2, 15, 0, 50, 4,
            "Loviturile din cădere dezactivează scuturile inamicilor din jur 3 secunde."),

    // --- 1.7.0: staffs (Blaze Rod / Breeze Rod, read by Paths) ---
    SPELLWEAVER("Spellweaver", 0x9B59B6, Target.STAFF, Category.STAFF, 3, 5, 5, 10, 35, 2,
            "Bolturile toiagului pleacă mai des."),
    BATTLE_FOCUS("Battle Focus", 0x5499C7, Target.COMBAT, Category.STAFF, 3, 2, 15, 10, 45, 4,
            "Abilitatea M2 și vraja path-ului se reîncarcă mai repede."),
    WILDFIRE("Wildfire", 0xE74C3C, Target.BLAZE_STAFF, Category.STAFF, 3, 2, 15, 10, 45, 4,
            "O creatură care arde, ucisă de bolturile tale, izbucnește în flăcări care aprind inamicii din jur."),
    INFERNO_CORE("Inferno Core", 0xD35400, Target.BLAZE_STAFF, Category.STAFF, 3, 2, 15, 10, 45, 4,
            "Abilitatea M2 și vraja de Pyromancer fac mai multe daune."),
    DEEP_FREEZE("Deep Freeze", 0x5DADE2, Target.BREEZE_STAFF, Category.STAFF, 3, 2, 15, 10, 45, 4,
            "Înghețurile tale de Frost Warden țin mai mult."),
    BRITTLE("Brittle", 0xAED6F1, Target.BREEZE_STAFF, Category.STAFF, 3, 5, 5, 10, 35, 2,
            "Inamicii încetiniți sau înghețați primesc mai multe daune de la bolturile tale."),

    // --- 1.7.0: mounts and pets ---
    STEED("Steed", 0xA0522D, Target.HORSE_ARMOR, Category.MOUNTS, 3, 5, 5, 10, 35, 2,
            "Calul care poartă armura e mai rapid."),
    TRAMPLE("Trample", 0x6E2C00, Target.HORSE_ARMOR, Category.MOUNTS, 2, 1, 25, 0, 60, 8, true,
            "Calul în galop dă la o parte și rănește monștrii din cale. Comoară."),
    LOYAL_GUARD("Loyal Guard", 0x85929E, Target.WOLF_ARMOR, Category.MOUNTS, 2, 2, 15, 10, 45, 4,
            "Lupul tău din apropiere preia o parte din daunele pe care le primești."),

    // --- 1.7.0: tools (the profession ones are read by Professions) ---
    SHEARERS_BOUNTY("Shearer's Bounty", 0xF8F9F9, Target.SHEARS, Category.TOOLS, 3, 5, 5, 10, 35, 2,
            "Șansă de lână în plus când tunzi oi."),
    DELVER("Delver", 0xA1887F, Target.SHOVEL, Category.TOOLS, 2, 2, 15, 10, 45, 4,
            "Mai multe descoperiri de Digging (Professions)."),
    BOUNTIFUL("Bountiful", 0xF4D03F, Target.HOE, Category.TOOLS, 2, 2, 15, 10, 45, 4,
            "Mai multe descoperiri de Farming (Professions)."),
    LURE_OF_THE_DEEP("Lure of the Deep", 0x2874A6, Target.FISHING, Category.TOOLS, 3, 5, 5, 10, 35, 2,
            "Mai mult XP de Fishing (Professions)."),

    // --- 1.7.0: weapons ---
    BLOODHOUND("Bloodhound", 0x943126, Target.WEAPON, Category.MELEE, 3, 5, 5, 10, 35, 2,
            "Mai mult XP de Hunting (Professions)."),
    CRESCENDO("Crescendo", 0xF39C12, Target.WEAPON, Category.MELEE, 2, 2, 15, 10, 45, 4,
            "Lovitura finală a combo-ului (x10) lovește și inamicii din jur."),
    ANCHOR("Anchor", 0x4A235A, Target.ANY_WEAPON, Category.MELEE, 1, 2, 15, 0, 50, 4,
            "Creaturile lovite nu se mai pot teleporta 3 secunde (Blinker)."),
    PURIFIER("Purifier", 0xF7F9F9, Target.ANY_WEAPON, Category.MELEE, 1, 2, 15, 0, 50, 4,
            "Creaturile lovite nu se mai vindecă 4 secunde (Regenerating, Vampire)."),

    // --- 1.7.0: armor ---
    AFTERIMAGE("Afterimage", 0x76D7C4, Target.FEET, Category.ARMOR, 3, 2, 15, 10, 45, 4,
            "Prima lovitură în 2 secunde după o eschivă face mai multe daune."),
    ANTIDOTE("Antidote", 0x27AE60, Target.CHEST, Category.ARMOR, 2, 5, 5, 10, 35, 2,
            "Otrava și Wither-ul țin mai puțin."),
    CRIMSON_WARD("Crimson Ward", 0x922B21, Target.ARMOR, Category.ARMOR, 3, 1, 25, 0, 60, 8, true,
            "Mai puține daune în nopțile cu Lună Sângerie. Comoară."),

    // --- 1.7.0: magic ---
    RESONANCE("Resonance", 0xBB8FCE, Target.ANY_WEAPON, Category.MAGIC, 2, 1, 25, 0, 60, 8, true,
            "Augment-urile abilităților de armă sunt mai puternice. Comoară.");

    /** Which items an enchantment goes on. */
    public enum Target {
        WEAPON("Săbii, topoare și sulițe"),
        SPEAR("Sulițe"),
        ARMOR("Orice armură"),
        HEAD("Coifuri"),
        CHEST("Platoșe"),
        LEGS("Pantaloni"),
        FEET("Cizme"),
        BOW("Arcuri"),
        CROSSBOW("Arbalete"),
        RANGED("Arcuri și arbalete"),
        MINING("Unelte"),
        DIGGER("Târnăcoape și lopeți"),
        FISHING("Undițe"),
        HOE("Sape"),
        ELYTRA("Elytre"),
        TRIDENT("Tridente"),
        MACE("Buzdugane"),
        SHIELD("Scuturi"),
        ANY_WEAPON("Orice armă"),
        GATHERING("Unelte și undițe"),
        ANYTHING("Orice obiect"),
        WOLF_ARMOR("Armură de lup"),
        AXE("Topoare"),
        PICKAXE("Târnăcoape"),
        STAFF("Toiege (Blaze Rod, Breeze Rod)"),
        BLAZE_STAFF("Toiag de foc (Blaze Rod)"),
        BREEZE_STAFF("Toiag de gheață (Breeze Rod)"),
        COMBAT("Orice armă sau toiag"),
        HORSE_ARMOR("Armură de cal"),
        SHEARS("Foarfece"),
        SHOVEL("Lopeți");

        private final String label;

        Target(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** The page an enchantment is listed on in /enchants. */
    public enum Category {
        MELEE(Material.IRON_SWORD),
        SPEAR(Material.IRON_SPEAR),
        ARMOR(Material.IRON_CHESTPLATE),
        RANGED(Material.BOW),
        TRIDENT(Material.TRIDENT),
        MACE(Material.MACE),
        SHIELD(Material.SHIELD),
        TOOLS(Material.IRON_PICKAXE),
        MAGIC(Material.AMETHYST_SHARD),
        STAFF(Material.BLAZE_ROD),
        MOUNTS(Material.SADDLE);

        private final Material icon;

        Category(Material icon) {
            this.icon = icon;
        }

        public Material icon() {
            return icon;
        }
    }

    public static final String NAMESPACE = "magicenchants";

    /** The magic enchantment behind a registered enchantment, or null for vanilla (and other plugins') ones. */
    public static MagicEnchant of(Enchantment enchantment) {
        if (!enchantment.getKey().getNamespace().equals(NAMESPACE)) {
            return null;
        }
        try {
            return valueOf(enchantment.getKey().getKey().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private final String displayName;
    private final TextColor color;
    private final Target target;
    private final Category category;
    private final int maxLevel;
    private final int weight;
    private final int minCostBase;
    private final int costPerLevel;
    private final int maxCostBase;
    private final int anvilCost;
    private final boolean treasure;
    private final String summary;

    MagicEnchant(String displayName, int color, Target target, Category category, int maxLevel, int weight, int minCostBase,
                 int costPerLevel, int maxCostBase, int anvilCost, String summary) {
        this(displayName, color, target, category, maxLevel, weight, minCostBase, costPerLevel, maxCostBase, anvilCost, false, summary);
    }

    MagicEnchant(String displayName, int color, Target target, Category category, int maxLevel, int weight, int minCostBase,
                 int costPerLevel, int maxCostBase, int anvilCost, boolean treasure, String summary) {
        this.displayName = displayName;
        this.color = TextColor.color(color);
        this.target = target;
        this.category = category;
        this.maxLevel = maxLevel;
        this.weight = weight;
        this.minCostBase = minCostBase;
        this.costPerLevel = costPerLevel;
        this.maxCostBase = maxCostBase;
        this.anvilCost = anvilCost;
        this.treasure = treasure;
        this.summary = summary;
    }

    public TypedKey<Enchantment> key() {
        return EnchantmentKeys.create(Key.key(NAMESPACE, name().toLowerCase(Locale.ROOT)));
    }

    public String displayName() {
        return displayName;
    }

    public TextColor color() {
        return color;
    }

    public Target target() {
        return target;
    }

    public Category category() {
        return category;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public int weight() {
        return weight;
    }

    public int minCostBase() {
        return minCostBase;
    }

    public int maxCostBase() {
        return maxCostBase;
    }

    public int costPerLevel() {
        return costPerLevel;
    }

    public int anvilCost() {
        return anvilCost;
    }

    /** Treasure enchantments are found in loot and trades, never from the enchanting table. */
    public boolean treasure() {
        return treasure;
    }

    public String summary() {
        return summary;
    }
}
