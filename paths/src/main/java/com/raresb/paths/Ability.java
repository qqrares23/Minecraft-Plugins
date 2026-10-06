package com.raresb.paths;

import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/**
 * Every path ability. A path offers two per slot (three on the magic paths, which also have a spell);
 * the player picks one of each in /paths. The
 * first ability of a slot is the default. Effects live in PathEffects, MorePathEffects and
 * MagicPathEffects; "upgrades" are the built-in improvements unlocked by path level.
 */
public enum Ability {
    // ------------------------------------------------------------- Duelist
    PRECISION(Path.DUELIST, Path.Slot.ON_HIT, "Precision", Material.TARGET, 0,
            "A 4-a lovitură în aceeași țintă este o tăietură critică garantată; loviturile critice fac daune în plus.",
            "Lvl 5: la fiecare a 3-a lovitură", "Lvl 20: loviturile Precision te vindecă"),
    RIPOSTE(Path.DUELIST, Path.Slot.ON_HIT, "Riposte", Material.IRON_SWORD, 0,
            "După ce primești o lovitură corp la corp, următoarea ta lovitură cu sabia (în 3s) face +40% daune.",
            "Lvl 10: și te vindecă o inimă"),
    FLEET_FOOT(Path.DUELIST, Path.Slot.PASSIVE, "Fleet Foot", Material.FEATHER, 0,
            "Mai multă viteză de mișcare și de atac.",
            "Lvl 15: se dublează sub jumătate din viață"),
    EN_GARDE(Path.DUELIST, Path.Slot.PASSIVE, "En Garde", Material.IRON_CHESTPLATE, 0,
            "Cât ții sabia în mână primești cu 8% mai puține daune corp la corp (crește cu nivelul).",
            "Lvl 10: și 15% șansă să eviți complet o lovitură corp la corp"),
    LUNGE(Path.DUELIST, Path.Slot.SKILL, "Lunge", Material.WIND_CHARGE, 8,
            "Țâșnești înainte și străpungi primul inamic.",
            "Lvl 10: ajunge mai departe și lovește mai tare"),
    FEINT(Path.DUELIST, Path.Slot.SKILL, "Feint", Material.ENDER_EYE, 10,
            "Sari înapoi, iar inamicul din fața ta e amețit; următoarea ta lovitură cu sabia (în 3s) e critică (x1.5).",
            "Lvl 10: amețirea durează dublu"),

    // ---------------------------------------------------------- Warbringer
    REND(Path.WARBRINGER, Path.Slot.ON_HIT, "Rend", Material.REDSTONE, 0,
            "Loviturile sfâșie armura: ținta primește mai multe daune timp de 4s (se cumulează de 3 ori).",
            "Lvl 5: se cumulează de 4 ori"),
    SUNDER(Path.WARBRINGER, Path.Slot.ON_HIT, "Sunder", Material.FLINT, 0,
            "Loviturile încărcate au 15% șansă (crește cu nivelul) să spargă armura țintei: −4 armură și Slăbiciune 5s.",
            "Lvl 10: −6 armură"),
    BLOODRAGE(Path.WARBRINGER, Path.Slot.PASSIVE, "Bloodrage", Material.NETHER_WART, 0,
            "Cu cât ai mai puțină viață, cu atât faci mai multe daune corp la corp.",
            "Lvl 15: te vindecă sub 30% viață"),
    IRONHIDE(Path.WARBRINGER, Path.Slot.PASSIVE, "Ironhide", Material.IRON_BLOCK, 0,
            "+2 armură (crește cu nivelul) și 30% rezistență la împingere.",
            "Lvl 10: rezistență la împingere dublă"),
    CRUSHING_BLOW(Path.WARBRINGER, Path.Slot.SKILL, "Crushing Blow", Material.HEAVY_CORE, 10,
            "Următoarea ta lovitură din 3s zdrobește pentru daune uriașe și amețește.",
            "Lvl 10: trimite o undă de șoc", "Lvl 20: uciderile resetează Crushing Blow"),
    BATTLE_LEAP(Path.WARBRINGER, Path.Slot.SKILL, "Battle Leap", Material.RABBIT_FOOT, 12,
            "Sari spre locul la care te uiți și aterizezi cu o lovitură care rănește și încetinește în jur.",
            "Lvl 10: rază mai mare și amețește"),

    // -------------------------------------------------------------- Ranger
    HEADHUNTER(Path.RANGER, Path.Slot.ON_HIT, "Headhunter", Material.SKELETON_SKULL, 0,
            "Săgețile trase de departe fac daune în plus (cu atât mai multe cu cât ești mai departe).",
            "Lvl 5: loviturile de peste 20 de blocuri încetinesc ținta"),
    VENOM_TIPS(Path.RANGER, Path.Slot.ON_HIT, "Venom Tips", Material.POISONOUS_POTATO, 0,
            "Săgețile trase cu arcul întins complet otrăvesc ținta 2s (crește cu nivelul).",
            "Lvl 10: Otravă II"),
    EAGLE_EYE(Path.RANGER, Path.Slot.PASSIVE, "Eagle Eye", Material.SPYGLASS, 0,
            "Săgeți mai rapide și șansa de a nu consuma săgeata.",
            "Lvl 15: păstrează săgeata de două ori mai des"),
    CAMOUFLAGE(Path.RANGER, Path.Slot.PASSIVE, "Camouflage", Material.FERN, 0,
            "Stai furișat nemișcat 2s și devii invizibil; prima săgeată trasă din invizibilitate face +30% daune.",
            "Lvl 10: +60% daune"),
    POWER_SHOT(Path.RANGER, Path.Slot.SKILL, "Power Shot", Material.SPECTRAL_ARROW, 10,
            "O săgeată care străpunge și împinge.",
            "Lvl 10: străpunge 5 ținte", "Lvl 20: țintele strălucesc și sunt încetinite"),
    SNARE_SHOT(Path.RANGER, Path.Slot.SKILL, "Snare Shot", Material.COBWEB, 12,
            "Săgeata imobilizează ținta 3s.",
            "Lvl 10: îi prinde și pe cei din jurul țintei"),

    // ------------------------------------------------------------ Arbalist
    ARMOR_PIERCER(Path.ARBALIST, Path.Slot.ON_HIT, "Armor Piercer", Material.IRON_HELMET, 0,
            "Săgețile de arbaletă fac daune în plus țintelor cu armură.",
            "Lvl 5: săgețile dezactivează scuturile ridicate"),
    CONCUSSION(Path.ARBALIST, Path.Slot.ON_HIT, "Concussion", Material.BELL, 0,
            "Săgețile de arbaletă au 15% șansă (crește cu nivelul) să amețească ținta 1.5s.",
            "Lvl 10: amețirea durează 2.5s"),
    STEADY_HANDS(Path.ARBALIST, Path.Slot.PASSIVE, "Steady Hands", Material.TRIPWIRE_HOOK, 0,
            "Săgețile lovesc mai tare când stai pe loc sau furișat; șansă de a încărca fără să consumi muniție.",
            "Lvl 15: păstrează muniția de două ori mai des"),
    DEADEYE(Path.ARBALIST, Path.Slot.PASSIVE, "Deadeye", Material.OBSERVER, 0,
            "Săgețile de arbaletă zboară cu 20% mai repede și drept (fără cădere) pe primele 15 blocuri.",
            "Lvl 10: străpung un inamic în plus"),
    HEAVY_BOLT(Path.ARBALIST, Path.Slot.SKILL, "Heavy Bolt", Material.ANVIL, 12,
            "O săgeată care aruncă ținta înapoi și o amețește.",
            "Lvl 10: trimite o undă de șoc", "Lvl 20: reîncărcarea se înjumătățește"),
    CLUSTER_BOLT(Path.ARBALIST, Path.Slot.SKILL, "Cluster Bolt", Material.FIREWORK_STAR, 12,
            "La impact, săgeata se desface în 5 săgeți mici care cad în jur.",
            "Lvl 10: 8 săgeți"),

    // ---------------------------------------------------------- Tidecaller
    SOAKED(Path.TIDECALLER, Path.Slot.ON_HIT, "Soaked", Material.WATER_BUCKET, 0,
            "Loviturile cu tridentul (și cele aruncate) udă ținta 5s: e încetinită și primește +10% daune de la tine (dublu pe ploaie).",
            "Lvl 10: Încetinire II"),
    UNDERTOW(Path.TIDECALLER, Path.Slot.ON_HIT, "Undertow", Material.KELP, 0,
            "Loviturile cu tridentul trag ținta spre tine și îi dau Slăbiciune 2s.",
            "Lvl 10: Slăbiciune II"),
    SEA_BLESSED(Path.TIDECALLER, Path.Slot.PASSIVE, "Sea Blessed", Material.HEART_OF_THE_SEA, 0,
            "În apă primești Grația delfinului; pe ploaie sau în apă faci +10% daune (crește cu nivelul).",
            "Lvl 10: și respiri sub apă"),
    STORMBORN(Path.TIDECALLER, Path.Slot.PASSIVE, "Stormborn", Material.LIGHTNING_ROD, 0,
            "Tridentele aruncate cheamă un fulger (fără foc) la impact: mereu pe ploaie, altfel 20% șansă.",
            "Lvl 10: fulgerul lovește și în jur"),
    MAELSTROM(Path.TIDECALLER, Path.Slot.SKILL, "Maelstrom", Material.NAUTILUS_SHELL, 14,
            "Un vârtej acolo unde te uiți trage inamicii spre centru și îi rănește 3s.",
            "Lvl 10: durează 5s"),
    GEYSER(Path.TIDECALLER, Path.Slot.SKILL, "Geyser", Material.PRISMARINE, 12,
            "Fântâni de apă aruncă în aer inamicii din fața ta.",
            "Lvl 10: și îi încetinesc"),

    // ---------------------------------------------------------- Juggernaut
    AFTERSHOCK(Path.JUGGERNAUT, Path.Slot.ON_HIT, "Aftershock", Material.POINTED_DRIPSTONE, 0,
            "Loviturile cu buzduganul crapă pământul: 25% din daune (crește cu nivelul) lovesc inamicii din jur.",
            "Lvl 10: rază mai mare"),
    SHATTERING(Path.JUGGERNAUT, Path.Slot.ON_HIT, "Shattering", Material.CRACKED_DEEPSLATE_BRICKS, 0,
            "Loviturile slăbesc ținta 4s; o lovitură din cădere (smash) o și amețește 2s.",
            "Lvl 10: amețire 3s"),
    UNSTOPPABLE(Path.JUGGERNAUT, Path.Slot.PASSIVE, "Unstoppable", Material.OBSIDIAN, 0,
            "50% rezistență la împingere; daunele din cădere scad cu 30% (crește cu nivelul), iar ce ai evitat se adaugă la următoarea lovitură cu buzduganul.",
            "Lvl 10: rezistență totală la împingere"),
    IRON_SKIN(Path.JUGGERNAUT, Path.Slot.PASSIVE, "Iron Skin", Material.NETHERITE_CHESTPLATE, 0,
            "+4 viață maximă și duritate a armurii în plus (crește cu nivelul).",
            "Lvl 10: +6 viață maximă"),
    SEISMIC_LEAP(Path.JUGGERNAUT, Path.Slot.SKILL, "Seismic Leap", Material.PISTON, 12,
            "Sari sus și te prăbușești cu o undă de șoc (mai puternică cu cât cazi de mai sus).",
            "Lvl 10: rază mai mare și amețește"),
    GRAVITY_CRUSH(Path.JUGGERNAUT, Path.Slot.SKILL, "Gravity Crush", Material.LODESTONE, 14,
            "Tragi spre tine inamicii din 8 blocuri, apoi îi strivești de pământ.",
            "Lvl 10: din 12 blocuri"),

    // ------------------------------------------------------------ Sentinel
    RETALIATION(Path.SENTINEL, Path.Slot.ON_HIT, "Retaliation", Material.GOLDEN_SWORD, 0,
            "Fiecare lovitură blocată cu scutul încarcă Retaliation: următorul tău atac (în 5s) face +30% daune pe cumul (max 3).",
            "Lvl 10: max 5 cumuluri"),
    SHIELD_SPIKES(Path.SENTINEL, Path.Slot.ON_HIT, "Shield Spikes", Material.CACTUS, 0,
            "Inamicii care îți lovesc scutul primesc daune înapoi și sunt împinși.",
            "Lvl 10: și sunt încetiniți"),
    STALWART(Path.SENTINEL, Path.Slot.PASSIVE, "Stalwart", Material.CHAINMAIL_CHESTPLATE, 0,
            "−15% daune din săgeți și explozii (crește cu nivelul).",
            "Lvl 10: și −10% din orice altă sursă"),
    GUARDIAN(Path.SENTINEL, Path.Slot.PASSIVE, "Guardian", Material.BEACON, 0,
            "Jucătorii din jurul tău (6 blocuri) primesc −8% daune (crește cu nivelul).",
            "Lvl 10: rază de 10 blocuri"),
    BULWARK(Path.SENTINEL, Path.Slot.SKILL, "Bulwark", Material.IRON_DOOR, 18,
            "3s nu primești daune, iar daunele corp la corp se întorc la atacator.",
            "Lvl 10: 5s"),
    SHIELD_CHARGE(Path.SENTINEL, Path.Slot.SKILL, "Shield Charge", Material.IRON_TRAPDOOR, 12,
            "Șarjezi înainte cu scutul, împingând și amețind tot ce lovești.",
            "Lvl 10: șarjă mai lungă și mai puternică"),

    // -------------------------------------------------------------- Shadow
    BACKSTAB(Path.SHADOW, Path.Slot.ON_HIT, "Backstab", Material.STONE_SWORD, 0,
            "Loviturile din spatele țintei fac +40% daune (crește cu nivelul).",
            "Lvl 10: și orbesc ținta 2s"),
    NIGHTBLADE(Path.SHADOW, Path.Slot.ON_HIT, "Nightblade", Material.BLACK_DYE, 0,
            "Noaptea sau în întuneric loviturile cu sabia fac +15% daune (crește cu nivelul).",
            "Lvl 10: și te vindecă 5% din daune"),
    SHADOW_VEIL(Path.SHADOW, Path.Slot.PASSIVE, "Shadow Veil", Material.PHANTOM_MEMBRANE, 0,
            "După o ucidere devii invizibil 2s și primești Viteză.",
            "Lvl 10: 4s"),
    ELUSIVE(Path.SHADOW, Path.Slot.PASSIVE, "Elusive", Material.RABBIT_HIDE, 0,
            "8% șansă (crește cu nivelul) să eviți complet o lovitură.",
            "Lvl 10: după ce eviți, următoarea lovitură cu sabia e critică (x1.5)"),
    VANISH(Path.SHADOW, Path.Slot.SKILL, "Vanish", Material.ENDER_PEARL, 14,
            "Devii invizibil 4s și creaturile te pierd din ochi; următoarea lovitură e critică (x1.8) și te scoate din invizibilitate.",
            "Lvl 10: 6s și Viteză"),
    SMOKE_BOMB(Path.SHADOW, Path.Slot.SKILL, "Smoke Bomb", Material.GUNPOWDER, 16,
            "O bombă de fum orbește și încetinește inamicii din 5 blocuri; tu primești Viteză II 3s.",
            "Lvl 10: 7 blocuri și Slăbiciune"),

    // --------------------------------------------------------- Beastmaster
    PACK_MARK(Path.BEASTMASTER, Path.Slot.ON_HIT, "Pack Mark", Material.LEAD, 0,
            "Loviturile tale (cu orice) marchează ținta 5s: animalele tale o atacă și îi fac +20% daune (crește cu nivelul).",
            "Lvl 10: ținta marcată strălucește"),
    PREDATOR(Path.BEASTMASTER, Path.Slot.ON_HIT, "Predator", Material.MUTTON, 0,
            "Faci +10% daune (crește cu nivelul) țintelor atacate în ultimele 5s de animalele tale.",
            "Lvl 10: te vindeci o inimă când un animal al tău ucide ceva"),
    ALPHA_BOND(Path.BEASTMASTER, Path.Slot.PASSIVE, "Alpha Bond", Material.GOLDEN_APPLE, 0,
            "Animalele tale îmblânzite din apropiere au +20% viață (crește cu nivelul) și Regenerare.",
            "Lvl 10: și +20% daune"),
    PACK_LEADER(Path.BEASTMASTER, Path.Slot.PASSIVE, "Pack Leader", Material.WOLF_ARMOR, 0,
            "Cu cel puțin 2 animale lângă tine primești Rezistență I, iar ele primesc Viteză.",
            "Lvl 10: cu 3+ animale, și Forță I"),
    CALL_OF_THE_WILD(Path.BEASTMASTER, Path.Slot.SKILL, "Call of the Wild", Material.BONE_MEAL, 30,
            "Chemi 2 lupi (30s) care luptă pentru tine.",
            "Lvl 10: 3 lupi"),
    RALLY(Path.BEASTMASTER, Path.Slot.SKILL, "Rally", Material.GOAT_HORN, 20,
            "Animalele tale din 24 de blocuri vin la tine, se vindecă și primesc Forță II și Viteză 10s.",
            "Lvl 10: și Rezistență"),

    // ---------------------------------------------------------- Pyromancer
    IGNITE(Path.PYROMANCER, Path.Slot.ON_HIT, "Ignite", Material.FLINT_AND_STEEL, 0,
            "Bolturile aprind ținta 3s; țintele care ard primesc +10% daune de la tine (crește cu nivelul).",
            "Lvl 10: focul sare la un inamic apropiat"),
    COMBUSTION(Path.PYROMANCER, Path.Slot.ON_HIT, "Combustion", Material.TNT, 0,
            "Fiecare al 4-lea bolt explodează (fără să spargă blocuri) și rănește în jur.",
            "Lvl 10: la fiecare al 3-lea"),
    FLAMEWARD(Path.PYROMANCER, Path.Slot.PASSIVE, "Flameward", Material.MAGMA_CREAM, 0,
            "Rezistență la foc permanentă.",
            "Lvl 10: și Viteză I în Nether"),
    EMBER_AURA(Path.PYROMANCER, Path.Slot.PASSIVE, "Ember Aura", Material.CAMPFIRE, 0,
            "Inamicii care te lovesc corp la corp iau foc 3s.",
            "Lvl 10: și primesc daune"),
    FIREBALL(Path.PYROMANCER, Path.Slot.SKILL, "Fireball", Material.FIRE_CHARGE, 8,
            "O minge de foc care explodează (fără să spargă blocuri) și rănește în 3 blocuri.",
            "Lvl 10: lasă un cerc de foc 3s"),
    FLAME_WAVE(Path.PYROMANCER, Path.Slot.SKILL, "Flame Wave", Material.BLAZE_POWDER, 12,
            "Un val de flăcări în fața ta (6 blocuri) arde și rănește.",
            "Lvl 10: 8 blocuri și împinge"),

    // -------------------------------------------------------- Frost Warden
    CHILL(Path.FROST_WARDEN, Path.Slot.ON_HIT, "Chill", Material.SNOWBALL, 0,
            "Bolturile răcesc ținta (încetinire); la 3 cumuluri ea îngheață 2s.",
            "Lvl 10: îngheață 3s și ia daune"),
    SHATTER_SHOT(Path.FROST_WARDEN, Path.Slot.ON_HIT, "Shatter Shot", Material.ICE, 0,
            "Țintele încetinite sau înghețate primesc +25% daune de la tine (crește cu nivelul).",
            "Lvl 10: 30% din daune lovesc și inamicii din jur"),
    WINTERS_GRACE(Path.FROST_WARDEN, Path.Slot.PASSIVE, "Winter's Grace", Material.PACKED_ICE, 0,
            "Apa îngheață sub pașii tăi, iar frigul (zăpada pudră) nu te rănește.",
            "Lvl 10: Viteză I pe gheață și zăpadă"),
    FROST_ARMOR(Path.FROST_WARDEN, Path.Slot.PASSIVE, "Frost Armor", Material.BLUE_ICE, 0,
            "Inamicii care te lovesc corp la corp sunt încetiniți 2s și primesc daune.",
            "Lvl 10: și sunt înghețați 1s"),
    BLIZZARD(Path.FROST_WARDEN, Path.Slot.SKILL, "Blizzard", Material.POWDER_SNOW_BUCKET, 16,
            "O furtună de gheață acolo unde te uiți (4s) rănește și încetinește, apoi îngheață tot.",
            "Lvl 10: rază mai mare"),
    FROST_NOVA(Path.FROST_WARDEN, Path.Slot.SKILL, "Frost Nova", Material.SNOW_BLOCK, 12,
            "O explozie de gheață îngheață 2s inamicii din 5 blocuri.",
            "Lvl 10: 7 blocuri și daune"),
    // Third choices and spells (2.5.0)
    CINDER_CHAIN(Path.PYROMANCER, Path.Slot.ON_HIT, "Cinder Chain", Material.IRON_CHAIN, 0,
            "Un bolt într-o țintă care arde sare la încă un inamic din 4 blocuri și îi face 50% din daune.",
            "Lvl 10: sare la 2 inamici"),
    KINDLED_SOUL(Path.PYROMANCER, Path.Slot.PASSIVE, "Kindled Soul", Material.SOUL_CAMPFIRE, 0,
            "Fiecare ucidere cu toiagul îți dă un cumul de Căldură (max 5, 8s): +5% daune la bolturi pe cumul.",
            "Lvl 10: la 5 cumuluri bolturile pleacă cu 30% mai des"),
    FIRE_PILLAR(Path.PYROMANCER, Path.Slot.SKILL, "Fire Pillar", Material.LAVA_BUCKET, 14,
            "O coloană de foc 3s acolo unde te uiți: rănește, aprinde și aruncă inamicii în sus.",
            "Lvl 10: mai lată și mai puternică"),
    BLAZING_LEAP(Path.PYROMANCER, Path.Slot.SPELL, "Blazing Leap", Material.FIREWORK_ROCKET, 10,
            "Sari înainte și în sus dintr-o explozie de flăcări care arde inamicii din jur; nu iei daune de cădere.",
            "Lvl 10: flăcări și unde aterizezi"),
    PHOENIX_WARD(Path.PYROMANCER, Path.Slot.SPELL, "Phoenix Ward", Material.TOTEM_OF_UNDYING, 25,
            "Un scut de foc 6s: +4 inimi de absorbție, cine te lovește corp la corp ia foc, iar la final scutul explodează.",
            "Lvl 10: +6 inimi"),
    ICICLE_PIERCE(Path.FROST_WARDEN, Path.Slot.ON_HIT, "Icicle Pierce", Material.POINTED_DRIPSTONE, 0,
            "Bolturile trec prin prima țintă și lovesc și inamicul din spatele ei (6 blocuri) cu 60% din daune.",
            "Lvl 10: țintele înghețate primesc +4 daune"),
    PERMAFROST(Path.FROST_WARDEN, Path.Slot.PASSIVE, "Permafrost", Material.PACKED_ICE, 0,
            "Monștrii din 4 blocuri în jurul tău sunt încetiniți.",
            "Lvl 10: 6 blocuri și primesc +10% daune de la tine"),
    GLACIAL_LANCE(Path.FROST_WARDEN, Path.Slot.SKILL, "Glacial Lance", Material.ICE, 12,
            "O suliță de gheață zboară 16 blocuri înainte, rănește și îngheață tot ce străpunge.",
            "Lvl 10: 24 de blocuri"),
    FROST_STEP(Path.FROST_WARDEN, Path.Slot.SPELL, "Frost Step", Material.ENDER_PEARL, 10,
            "Te teleportezi 6 blocuri înainte, lăsând o dâră de gheață care încetinește inamicii.",
            "Lvl 10: 9 blocuri și o mică explozie de gheață unde ajungi"),
    ICE_BLOCK(Path.FROST_WARDEN, Path.Slot.SPELL, "Ice Block", Material.BLUE_ICE, 45,
            "Te închizi în gheață 3s: nu primești daune și nu te poți mișca, dar te vindeci o inimă pe secundă.",
            "Lvl 10: îți scoate și efectele negative"),

    // -------------------------------------------------------------- Lancer
    SKEWER(Path.LANCER, Path.Slot.ON_HIT, "Skewer", Material.IRON_SPEAR, 0,
            "Loviturile încărcate cu sulița străpung: inamicul din spatele țintei (3 blocuri) primește 40% din daune.",
            "Lvl 10: 60% din daune"),
    MOMENTUM_STRIKE(Path.LANCER, Path.Slot.ON_HIT, "Momentum Strike", Material.SADDLE, 0,
            "Loviturile date în sprint sau călare fac +25% daune (crește cu nivelul) și împing ținta mai departe.",
            "Lvl 15: și încetinesc ținta 2s"),
    LONG_REACH(Path.LANCER, Path.Slot.PASSIVE, "Long Reach", Material.BAMBOO, 0,
            "Cât ții sulița ajungi mai departe: +0,5 blocuri rază de atac (crește cu nivelul).",
            "Lvl 15: încă +0,5 blocuri"),
    CAVALIER(Path.LANCER, Path.Slot.PASSIVE, "Cavalier", Material.GOLDEN_HORSE_ARMOR, 0,
            "Cât ții sulița: +8% viteză de mișcare; călare primești cu 15% mai puține daune.",
            "Lvl 10: 25% mai puține daune călare"),
    CHARGE_LINE(Path.LANCER, Path.Slot.SKILL, "Charge Line", Material.WIND_CHARGE, 10,
            "Țâșnești 8 blocuri înainte cu sulița în față și lovești tot ce e pe linie.",
            "Lvl 10: 11 blocuri, iar inamicii loviți sunt încetiniți"),
    WAR_BANNER(Path.LANCER, Path.Slot.SKILL, "War Banner", Material.RED_BANNER, 20,
            "Înfigi un steag de luptă 8s: tu și jucătorii din 6 blocuri primiți Forță I.",
            "Lvl 10: și Rezistență I");

    private final Path path;
    private final Path.Slot slot;
    private final String displayName;
    private final Material icon;
    private final int cooldown;
    private final String description;
    private final List<String> upgrades;

    Ability(Path path, Path.Slot slot, String displayName, Material icon, int cooldown, String description, String... upgrades) {
        this.path = path;
        this.slot = slot;
        this.displayName = displayName;
        this.icon = icon;
        this.cooldown = cooldown;
        this.description = description;
        this.upgrades = List.of(upgrades);
    }

    public Path path() {
        return path;
    }

    public Path.Slot slot() {
        return slot;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    /** M2 cooldown in seconds (0 for passives). */
    public int cooldown() {
        return cooldown;
    }

    /** What the ability does (lang key ability.<id>.description). */
    public String description() {
        return PathsPlugin.text("ability." + id() + ".description", description);
    }

    /** "Lvl N: ..." improvements unlocked by path level (lang key ability.<id>.upgrades). */
    public List<String> upgrades() {
        return PathsPlugin.textList("ability." + id() + ".upgrades", upgrades);
    }

    /** The level an upgrade line unlocks at. */
    public static int upgradeLevel(String upgrade) {
        try {
            return Integer.parseInt(upgrade.substring(4, upgrade.indexOf(':')).trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Ability fromId(String id) {
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
