package com.raresb.professions;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/** Every profession. Players have all of them and level each one by doing that kind of work. */
public enum Profession {
    WOODCUTTING("Woodcutting", "♣", 0x8D6E63, Material.IRON_AXE, "Taie bușteni.",
            List.of("Mai mulți bușteni per buștean pe măsură ce crești în nivel (până la trei)", "Din copaci: crengi, puieți ai acelui copac, mere,", "cuiburi de păsări, faguri, rareori mere de aur")),
    MINING("Mining", "⛏", 0x95A5A6, Material.IRON_PICKAXE, "Sparge piatră, deepslate și minereuri.",
            List.of("Descoperirile țin de locul unde minezi: cărbune și minereu brut,", "redstone, lapis și diamante la adâncime, cuarț în Nether", "Șansă de minereu dublu; impulsuri de Haste de la nivelul 25")),
    DIGGING("Digging", "▼", 0xC19A6B, Material.IRON_SHOVEL, "Sapă pământ, nisip, pietriș, lut, zăpadă, noroi...",
            List.of("Ce găsești ține de sol: cremene în pietriș, lut,", "oase, pepite, cioburi de ceramică, rareori smaralde și diamante", "Șansă de recoltă dublă")),
    FARMING("Farming", "☘", 0xF4D03F, Material.IRON_HOE, "Recoltează culturi complet crescute.",
            List.of("Șansă de recoltă dublă", "Recolte bogate, făină de oase, morcovi de aur din morcovi,", "pepene strălucitor din pepeni, semințe rare", "Replantare automată de la nivelul 20")),
    FORAGING("Foraging", "✿", 0x58D68D, Material.SHEARS, "Rupe iarbă, flori, frunze, ciuperci, liane, fructe de pădure.",
            List.of("Mai mult din ce culegi: flori, fructe de pădure, ciuperci, liane", "Frunze: bețe, puieți, mere. Iarbă: semințe", "Rar: semințe de torchflower, pitcher pod, spore blossom")),
    FISHING("Fishing", "∽", 0x3498DB, Material.FISHING_ROD, "Prinde orice cu undița.",
            List.of("Peștii mușcă mai repede (se cumulează cu Lure)", "Șansă de a prinde pești în plus", "Comori din apă: alge, cerneală, prismarin, cochilii de nautilus,", "rareori cărți, bureți, heart of the sea, tridente")),
    HUNTING("Hunting", "⚔", 0xC0392B, Material.BOW, "Ucide creaturi.",
            List.of("Șansă de pradă dublă", "Rareori capete de creaturi", "Mai mult XP")),
    HUSBANDRY("Husbandry", "♘", 0xF5CBA7, Material.WHEAT, "Înmulțește și tunde animale.",
            List.of("Șansă de pui gemeni", "Părinții se pot înmulți din nou mai repede", "Lână în plus la tuns")),
    SMELTING("Smelting", "♨", 0xE67E22, Material.FURNACE, "Scoate obiectele topite din cuptoare.",
            List.of("Șansă de produs în plus")),
    ENCHANTING("Enchanting", "✨", 0xAF7AC5, Material.ENCHANTING_TABLE, "Vrăjește obiecte la masa de vrăjit.",
            List.of("Șansă de a primi nivelurile înapoi")),
    ALCHEMY("Alchemy", "⚗", 0xBB8FCE, Material.BREWING_STAND, "Prepară poțiuni.",
            List.of("Șansă de a păstra ingredientul")),
    SMITHING("Smithing", "⚒", 0x90A4AE, Material.SMITHING_TABLE,
            "Repară și combină la nicovală, îmbunătățește la masa de forjă, fabrică unelte, arme și armuri.",
            List.of("Reparațiile la nicovală refac mai multă durabilitate", "(+0,5% pe nivel)"));

    private final String displayName;
    private final String icon;
    private final TextColor color;
    private final Material iconItem;
    private final String howToLevel;
    private final List<String> perks;

    Profession(String displayName, String icon, int color, Material iconItem, String howToLevel, List<String> perks) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = TextColor.color(color);
        this.iconItem = iconItem;
        this.howToLevel = howToLevel;
        this.perks = perks;
    }

    public String displayName() {
        return displayName;
    }

    /** Text symbol for chat and the action bar. */
    public String icon() {
        return icon;
    }

    public TextColor color() {
        return color;
    }

    public Material iconItem() {
        return iconItem;
    }

    /** How to earn XP (lang key profession.<id>.how). */
    public String howToLevel() {
        return ProfessionsPlugin.text("profession." + id() + ".how", howToLevel);
    }

    /** The level bonuses (lang key profession.<id>.bonuses). */
    public List<String> perks() {
        return ProfessionsPlugin.textList("profession." + id() + ".bonuses", perks);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
