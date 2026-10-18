package com.raresb.weaponskills;

import com.raresb.weaponskills.common.Lang;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * The in-game guide book (/guide): controls, progression, every skill, and the other systems. The
 * text comes from the language file (guide.*). Sections about the other plugins only appear when
 * that plugin is installed, so the book fits any server.
 */
final class Guide {
    /** Optional sections: lang key under guide.sections and the plugin they need. */
    private static final String[][] SECTIONS = {
            {"paths", "Paths"}, {"professions", "Professions"}, {"enchants", "MagicEnchants"}, {"mobs", "MightyMobs"}};

    private final WeaponSkillsPlugin plugin;

    Guide(WeaponSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    void open(Player player) {
        Lang lang = plugin.lang();
        Object[] values = {"max-level", plugin.progress().maxLevel(), "evolve", plugin.getConfig().getInt("progression.evolve-level", 10)};
        List<String> installed = new ArrayList<>();
        for (String[] section : SECTIONS) {
            if (plugin.getServer().getPluginManager().getPlugin(section[1]) != null) {
                installed.add(section[0]);
            }
        }

        List<Component> pages = new ArrayList<>();
        // Contents page: the fixed chapters, then one per installed plugin, then commands.
        StringBuilder contents = new StringBuilder(lang.raw("guide.contents-title"));
        List<String> chapters = new ArrayList<>(List.of("skills", "levels", "all-skills"));
        chapters.addAll(installed);
        chapters.add("commands");
        for (int i = 0; i < chapters.size(); i++) {
            contents.append("\n").append(i + 1).append(". ").append(lang.raw("guide.contents." + chapters.get(i)));
        }
        contents.append("\n\n").append(lang.raw("guide.turn-page"));
        pages.add(Lang.parse(contents.toString()));

        for (String page : lang.rawList("guide.intro")) {
            pages.add(Lang.parse(page, values));
        }
        for (Skill skill : Skill.values()) {
            String how = skill.defaultSlot() != null && skill.unlockMastery() <= 0
                    ? skill.defaultSlot().description()
                    : lang.raw("guide.unlock").replace("<weapon>", skill.weapon().displayName())
                            .replace("<mastery>", Integer.toString(skill.unlockMastery()));
            pages.add(lang.get("guide.skill-page", "skill", skill.displayName(), "weapon", skill.weapon().displayName(),
                    "ultimate", skill.kind() == Skill.Kind.ULTIMATE ? lang.raw("guide.ultimate") : "", "how", how,
                    "description", skill.description(), "evolve", values[3], "evolution", Evolutions.of(skill)));
        }
        for (String section : installed) {
            for (String page : lang.rawList("guide.sections." + section)) {
                pages.add(Lang.parse(page, values));
            }
        }
        StringBuilder commands = new StringBuilder(lang.raw("guide.commands.title"));
        for (String line : lang.rawList("guide.commands.always")) {
            commands.append("\n").append(line);
        }
        for (String section : installed) {
            for (String line : lang.rawList("guide.commands." + section)) {
                commands.append("\n").append(line);
            }
        }
        for (String line : lang.rawList("guide.commands.extra")) {
            commands.append("\n").append(line);
        }
        pages.add(Lang.parse(commands.toString()));

        player.openBook(Book.book(lang.get("guide.title"), lang.get("guide.author"), pages));
    }
}
