package com.raresb.magicenchants;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.util.Vector;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.util.RayTraceResult;

/** Tool enchantments: Auto-Smelt, Magnet, Excavator, the fishing rod ones and the hoe ones. */
final class ToolEffects implements Listener {
    private final MagicEnchantsPlugin plugin;
    /** Smelting results by input item, looked up from the furnace recipes. */
    private final Map<Material, Optional<ItemStack>> smelted = new HashMap<>();
    /** What Auto-Smelt smelts: ore drops only (1.9.2) - not logs, sand, stone or crops. */
    private static final java.util.Set<Material> SMELTABLE = java.util.EnumSet.of(
            Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER, Material.ANCIENT_DEBRIS);
    /** Set while Excavator breaks the extra blocks, so they don't start their own 3x3. */
    private boolean excavating;

    /** Crops Replenish replants and Green Thumb grows, with the item that plants them. */
    private static final Map<Material, Material> CROPS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.NETHER_WART, Material.NETHER_WART,
            Material.MELON_STEM, Material.MELON_SEEDS, Material.PUMPKIN_STEM, Material.PUMPKIN_SEEDS);
    private static final NamespacedKey FISHING_TREASURE = NamespacedKey.minecraft("gameplay/fishing/treasure");

    ToolEffects(MagicEnchantsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickGreenThumb, 40, 40);
    }

    // --- Fishing rods ---

    private static ItemStack rod(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return main.getType() == Material.FISHING_ROD ? main : player.getInventory().getItemInOffHand();
    }

    /** Treasure Hunter swaps a catch for treasure now and then; Snagging yanks hooked mobs. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!plugin.enabledIn(player.getWorld())) {
            return;
        }
        ItemStack rod = rod(player);
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item caught) {
            int hunter = plugin.level(rod, MagicEnchant.TREASURE_HUNTER);
            LootTable table = Bukkit.getLootTable(FISHING_TREASURE);
            if (hunter > 0 && table != null && ThreadLocalRandom.current().nextDouble() < 0.04 * hunter) {
                LootContext context = new LootContext.Builder(caught.getLocation()).luck(hunter).killer(player).build();
                table.populateLoot(ThreadLocalRandom.current(), context).stream().filter(item -> !item.isEmpty()).findFirst().ifPresent(treasure -> {
                    caught.setItemStack(treasure);
                    player.sendActionBar(plugin.lang().get("treasure"));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
                    caught.getWorld().spawnParticle(Particle.WAX_OFF, caught.getLocation(), 20, 0.3, 0.3, 0.3, 0.5);
                });
            }
        } else if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() instanceof LivingEntity hooked
                && !(hooked instanceof Player)) {
            int snag = plugin.level(rod, MagicEnchant.SNAGGING);
            if (snag > 0) {
                // A tick later, so vanilla's own weak pull doesn't overwrite it.
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Vector pull = player.getLocation().toVector().subtract(hooked.getLocation().toVector());
                    double distance = pull.length();
                    if (distance > 0.5 && hooked.isValid()) {
                        hooked.setVelocity(pull.normalize().multiply(Math.min(2.0, 0.3 + distance * 0.08 * (1 + snag))).setY(0.35 + 0.1 * snag));
                        hooked.getWorld().playSound(hooked.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
                    }
                });
            }
        }
    }

    // --- Hoes ---

    /** Replenish: a ripe crop harvested with the hoe is planted again, using one seed from its drops. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHarvest(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Material crop = event.getBlockState().getType();
        Material seed = CROPS.get(crop);
        if (seed == null || crop == Material.MELON_STEM || crop == Material.PUMPKIN_STEM || !plugin.enabledIn(player.getWorld())
                || !(event.getBlockState().getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()
                || plugin.level(player.getInventory().getItemInMainHand(), MagicEnchant.REPLENISH) <= 0) {
            return;
        }
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack.getType() == seed) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                    item.setItemStack(stack);
                } else {
                    event.getItems().remove(item);
                }
                Block block = event.getBlock();
                if (block.getType().isAir()) {
                    block.setType(crop);
                    block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.3, 0.5), 4, 0.25, 0.1, 0.25, 0);
                }
                return;
            }
        }
    }

    /** Green Thumb: crops around a player holding the hoe grow a step now and then. */
    private void tickGreenThumb() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            int level = plugin.level(player.getInventory().getItemInMainHand(), MagicEnchant.GREEN_THUMB);
            if (level <= 0 || !plugin.enabledIn(player.getWorld())) {
                continue;
            }
            Block center = player.getLocation().getBlock();
            // A few random spots instead of scanning the whole area: cheap, and growth stays gradual.
            for (int i = 0; i < 6 * level; i++) {
                Block block = center.getRelative(random.nextInt(-4, 5), random.nextInt(-2, 2), random.nextInt(-4, 5));
                if (CROPS.containsKey(block.getType()) && block.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
                    age.setAge(age.getAge() + 1);
                    block.setBlockData(age);
                    block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 3, 0.25, 0.2, 0.25, 0);
                }
            }
        }
    }

    private Optional<ItemStack> smeltResult(ItemStack input) {
        return smelted.computeIfAbsent(input.getType(), type -> {
            Iterator<Recipe> recipes = Bukkit.recipeIterator();
            while (recipes.hasNext()) {
                if (recipes.next() instanceof FurnaceRecipe furnace && furnace.getInputChoice().test(new ItemStack(type))) {
                    return Optional.of(furnace.getResult());
                }
            }
            return Optional.empty();
        });
    }

    /** Auto-Smelt and Magnet act on the items a block drops. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        if (!plugin.enabledIn(event.getBlock().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        // Silk Touch wins over Auto-Smelt (they can be on the same tool since 1.8.0).
        boolean smelt = plugin.level(tool, MagicEnchant.AUTO_SMELT) > 0 && !tool.containsEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH);
        boolean magnet = plugin.level(tool, MagicEnchant.MAGNET) > 0;
        if (!smelt && !magnet) {
            return;
        }
        boolean smeltedAny = false;
        Iterator<Item> items = event.getItems().iterator();
        while (items.hasNext()) {
            Item item = items.next();
            ItemStack stack = item.getItemStack();
            if (smelt && SMELTABLE.contains(stack.getType())) {
                Optional<ItemStack> result = smeltResult(stack);
                if (result.isPresent()) {
                    ItemStack output = result.get().clone();
                    output.setAmount(Math.min(output.getMaxStackSize(), output.getAmount() * stack.getAmount()));
                    stack = output;
                    item.setItemStack(stack);
                    smeltedAny = true;
                }
            }
            if (magnet) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                if (leftover.isEmpty()) {
                    items.remove();
                } else {
                    item.setItemStack(leftover.values().iterator().next());
                }
            }
        }
        if (smeltedAny) {
            Block block = event.getBlock();
            block.getWorld().spawnParticle(Particle.FLAME, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.02);
            // true = Mending gets it first, like an XP orb (plain giveExp skips Mending).
            player.giveExp(1, true);
        }
        if (magnet) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.4f);
        }
    }

    /** Excavator: break the 3x3 around the mined block, facing the side you mined from. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.enabledIn(event.getBlock().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        if (excavating || player.isSneaking() || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (plugin.level(tool, MagicEnchant.EXCAVATOR) <= 0) {
            return;
        }
        Block origin = event.getBlock();
        RayTraceResult aim = player.rayTraceBlocks(6);
        // Only the block the player is mining themselves: blocks broken for them by skills (Tunnel Bore, Shatter,
        // Burrow...), VeinMiner or Timberella also fire BlockBreakEvent and must not each start a 3x3 (1.9.2).
        if (aim == null || !origin.equals(aim.getHitBlock())) {
            return;
        }
        BlockFace face = aim != null && aim.getHitBlockFace() != null ? aim.getHitBlockFace() : BlockFace.UP;
        float originHardness = origin.getType().getHardness();

        excavating = true;
        try {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }
                    Block other = switch (face) {
                        case UP, DOWN -> origin.getRelative(a, 0, b);
                        case NORTH, SOUTH -> origin.getRelative(a, b, 0);
                        default -> origin.getRelative(0, a, b);
                    };
                    float hardness = other.getType().getHardness();
                    if (other.getType().isAir() || other.isLiquid() || hardness < 0 || hardness > originHardness + 1.5f
                            || other.getState() instanceof TileState || !other.isPreferredTool(tool)) {
                        continue;
                    }
                    player.breakBlock(other);
                    if (player.getInventory().getItemInMainHand().isEmpty()) {
                        return; // the tool broke
                    }
                }
            }
        } finally {
            excavating = false;
        }
    }
}
