package com.raresb.paths;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * /paths: a page with every path, and a page per path where the player turns it on or off,
 * picks one ability for each slot and a talent at levels 5, 10, 15 and 20.
 */
final class PathMenu implements Listener {
    private static final int SIZE = 54;
    private static final int[] PATH_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24};
    private static final int INFO_SLOT = 40;
    private static final int BACK_SLOT = 0;
    private static final int HEADER_SLOT = 4;
    private static final int TOGGLE_SLOT = 8;
    /**
     * Per slot (Path.Slot order): the label, then up to three ability choices. Two slots per row:
     * on-hit | always-on, then M2 | spell (spells only exist on the magic paths).
     */
    private static final int[][] SLOT_ROWS = {{10, 11, 12, 13}, {14, 15, 16, 17}, {19, 20, 21, 22}, {23, 24, 25, 26}};
    private static final int TALENT_LABEL = 37;
    /** Talent tiers 5/10/15/20/30/40/50: choice A in row 5, choice B in row 6. */
    private static final int[] TALENT_A = {38, 39, 40, 41, 42, 43, 44};
    private static final int[] TALENT_B = {47, 48, 49, 50, 51, 52, 53};

    private final PathsPlugin plugin;

    PathMenu(PathsPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private Path path; // null = the list of paths

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = plugin.getServer().createInventory(holder, SIZE, plugin.lang().get("menu.title", "max", plugin.maxActive()));
        draw(holder, player);
        player.openInventory(holder.inventory);
    }

    private void draw(Holder holder, Player player) {
        ItemStack pane = named(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < SIZE; i++) {
            holder.inventory.setItem(i, pane);
        }
        if (holder.path == null) {
            drawMain(holder.inventory, player);
        } else {
            drawPath(holder.inventory, player, holder.path);
        }
    }

    // --- Main page ---

    private void drawMain(Inventory inventory, Player player) {
        List<Path> paths = shown(player);
        for (int i = 0; i < paths.size() && i < PATH_SLOTS.length; i++) {
            inventory.setItem(PATH_SLOTS[i], pathButton(player, paths.get(i)));
        }
        long cooldown = plugin.switchCooldownLeft(player);
        Set<Path> active = plugin.active(player);
        List<Component> info = new ArrayList<>();
        info.add(plugin.lang().item("menu.info.active", "paths", active.isEmpty() ? plugin.lang().raw("menu.info.none")
                : active.stream().map(Path::displayName).collect(Collectors.joining(", ")), "count", active.size(), "max", plugin.maxActive()));
        info.addAll(plugin.lang().lore("menu.info.lore"));
        info.add(cooldown > 0 ? plugin.lang().item("menu.info.cooldown", "time", minutes(cooldown)) : plugin.lang().item("menu.info.can-switch"));
        inventory.setItem(INFO_SLOT, named(Material.CLOCK, plugin.lang().item("menu.info.name"), info));
    }

    private ItemStack pathButton(Player player, Path path) {
        boolean active = plugin.isActive(player, path);
        int level = plugin.level(player, path);
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.lang().item(active ? "menu.active" : "menu.inactive"));
        lore.add(plugin.lang().item("menu.level", "level", level, "max", plugin.maxLevel()));
        lore.add(xpBar(player, path));
        lore.add(Component.empty());
        for (Path.Slot slot : path.slots()) {
            lore.add(plugin.lang().item("menu.slot-line", "slot", slot.label(), "ability", plugin.ability(player, path, slot).displayName()));
        }
        int open = plugin.openTalents(player, path).size();
        if (open > 0) {
            lore.add(plugin.lang().item("menu.open-talents", "count", plugin.lang().count("unit.talent", open)));
        }
        lore.add(Component.empty());
        lore.add(plugin.lang().item("menu.m2", "trigger", path.trigger()));
        lore.add(plugin.lang().item("menu.click-open"));
        ItemStack icon = named(path.icon(), text(path.displayName(), path.color()).decoration(TextDecoration.BOLD, true)
                .append(text("  Lvl " + level, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, false)), lore);
        icon.editMeta(meta -> meta.setEnchantmentGlintOverride(active));
        customIcon(icon, player, "path/" + path.id());
        return icon;
    }

    // --- Path page ---

    private void drawPath(Inventory inventory, Player player, Path path) {
        boolean active = plugin.isActive(player, path);
        int level = plugin.level(player, path);
        int power = plugin.power(player, path);
        inventory.setItem(BACK_SLOT, named(Material.ARROW, plugin.lang().item("menu.back"), List.of()));

        List<Component> header = new ArrayList<>();
        header.add(plugin.lang().item(active ? "menu.active" : "menu.inactive"));
        header.add(plugin.lang().item("menu.level", "level", level, "max", plugin.maxLevel()));
        header.add(xpBar(player, path));
        int veteran = (int) Math.round((plugin.veteranBonus(player, path) - 1) * 1000) / 10;
        header.add(plugin.lang().item(veteran > 0 ? "menu.veteran-on" : "menu.veteran", "bonus", veteran));
        header.add(Component.empty());
        header.add(plugin.lang().item("menu.m2-header", "trigger", path.trigger()));
        if (path.magic()) {
            header.add(plugin.lang().item(path == Path.PYROMANCER ? "menu.bolt-fire" : "menu.bolt-ice"));
            header.add(plugin.lang().item("menu.spell-header"));
        }
        header.add(plugin.lang().item("menu.xp-from", "source", path.xpSource()));
        ItemStack headerIcon = named(path.icon(), text(path.displayName(), path.color()).decoration(TextDecoration.BOLD, true), header);
        customIcon(headerIcon, player, "path/" + path.id());
        inventory.setItem(HEADER_SLOT, headerIcon);

        long cooldown = plugin.switchCooldownLeft(player);
        List<Component> toggle = new ArrayList<>();
        if (active) {
            toggle.add(plugin.lang().item("menu.drop.kept"));
            toggle.add(cooldown > 0 ? plugin.lang().item("menu.drop.cooldown", "time", minutes(cooldown))
                    : plugin.lang().item("menu.drop.pause", "time", minutes(plugin.getConfig().getLong("switch-cooldown-minutes", 60) * 60_000L)));
            inventory.setItem(TOGGLE_SLOT, named(Material.RED_DYE, plugin.lang().item("menu.drop.name", "path", path.displayName()), toggle));
        } else {
            toggle.add(plugin.lang().item("menu.pick.count", "count", plugin.active(player).size(), "max", plugin.maxActive()));
            inventory.setItem(TOGGLE_SLOT, named(Material.LIME_DYE, plugin.lang().item("menu.pick.name", "path", path.displayName()), toggle));
        }

        for (Path.Slot slot : path.slots()) {
            int[] row = SLOT_ROWS[slot.ordinal()];
            inventory.setItem(row[0], named(Material.PAPER, plugin.lang().item("menu.slot-name", "slot", slot.label()),
                    plugin.lang().lore(slot == Path.Slot.SPELL ? "menu.spell-lore" : "menu.slot-lore")));
            List<Ability> options = path.abilities(slot);
            for (int i = 0; i < options.size() && i < row.length - 1; i++) {
                inventory.setItem(row[i + 1], abilityButton(player, options.get(i), power));
            }
        }

        inventory.setItem(TALENT_LABEL, named(Material.ENCHANTED_BOOK, plugin.lang().item("menu.talents.name"), plugin.lang().lore("menu.talents.lore")));
        for (int i = 0; i < Path.TALENT_TIERS.length; i++) {
            int tier = Path.TALENT_TIERS[i];
            Path.Talent[] pair = path.talents(tier);
            inventory.setItem(TALENT_A[i], talentButton(player, path, tier, pair[0], level));
            inventory.setItem(TALENT_B[i], talentButton(player, path, tier, pair[1], level));
        }
    }

    private ItemStack abilityButton(Player player, Ability ability, int power) {
        boolean chosen = plugin.ability(player, ability.path(), ability.slot()) == ability;
        List<Component> lore = new ArrayList<>();
        lore.addAll(wrap(ability.description(), NamedTextColor.GRAY));
        if (ability.cooldown() > 0) {
            lore.add(plugin.lang().item("menu.cooldown", "seconds", ability.cooldown()));
        }
        for (String upgrade : ability.upgrades()) {
            boolean reached = power >= Ability.upgradeLevel(upgrade);
            lore.add(text((reached ? "✔ " : "✖ ") + upgrade, reached ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        lore.add(plugin.lang().item(chosen ? "menu.ability-chosen" : "menu.ability-pick"));
        ItemStack icon = named(ability.icon(), text(ability.displayName(), chosen ? ability.path().color() : NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, true), lore);
        icon.editMeta(meta -> meta.setEnchantmentGlintOverride(chosen));
        customIcon(icon, player, "ability/" + ability.id());
        return icon;
    }

    private ItemStack talentButton(Player player, Path path, int tier, Path.Talent talent, int power) {
        boolean reached = power >= tier;
        boolean chosen = plugin.talent(player, path, tier) == talent;
        List<Component> lore = new ArrayList<>();
        lore.addAll(wrap(talent.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (!plugin.talentEnabled(talent)) {
            lore.add(plugin.lang().item("menu.talent-off"));
        } else if (!reached) {
            lore.add(plugin.lang().item("menu.talent-locked", "level", tier));
        } else if (chosen) {
            lore.add(plugin.lang().item("menu.talent-chosen"));
        } else {
            lore.add(plugin.lang().item("menu.talent-pick"));
        }
        Material material = !reached ? Material.GRAY_DYE : chosen ? Material.NETHER_STAR : Material.GLOWSTONE_DUST;
        ItemStack icon = named(material, text("Lvl " + tier + ": " + talent.displayName(),
                chosen ? NamedTextColor.LIGHT_PURPLE : reached ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY), lore);
        icon.editMeta(meta -> meta.setEnchantmentGlintOverride(chosen));
        if (reached) {
            customIcon(icon, player, "talent/" + talent.name().toLowerCase(Locale.ROOT));
        }
        return icon;
    }

    private Component xpBar(Player player, Path path) {
        double[] progress = plugin.progress(player, path);
        if (progress[1] <= 0) {
            return plugin.lang().item("menu.max-level");
        }
        int filled = (int) Math.round(10 * progress[0] / progress[1]);
        return text("■".repeat(filled), NamedTextColor.GREEN).append(text("■".repeat(10 - filled), NamedTextColor.DARK_GRAY))
                .append(text("  " + (int) progress[0] + "/" + (int) progress[1] + " XP", NamedTextColor.DARK_GRAY));
    }

    // --- Clicks ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= SIZE) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.path == null) {
            List<Path> paths = shown(player);
            for (int i = 0; i < paths.size() && i < PATH_SLOTS.length; i++) {
                if (PATH_SLOTS[i] == slot) {
                    holder.path = paths.get(i);
                    click(player);
                    draw(holder, player);
                }
            }
            return;
        }
        Path path = holder.path;
        if (slot == BACK_SLOT) {
            holder.path = null;
            click(player);
        } else if (slot == TOGGLE_SLOT) {
            toggle(player, path);
        } else {
            clickChoice(player, path, slot);
        }
        draw(holder, player);
    }

    private void clickChoice(Player player, Path path, int slot) {
        for (Path.Slot pathSlot : path.slots()) {
            int[] row = SLOT_ROWS[pathSlot.ordinal()];
            for (int i = 1; i < row.length; i++) {
                if (row[i] != slot) {
                    continue;
                }
                List<Ability> options = path.abilities(pathSlot);
                if (i - 1 >= options.size() || plugin.ability(player, path, pathSlot) == options.get(i - 1) || !outOfCombat(player)) {
                    return;
                }
                plugin.choose(player, options.get(i - 1));
                plugin.effects().refresh(player);
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.2f);
                player.sendActionBar(plugin.lang().get("chose-ability", "slot", pathSlot.label(), "ability", Component.text(options.get(i - 1).displayName(), path.color())));
                return;
            }
        }
        for (int i = 0; i < Path.TALENT_TIERS.length; i++) {
            if (slot != TALENT_A[i] && slot != TALENT_B[i]) {
                continue;
            }
            int tier = Path.TALENT_TIERS[i];
            Path.Talent talent = path.talents(tier)[slot == TALENT_A[i] ? 0 : 1];
            if (plugin.level(player, path) < tier) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                player.sendActionBar(plugin.lang().get("talent-locked", "level", tier));
                return;
            }
            if (plugin.talent(player, path, tier) == talent || !plugin.talentEnabled(talent) || !outOfCombat(player)) {
                return;
            }
            plugin.chooseTalent(player, path, tier, talent);
            plugin.effects().refresh(player);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
            player.sendActionBar(plugin.lang().get("chose-talent", "talent", talent.displayName()));
            return;
        }
    }

    private boolean outOfCombat(Player player) {
        if (plugin.effects().inCombat(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            player.sendActionBar(plugin.lang().get("in-combat", "seconds", plugin.getConfig().getLong("combat-lock-seconds", 10)));
            return false;
        }
        return true;
    }

    /** Choosing is free while a slot is open; dropping a path starts the switch cooldown. */
    private void toggle(Player player, Path path) {
        // Picked paths, minus ones an admin turned off (those free their slot).
        Set<Path> active = plugin.storedActive(player);
        active.removeIf(p -> !plugin.enabled(p));
        if (active.contains(path)) {
            long cooldown = plugin.switchCooldownLeft(player);
            if (cooldown > 0) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                player.sendMessage(plugin.lang().get("drop-cooldown", "time", minutes(cooldown)));
                return;
            }
            active.remove(path);
            plugin.setActive(player, active);
            plugin.markDropped(player);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 0.8f);
            player.sendMessage(plugin.lang().get("dropped", "path", path.displayName()));
        } else {
            if (!plugin.available(player, path)) {
                return;
            }
            if (active.size() >= plugin.maxActive()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                player.sendMessage(plugin.lang().get("too-many", "max", plugin.maxActive()));
                return;
            }
            Path clash = plugin.activeFor(player, path.weapon());
            if (clash != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                player.sendMessage(plugin.lang().get("weapon-clash", "other", clash.displayName(), "path", path.displayName()));
                return;
            }
            active.add(path);
            plugin.setActive(player, active);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 1.2f);
            player.sendMessage(plugin.lang().get("picked", "path", Component.text(path.displayName(), path.color(), TextDecoration.BOLD)));
        }
        plugin.effects().refresh(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    // --- Helpers ---

    private static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private String minutes(long millis) {
        return plugin.lang().count("unit.minute", (millis + 59_999) / 60_000);
    }

    /** Paths on the main page: enabled ones the player has permission for. */
    private List<Path> shown(Player player) {
        return java.util.Arrays.stream(Path.values()).filter(p -> plugin.available(player, p)).toList();
    }

    /** Splits a long description into lore lines of about 40 characters. */
    private static List<Component> wrap(String content, TextColor color) {
        List<Component> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : content.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() > 40) {
                lines.add(text(line.toString(), color));
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(text(line.toString(), color));
        }
        return lines;
    }

    static Component text(String content, TextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Set by the Hud plugin while the player has the server resource pack loaded. */
    private static final NamespacedKey HAS_PACK = new NamespacedKey("hud", "pack");

    /** Swaps in the custom icon from the resource pack, for players who have it (and only if the pack has that icon). */
    private void customIcon(ItemStack item, Player player, String model) {
        if (player.getPersistentDataContainer().has(HAS_PACK, PersistentDataType.BYTE) && PackModels.has(plugin, model)) {
            item.editMeta(meta -> meta.setItemModel(new NamespacedKey("raresb", model)));
        }
    }

    private static ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }
}
