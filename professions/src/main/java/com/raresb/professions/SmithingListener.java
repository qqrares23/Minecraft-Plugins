package com.raresb.professions;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.view.AnvilView;

/**
 * Smithing: XP from anvil work, smithing-table upgrades and crafting gear; better anvil repairs with
 * level; and the Smithing perks.
 */
final class SmithingListener implements Listener {
    private final ProfessionsPlugin plugin;

    SmithingListener(ProfessionsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Extra durability on anvil repairs: 0.5% per level, +25% with Mender's Touch. */
    static double repairBonus(ProfessionsPlugin plugin, Player player) {
        return 0.005 * plugin.level(player, Profession.SMITHING) + (plugin.has(player, Perk.MENDERS_TOUCH) ? 0.25 : 0);
    }

    private void xp(Player player, double amount) {
        plugin.addXp(player, Profession.SMITHING, plugin.has(player, Perk.FORGE_ZEAL) ? amount * 1.2 : amount);
    }

    /** Tools, weapons and armor: what Smithing crafts. */
    static boolean isGear(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE") || name.endsWith("_SPEAR") || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || type == Material.SHIELD || type == Material.BOW
                || type == Material.CROSSBOW || type == Material.MACE || type == Material.FISHING_ROD;
    }

    private static boolean isArmor(Material type) {
        String name = type.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private static double craftXp(Material type) {
        String name = type.name();
        if (name.startsWith("NETHERITE_")) {
            return 20;
        }
        if (name.startsWith("DIAMOND_") || type == Material.MACE) {
            return 15;
        }
        if (name.startsWith("IRON_") || name.startsWith("GOLDEN_") || name.startsWith("COPPER_") || name.startsWith("CHAINMAIL_")
                || type == Material.CROSSBOW || type == Material.SHIELD) {
            return 8;
        }
        return 3; // wood, stone, leather, bows, rods
    }

    // ============================================================ Anvil

    /** Master Smith: lift the server's 40-level limit before the anvil works out its result. */
    @EventHandler
    public void onOpenAnvil(InventoryOpenEvent event) {
        if (event.getView() instanceof AnvilView view && event.getPlayer() instanceof Player player
                && plugin.has(player, Perk.MASTER_SMITH)) {
            view.setMaximumRepairCost(1000);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getResult();
        if (result == null || result.isEmpty()) {
            return;
        }
        AnvilView view = event.getView();
        // Better repairs: the result regains more of the durability the repair restored.
        ItemStack first = event.getInventory().getFirstItem();
        if (first != null && first.getType() == result.getType() && first.getItemMeta() instanceof Damageable before
                && result.getItemMeta() instanceof Damageable after && after.getDamage() < before.getDamage()) {
            int restored = before.getDamage() - after.getDamage();
            int extra = (int) Math.round(restored * repairBonus(plugin, player));
            if (extra > 0) {
                ItemStack better = result.clone();
                better.editMeta(Damageable.class, meta -> meta.setDamage(Math.max(0, meta.getDamage() - extra)));
                event.setResult(better);
            }
        }
        int cost = view.getRepairCost();
        if (plugin.has(player, Perk.ANVIL_MASTER) && cost > 1) {
            cost--;
        }
        if (plugin.has(player, Perk.MASTER_SMITH)) {
            cost = Math.min(cost, 39); // the client shows "Too Expensive" from 40 up
        }
        view.setRepairCost(cost);
    }

    /** XP for taking a result out of the anvil (if the player can pay for it). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilTake(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory) || event.getRawSlot() != 2
                || !(event.getWhoClicked() instanceof Player player) || !(event.getView() instanceof AnvilView view)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        int cost = view.getRepairCost();
        if (result == null || result.isEmpty() || cost <= 0 || (player.getLevel() < cost && !player.getGameMode().name().equals("CREATIVE"))) {
            return;
        }
        xp(player, 6 + 3 * cost);
    }

    // ============================================================ Smithing table

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null || event.getCurrentItem().isEmpty()) {
            return;
        }
        SmithingInventory table = event.getInventory();
        ItemStack template = table.getInputTemplate();
        ItemStack mineral = table.getInputMineral();
        boolean upgrade = template != null && template.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
        xp(player, upgrade ? 40 : 15);
        ItemStack refundTemplate = template != null && plugin.has(player, Perk.TEMPLATE_SAVER) && ProfessionsPlugin.roll(0.2)
                ? template.asOne() : null;
        ItemStack refundIngot = upgrade && mineral != null && mineral.getType() == Material.NETHERITE_INGOT
                && plugin.has(player, Perk.NETHERITE_SAVANT) && ProfessionsPlugin.roll(0.25) ? mineral.asOne() : null;
        for (ItemStack refund : new ItemStack[] {refundTemplate, refundIngot}) {
            if (refund != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(refund).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                    player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.5f);
                    player.sendActionBar(plugin.lang().get("smithing.refund", "item", Component.translatable(refund.getType().translationKey())));
                });
            }
        }
    }

    // ============================================================ Crafting

    /** Tempering: crafted gear shows (and gets) +15% max durability. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !isGear(result.getType()) || !(event.getView().getPlayer() instanceof Player player)
                || !plugin.has(player, Perk.TEMPERING)) {
            return;
        }
        ItemStack tempered = result.clone();
        int max = tempered.getType().getMaxDurability();
        if (max > 0) {
            tempered.editMeta(Damageable.class, meta -> meta.setMaxDamage((int) Math.round(max * 1.15)));
            event.getInventory().setResult(tempered);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null || !isGear(result.getType()) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        xp(player, craftXp(result.getType()));
        // Masterwork (normal clicks only: a shift-click takes the item before it can be changed).
        if (!event.isShiftClick() && plugin.has(player, Perk.MASTERWORK) && ProfessionsPlugin.roll(0.1)) {
            ItemStack masterwork = result.clone();
            masterwork.editMeta(meta -> {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.itemName(plugin.lang().get("smithing.masterwork-name", "item", Component.translatable(result.getType().translationKey()))
                        .decoration(TextDecoration.ITALIC, false));
            });
            event.setCurrentItem(masterwork);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.6f);
            player.getWorld().spawnParticle(Particle.WAX_OFF, player.getLocation().add(0, 1.2, 0), 15, 0.4, 0.4, 0.4, 0.5);
            player.sendActionBar(plugin.lang().get("smithing.masterwork", "item", Component.translatable(result.getType().translationKey())));
        }
    }

    // ============================================================ Wear

    /** Reinforced: armor wears 20% slower. */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (isArmor(event.getItem().getType()) && plugin.has(event.getPlayer(), Perk.REINFORCED) && ProfessionsPlugin.roll(0.2)) {
            event.setCancelled(true);
        }
    }

    /** Field Repair: every 30 s the held tool or weapon regains 1% durability. */
    void fieldRepair() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.has(player, Perk.FIELD_REPAIR)) {
                continue;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.isEmpty() || !(held.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) {
                continue;
            }
            int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : held.getType().getMaxDurability();
            int heal = Math.max(1, (int) Math.ceil(max * 0.01));
            held.editMeta(Damageable.class, meta -> meta.setDamage(Math.max(0, meta.getDamage() - heal)));
            player.getInventory().setItemInMainHand(held);
        }
    }
}
