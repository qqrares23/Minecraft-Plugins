package com.raresb.magicenchants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

/** The /enchants menu: a page of categories, then every enchantment of the chosen category. */
final class EnchantMenu implements Listener {
    /** The category buttons: two rows of five and one in the middle below. */
    private static final int[] CATEGORY_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 40};
    private static final int BACK_SLOT = 49;
    /** Four rows of seven, with a border around them. */
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    /** Set by the Hud plugin while the player has the server resource pack loaded. */
    private static final NamespacedKey HAS_PACK = new NamespacedKey("hud", "pack");

    private final MagicEnchantsPlugin plugin;

    EnchantMenu(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        /** null = the category page. */
        private MagicEnchant.Category category;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** The first page: one button per category. */
    void open(Player player) {
        var lang = plugin.lang();
        Holder holder = page(player, lang.get("menu.title"), null);
        holder.inventory.setItem(4, named(Material.ENCHANTING_TABLE, lang.item("menu.about.name"), lang.lore("menu.about.lore")));
        boolean hasPack = hasPack(player);
        MagicEnchant.Category[] categories = MagicEnchant.Category.values();
        for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
            MagicEnchant.Category category = categories[i];
            List<MagicEnchant> enchants = enchants(category);
            List<Component> lore = new ArrayList<>(lang.lore("category." + id(category) + ".lore"));
            lore.add(Component.empty());
            lore.add(lang.item(enchants.size() == 1 ? "menu.count-one" : "menu.count", "count", enchants.size()));
            ItemStack button = named(category.icon(), lang.item("menu.category-button", "category", lang.raw("category." + id(category) + ".name")), lore);
            if (hasPack && !enchants.isEmpty() && inPack(enchants.get(0))) {
                // The icon of the category's first enchantment.
                button.editMeta(meta -> meta.setItemModel(model(enchants.get(0))));
            }
            holder.inventory.setItem(CATEGORY_SLOTS[i], button);
        }
        player.openInventory(holder.inventory);
    }

    /** The enchantments of one category. */
    void open(Player player, MagicEnchant.Category category) {
        var lang = plugin.lang();
        Holder holder = page(player, lang.get("menu.category-title", "category", lang.raw("category." + id(category) + ".name")), category);
        boolean hasPack = hasPack(player);
        List<MagicEnchant> enchants = enchants(category);
        for (int i = 0; i < enchants.size() && i < SLOTS.length; i++) {
            holder.inventory.setItem(SLOTS[i], icon(enchants.get(i), hasPack));
        }
        holder.inventory.setItem(BACK_SLOT, named(Material.ARROW, lang.item("menu.back"), List.of()));
        player.openInventory(holder.inventory);
    }

    private Holder page(Player player, Component title, MagicEnchant.Category category) {
        Holder holder = new Holder();
        holder.category = category;
        holder.inventory = player.getServer().createInventory(holder, 54, title);
        ItemStack pane = named(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 54; i++) {
            holder.inventory.setItem(i, pane);
        }
        return holder;
    }

    private List<MagicEnchant> enchants(MagicEnchant.Category category) {
        return java.util.Arrays.stream(MagicEnchant.values())
                .filter(e -> e.category() == category && plugin.settings().enabled(e)).toList();
    }

    private static boolean hasPack(Player player) {
        return player.getPersistentDataContainer().has(HAS_PACK, PersistentDataType.BYTE);
    }

    private static NamespacedKey model(MagicEnchant enchant) {
        return new NamespacedKey("raresb", "enchant/" + enchant.name().toLowerCase(Locale.ROOT));
    }

    /** Whether the resource pack in plugins/Hud/pack.zip (the one players get) has this enchantment's icon. */
    private boolean inPack(MagicEnchant enchant) {
        return PackModels.has(plugin, "enchant/" + enchant.name().toLowerCase(Locale.ROOT));
    }

    static String id(MagicEnchant.Category category) {
        return category.name().toLowerCase(Locale.ROOT);
    }

    private ItemStack icon(MagicEnchant enchant, boolean hasPack) {
        var lang = plugin.lang();
        int maxLevel = plugin.settings().maxLevel(enchant);
        int weight = plugin.settings().weight(enchant);
        List<Component> lore = new ArrayList<>();
        lore.add(lang.item("menu.goes-on", "target", plugin.targetLabel(enchant.target())));
        lore.add(lang.item("menu.levels", "levels", maxLevel == 1 ? "I" : "I - " + roman(maxLevel)));
        lore.add(lang.item("menu.rarity", "rarity", lang.get(enchant.treasure() ? "menu.treasure"
                : weight >= 5 ? "menu.common" : weight >= 2 ? "menu.rare" : "menu.very-rare")));
        lore.add(Component.empty());
        for (String line : wrap(lang.plain("enchant." + EnchantSettings.id(enchant)), 38)) {
            lore.add(text(line, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.addAll(howToGet(enchant));
        ItemStack icon = named(Material.ENCHANTED_BOOK, text(enchant.displayName(), enchant.color()).decoration(TextDecoration.BOLD, true), lore);
        if (hasPack && inPack(enchant)) {
            // The custom icon from the resource pack (only if the pack players get has it: else a missing texture).
            icon.editMeta(meta -> {
                meta.setItemModel(model(enchant));
                meta.setEnchantmentGlintOverride(false);
            });
        }
        return icon;
    }

    /** Items the enchanting table can't enchant: their enchantments only go on from a book, on an anvil. */
    private static boolean bookOnly(MagicEnchant.Target target) {
        return switch (target) {
            case HORSE_ARMOR, WOLF_ARMOR, SHIELD, ELYTRA, SHEARS -> true;
            default -> false;
        };
    }

    /** Where the enchantment can be found, from its config (enchanting-table, loot, trades) and the treasure flag. */
    private List<Component> howToGet(MagicEnchant enchant) {
        var lang = plugin.lang();
        var settings = plugin.settings();
        List<Component> lines = new ArrayList<>();
        lines.add(lang.item("menu.obtain"));
        boolean table = settings.enchantingTable(enchant);
        if (table) {
            lines.add(lang.item(bookOnly(enchant.target()) ? "menu.obtain-table-books" : "menu.obtain-table"));
            if (plugin.getServer().getPluginManager().getPlugin("MightyMobs") != null) {
                lines.add(lang.item("menu.obtain-mobs")); // MightyMobs rare books roll enchanting-table enchantments
            }
        }
        if (settings.loot(enchant)) {
            lines.add(lang.item("menu.obtain-loot"));
        }
        if (settings.trades(enchant)) {
            lines.add(lang.item("menu.obtain-trades"));
        }
        if (lines.size() == 1) {
            lines.add(lang.item("menu.obtain-none"));
        }
        lines.add(lang.item(bookOnly(enchant.target()) ? "menu.obtain-anvil-only" : "menu.obtain-anvil"));
        return lines;
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() > 0 ? " " : "").append(word);
        }
        lines.add(line.toString());
        return lines;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != holder.inventory) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.category == null) {
            for (int i = 0; i < CATEGORY_SLOTS.length && i < MagicEnchant.Category.values().length; i++) {
                if (CATEGORY_SLOTS[i] == slot) {
                    MagicEnchant.Category category = MagicEnchant.Category.values()[i];
                    // Open the next page after this click is handled.
                    plugin.getServer().getScheduler().runTask(plugin, () -> open(player, category));
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                }
            }
        } else if (slot == BACK_SLOT) {
            plugin.getServer().getScheduler().runTask(plugin, () -> open(player));
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private static Component text(String content, TextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
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
