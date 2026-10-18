package com.raresb.weaponskills;

import java.util.List;
import java.util.Locale;
import org.bukkit.Material;

/**
 * Skill augments: at skill levels 30, 40 and 50 the player picks one of two augments for that skill
 * (levels 21-50 had no reward before). Every augment works on any skill through shared hooks
 * (damage, cooldown, on-hit, on-use, on-kill), so all 85 skills get them without per-skill code.
 */
public enum Augment {
    BRUTAL("Brutal", Material.NETHERITE_SCRAP, "+25% damage, but 15% longer cooldown."),
    SWIFT("Swift", Material.SUGAR, "20% shorter cooldown (10% less damage)."),
    EXECUTE("Execute", Material.WITHER_SKELETON_SKULL, "+40% damage to enemies below 30% health."),
    SEARING("Searing", Material.BLAZE_POWDER, "The hit sets the target on fire for 3 seconds."),
    FROST("Frost", Material.PACKED_ICE, "The hit slows the target (Slowness II, 2 seconds)."),
    VENOM("Venom", Material.SPIDER_EYE, "The hit poisons the target for 3 seconds."),
    SUNDER("Sunder", Material.IRON_NUGGET, "The hit weakens the target (Weakness, 4 seconds)."),
    VAMPIRIC("Vampiric", Material.REDSTONE, "Heal for 6% of the damage you deal."),
    REAPING("Reaping", Material.BONE, "A kill with the skill cuts the remaining cooldown by 30%."),
    ECHO("Echo", Material.ECHO_SHARD, "20% chance the skill doesn't go on cooldown."),
    GUARDED("Guarded", Material.IRON_CHESTPLATE, "After use: Resistance I, 4 seconds."),
    FLEET("Fleet", Material.FEATHER, "After use: Speed II, 4 seconds."),
    MENDING("Mending", Material.GLISTERING_MELON_SLICE, "After use: heal 3 hearts."),
    VIGOR("Vigor", Material.BLAZE_ROD, "After use: Strength I, 5 seconds.");

    /** Skill level at which each tier's choice opens. */
    public static final int[] TIER_LEVELS = {30, 40, 50};

    private final String displayName;
    private final Material icon;
    private final String description;

    Augment(String displayName, Material icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    /** What the augment does (lang key augment.<id>). */
    public String description() {
        return WeaponSkillsPlugin.text("augment." + id(), description);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Augment fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The two augments offered for a skill at a tier (0 = level 30, 1 = level 40, 2 = level 50). */
    public static List<Augment> options(Skill skill, int tier) {
        if (!skill.dealsDamage()) {
            // Utility skills (buffs, mobility, tools): only cooldown and on-use augments make sense.
            return switch (tier) {
                case 0 -> List.of(SWIFT, ECHO);
                case 1 -> List.of(GUARDED, FLEET);
                default -> List.of(MENDING, VIGOR);
            };
        }
        return switch (tier) {
            case 0 -> List.of(BRUTAL, SWIFT);
            case 1 -> switch (skill.weapon()) {
                case SWORD -> List.of(VAMPIRIC, EXECUTE);
                case AXE -> List.of(SUNDER, EXECUTE);
                case SHIELD -> List.of(SUNDER, FROST);
                case BOW -> List.of(SEARING, VENOM);
                case CROSSBOW -> List.of(SEARING, FROST);
                case TRIDENT -> List.of(FROST, VAMPIRIC);
                case MACE -> List.of(SUNDER, SEARING);
                case SPEAR -> List.of(EXECUTE, VENOM);
                default -> List.of(SEARING, FROST);
            };
            default -> List.of(REAPING, ECHO);
        };
    }
}
