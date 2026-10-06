package com.raresb.professions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Gives profession XP for work and applies the perks. */
final class GatherListener implements Listener {
    private final ProfessionsPlugin plugin;
    /** Who last opened each brewing stand (brewing itself has no player). */
    private final Map<Location, UUID> brewers = new HashMap<>();

    GatherListener(ProfessionsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------ Block categories

    private static boolean isLog(Material type) {
        return Tag.LOGS.isTagged(type) && !type.name().startsWith("STRIPPED_");
    }

    private static boolean isOre(Material type) {
        return type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
    }

    private static boolean isStone(Material type) {
        return switch (type) {
            case STONE, DEEPSLATE, GRANITE, DIORITE, ANDESITE, TUFF, CALCITE, NETHERRACK, BLACKSTONE, BASALT, SMOOTH_BASALT,
                 END_STONE, SANDSTONE, RED_SANDSTONE, DRIPSTONE_BLOCK, MAGMA_BLOCK, OBSIDIAN, AMETHYST_CLUSTER, TERRACOTTA -> true;
            default -> false;
        };
    }

    private static boolean isDiggable(Material type) {
        return switch (type) {
            case DIRT, GRASS_BLOCK, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM, SAND, RED_SAND, GRAVEL, CLAY, SOUL_SAND, SOUL_SOIL,
                 MUD, SNOW_BLOCK, SNOW, SUSPICIOUS_SAND, SUSPICIOUS_GRAVEL -> true;
            default -> false;
        };
    }

    private static boolean isForage(Material type) {
        if (Tag.LEAVES.isTagged(type) || Tag.FLOWERS.isTagged(type)) {
            return true;
        }
        return switch (type) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, VINE, GLOW_LICHEN, RED_MUSHROOM, BROWN_MUSHROOM, SEAGRASS,
                 TALL_SEAGRASS, KELP, KELP_PLANT, SWEET_BERRY_BUSH, CAVE_VINES, CAVE_VINES_PLANT, HANGING_ROOTS, MOSS_CARPET,
                 CRIMSON_ROOTS, WARPED_ROOTS, NETHER_SPROUTS, TWISTING_VINES, WEEPING_VINES, BUSH, FIREFLY_BUSH, SHORT_DRY_GRASS,
                 TALL_DRY_GRASS, LEAF_LITTER -> true;
            default -> false;
        };
    }

    /** A crop that counts as a harvest: fully grown ageable crops, and melons/pumpkins/cane/cactus/bamboo. */
    private static boolean isHarvest(Block block) {
        Material type = block.getType();
        switch (type) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, COCOA, TORCHFLOWER_CROP, PITCHER_CROP -> {
                return block.getBlockData() instanceof Ageable crop && crop.getAge() >= crop.getMaximumAge();
            }
            case MELON, PUMPKIN, SUGAR_CANE, CACTUS, BAMBOO -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Blocks worth remembering as player-placed (only ones that give XP). */
    private static boolean tracked(Material type) {
        return isLog(type) || isOre(type) || isStone(type) || isDiggable(type) || isForage(type)
                || type == Material.MELON || type == Material.PUMPKIN || type == Material.SUGAR_CANE
                || type == Material.CACTUS || type == Material.BAMBOO;
    }

    private static double oreXp(Material type) {
        String name = type.name();
        if (type == Material.ANCIENT_DEBRIS) {
            return 40;
        }
        if (name.contains("DIAMOND") || name.contains("EMERALD")) {
            return 25;
        }
        if (name.contains("GOLD") || name.contains("LAPIS") || name.contains("REDSTONE")) {
            return 12;
        }
        if (name.contains("IRON") || name.contains("QUARTZ")) {
            return 8;
        }
        return 5; // coal, copper
    }

    // ------------------------------------------------------ Placing / breaking

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (tracked(event.getBlock().getType())) {
            plugin.placed().markPlaced(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();
        if (!tracked(type) && !isHarvest(block)) {
            return;
        }
        boolean wasPlaced = tracked(type) && plugin.placed().isPlaced(block);
        if (wasPlaced) {
            plugin.placed().clear(block);
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (isLog(type)) {
            plugin.addXp(player, Profession.WOODCUTTING, 5);
            plugin.tryFind(player, Profession.WOODCUTTING, Finds.WOODCUTTING,
                    plugin.findChance(player, Profession.WOODCUTTING, 0.01, 0.001), center, type);
            extraLogs(player, block, tool, center);
            plugin.perks().onGather(player, Profession.WOODCUTTING, block, type, center);
        } else if (isOre(type) || isStone(type)) {
            boolean ore = isOre(type);
            plugin.addXp(player, Profession.MINING, ore ? oreXp(type) : 1.5);
            plugin.tryFind(player, Profession.MINING, Finds.MINING, plugin.findChance(player, Profession.MINING, 0.005, 0.0006), center, type);
            if (ore) {
                doubleDrops(player, Profession.MINING, block, tool, center);
                double haste = plugin.has(player, Perk.RUSH) ? 0.10 : 0.05;
                if (plugin.level(player, Profession.MINING) >= 25 && ProfessionsPlugin.roll(haste)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 1));
                    player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.1);
                }
            }
            plugin.perks().onGather(player, Profession.MINING, block, type, center);
        } else if (isDiggable(type)) {
            plugin.addXp(player, Profession.DIGGING, 1.5);
            plugin.tryFind(player, Profession.DIGGING, Finds.DIGGING, plugin.findChance(player, Profession.DIGGING, 0.008, 0.0008), center, type);
            doubleDrops(player, Profession.DIGGING, block, tool, center);
            plugin.perks().onGather(player, Profession.DIGGING, block, type, center);
        } else if (isHarvest(block)) {
            plugin.addXp(player, Profession.FARMING, 3);
            plugin.tryFind(player, Profession.FARMING, Finds.FARMING, plugin.findChance(player, Profession.FARMING, 0.015, 0.001), center, type);
            doubleDrops(player, Profession.FARMING, block, tool, center);
            replant(player, block);
            plugin.perks().onGather(player, Profession.FARMING, block, type, center);
        } else if (isForage(type)) {
            plugin.addXp(player, Profession.FORAGING, 1);
            plugin.tryFind(player, Profession.FORAGING, Finds.FORAGING, plugin.findChance(player, Profession.FORAGING, 0.02, 0.001), center, type);
            plugin.perks().onGather(player, Profession.FORAGING, block, type, center);
        }
    }

    /** Woodcutting yield: the higher the level, the more logs a log gives (up to three). */
    private void extraLogs(Player player, Block block, ItemStack tool, Location at) {
        int extra = (ProfessionsPlugin.roll(plugin.extraLogChance(player)) ? 1 : 0) + (ProfessionsPlugin.roll(plugin.thirdLogChance(player)) ? 1 : 0);
        if (extra == 0) {
            return;
        }
        for (ItemStack drop : block.getDrops(tool, player)) {
            ItemStack more = drop.clone();
            more.setAmount(drop.getAmount() * extra);
            block.getWorld().dropItemNaturally(at, more);
        }
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 4, 0.3, 0.3, 0.3, 0);
    }

    /** Drops a second copy of what the block drops, with the player's level as the chance. */
    private void doubleDrops(Player player, Profession profession, Block block, ItemStack tool, Location at) {
        double chance = plugin.doubleChance(player, profession);
        String name = block.getType().name();
        if (plugin.has(player, Perk.GEM_CUTTER) && (name.contains("DIAMOND_ORE") || name.contains("EMERALD_ORE"))) {
            chance += 0.1;
        }
        if (!ProfessionsPlugin.roll(chance)) {
            return;
        }
        // Abundance: some double harvests are triple.
        int copies = profession == Profession.FARMING && plugin.has(player, Perk.ABUNDANCE) && ProfessionsPlugin.roll(0.25) ? 2 : 1;
        Collection<ItemStack> drops = block.getDrops(tool, player);
        for (int copy = 0; copy < copies; copy++) {
            for (ItemStack drop : drops) {
                block.getWorld().dropItemNaturally(at, drop.clone());
            }
        }
        if (!drops.isEmpty()) {
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 6, 0.3, 0.3, 0.3, 0);
        }
    }

    /** Farming level 20+: harvested crops replant themselves, using a seed from the inventory. */
    private void replant(Player player, Block block) {
        if (plugin.level(player, Profession.FARMING) < 20) {
            return;
        }
        Material crop = block.getType();
        Material seed = switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
        if (seed == null || !player.getInventory().containsAtLeast(new ItemStack(seed), 1)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getType().isAir()) {
                player.getInventory().removeItem(new ItemStack(seed, 1));
                block.setType(crop);
                block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.3, 0.5), 5, 0.2, 0.1, 0.2, 0);
            }
        });
    }

    // ------------------------------------------------------------- Fishing

    /** How much faster fish bite at a Fishing level (up to 60% faster at level 50). */
    static double fishingSpeedup(int level) {
        return Math.min(0.6, 0.012 * level);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        int level = plugin.level(player, Profession.FISHING);
        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> {
                // Shorter waits; applied on top of Lure, which vanilla subtracts afterwards.
                double factor = 1 - fishingSpeedup(level) - (plugin.has(player, Perk.QUICK_BITE) ? 0.05 : 0)
                        - (plugin.has(player, Perk.RAIN_DANCER) && player.getWorld().hasStorm() ? 0.15 : 0);
                hook.setWaitTime((int) (hook.getMinWaitTime() * factor), (int) (hook.getMaxWaitTime() * factor));
            }
            case CAUGHT_FISH -> {
                plugin.addXp(player, Profession.FISHING, 20);
                plugin.perks().onCatch(player, event);
                double extraFish = 0.01 * level + (plugin.has(player, Perk.DOUBLE_HOOK) ? 0.05 : 0);
                if (event.getCaught() instanceof Item caught && ProfessionsPlugin.roll(extraFish)) {
                    ItemStack extra = caught.getItemStack().clone();
                    Item bonus = player.getWorld().dropItem(caught.getLocation(), extra);
                    bonus.setVelocity(caught.getVelocity());
                    player.getWorld().spawnParticle(Particle.SPLASH, caught.getLocation(), 15, 0.3, 0.2, 0.3, 0.1);
                    player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 1f, 1.4f);
                }
                plugin.tryFind(player, Profession.FISHING, Finds.FISHING, plugin.findChance(player, Profession.FISHING, 0.03, 0.002),
                        player.getLocation().add(player.getLocation().getDirection().multiply(1.5)).add(0, 1, 0), Material.WATER);
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------- Hunting

    @EventHandler(priority = EventPriority.HIGH)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player player = entity.getKiller();
        if (player == null || entity instanceof Player || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        double xp = entity instanceof Boss ? 200 : entity instanceof Enemy ? 10 : 4;
        if (entity.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            xp *= plugin.getConfig().getDouble("spawner-xp-fraction", 0.25);
        }
        plugin.addXp(player, Profession.HUNTING, xp);
        int level = plugin.level(player, Profession.HUNTING);

        if (ProfessionsPlugin.roll(plugin.doubleChance(player, Profession.HUNTING)) && !event.getDrops().isEmpty()) {
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack drop : event.getDrops()) {
                extra.add(drop.clone());
            }
            event.getDrops().addAll(extra);
            entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3, 0);
        }
        event.setDroppedExp((int) Math.round(event.getDroppedExp() * (1 + 0.02 * level)));

        Material head = switch (entity.getType()) {
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case PIGLIN -> Material.PIGLIN_HEAD;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            default -> null;
        };
        if (head != null && ProfessionsPlugin.roll(0.0002 * level * (plugin.has(player, Perk.TROPHY_HUNTER) ? 2 : 1))) {
            plugin.dropFind(player, new ItemStack(head), Finds.Rarity.RARE, entity.getLocation().add(0, 0.5, 0));
        }
        plugin.perks().onKill(player, event);
    }

    // ----------------------------------------------------------- Husbandry

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) {
            return;
        }
        plugin.addXp(player, Profession.HUSBANDRY, 15);
        int level = plugin.level(player, Profession.HUSBANDRY);
        // Parents can breed again sooner (vanilla: 5 minutes).
        int cooldown = (int) (6000 * (1 - 0.01 * level - (plugin.has(player, Perk.MASTER_BREEDER) ? 0.15 : 0)));
        for (LivingEntity parent : new LivingEntity[] {event.getMother(), event.getFather()}) {
            if (parent instanceof Animals animal) {
                plugin.getServer().getScheduler().runTask(plugin, () -> animal.setAge(cooldown));
            }
        }
        // Twins.
        plugin.perks().onBreed(player, event);
        double twins = 0.006 * level + (plugin.has(player, Perk.BIG_LITTER) ? 0.03 : 0);
        if (event.getEntity() instanceof Animals baby && ProfessionsPlugin.roll(twins)) {
            Entity twin = baby.getWorld().spawnEntity(baby.getLocation(), baby.getType(), CreatureSpawnEvent.SpawnReason.BREEDING);
            if (twin instanceof Animals twinAnimal) {
                twinAnimal.setBaby();
            }
            baby.getWorld().spawnParticle(Particle.HEART, baby.getLocation().add(0, 1, 0), 6, 0.4, 0.3, 0.4);
            player.playSound(baby.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 1.2f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        plugin.addXp(player, Profession.HUSBANDRY, 5);
        plugin.perks().onShear(player);
        if (ProfessionsPlugin.roll(plugin.doubleChance(player, Profession.HUSBANDRY))) {
            List<ItemStack> drops = new ArrayList<>(event.getDrops());
            for (ItemStack drop : event.getDrops()) {
                drops.add(drop.clone());
            }
            event.setDrops(drops);
        }
    }

    // ------------------------------------------------------------ Smelting

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceTake(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        int amount = event.getItemAmount();
        plugin.addXp(player, Profession.SMELTING, amount * 1.5);
        int level = plugin.level(player, Profession.SMELTING);
        plugin.perks().onExtract(player, event);
        double chance = 0.004 * level + (plugin.has(player, Perk.BULK_SMELTER) ? 0.02 : 0);
        int bonus = 0;
        for (int i = 0; i < amount; i++) {
            if (ProfessionsPlugin.roll(chance)) {
                bonus++;
            }
        }
        if (bonus > 0) {
            plugin.dropFind(player, new ItemStack(event.getItemType(), bonus), Finds.Rarity.COMMON, player.getLocation().add(0, 1, 0));
        }
    }

    // ---------------------------------------------------------- Enchanting

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        int spent = event.whichButton() + 1; // the levels actually taken (1-3)
        double xp = 8.0 * event.getExpLevelCost() / 10 + 8 * spent;
        plugin.addXp(player, Profession.ENCHANTING, plugin.has(player, Perk.STUDIOUS) ? xp * 1.2 : xp);
        plugin.perks().onEnchant(player, event);
        double refund = 0.008 * plugin.level(player, Profession.ENCHANTING)
                + (plugin.has(player, Perk.ARCANE_REFUND) ? 0.05 : 0) + (plugin.has(player, Perk.ARCHMAGE) ? 0.05 : 0);
        if (ProfessionsPlugin.roll(refund)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.giveExpLevels(spent);
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.6f);
                player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.5, 0), 40, 0.5, 0.5, 0.5, 1);
                player.sendActionBar(plugin.lang().get("enchanting-refund", "levels", spent));
            });
        }
    }

    // ------------------------------------------------------------- Alchemy

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpenStand(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.BREWING && event.getInventory().getLocation() != null
                && event.getPlayer() instanceof Player player) {
            brewers.put(event.getInventory().getLocation().toBlockLocation(), player.getUniqueId());
        }
    }

    /** The player who last opened this brewing stand, if online. */
    Player brewer(Block block) {
        UUID id = brewers.get(block.getLocation().toBlockLocation());
        return id == null ? null : plugin.getServer().getPlayer(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        UUID id = brewers.get(event.getBlock().getLocation().toBlockLocation());
        Player player = id == null ? null : plugin.getServer().getPlayer(id);
        if (player == null) {
            return;
        }
        int bottles = 0;
        for (ItemStack result : event.getResults()) {
            if (result != null && !result.isEmpty()) {
                bottles++;
            }
        }
        plugin.addXp(player, Profession.ALCHEMY, 10.0 * bottles);
        plugin.perks().onBrew(player, event);
        BrewerInventory stand = event.getContents();
        ItemStack ingredient = stand.getIngredient();
        double keep = 0.008 * plugin.level(player, Profession.ALCHEMY)
                + (plugin.has(player, Perk.INGREDIENT_SAVER) ? 0.05 : 0) + (plugin.has(player, Perk.PHILOSOPHER) ? 0.05 : 0);
        if (ingredient != null && !ingredient.isEmpty() && ProfessionsPlugin.roll(keep)) {
            ItemStack kept = ingredient.clone();
            kept.setAmount(1);
            // The ingredient is used up after this event: put one back on the next tick.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack now = stand.getIngredient();
                if (now == null || now.isEmpty()) {
                    stand.setIngredient(kept);
                } else if (now.isSimilar(kept) && now.getAmount() < now.getMaxStackSize()) {
                    now.setAmount(now.getAmount() + 1);
                    stand.setIngredient(now);
                } else {
                    return;
                }
                event.getBlock().getWorld().spawnParticle(Particle.WITCH, event.getBlock().getLocation().add(0.5, 1, 0.5), 10, 0.3, 0.3, 0.3, 0);
            });
        }
    }

}
