package com.raresb.weaponskills;

import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/**
 * Every skill. Each weapon has a few trigger slots (F while standing, in mid-air, ...); a skill
 * starts in its default slot, and extra skills unlock with the weapon's mastery and can be
 * swapped into any slot from the /skills menu.
 */
public enum Skill {
    // ------------------------------------------------------------------ Sword
    WHIRLWIND("Whirlwind", "Whirl", "↻", Weapon.SWORD, Trigger.STANDING, Kind.NORMAL, 0, Material.WIND_CHARGE, 8, 1.0,
            "Te rotești pe loc și lovești tot ce e în jurul tău."),
    GROUND_SLAM("Ground Slam", "Slam", "⬇", Weapon.SWORD, Trigger.AIRBORNE, Kind.AERIAL, 0, Material.ANVIL, 12, 1.2,
            "Plonjezi și trimiți o undă de șoc. Cu cât cazi de mai sus, cu atât lovești mai tare."),
    DASH_STRIKE("Dash Strike", "Dash", "➠", Weapon.SWORD, Trigger.SPRINTING, Kind.NORMAL, 0, Material.FEATHER, 8, 1.0,
            "Țâșnești înainte și tai tot ce îți stă în cale."),
    PARRY("Parry", "Parry", "✋", Weapon.SWORD, Trigger.SNEAKING, Kind.NORMAL, 0, Material.CLOCK, 10, 1.5,
            "Poziție scurtă de gardă: blochezi următoarea lovitură și ripostezi."),
    RISING_SLASH("Rising Slash", "Rise", "⇡", Weapon.SWORD, Trigger.LOOKING_UP, Kind.NORMAL, 0, Material.FIREWORK_ROCKET, 9, 0.9,
            "O lovitură de jos în sus care aruncă în aer inamicii din fața ta."),
    BLADE_FLURRY("Blade Flurry", "Flurry", "≋", Weapon.SWORD, Trigger.LOOKING_DOWN, Kind.NORMAL, 0, Material.IRON_SWORD, 10, 0.45,
            "Șase tăieturi rapide în tot ce e în fața ta."),
    SHADOW_STEP("Shadow Step", "Step", "✧", Weapon.SWORD, null, Kind.NORMAL, 5, Material.ENDER_PEARL, 12, 1.2,
            "Te teleportezi în spatele inamicului la care te uiți și îl lovești, orbindu-l."),
    BLADE_DANCE("Blade Dance", "Dance", "✲", Weapon.SWORD, null, Kind.NORMAL, 10, Material.GOLDEN_SWORD, 25, 0,
            "Timp de 6 secunde: atacuri mult mai rapide și puțină viteză în plus."),
    MIRROR_IMAGE("Mirror Image", "Mirror", "☯", Weapon.SWORD, null, Kind.NORMAL, 15, Material.ARMOR_STAND, 30, 0,
            "Creezi două copii ale tale care atrag inamicii și dispari pentru o clipă."),
    CRESCENT_WAVE("Crescent Wave", "Wave", "☽", Weapon.SWORD, null, Kind.NORMAL, 20, Material.PRISMARINE_SHARD, 10, 1.1,
            "Trimiți înainte un val de lamă care străpunge tot ce întâlnește."),
    COUNTER_STANCE("Counter Stance", "Counter", "⊗", Weapon.SWORD, null, Kind.NORMAL, 30, Material.IRON_BARS, 16, 1.2,
            "2 secunde primești jumătate din daune, apoi le eliberezi pe toate într-o singură lovitură."),
    THOUSAND_CUTS("Thousand Cuts", "Cuts", "✂", Weapon.SWORD, null, Kind.NORMAL, 40, Material.SHEARS, 18, 0.3,
            "Douăsprezece tăieturi fulgerătoare în inamicul la care te uiți."),
    JUDGMENT("Judgment", "Judgment", "✠", Weapon.SWORD, Trigger.ULTIMATE, Kind.ULTIMATE, 25, Material.NETHERITE_SWORD, 90, 1.5,
            "ULTIMATĂ: o furtună de lame cade din cer în jurul tău."),

    // -------------------------------------------------------------------- Axe
    CLEAVE("Cleave", "Cleave", "⚔", Weapon.AXE, Trigger.STANDING, Kind.NORMAL, 0, Material.IRON_AXE, 8, 1.3,
            "O lovitură grea în fața ta care dezactivează și scuturile."),
    EXECUTIONERS_DROP("Executioner's Drop", "Drop", "☠", Weapon.AXE, Trigger.AIRBORNE, Kind.AERIAL, 0, Material.WITHER_SKELETON_SKULL, 14, 2.0,
            "Plonjezi peste inamici: daune mari și amețire."),
    BERSERKER_CHARGE("Berserker Charge", "Charge", "➤", Weapon.AXE, Trigger.SPRINTING, Kind.NORMAL, 0, Material.BLAZE_POWDER, 16, 0.8,
            "Șarjezi înainte și împingi inamicii în lături, apoi primești Forță și Viteză."),
    AXE_THROW("Axe Throw", "Throw", "↺", Weapon.AXE, Trigger.SNEAKING, Kind.NORMAL, 0, Material.TRIDENT, 8, 1.6,
            "Arunci un topor rotitor care lovește primul inamic și se întoarce."),
    WAR_CRY("War Cry", "Cry", "✪", Weapon.AXE, Trigger.LOOKING_UP, Kind.NORMAL, 0, Material.GOAT_HORN, 25, 0,
            "Un răcnet puternic: inamicii din apropiere sunt slăbiți și încetiniți, iar tu primești Rezistență."),
    EARTHSPLITTER("Earthsplitter", "Split", "⚒", Weapon.AXE, Trigger.LOOKING_DOWN, Kind.NORMAL, 0, Material.CRACKED_STONE_BRICKS, 12, 1.1,
            "Despici pământul în linie dreaptă, aruncând în aer și amețind inamicii."),
    BLOODLUST("Bloodlust", "Lust", "❣", Weapon.AXE, null, Kind.NORMAL, 5, Material.REDSTONE, 25, 0,
            "Timp de 8 secunde: fiecare ucidere te vindecă, iar loviturile tale devin mai puternice."),
    GROUND_POUND("Ground Pound", "Pound", "◉", Weapon.AXE, null, Kind.NORMAL, 10, Material.HEAVY_CORE, 14, 0.6,
            "Izbești pământul: rănești și încetinești puternic tot ce e în jurul tău."),
    CHAIN_HOOK("Chain Hook", "Hook", "⚓", Weapon.AXE, null, Kind.NORMAL, 15, Material.LEAD, 10, 0.6,
            "Agăți inamicul la care te uiți și îl tragi spre tine."),
    TOMAHAWK_RAIN("Tomahawk Rain", "Tomahawks", "⇊", Weapon.AXE, null, Kind.NORMAL, 20, Material.STONE_AXE, 18, 0.8,
            "O ploaie de topoare cade acolo unde te uiți."),
    LUMBERJACKS_FURY("Lumberjack's Fury", "Fury", "❂", Weapon.AXE, null, Kind.NORMAL, 30, Material.GOLDEN_AXE, 22, 0.5,
            "Următoarele 5 lovituri cu toporul (în 10s) lovesc și inamicii din jurul țintei."),
    BONE_BREAKER("Bone Breaker", "Break", "☓", Weapon.AXE, null, Kind.NORMAL, 40, Material.BONE, 14, 1.2,
            "O lovitură care rupe oasele: ținta abia se mai mișcă și nu mai poate sări 5 secunde."),
    RAGNAROK("Ragnarok", "Ragnarok", "♛", Weapon.AXE, Trigger.ULTIMATE, Kind.ULTIMATE, 25, Material.NETHER_STAR, 120, 0,
            "ULTIMATĂ: devii uriaș timp de 10 secunde, cu daune și rezistență enorme."),

    // ----------------------------------------------------------------- Shield
    SHIELD_BASH("Shield Bash", "Bash", "◘", Weapon.SHIELD, Trigger.BLOCKING, Kind.NORMAL, 0, Material.SHIELD, 6, 4.0,
            "Izbești cu scutul înainte: împingere, amețire și scuturile inamice dezactivate."),
    FORTIFY("Fortify", "Fortify", "▣", Weapon.SHIELD, Trigger.BLOCKING_SNEAKING, Kind.NORMAL, 0, Material.IRON_CHESTPLATE, 20, 0,
            "Te pregătești de impact: Rezistență II pentru câteva secunde."),
    REFLECT("Reflect", "Reflect", "⟲", Weapon.SHIELD, null, Kind.NORMAL, 2, Material.SPECTRAL_ARROW, 15, 0,
            "Timp de 4 secunde, săgețile și proiectilele care te lovesc se întorc la cel care a tras."),
    GUARDIAN_AURA("Guardian Aura", "Aura", "✙", Weapon.SHIELD, null, Kind.NORMAL, 4, Material.BEACON, 30, 0,
            "Timp de 8 secunde, tu și jucătorii din apropiere primiți cu 30% mai puține daune."),
    TAUNT("Taunt", "Taunt", "‼", Weapon.SHIELD, null, Kind.NORMAL, 6, Material.BELL, 15, 0,
            "Forțezi creaturile din apropiere să te atace și primești Rezistență."),
    SHIELD_THROW("Shield Throw", "Throw", "⟳", Weapon.SHIELD, null, Kind.NORMAL, 8, Material.IRON_TRAPDOOR, 12, 5.0,
            "Arunci scutul: sare de la un inamic la altul (3 ținte), apoi se întoarce."),
    PHALANX("Phalanx", "Phalanx", "▤", Weapon.SHIELD, null, Kind.NORMAL, 10, Material.IRON_DOOR, 30, 0,
            "Timp de 6 secunde, jucătorii din jurul tău primesc Rezistență II, iar tu Rezistență I."),

    // -------------------------------------------------------------------- Bow
    ARROW_RAIN("Arrow Rain", "Rain", "☔", Weapon.BOW, Trigger.STANDING, Kind.NORMAL, 0, Material.ARROW, 14, 3.0,
            "O ploaie de săgeți cade acolo unde țintești."),
    PIERCING_SHOT("Piercing Shot", "Pierce", "➶", Weapon.BOW, Trigger.SNEAKING, Kind.NORMAL, 0, Material.SPECTRAL_ARROW, 8, 9.0,
            "O săgeată puternică ce trece prin mai mulți inamici."),
    VOLLEY("Volley", "Volley", "⋙", Weapon.BOW, Trigger.SPRINTING, Kind.NORMAL, 0, Material.TIPPED_ARROW, 8, 5.0,
            "Tragi cinci săgeți în evantai."),
    WIND_ARROW("Wind Arrow", "Wind", "≋", Weapon.BOW, Trigger.LOOKING_UP, Kind.NORMAL, 0, Material.WIND_CHARGE, 10, 4.0,
            "O săgeată cu o rafală de vânt: explodează unde lovește și aruncă inamicii înapoi și în sus."),
    HOVER_SHOT("Hover Shot", "Hover", "☁", Weapon.BOW, Trigger.AIRBORNE, Kind.AERIAL, 0, Material.PHANTOM_MEMBRANE, 12, 5.0,
            "Rămâi în aer o clipă și tragi trei săgeți."),
    RAIN_OF_FIRE("Rain of Fire", "Fire", "♨", Weapon.BOW, null, Kind.NORMAL, 5, Material.MAGMA_CREAM, 18, 3.0,
            "O ploaie de săgeți în flăcări cade acolo unde țintești."),
    TRAP_ARROW("Trap Arrow", "Trap", "⊛", Weapon.BOW, null, Kind.NORMAL, 10, Material.STRING, 14, 3.0,
            "Săgeata lasă o capcană unde aterizează: primul inamic care calcă în ea e imobilizat 4 secunde."),
    SPLIT_SHOT("Split Shot", "Split", "⋔", Weapon.BOW, null, Kind.NORMAL, 15, Material.AMETHYST_SHARD, 10, 4.0,
            "O săgeată care se desface în zbor în cinci săgeți."),
    SNIPER_MODE("Sniper Mode", "Sniper", "⊕", Weapon.BOW, null, Kind.NORMAL, 20, Material.TARGET, 16, 14.0,
            "Țintești o clipă (zoom), apoi tragi o săgeată foarte grea și foarte rapidă."),
    STORM_OF_ARROWS("Storm of Arrows", "Storm", "⇶", Weapon.BOW, Trigger.ULTIMATE, Kind.ULTIMATE, 25, Material.DISPENSER, 90, 4.0,
            "ULTIMATĂ: 5 secunde, săgețile plouă peste toți inamicii din jurul tău."),

    // --------------------------------------------------------------- Crossbow
    EXPLOSIVE_BOLT("Explosive Bolt", "Blast", "✸", Weapon.CROSSBOW, Trigger.STANDING, Kind.NORMAL, 0, Material.TNT, 10, 8.0,
            "O săgeată care explodează la impact (nu sparge niciodată blocuri)."),
    NET_SHOT("Net Shot", "Net", "#", Weapon.CROSSBOW, Trigger.SNEAKING, Kind.NORMAL, 0, Material.COBWEB, 14, 2.0,
            "O săgeată care se desface într-o plasă și imobilizează tot ce e în apropiere."),
    HARPOON_BOLT("Harpoon Bolt", "Harpoon", "⚓", Weapon.CROSSBOW, Trigger.SPRINTING, Kind.NORMAL, 0, Material.TRIPWIRE_HOOK, 10, 5.0,
            "O săgeată care trage spre tine ce lovește."),
    BURST_FIRE("Burst Fire", "Burst", "⁂", Weapon.CROSSBOW, Trigger.LOOKING_DOWN, Kind.NORMAL, 0, Material.FIREWORK_STAR, 9, 5.0,
            "Tragi trei săgeți una după alta."),
    RECOIL_SHOT("Recoil Shot", "Recoil", "⤒", Weapon.CROSSBOW, Trigger.AIRBORNE, Kind.AERIAL, 0, Material.BREEZE_ROD, 10, 7.0,
            "Tragi drept înainte și ești aruncat înapoi de recul."),
    GATLING("Gatling", "Gatling", "⁘", Weapon.CROSSBOW, null, Kind.NORMAL, 5, Material.REPEATER, 18, 3.0,
            "Tragi zece săgeți una după alta, foarte repede."),
    SMOKE_BOLT("Smoke Bolt", "Smoke", "▒", Weapon.CROSSBOW, null, Kind.NORMAL, 10, Material.GUNPOWDER, 16, 0,
            "O săgeată care face un nor de fum: inamicii din el sunt orbiți, încetiniți și te pierd din ochi."),
    CHAIN_BOLT("Chain Bolt", "Chain", "⛓", Weapon.CROSSBOW, null, Kind.NORMAL, 15, Material.LEAD, 14, 5.0,
            "Ținta lovită e legată în lanțuri de doi inamici din apropiere: sunt trași unul spre altul 4 secunde."),
    ARTILLERY_BARRAGE("Artillery Barrage", "Barrage", "✷", Weapon.CROSSBOW, Trigger.ULTIMATE, Kind.ULTIMATE, 25, Material.END_CRYSTAL, 100, 6.0,
            "ULTIMATĂ: douăsprezece obuze explodează în zona la care te uiți (nu sparg blocuri)."),

    // ---------------------------------------------------------------- Trident
    RIPTIDE_SURGE("Riptide Surge", "Surge", "≈", Weapon.TRIDENT, Trigger.STANDING, Kind.NORMAL, 0, Material.HEART_OF_THE_SEA, 8, 1.0,
            "Te lansezi înainte ca la Riptide, chiar și fără ploaie."),
    TIDAL_WAVE("Tidal Wave", "Wave", "∿", Weapon.TRIDENT, Trigger.SNEAKING, Kind.NORMAL, 0, Material.WATER_BUCKET, 14, 0.8,
            "Trimiți înainte un val de apă care rănește și împinge inamicii."),
    LIGHTNING_CALL("Lightning Call", "Storm", "ϟ", Weapon.TRIDENT, Trigger.LOOKING_UP, Kind.NORMAL, 0, Material.LIGHTNING_ROD, 16, 1.4,
            "Chemi un fulger peste inamicul la care te uiți."),
    POSEIDONS_CALL("Poseidon's Call", "Poseidon", "♆", Weapon.TRIDENT, null, Kind.NORMAL, 5, Material.NAUTILUS_SHELL, 16, 0.8,
            "Chemi un val care te poartă înainte, izbind inamicii din cale."),
    HARPOON_THROW("Harpoon Throw", "Harpoon", "↩", Weapon.TRIDENT, null, Kind.NORMAL, 10, Material.PRISMARINE_CRYSTALS, 10, 1.3,
            "Arunci un harpon care lovește primul inamic și îl târăște înapoi la tine."),
    TSUNAMI("Tsunami", "Tsunami", "☵", Weapon.TRIDENT, Trigger.ULTIMATE, Kind.ULTIMATE, 25, Material.CONDUIT, 100, 1.0,
            "ULTIMATĂ: trei valuri uriașe mătură tot ce e în fața ta."),

    // ------------------------------------------------------------------- Mace
    GRAVITY_WELL("Gravity Well", "Well", "◎", Weapon.MACE, Trigger.STANDING, Kind.NORMAL, 0, Material.ENDER_EYE, 16, 0.5,
            "Tragi spre tine toți inamicii din apropiere."),
    TREMOR("Tremor", "Tremor", "⌇", Weapon.MACE, Trigger.SNEAKING, Kind.NORMAL, 0, Material.POINTED_DRIPSTONE, 12, 0.7,
            "Cutremuri pământul: rănești și amețești tot ce e în jurul tău."),
    METEOR("Meteor", "Meteor", "☄", Weapon.MACE, Trigger.AIRBORNE, Kind.AERIAL, 0, Material.FIRE_CHARGE, 18, 1.5,
            "Te prăbușești ca un meteorit. Mult mai puternic cu cât cazi de mai sus."),
    WIND_BURST("Wind Burst", "Burst", "⇑", Weapon.MACE, null, Kind.NORMAL, 5, Material.ELYTRA, 10, 0.5,
            "O rafală te aruncă drept în sus și îi împinge pe cei din jur. Perfect pentru o lovitură de buzdugan din cădere."),
    ANVIL_DROP("Anvil Drop", "Anvil", "▼", Weapon.MACE, null, Kind.NORMAL, 10, Material.CHIPPED_ANVIL, 14, 1.6,
            "O nicovală cade din cer peste inamicul la care te uiți și îl amețește."),
    CATACLYSM("Cataclysm", "Cataclysm", "✺", Weapon.MACE, Trigger.ULTIMATE, Kind.ULTIMATE, 25, Material.MAGMA_BLOCK, 120, 1.4,
            "ULTIMATĂ: sari sus și te prăbușești cu trei unde de șoc tot mai largi."),

    // ------------------------------------------------------------------ Spear
    IMPALE("Impale", "Impale", "↣", Weapon.SPEAR, Trigger.STANDING, Kind.NORMAL, 0, Material.IRON_SPEAR, 8, 1.2,
            "Un fandat lung (6 blocuri) care străpunge tot ce e în linie în fața ta. Prima țintă e țintuită o secundă."),
    DRAGOON_DIVE("Dragoon Dive", "Dragoon", "⤓", Weapon.SPEAR, Trigger.AIRBORNE, Kind.AERIAL, 0, Material.PHANTOM_MEMBRANE, 12, 1.4,
            "Plonjezi cu sulița înainte spre locul din fața ta. Cu cât cazi de mai sus, cu atât lovești mai tare."),
    LANCE_CHARGE("Lance Charge", "Lance", "➾", Weapon.SPEAR, Trigger.SPRINTING, Kind.NORMAL, 0, Material.SADDLE, 10, 1.4,
            "Șarjezi cu sulița în față: primul inamic e izbit, aruncat înainte și primește daune mari."),
    JAVELIN("Javelin", "Javelin", "➹", Weapon.SPEAR, Trigger.SNEAKING, Kind.NORMAL, 0, Material.STONE_SPEAR, 9, 1.5,
            "Arunci o suliță care zboară departe și străpunge până la trei inamici."),
    POLE_VAULT("Pole Vault", "Vault", "⤴", Weapon.SPEAR, Trigger.LOOKING_UP, Kind.NORMAL, 0, Material.BAMBOO, 10, 0,
            "Te avânți pe suliță: sari înainte și în sus, apoi cobori lin."),
    SWEEPING_ARC("Sweeping Arc", "Arc", "◌", Weapon.SPEAR, Trigger.LOOKING_DOWN, Kind.NORMAL, 0, Material.WOODEN_SPEAR, 9, 0.9,
            "Rotești sulița în cerc: rănești și împingi tot ce e la 4,5 blocuri de tine."),
    SPEAR_WALL("Spear Wall", "Wall", "⫼", Weapon.SPEAR, null, Kind.NORMAL, 5, Material.BAMBOO_FENCE, 18, 0.6,
            "Îți proptești sulița 4 secunde: inamicii care se apropie din față sunt înțepați și respinși. Primești Rezistență."),
    THRUST_FLURRY("Thrust Flurry", "Thrusts", "⋙", Weapon.SPEAR, null, Kind.NORMAL, 10, Material.FLINT, 14, 0.35,
            "Opt împunsături rapide în linie (5 blocuri), fiecare străpunge toți inamicii din cale."),
    PINNING_THROW("Pinning Throw", "Pin", "⊸", Weapon.SPEAR, null, Kind.NORMAL, 15, Material.IRON_CHAIN, 14, 1.0,
            "Arunci sulița în inamicul la care te uiți: e țintuit în pământ 3 secunde și strălucește."),
    DRAGON_LEAP("Dragon Leap", "Leap", "⇗", Weapon.SPEAR, null, Kind.NORMAL, 20, Material.DRAGON_BREATH, 16, 1.6,
            "Sari până la locul la care țintești (16 blocuri) și aterizezi cu sulița înfiptă, aruncând în aer inamicii din jur."),
    GUNGNIR("Gungnir", "Gungnir", "⇩", Weapon.SPEAR, Trigger.ULTIMATE, Kind.ULTIMATE, 25, Material.END_ROD, 100, 3.0,
            "ULTIMATĂ: o suliță uriașă cade din cer în fața ta, apoi țepi de lumină țâșnesc din pământ în cercuri tot mai largi."),

    // ---------------------------------------------------------------- Pickaxe
    TREMOR_SENSE("Tremor Sense", "Sense", "◈", Weapon.PICKAXE, Trigger.STANDING, Kind.NORMAL, 0, Material.SPYGLASS, 30, 0,
            "Dezvăluie minereurile din apropiere prin pereți timp de 10 secunde (doar tu le vezi)."),
    TUNNEL_BORE("Tunnel Bore", "Bore", "▦", Weapon.PICKAXE, Trigger.SNEAKING, Kind.NORMAL, 0, Material.RAIL, 20, 0,
            "Sapi un tunel de 3x3, adânc de trei blocuri, în direcția în care privești."),
    MINERS_RUSH("Miner's Rush", "Rush", "⚡", Weapon.PICKAXE, Trigger.LOOKING_UP, Kind.NORMAL, 0, Material.GOLDEN_PICKAXE, 45, 0,
            "Minezi mult mai repede timp de 10 secunde."),
    SHATTER("Shatter", "Shatter", "✹", Weapon.PICKAXE, Trigger.LOOKING_DOWN, Kind.NORMAL, 0, Material.TNT_MINECART, 25, 0,
            "Sfărâmi roca din jurul blocului la care te uiți."),

    // ------------------------------------------- Woodcutting (axe, looking at a tree)
    CLEAR_CUT("Clear Cut", "Cut", "♣", Weapon.WOODCUTTING, Trigger.STANDING, Kind.NORMAL, 0, Material.OAK_LOG, 45, 0,
            "Dobori toți copacii pe o suprafață de 10x10 în jurul tău și replantezi puieții."),
    GROVE_GROWTH("Grove Growth", "Grove", "↟", Weapon.WOODCUTTING, Trigger.SNEAKING, Kind.NORMAL, 0, Material.OAK_SAPLING, 40, 0,
            "Toți puieții din jurul tău (8 blocuri) cresc pe loc în copaci."),
    LUMBER_FRENZY("Lumber Frenzy", "Frenzy", "✵", Weapon.WOODCUTTING, Trigger.LOOKING_UP, Kind.NORMAL, 0, Material.GOLDEN_AXE, 60, 0,
            "20 de secunde: fiecare buștean tăiat dă unul în plus și tai mai repede (Grabă II)."),

    // ------------------------------------------------------------------ Tools
    HARVEST_WAVE("Harvest Wave", "Harvest", "⚘", Weapon.HOE, Trigger.STANDING, Kind.NORMAL, 0, Material.WHEAT, 20, 0,
            "Culegi și replantezi toate culturile coapte dintr-o zonă de 5x5."),
    BURROW("Burrow", "Burrow", "⊔", Weapon.SHOVEL, Trigger.STANDING, Kind.NORMAL, 0, Material.DIAMOND_SHOVEL, 30, 0,
            "5 secunde spargi instant pământul, nisipul, pietrișul și alte blocuri moi."),
    GRAPPLE_HOOK("Grapple Hook", "Hook", "↗", Weapon.FISHING_ROD, Trigger.STANDING, Kind.NORMAL, 0, Material.FISHING_ROD, 8, 0,
            "Agăți blocul la care te uiți (până la 30 de blocuri) și ești tras până la el.");

    /** Aerial skills only go in the mid-air slot; ultimates only in the ultimate slot. */
    public enum Kind {
        NORMAL, AERIAL, ULTIMATE
    }

    public enum Weapon {
        SWORD("Sword", Material.DIAMOND_SWORD, List.of(Trigger.STANDING, Trigger.AIRBORNE, Trigger.SPRINTING, Trigger.SNEAKING,
                Trigger.LOOKING_UP, Trigger.LOOKING_DOWN, Trigger.ULTIMATE)),
        AXE("Axe", Material.DIAMOND_AXE, List.of(Trigger.STANDING, Trigger.AIRBORNE, Trigger.SPRINTING, Trigger.SNEAKING,
                Trigger.LOOKING_UP, Trigger.LOOKING_DOWN, Trigger.ULTIMATE)),
        SHIELD("Shield", Material.SHIELD, List.of(Trigger.BLOCKING, Trigger.BLOCKING_SNEAKING)),
        BOW("Bow", Material.BOW, List.of(Trigger.STANDING, Trigger.SNEAKING, Trigger.SPRINTING, Trigger.LOOKING_UP, Trigger.AIRBORNE,
                Trigger.ULTIMATE)),
        CROSSBOW("Crossbow", Material.CROSSBOW, List.of(Trigger.STANDING, Trigger.SNEAKING, Trigger.SPRINTING, Trigger.LOOKING_DOWN,
                Trigger.AIRBORNE, Trigger.ULTIMATE)),
        TRIDENT("Trident", Material.TRIDENT, List.of(Trigger.STANDING, Trigger.SNEAKING, Trigger.LOOKING_UP, Trigger.ULTIMATE)),
        MACE("Mace", Material.MACE, List.of(Trigger.STANDING, Trigger.SNEAKING, Trigger.AIRBORNE, Trigger.ULTIMATE)),
        SPEAR("Spear", Material.IRON_SPEAR, List.of(Trigger.STANDING, Trigger.AIRBORNE, Trigger.SPRINTING, Trigger.SNEAKING,
                Trigger.LOOKING_UP, Trigger.LOOKING_DOWN, Trigger.ULTIMATE)),
        PICKAXE("Pickaxe", Material.DIAMOND_PICKAXE, List.of(Trigger.STANDING, Trigger.SNEAKING, Trigger.LOOKING_UP, Trigger.LOOKING_DOWN)),
        /** Not an item of its own: an axe counts as this while the player looks at a log, leaves or a sapling. */
        WOODCUTTING("Woodcutting", Material.OAK_LOG, List.of(Trigger.STANDING, Trigger.SNEAKING, Trigger.LOOKING_UP)),
        HOE("Hoe", Material.DIAMOND_HOE, List.of(Trigger.STANDING)),
        SHOVEL("Shovel", Material.DIAMOND_SHOVEL, List.of(Trigger.STANDING)),
        FISHING_ROD("Fishing Rod", Material.FISHING_ROD, List.of(Trigger.STANDING));

        private final String displayName;
        private final Material icon;
        private final List<Trigger> slots;

        Weapon(String displayName, Material icon, List<Trigger> slots) {
            this.displayName = displayName;
            this.icon = icon;
            this.slots = slots;
        }

        public String displayName() {
            return displayName;
        }

        public Material icon() {
            return icon;
        }

        /** The F trigger slots this weapon has. */
        public List<Trigger> slots() {
            return slots;
        }


        /** Tools: no dodge roll. */
        public boolean isTool() {
            return this == PICKAXE || this == WOODCUTTING || this == HOE || this == SHOVEL || this == FISHING_ROD;
        }

        /** Skill damage for these weapons is a flat number instead of a multiple of attack damage. */
        public boolean flatDamage() {
            return this == SHIELD || this == BOW || this == CROSSBOW;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The weapon a held item counts as (shields are handled separately, by blocking). */
        public static Weapon of(Material material) {
            String name = material.name();
            if (name.endsWith("_SWORD")) {
                return SWORD;
            }
            if (name.endsWith("_PICKAXE")) {
                return PICKAXE;
            }
            if (name.endsWith("_AXE")) {
                return AXE;
            }
            if (name.endsWith("_HOE")) {
                return HOE;
            }
            if (name.endsWith("_SHOVEL")) {
                return SHOVEL;
            }
            if (name.endsWith("_SPEAR")) {
                return SPEAR;
            }
            return switch (material) {
                case BOW -> BOW;
                case CROSSBOW -> CROSSBOW;
                case TRIDENT -> TRIDENT;
                case MACE -> MACE;
                case FISHING_ROD -> FISHING_ROD;
                default -> null;
            };
        }
    }

    /** What the player is doing when they press the swap-hands key. */
    public enum Trigger {
        STANDING("F"),
        AIRBORNE("F în aer"),
        SPRINTING("F în sprint"),
        SNEAKING("F furișat"),
        LOOKING_UP("F privind în sus"),
        LOOKING_DOWN("F privind în jos"),
        ULTIMATE("F furișat și privind în sus"),
        BLOCKING("F în timp ce blochezi"),
        BLOCKING_SNEAKING("F blocând și furișat");

        private final String description;

        Trigger(String description) {
            this.description = description;
        }

        /** How the slot is triggered (lang key trigger.<id>). */
        public String description() {
            return WeaponSkillsPlugin.text("trigger." + id(), description);
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final String displayName;
    private final String shortName;
    private final String icon;
    private final Weapon weapon;
    private final Trigger defaultSlot;
    private final Kind kind;
    private final int unlockMastery;
    private final Material iconItem;
    private final double defaultCooldown;
    private final double defaultDamage;
    private final String description;

    Skill(String displayName, String shortName, String icon, Weapon weapon, Trigger defaultSlot, Kind kind, int unlockMastery,
          Material iconItem, double defaultCooldown, double defaultDamage, String description) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.icon = icon;
        this.weapon = weapon;
        this.defaultSlot = defaultSlot;
        this.kind = kind;
        this.unlockMastery = unlockMastery;
        this.iconItem = iconItem;
        this.defaultCooldown = defaultCooldown;
        this.defaultDamage = defaultDamage;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    /** Compact name for the cooldown bar above the hotbar. */
    public String shortName() {
        return shortName;
    }

    /** Text symbol used on the action bar. */
    public String icon() {
        return icon;
    }

    public Weapon weapon() {
        return weapon;
    }

    /** The slot this skill starts in, or null for skills that must be unlocked and equipped. */
    public Trigger defaultSlot() {
        return defaultSlot;
    }

    public Kind kind() {
        return kind;
    }

    /** Weapon mastery needed to use this skill (0 = available from the start). */
    public int unlockMastery() {
        return unlockMastery;
    }

    /** Vanilla item shown for this skill in the skills menu. */
    public Material iconItem() {
        return iconItem;
    }

    public double defaultCooldown() {
        return defaultCooldown;
    }

    public double defaultDamage() {
        return defaultDamage;
    }

    /** What the skill does (lang key skill.<id>). */
    public String description() {
        return WeaponSkillsPlugin.text("skill." + id(), description);
    }

    /** Whether this skill deals damage (so leveling it up increases damage). */
    public boolean dealsDamage() {
        return defaultDamage > 0;
    }

    /** Whether this skill may be put in the given slot of its weapon. */
    public boolean fits(Trigger slot) {
        if (!weapon.slots().contains(slot)) {
            return false;
        }
        return switch (kind) {
            case AERIAL -> slot == Trigger.AIRBORNE;
            case ULTIMATE -> slot == Trigger.ULTIMATE;
            case NORMAL -> slot != Trigger.ULTIMATE;
        };
    }

    /**
     * Skills that keep the real look direction even when cast with a look-up/look-down key, because
     * aiming up or down is the point (grapples, launching yourself, digging). All others aim straight
     * ahead from those keys - see {@link Aim}.
     */
    public boolean freeAim() {
        return switch (this) {
            case RIPTIDE_SURGE, GRAPPLE_HOOK, TUNNEL_BORE, SHATTER, HARVEST_WAVE, WIND_BURST -> true;
            default -> false;
        };
    }

    /** Key used in the config and in saved progress. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Skill fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The skill that starts in this slot. */
    public static Skill defaultFor(Weapon weapon, Trigger slot) {
        for (Skill skill : values()) {
            if (skill.weapon == weapon && skill.defaultSlot == slot) {
                return skill;
            }
        }
        return null;
    }
}
