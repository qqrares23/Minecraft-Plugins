package com.raresb.magicenchants;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.view.AnvilView;

/**
 * Combines two staffs (Blaze Rod + Blaze Rod, Breeze Rod + Breeze Rod) on an anvil. Vanilla only
 * merges two copies of an item that has durability, so it shows no result for rods; here the
 * right rod's enchantments go onto the left one with vanilla's rules: equal levels go up by one
 * (to the max), otherwise the higher level wins; cost = each enchantment's anvil cost × level +
 * both prior-work penalties (+1 for a rename), and the result's prior-work penalty doubles.
 * Runs at LOW so Professions' anvil perks (discounts, Master Smith cap) still apply on top.
 */
final class StaffAnvil implements Listener {
    private final MagicEnchantsPlugin plugin;

    StaffAnvil(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPrepare(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getFirstItem();
        ItemStack right = event.getInventory().getSecondItem();
        if (left == null || right == null || left.getType() != right.getType()
                || (left.getType() != Material.BLAZE_ROD && left.getType() != Material.BREEZE_ROD)
                || left.getAmount() != 1 || right.getAmount() != 1 || right.getEnchantments().isEmpty()
                || !plugin.enabledIn(event.getView().getPlayer().getWorld())) {
            return;
        }
        AnvilView view = event.getView();
        ItemStack result = left.clone();
        int cost = priorWork(left) + priorWork(right);
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : right.getEnchantments().entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!enchantment.canEnchantItem(left)) {
                cost++; // vanilla charges 1 for an enchantment that can't go on
                continue;
            }
            int had = result.getEnchantmentLevel(enchantment);
            int level = had == entry.getValue() ? had + 1 : Math.max(had, entry.getValue());
            level = Math.min(level, enchantment.getMaxLevel());
            if (level != had) {
                result.addUnsafeEnchantment(enchantment, level);
                changed = true;
            }
            cost += Math.max(1, enchantment.getAnvilCost()) * level;
        }
        if (!changed) {
            return;
        }
        String rename = view.getRenameText();
        if (rename != null && !rename.isBlank()) {
            result.editMeta(meta -> meta.displayName(net.kyori.adventure.text.Component.text(rename)));
            cost++;
        }
        int penalty = Math.max(priorWork(left), priorWork(right)) * 2 + 1;
        result.editMeta(Repairable.class, meta -> meta.setRepairCost(penalty));
        event.setResult(result);
        view.setRepairCost(Math.max(1, cost));
    }

    private static int priorWork(ItemStack item) {
        return item.getItemMeta() instanceof Repairable repairable ? repairable.getRepairCost() : 0;
    }
}
