package com.raresb.paths;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/**
 * The combat paths. Each has three slots (on-hit passive, always-on passive, M2 skill) with two
 * abilities to choose from in each (three for the magic paths, which also have a SPELL slot – see
 * {@link Ability}), and a talent choice at levels 5 to 50 (see {@link Talent}).
 */
public enum Path {
    DUELIST("Duelist", Weapon.SWORD, 0x5DADE2, Material.DIAMOND_SWORD, "click dreapta cu sabia",
            "lovituri și ucideri cu sabia", false),
    WARBRINGER("Warbringer", Weapon.AXE, 0xC0392B, Material.DIAMOND_AXE, "click dreapta cu toporul",
            "lovituri și ucideri cu toporul", true),
    RANGER("Ranger", Weapon.BOW, 0x58D68D, Material.BOW, "furișat + arc întins complet",
            "săgeți trase cu arcul", false),
    ARBALIST("Arbalist", Weapon.CROSSBOW, 0xF5B041, Material.CROSSBOW, "furișat + tragi cu arbaleta",
            "săgeți trase cu arbaleta", true),
    TIDECALLER("Tidecaller", Weapon.TRIDENT, 0x1ABC9C, Material.TRIDENT, "furișat + click dreapta cu tridentul",
            "lovituri cu tridentul (și aruncat)", false),
    JUGGERNAUT("Juggernaut", Weapon.MACE, 0x7F8C8D, Material.MACE, "click dreapta cu buzduganul",
            "lovituri și ucideri cu buzduganul", true),
    SENTINEL("Sentinel", Weapon.SHIELD, 0x2E86C1, Material.SHIELD, "furișat + click dreapta cu scutul",
            "lovituri blocate cu scutul și ucideri cu scutul la tine", true),
    SHADOW("Shadow", Weapon.SWORD, 0x6C3483, Material.NETHERITE_SWORD, "click dreapta cu sabia",
            "lovituri și ucideri cu sabia", false),
    BEASTMASTER("Beastmaster", Weapon.BONE, 0xA04000, Material.BONE, "click dreapta cu un os",
            "lovituri și ucideri ale animalelor tale îmblânzite", true),
    PYROMANCER("Pyromancer", Weapon.BLAZE_ROD, 0xE67E22, Material.BLAZE_ROD, "click dreapta cu toiagul (Blaze Rod)",
            "bolturi de foc (click stânga cu toiagul)", true),
    FROST_WARDEN("Frost Warden", Weapon.BREEZE_ROD, 0x85C1E9, Material.BREEZE_ROD, "click dreapta cu toiagul (Breeze Rod)",
            "bolturi de gheață (click stânga cu toiagul)", false),
    LANCER("Lancer", Weapon.SPEAR, 0xB8860B, Material.IRON_SPEAR, "furișat + click dreapta cu sulița",
            "lovituri și ucideri cu sulița", false);

    /** The item a path is played with. Two active paths can't share one. */
    public enum Weapon {
        SWORD, AXE, BOW, CROSSBOW, TRIDENT, MACE, SHIELD, BONE, BLAZE_ROD, BREEZE_ROD, SPEAR;

        /** The path weapon a held item counts as, or null. */
        public static Weapon of(Material material) {
            String name = material.name();
            if (name.endsWith("_SWORD")) {
                return SWORD;
            }
            if (name.endsWith("_AXE") && !name.endsWith("_PICKAXE")) {
                return AXE;
            }
            if (name.endsWith("_SPEAR")) {
                return SPEAR;
            }
            return switch (material) {
                case BOW -> BOW;
                case CROSSBOW -> CROSSBOW;
                case TRIDENT -> TRIDENT;
                case MACE -> MACE;
                case SHIELD -> SHIELD;
                case BONE -> BONE;
                case BLAZE_ROD -> BLAZE_ROD;
                case BREEZE_ROD -> BREEZE_ROD;
                default -> null;
            };
        }
    }

    /** The ability slots of a path. SPELL (sneak + M2) exists only for the magic paths. */
    public enum Slot {
        ON_HIT("La lovitură"), PASSIVE("Permanent"), SKILL("Abilitate (M2)"), SPELL("Vrajă (furișat + M2)");

        private final String label;

        Slot(String label) {
            this.label = label;
        }

        public String label() {
            return PathsPlugin.text("slot." + name().toLowerCase(Locale.ROOT), label);
        }
    }

    /** Levels at which a talent is chosen. */
    public static final int[] TALENT_TIERS = {5, 10, 15, 20, 30, 40, 50};

    private final String displayName;
    private final Weapon weapon;
    private final TextColor color;
    private final Material icon;
    private final String trigger;
    private final String xpSource;
    /** Sturdy paths get Guard as their level-15 talent choice, agile ones get Swift. */
    private final boolean sturdy;

    Path(String displayName, Weapon weapon, int color, Material icon, String trigger, String xpSource, boolean sturdy) {
        this.displayName = displayName;
        this.weapon = weapon;
        this.color = TextColor.color(color);
        this.icon = icon;
        this.trigger = trigger;
        this.xpSource = xpSource;
        this.sturdy = sturdy;
    }

    public String displayName() {
        return displayName;
    }

    public Weapon weapon() {
        return weapon;
    }

    public TextColor color() {
        return color;
    }

    public Material icon() {
        return icon;
    }

    /** How the M2 skill is used (lang key path.<id>.trigger). */
    public String trigger() {
        return PathsPlugin.text("path." + id() + ".trigger", trigger);
    }

    /** What earns this path XP (lang key path.<id>.xp-source). */
    public String xpSource() {
        return PathsPlugin.text("path." + id() + ".xp-source", xpSource);
    }

    /** Pyromancer and Frost Warden: left-click with their staff casts a bolt. */
    public boolean magic() {
        return this == PYROMANCER || this == FROST_WARDEN;
    }

    /** The slots this path has abilities for (SPELL only on the magic paths). */
    public List<Slot> slots() {
        List<Slot> list = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            if (!abilities(slot).isEmpty()) {
                list.add(slot);
            }
        }
        return list;
    }

    /** The abilities this path offers in a slot; the first one is the default. */
    public List<Ability> abilities(Slot slot) {
        List<Ability> list = new ArrayList<>();
        for (Ability ability : Ability.values()) {
            if (ability.path() == this && ability.slot() == slot) {
                list.add(ability);
            }
        }
        return list;
    }

    /** The two talents to choose from at a tier (5, 10, 15 or 20). */
    public Talent[] talents(int tier) {
        return switch (tier) {
            case 5 -> new Talent[] {Talent.HIT_POWER, Talent.LIFESTEAL};
            case 10 -> new Talent[] {Talent.SKILL_POWER, Talent.SKILL_HASTE};
            case 15 -> new Talent[] {Talent.PASSIVE_POWER, sturdy ? Talent.GUARD : Talent.SWIFT};
            case 20 -> new Talent[] {Talent.MIGHT, Talent.KILL_RESET};
            // Levels 21-50 (2.2.0): before, those levels gave nothing.
            case 30 -> new Talent[] {Talent.ENDURANCE, Talent.FOCUS};
            case 40 -> new Talent[] {Talent.FERVOR, Talent.SECOND_WIND};
            default -> new Talent[] {Talent.LEGEND, Talent.UNYIELDING};
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Path fromId(String id) {
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The talents: path-wide upgrades picked at levels 5, 10, 15 and 20. */
    public enum Talent {
        HIT_POWER("Lovituri puternice", "Pasiva la lovitură e cu 30% mai puternică."),
        LIFESTEAL("Sete de sânge", "Daunele făcute cu path-ul te vindecă cu 5% din ele."),
        SKILL_POWER("Abilitate amplificată", "Abilitatea M2 e cu 30% mai puternică."),
        SKILL_HASTE("Reîncărcare rapidă", "Abilitatea M2 se reîncarcă cu 25% mai repede."),
        PASSIVE_POWER("Pasivă întărită", "Pasiva permanentă e cu 30% mai puternică."),
        SWIFT("Pas ușor", "+10% viteză de mișcare cât path-ul e activ."),
        GUARD("Piele groasă", "Primești cu 10% mai puține daune."),
        MIGHT("Forța path-ului", "Faci cu 10% mai multe daune cu path-ul."),
        KILL_RESET("Vânătoare", "Fiecare ucidere cu path-ul înjumătățește reîncărcarea abilității M2."),
        ENDURANCE("Rezistență de veteran", "+2 inimi de viață maximă cât path-ul e activ."),
        FOCUS("Concentrare", "Abilitatea M2 se reîncarcă cu încă 15% mai repede."),
        FERVOR("Fervoare", "+10% viteză de atac cât path-ul e activ."),
        SECOND_WIND("Al doilea suflu", "Fiecare ucidere cu path-ul te vindecă cu 2 inimi."),
        LEGEND("Legendă", "Faci cu 10% mai multe daune cu path-ul și primești ★ în titlul din chat."),
        UNYIELDING("Neclintit", "O dată la 3 minute, o lovitură mortală te lasă cu o inimă în loc să te omoare.");

        private final String displayName;
        private final String description;

        Talent(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return PathsPlugin.text("talent." + name().toLowerCase(Locale.ROOT) + ".name", displayName);
        }

        public String description() {
            return PathsPlugin.text("talent." + name().toLowerCase(Locale.ROOT) + ".description", description);
        }
    }
}
