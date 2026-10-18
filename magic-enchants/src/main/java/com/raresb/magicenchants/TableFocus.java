package com.raresb.magicenchants;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.persistence.PersistentDataType;

/**
 * The enchanting table's <b>focus</b>: with 90 magic enchantments next to the vanilla ones, a table
 * roll rarely gives what the player wants. Shift + right-click on a table opens a picker (All,
 * Vanilla only, Magic only, or one /enchants category); the table then rolls only from that group.
 * The choice is kept in the player's PDC ({@code magicenchants:table_focus}) until changed.
 *
 * <p>Also lets the table enchant the path staffs (one Blaze Rod or Breeze Rod): vanilla doesn't
 * count them as enchantable and offers nothing, but Paper still fires the (cancelled) prepare
 * event, so offers are filled in here. Every roll is vanilla-style and depends only on the player's
 * enchantment seed, the button, its cost and the focus, so the click gives exactly what was shown.
 */
final class TableFocus implements Listener {
    /** How well staffs take enchantments (vanilla scale: iron 9-14, gold 22-25). */
    private static final int STAFF_ENCHANTABILITY = 15;
    /** Category buttons: two rows of five and one in the middle below (like /enchants). */
    private static final int[] CATEGORY_SLOTS = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42, 49};
    private static final int ALL_SLOT = 11;
    private static final int VANILLA_SLOT = 13;
    private static final int MAGIC_SLOT = 15;

    private final MagicEnchantsPlugin plugin;
    private final NamespacedKey focusKey;
    /** Enchantments the table can roll (the in_enchanting_table tag, as the bootstrapper built it). */
    private final Set<Enchantment> tableEnchantments;

    TableFocus(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
        this.focusKey = new NamespacedKey(plugin, "table_focus");
        this.tableEnchantments = Set.copyOf(RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .getTagValues(EnchantmentTagKeys.IN_ENCHANTING_TABLE));
    }

    // ============================================================ The focus

    /** "all", "vanilla", "magic" or a category id. */
    private String focus(Player player) {
        String focus = player.getPersistentDataContainer().get(focusKey, PersistentDataType.STRING);
        if (focus == null || (!focus.equals("vanilla") && !focus.equals("magic") && category(focus) == null)) {
            return "all";
        }
        return focus;
    }

    private static MagicEnchant.Category category(String focus) {
        for (MagicEnchant.Category category : MagicEnchant.Category.values()) {
            if (EnchantMenu.id(category).equals(focus)) {
                return category;
            }
        }
        return null;
    }

    private String focusName(String focus) {
        return switch (focus) {
            case "all", "vanilla", "magic" -> plugin.lang().raw("focus." + focus + ".name");
            default -> plugin.lang().raw("category." + focus + ".name");
        };
    }

    private boolean inFocus(Enchantment enchantment, String focus) {
        boolean magic = enchantment.getKey().getNamespace().equals(MagicEnchant.NAMESPACE);
        return switch (focus) {
            case "all" -> true;
            case "vanilla" -> !magic;
            case "magic" -> magic;
            default -> {
                MagicEnchant enchant = MagicEnchant.of(enchantment);
                yield enchant != null && EnchantMenu.id(enchant.category()).equals(focus);
            }
        };
    }

    // ============================================================ The picker

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private Location table;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTableClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null
                || block.getType() != Material.ENCHANTING_TABLE || !event.getPlayer().isSneaking()
                || !plugin.enabledIn(block.getWorld())) {
            return;
        }
        event.setCancelled(true);
        openPicker(event.getPlayer(), block.getLocation());
    }

    private void openPicker(Player player, Location table) {
        var lang = plugin.lang();
        Holder holder = new Holder();
        holder.table = table;
        holder.inventory = player.getServer().createInventory(holder, 54, lang.get("focus.title"));
        ItemStack pane = named(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of(), false);
        for (int i = 0; i < 54; i++) {
            holder.inventory.setItem(i, pane);
        }
        String current = focus(player);
        holder.inventory.setItem(4, named(Material.ENCHANTING_TABLE, lang.item("focus.about.name"), lang.lore("focus.about.lore"), false));
        holder.inventory.setItem(ALL_SLOT, button(Material.ENCHANTING_TABLE, "all", lang.lore("focus.all.lore"), current));
        holder.inventory.setItem(VANILLA_SLOT, button(Material.ENCHANTED_BOOK, "vanilla", lang.lore("focus.vanilla.lore"), current));
        holder.inventory.setItem(MAGIC_SLOT, button(Material.NETHER_STAR, "magic", lang.lore("focus.magic.lore"), current));
        MagicEnchant.Category[] categories = MagicEnchant.Category.values();
        for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
            String id = EnchantMenu.id(categories[i]);
            holder.inventory.setItem(CATEGORY_SLOTS[i], button(categories[i].icon(), id, lang.lore("category." + id + ".lore"), current));
        }
        player.openInventory(holder.inventory);
    }

    private ItemStack button(Material material, String focus, List<Component> about, String current) {
        var lang = plugin.lang();
        List<Component> lore = new ArrayList<>(about);
        lore.add(Component.empty());
        lore.add(lang.item(focus.equals(current) ? "focus.selected" : "focus.pick"));
        return named(material, lang.item("focus.button", "focus", focusName(focus)), lore, focus.equals(current));
    }

    @EventHandler
    public void onPickerClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        String focus = null;
        if (slot == ALL_SLOT) {
            focus = "all";
        } else if (slot == VANILLA_SLOT) {
            focus = "vanilla";
        } else if (slot == MAGIC_SLOT) {
            focus = "magic";
        } else {
            MagicEnchant.Category[] categories = MagicEnchant.Category.values();
            for (int i = 0; i < categories.length && i < CATEGORY_SLOTS.length; i++) {
                if (CATEGORY_SLOTS[i] == slot) {
                    focus = EnchantMenu.id(categories[i]);
                }
            }
        }
        if (focus == null) {
            return;
        }
        player.getPersistentDataContainer().set(focusKey, PersistentDataType.STRING, focus);
        Location table = holder.table;
        // Open the real table next tick (opening an inventory from a click handler of another is unsafe).
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            if (table.getBlock().getType() == Material.ENCHANTING_TABLE && player.getWorld().equals(table.getWorld())
                    && player.getLocation().distanceSquared(table) <= 64) {
                player.openEnchanting(table, false);
            }
        });
    }

    @EventHandler
    public void onPickerDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    /** Opening a table reminds the player of the focus and how to change it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTableOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof EnchantingInventory && event.getPlayer() instanceof Player player) {
            player.sendActionBar(plugin.lang().get("focus.actionbar", "focus", focusName(focus(player))));
        }
    }

    // ============================================================ The table

    private static boolean staff(ItemStack item) {
        return item != null && item.getAmount() == 1 && item.getEnchantments().isEmpty()
                && (item.getType() == Material.BLAZE_ROD || item.getType() == Material.BREEZE_ROD);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();
        String focus = focus(player);
        boolean staff = staff(item);
        if ((!staff && (event.isCancelled() || focus.equals("all"))) || !plugin.enabledIn(event.getEnchantBlock().getWorld())) {
            return; // vanilla's own offers (or nothing, for items the table can't take)
        }
        if (candidates(item, focus).isEmpty()) {
            for (int slot = 0; slot < 3; slot++) {
                event.getOffers()[slot] = null;
            }
            event.setCancelled(true);
            player.sendActionBar(plugin.lang().get("focus.none", "focus", focusName(focus)));
            return;
        }
        int seed = player.getEnchantmentSeed();
        int[] costs = event.getExpLevelCostsOffered().clone();
        if (staff) {
            // Vanilla's three costs for this bookshelf count (vanilla computed none: the rod isn't enchantable).
            int bonus = Math.min(event.getEnchantmentBonus(), 15);
            Random random = new Random(seed);
            int base = random.nextInt(8) + 1 + (bonus >> 1) + random.nextInt(bonus + 1);
            costs = new int[] {Math.max(base / 3, 1), base * 2 / 3 + 1, Math.max(base, bonus * 2)};
        }
        EnchantmentOffer[] offers = event.getOffers();
        for (int slot = 0; slot < 3; slot++) {
            Map<Enchantment, Integer> roll = costs[slot] > 0 ? roll(item, focus, seed, slot, costs[slot]) : Map.of();
            if (roll.isEmpty()) {
                offers[slot] = null;
                continue;
            }
            Map.Entry<Enchantment, Integer> hint = roll.entrySet().iterator().next();
            offers[slot] = new EnchantmentOffer(hint.getKey(), hint.getValue(), costs[slot]);
        }
        event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        String focus = focus(event.getEnchanter());
        if (!staff(item) && focus.equals("all")) {
            return;
        }
        Map<Enchantment, Integer> roll = roll(item, focus, event.getEnchanter().getEnchantmentSeed(), event.whichButton(), event.getExpLevelCost());
        if (roll.isEmpty()) {
            event.setCancelled(true);
            return;
        }
        event.getEnchantsToAdd().clear();
        event.getEnchantsToAdd().putAll(roll);
    }

    /** What the table can put on this item within the focus (books take anything, like vanilla). */
    private List<Enchantment> candidates(ItemStack item, String focus) {
        boolean book = item.getType() == Material.BOOK;
        TypedKey<ItemType> type = TypedKey.create(RegistryKey.ITEM, item.getType().getKey());
        List<Enchantment> list = new ArrayList<>();
        for (Enchantment enchantment : tableEnchantments) {
            MagicEnchant magic = MagicEnchant.of(enchantment);
            if (magic != null && !plugin.settings().enchantingTable(magic)) {
                continue;
            }
            RegistryKeySet<ItemType> primary = enchantment.getPrimaryItems();
            boolean fits = book || (primary != null ? primary.contains(type) : enchantment.canEnchantItem(item));
            if (fits && inFocus(enchantment, focus)) {
                list.add(enchantment);
            }
        }
        // Registry order isn't guaranteed; sort so the seeded roll is the same on prepare and on click.
        list.sort((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()));
        return list;
    }

    /** Vanilla's enchanting-table selection over the candidates. The first entry is the table's hint. */
    private Map<Enchantment, Integer> roll(ItemStack item, String focus, int seed, int slot, int cost) {
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        List<Enchantment> pool = candidates(item, focus);
        if (pool.isEmpty() || cost <= 0) {
            return result;
        }
        int enchantability = item.hasData(DataComponentTypes.ENCHANTABLE)
                ? item.getData(DataComponentTypes.ENCHANTABLE).value() : STAFF_ENCHANTABILITY;
        Random random = new Random(seed + slot);
        int power = cost + 1 + random.nextInt(enchantability / 4 + 1) + random.nextInt(enchantability / 4 + 1);
        float spread = (random.nextFloat() + random.nextFloat() - 1) * 0.15f;
        power = Math.max(1, Math.round(power + power * spread));
        while (!pool.isEmpty()) {
            Map<Enchantment, Integer> available = new LinkedHashMap<>();
            for (Enchantment enchantment : pool) {
                for (int level = enchantment.getMaxLevel(); level >= 1; level--) {
                    if (power >= enchantment.getMinModifiedCost(level) && power <= enchantment.getMaxModifiedCost(level)) {
                        available.put(enchantment, level);
                        break;
                    }
                }
            }
            if (available.isEmpty()) {
                break;
            }
            Enchantment picked = weighted(available.keySet(), random);
            result.put(picked, available.get(picked));
            pool.remove(picked);
            if (random.nextInt(50) > power) {
                break;
            }
            power /= 2;
        }
        if (result.isEmpty()) {
            // Too cheap for any of them: the cheapest one at level I, so every offer gives something.
            Enchantment cheapest = pool.stream().min((a, b) -> Integer.compare(a.getMinModifiedCost(1), b.getMinModifiedCost(1))).orElseThrow();
            result.put(cheapest, 1);
        }
        if (item.getType() == Material.BOOK && result.size() > 1) {
            // Like vanilla: a book loses one of its rolled enchantments (never the hint).
            List<Enchantment> rest = new ArrayList<>(result.keySet()).subList(1, result.size());
            result.remove(rest.get(random.nextInt(rest.size())));
        }
        return result;
    }

    private static Enchantment weighted(Set<Enchantment> options, Random random) {
        int total = options.stream().mapToInt(e -> Math.max(1, e.getWeight())).sum();
        int pick = random.nextInt(total);
        Enchantment last = null;
        for (Enchantment enchantment : options) {
            last = enchantment;
            pick -= Math.max(1, enchantment.getWeight());
            if (pick < 0) {
                return enchantment;
            }
        }
        return last;
    }

    private static ItemStack named(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            meta.setEnchantmentGlintOverride(glow);
        });
        return item;
    }
}
