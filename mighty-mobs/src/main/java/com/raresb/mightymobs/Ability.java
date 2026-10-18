package com.raresb.mightymobs;

import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** The special abilities a mighty mob can have. */
public enum Ability {
    TANK("Tank", "■", NamedTextColor.GRAY),
    SWIFT("Swift", "⚡", NamedTextColor.AQUA),
    BERSERKER("Berserker", "⚔", NamedTextColor.RED),
    VENOMOUS("Venomous", "☠", NamedTextColor.DARK_GREEN),
    FROST("Frost", "❄", NamedTextColor.BLUE),
    VAMPIRE("Vampire", "♦", NamedTextColor.DARK_RED),
    EXPLOSIVE("Explosive", "✹", NamedTextColor.GOLD),
    LEAPER("Leaper", "⇑", NamedTextColor.GREEN),
    REGENERATING("Regenerating", "✚", NamedTextColor.LIGHT_PURPLE),
    BLINKER("Blinker", "✦", NamedTextColor.DARK_PURPLE),
    NECROMANCER("Necromancer", "☗", NamedTextColor.DARK_GRAY);

    private final String displayName;
    private final String icon;
    private final TextColor color;

    Ability(String displayName, String icon, TextColor color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String icon() {
        return icon;
    }

    public TextColor color() {
        return color;
    }

    /** Lower-case id used in the config, commands and entity data. */
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
