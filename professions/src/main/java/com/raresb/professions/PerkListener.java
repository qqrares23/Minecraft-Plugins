package com.raresb.professions;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Animals;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** What the profession perks do (see {@link Perk}). Hooks called by GatherListener plus its own events. */
final class PerkListener implements Listener {
    /** A tool use counts for a profession if the work happened this recently. */
    private static final long ACTION_WINDOW = 150;
    private static final Set<PotionEffectType> HARMFUL = Set.of(PotionEffectType.POISON, PotionEffectType.HUNGER,
            PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS, PotionEffectType.NAUSEA, PotionEffectType.MINING_FATIGUE,
            PotionEffectType.WITHER, PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS);

    private final ProfessionsPlugin plugin;
    private final NamespacedKey heartyKey;
    private final Map<UUID, Profession> lastProfession = new HashMap<>();
    private final Map<UUID, Long> lastActionAt = new HashMap<>();
    private final Map<Location, UUID> furnaceUsers = new HashMap<>();
    private final Map<UUID, Long> bloodthirstAt = new HashMap<>();
    private final Map<UUID, Long> oreSenseAt = new HashMap<>();
    private final List<Entity> outlines = new ArrayList<>();

    PerkListener(ProfessionsPlugin plugin) {
        this.plugin = plugin;
        this.heartyKey = new NamespacedKey(plugin, "hearty_stock");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::fertileTouch, 200, 200);
    }

    private boolean has(Player player, Perk perk) {
        return plugin.has(player, perk);
    }

    private static boolean roll(double chance) {
        return ProfessionsPlugin.roll(chance);
    }

    private static void buff(Player player, PotionEffectType type, int ticks) {
        player.addPotionEffect(new PotionEffect(type, ticks, 0, false, false, true));
    }

    private void bonus(Player player, ItemStack item, Location at) {
        plugin.dropFind(player, item, Finds.Rarity.COMMON, at);
    }

    /** Remembers which profession the player just worked in (for tool durability and hunger perks). */
    void action(Player player, Profession profession) {
        lastProfession.put(player.getUniqueId(), profession);
        lastActionAt.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean recent(Player player, Profession profession) {
        return lastProfession.get(player.getUniqueId()) == profession
                && System.currentTimeMillis() - lastActionAt.getOrDefault(player.getUniqueId(), 0L) <= ACTION_WINDOW;
    }

    // ============================================================ Gathering (from GatherListener.onBreak)

    void onGather(Player player, Profession profession, Block block, Material type, Location at) {
        action(player, profession);
        switch (profession) {
            case WOODCUTTING -> woodcutting(player, type, at);
            case MINING -> mining(player, block, type, at);
            case DIGGING -> digging(player, type, at);
            case FARMING -> farming(player, type, at);
            case FORAGING -> foraging(player, type, at);
            default -> {
            }
        }
    }

    private void woodcutting(Player player, Material log, Location at) {
        String wood = log.name().replace("_LOG", "").replace("_STEM", "").replace("_WOOD", "").replace("_HYPHAE", "");
        if (has(player, Perk.ARBORIST) && roll(0.2)) {
            Material sapling = log == Material.MANGROVE_LOG ? Material.MANGROVE_PROPAGULE : Material.matchMaterial(wood + "_SAPLING");
            if (sapling != null) {
                bonus(player, new ItemStack(sapling), at);
            }
        }
        if (has(player, Perk.WOODSMANS_PACE)) {
            buff(player, PotionEffectType.SPEED, 80);
        }
        if (has(player, Perk.KINDLING) && roll(0.06)) {
            bonus(player, new ItemStack(Material.CHARCOAL), at);
        }
        if (has(player, Perk.STEADY_SWING)) {
            buff(player, PotionEffectType.HASTE, 80);
        }
        if (has(player, Perk.SAWMILL) && roll(0.05)) {
            Material planks = Material.matchMaterial(wood + "_PLANKS");
            bonus(player, new ItemStack(planks != null ? planks : Material.OAK_PLANKS, 4), at);
        }
        if (has(player, Perk.WOODLAND_WISDOM) && roll(0.04)) {
            at.getWorld().spawn(at, ExperienceOrb.class, orb -> orb.setExperience(3));
        }
        if (has(player, Perk.GOLDEN_GROVE) && roll(0.005)) {
            plugin.dropFind(player, new ItemStack(Material.GOLDEN_APPLE), Finds.Rarity.RARE, at);
        }
    }

    private void mining(Player player, Block block, Material type, Location at) {
        boolean ore = type.name().endsWith("_ORE") || type == Material.ANCIENT_DEBRIS;
        if (has(player, Perk.DEEP_SIGHT) && block.getY() < 0) {
            buff(player, PotionEffectType.NIGHT_VISION, 300);
        }
        if (!ore && has(player, Perk.SIFTER) && roll(0.03)) {
            bonus(player, new ItemStack(roll(0.7) ? Material.IRON_NUGGET : Material.GOLD_NUGGET), at);
        }
        if (ore && has(player, Perk.ORE_SENSE)) {
            oreSense(player, block, type);
        }
    }

    /** Ore Sense: outlines ores of the same kind near the mined one, only for the miner. */
    private void oreSense(Player player, Block mined, Material type) {
        long now = System.currentTimeMillis();
        if (oreSenseAt.getOrDefault(player.getUniqueId(), 0L) + 3000 > now) {
            return;
        }
        oreSenseAt.put(player.getUniqueId(), now);
        String kind = type.name().replace("DEEPSLATE_", "");
        List<BlockDisplay> shown = new ArrayList<>();
        World world = mined.getWorld();
        for (int x = -4; x <= 4 && shown.size() < 12; x++) {
            for (int y = -4; y <= 4 && shown.size() < 12; y++) {
                for (int z = -4; z <= 4 && shown.size() < 12; z++) {
                    Block block = mined.getRelative(x, y, z);
                    if ((x == 0 && y == 0 && z == 0) || !block.getType().name().replace("DEEPSLATE_", "").equals(kind)) {
                        continue;
                    }
                    BlockDisplay outline = world.spawn(block.getLocation(), BlockDisplay.class, d -> {
                        d.setBlock(block.getBlockData());
                        d.setPersistent(false);
                        d.setVisibleByDefault(false);
                        d.setGlowing(true);
                        d.setGlowColorOverride(Color.YELLOW);
                        d.setTransformation(new Transformation(new Vector3f(0.02f, 0.02f, 0.02f), new Quaternionf(),
                                new Vector3f(0.96f, 0.96f, 0.96f), new Quaternionf()));
                    });
                    player.showEntity(plugin, outline);
                    shown.add(outline);
                }
            }
        }
        outlines.addAll(shown);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            shown.forEach(Entity::remove);
            outlines.removeAll(shown);
        }, 100);
    }

    private void digging(Player player, Material type, Location at) {
        if (has(player, Perk.QUICK_SHOVEL)) {
            buff(player, PotionEffectType.HASTE, 60);
        }
        if (has(player, Perk.CLAY_WORKER)) {
            if (type == Material.CLAY || (type == Material.MUD && roll(0.1))) {
                bonus(player, new ItemStack(Material.CLAY_BALL), at);
            }
        }
        if (has(player, Perk.GLASSMAKER) && (type == Material.SAND || type == Material.RED_SAND) && roll(0.05)) {
            bonus(player, new ItemStack(Material.GLASS), at);
        }
        if (has(player, Perk.FLINT_SIFTER) && type == Material.GRAVEL && roll(0.1)) {
            bonus(player, new ItemStack(Material.FLINT), at);
        }
        if (has(player, Perk.EXCAVATORS_PACE)) {
            buff(player, PotionEffectType.SPEED, 80);
        }
        if (has(player, Perk.BONE_COLLECTOR) && roll(0.03)) {
            bonus(player, new ItemStack(roll(0.5) ? Material.BONE : Material.BONE_MEAL), at);
        }
    }

    private void farming(Player player, Material crop, Location at) {
        if (has(player, Perk.SEED_SAVER)) {
            if (crop == Material.WHEAT) {
                bonus(player, new ItemStack(Material.WHEAT_SEEDS), at);
            } else if (crop == Material.BEETROOTS) {
                bonus(player, new ItemStack(Material.BEETROOT_SEEDS), at);
            }
        }
        if (has(player, Perk.COMPOST) && roll(0.05)) {
            bonus(player, new ItemStack(Material.BONE_MEAL), at);
        }
        if (has(player, Perk.HARVESTERS_STRIDE)) {
            buff(player, PotionEffectType.SPEED, 80);
        }
        if (has(player, Perk.BAKER) && roll(0.04)) {
            if (crop == Material.WHEAT) {
                bonus(player, new ItemStack(Material.BREAD), at);
            } else if (crop == Material.POTATOES) {
                bonus(player, new ItemStack(Material.BAKED_POTATO), at);
            }
        }
        if (crop == Material.NETHER_WART && has(player, Perk.WART_GROWER)) {
            bonus(player, new ItemStack(Material.NETHER_WART), at);
        }
    }

    private void foraging(Player player, Material type, Location at) {
        boolean flower = Tag.FLOWERS.isTagged(type);
        boolean leaves = Tag.LEAVES.isTagged(type);
        if (has(player, Perk.HERBALIST) && flower && roll(0.1)) {
            bonus(player, new ItemStack(type), at);
        }
        if (has(player, Perk.LEAF_SIFTER) && leaves && roll(0.04)) {
            Material sapling = Material.matchMaterial(type.name().replace("_LEAVES", "_SAPLING"));
            if (sapling != null && sapling.isItem()) {
                bonus(player, new ItemStack(sapling), at);
            }
        }
        if (has(player, Perk.NATURES_STEP)) {
            buff(player, PotionEffectType.SPEED, 80);
        }
        if (has(player, Perk.FORAGERS_SNACK) && roll(0.04)) {
            bonus(player, new ItemStack(roll(0.5) ? Material.APPLE : Material.SWEET_BERRIES), at);
        }
        if (has(player, Perk.WILD_HONEY) && (flower || leaves) && roll(0.015)) {
            bonus(player, new ItemStack(Material.HONEYCOMB), at);
        }
        if (has(player, Perk.RARE_SEEDS) && roll(0.005)) {
            plugin.dropFind(player, new ItemStack(roll(0.5) ? Material.TORCHFLOWER_SEEDS : Material.PITCHER_POD), Finds.Rarity.RARE, at);
        }
        if (has(player, Perk.VERDANT) && roll(0.05)) {
            buff(player, PotionEffectType.REGENERATION, 60);
        }
    }

    /** Fertile Touch: every 10 s one crop near the player grows a stage. */
    private void fertileTouch() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!has(player, Perk.FERTILE_TOUCH)) {
                continue;
            }
            Block center = player.getLocation().getBlock();
            for (int tries = 0; tries < 60; tries++) {
                Block block = center.getRelative(random.nextInt(-5, 6), random.nextInt(-2, 3), random.nextInt(-5, 6));
                if (block.getBlockData() instanceof Ageable crop && crop.getAge() < crop.getMaximumAge()
                        && (Tag.CROPS.isTagged(block.getType()) || block.getType() == Material.NETHER_WART)) {
                    crop.setAge(crop.getAge() + 1);
                    block.setBlockData(crop);
                    block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.4, 0.5), 5, 0.2, 0.2, 0.2);
                    break;
                }
            }
        }
    }

    // ============================================================ Fishing, hunting, husbandry

    void onCatch(Player player, PlayerFishEvent event) {
        action(player, Profession.FISHING);
        if (event.getCaught() instanceof Item caught && has(player, Perk.FRESH_CATCH) && roll(0.2)) {
            ItemStack stack = caught.getItemStack();
            Material cooked = stack.getType() == Material.COD ? Material.COOKED_COD : stack.getType() == Material.SALMON ? Material.COOKED_SALMON : null;
            if (cooked != null) {
                caught.setItemStack(stack.withType(cooked));
            }
        }
        if (has(player, Perk.SEA_LEGS)) {
            buff(player, PotionEffectType.DOLPHINS_GRACE, 100);
        }
        if (has(player, Perk.ANGLERS_WISDOM)) {
            event.setExpToDrop((int) Math.round(event.getExpToDrop() * 1.5));
        }
        if (has(player, Perk.BOOK_ANGLER) && roll(0.01)) {
            Enchantment enchantment = roll(0.5) ? Enchantment.LUCK_OF_THE_SEA : Enchantment.LURE;
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            int level = ThreadLocalRandom.current().nextInt(1, enchantment.getMaxLevel() + 1);
            book.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(enchantment, level, true));
            plugin.dropFind(player, book, Finds.Rarity.RARE, player.getLocation().add(0, 1, 0));
        }
        if (has(player, Perk.LUCKY_CATCH)) {
            buff(player, PotionEffectType.LUCK, 600);
        }
    }

    void onKill(Player player, EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        boolean hostile = entity instanceof Enemy;
        List<ItemStack> drops = event.getDrops();
        if (entity instanceof Animals && has(player, Perk.BUTCHER) && roll(0.2)) {
            for (ItemStack drop : drops) {
                if (drop.getType().isEdible()) {
                    drops.add(new ItemStack(drop.getType()));
                    break;
                }
            }
        }
        if (has(player, Perk.LEATHERWORKER) && roll(0.25)) {
            switch (entity.getType()) {
                case COW, MOOSHROOM, HORSE, DONKEY, MULE, LLAMA, TRADER_LLAMA -> drops.add(new ItemStack(Material.LEATHER));
                case RABBIT -> drops.add(new ItemStack(Material.RABBIT_HIDE));
                default -> {
                }
            }
        }
        if (hostile && has(player, Perk.PREDATORS_PACE)) {
            buff(player, PotionEffectType.SPEED, 80);
        }
        if (has(player, Perk.SOUL_COLLECTOR)) {
            event.setDroppedExp((int) Math.round(event.getDroppedExp() * 1.1));
        }
        if (has(player, Perk.BONE_PICKER) && roll(0.3)) {
            switch (entity.getType()) {
                case SKELETON, STRAY, BOGGED, WITHER_SKELETON, SKELETON_HORSE -> drops.add(new ItemStack(Material.BONE));
                default -> {
                }
            }
        }
        if (hostile && has(player, Perk.BLOODTHIRST)) {
            long now = System.currentTimeMillis();
            if (bloodthirstAt.getOrDefault(player.getUniqueId(), 0L) + 5000 <= now) {
                bloodthirstAt.put(player.getUniqueId(), now);
                AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
                player.setHealth(Math.min(max != null ? max.getValue() : 20, player.getHealth() + 2));
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 1);
            }
        }
        if (has(player, Perk.FIELD_COOK) && roll(0.25)) {
            drops.replaceAll(drop -> {
                Material cooked = switch (drop.getType()) {
                    case BEEF -> Material.COOKED_BEEF;
                    case PORKCHOP -> Material.COOKED_PORKCHOP;
                    case CHICKEN -> Material.COOKED_CHICKEN;
                    case MUTTON -> Material.COOKED_MUTTON;
                    case RABBIT -> Material.COOKED_RABBIT;
                    case COD -> Material.COOKED_COD;
                    case SALMON -> Material.COOKED_SALMON;
                    default -> null;
                };
                return cooked == null ? drop : drop.withType(cooked);
            });
        }
        if (hostile && has(player, Perk.SPOILS) && entity.getEntitySpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER && roll(0.02)) {
            plugin.dropFind(player, new ItemStack(Material.EMERALD), Finds.Rarity.UNCOMMON, entity.getLocation().add(0, 0.5, 0));
        }
    }

    void onBreed(Player player, EntityBreedEvent event) {
        LivingEntity baby = event.getEntity();
        if (has(player, Perk.FEEDER) && event.getBredWith() != null && roll(0.2)) {
            ItemStack food = event.getBredWith().clone();
            food.setAmount(1);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.getInventory().addItem(food)
                    .values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)));
        }
        if (has(player, Perk.QUICK_GROWTH) && baby instanceof Animals animal) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (animal.isValid() && !animal.isAdult()) {
                    animal.setAge(-18000); // vanilla -24000
                }
            });
        }
        if (has(player, Perk.RANCHERS_PACE)) {
            buff(player, PotionEffectType.SPEED, 80);
        }
        if (has(player, Perk.ANIMAL_WHISPERER)) {
            event.setExperience(event.getExperience() * 2);
        }
        if (has(player, Perk.HEARTY_STOCK)) {
            AttributeInstance health = baby.getAttribute(Attribute.MAX_HEALTH);
            if (health != null && health.getModifier(heartyKey) == null) {
                health.addModifier(new AttributeModifier(heartyKey, 0.2, AttributeModifier.Operation.ADD_SCALAR));
                plugin.getServer().getScheduler().runTask(plugin, () -> baby.setHealth(health.getValue()));
            }
        }
        if (has(player, Perk.PRIZE_STOCK) && roll(0.05)) {
            Material[] prizes = {Material.LEATHER, Material.WHITE_WOOL, Material.FEATHER, Material.EGG};
            bonus(player, new ItemStack(prizes[ThreadLocalRandom.current().nextInt(prizes.length)]), baby.getLocation().add(0, 0.5, 0));
        }
    }

    void onShear(Player player) {
        action(player, Profession.HUSBANDRY);
        if (has(player, Perk.RANCHERS_PACE)) {
            buff(player, PotionEffectType.SPEED, 80);
        }
    }

    // ============================================================ Smelting

    void onExtract(Player player, FurnaceExtractEvent event) {
        if (has(player, Perk.EMBER_HANDS)) {
            event.setExpToDrop((int) Math.round(event.getExpToDrop() * 1.25));
        }
        Material type = event.getItemType();
        double chance = 0;
        if (type == Material.CHARCOAL && has(player, Perk.CHARCOAL_KILN)) {
            chance = 0.1;
        } else if (type == Material.GLASS && has(player, Perk.GLASSBLOWER)) {
            chance = 0.1;
        } else if (type.isEdible() && has(player, Perk.CHEF)) {
            chance = 0.1;
        }
        int extra = 0;
        for (int i = 0; i < event.getItemAmount(); i++) {
            if (roll(chance)) {
                extra++;
            }
        }
        if (extra > 0) {
            bonus(player, new ItemStack(type, extra), player.getLocation().add(0, 1, 0));
        }
    }

    /** Remembers who last used each furnace (furnaces work without a player). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpenFurnace(InventoryOpenEvent event) {
        InventoryType type = event.getInventory().getType();
        if ((type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER)
                && event.getInventory().getLocation() != null && event.getPlayer() instanceof Player player) {
            furnaceUsers.put(event.getInventory().getLocation().toBlockLocation(), player.getUniqueId());
        }
    }

    private Player furnaceUser(Block block) {
        UUID id = furnaceUsers.get(block.getLocation().toBlockLocation());
        return id == null ? null : plugin.getServer().getPlayer(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onStartSmelt(FurnaceStartSmeltEvent event) {
        Player player = furnaceUser(event.getBlock());
        if (player == null) {
            return;
        }
        double factor = 1;
        if (has(player, Perk.BELLOWS)) {
            factor *= 0.9;
        }
        Material type = event.getBlock().getType();
        if (has(player, Perk.KILN_MASTER) && (type == Material.BLAST_FURNACE || type == Material.SMOKER)) {
            factor *= 0.85;
        }
        if (has(player, Perk.MASTER_SMELTER)) {
            factor *= 0.85;
        }
        if (factor < 1) {
            event.setTotalCookTime(Math.max(1, (int) Math.round(event.getTotalCookTime() * factor)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        Player player = furnaceUser(event.getBlock());
        if (player == null) {
            return;
        }
        if (has(player, Perk.THRIFTY_FUEL)) {
            event.setBurnTime((int) Math.round(event.getBurnTime() * 1.1));
        }
        if (has(player, Perk.ETERNAL_FLAME) && event.getFuel().getType() != Material.LAVA_BUCKET && roll(0.05)) {
            event.setConsumeFuel(false);
        }
    }

    // ============================================================ Enchanting

    void onEnchant(Player player, EnchantItemEvent event) {
        if (has(player, Perk.LAPIS_SAVER) && roll(0.15) && event.getInventory() instanceof EnchantingInventory table) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack lapis = table.getSecondary();
                if (lapis == null || lapis.isEmpty()) {
                    table.setSecondary(new ItemStack(Material.LAPIS_LAZULI));
                } else if (lapis.getType() == Material.LAPIS_LAZULI && lapis.getAmount() < 64) {
                    lapis.setAmount(lapis.getAmount() + 1);
                    table.setSecondary(lapis);
                }
            });
        }
        Map<Enchantment, Integer> enchants = event.getEnchantsToAdd();
        if (has(player, Perk.BONUS_RUNE) && roll(0.05)) {
            List<Enchantment> raisable = new ArrayList<>();
            enchants.forEach((enchantment, level) -> {
                if (level < enchantment.getMaxLevel()) {
                    raisable.add(enchantment);
                }
            });
            if (!raisable.isEmpty()) {
                Enchantment chosen = raisable.get(ThreadLocalRandom.current().nextInt(raisable.size()));
                enchants.put(chosen, enchants.get(chosen) + 1);
                player.sendActionBar(plugin.lang().get("perk-message.bonus-rune"));
            }
        }
        if (has(player, Perk.TREASURE_SCHOLAR) && roll(0.02)) {
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
            List<Enchantment> options = new ArrayList<>();
            boolean book = event.getItem().getType() == Material.BOOK;
            for (var key : registry.getTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE).values()) {
                Enchantment enchantment = registry.get(key);
                if (enchantment == null || enchants.containsKey(enchantment) || (!book && !enchantment.canEnchantItem(event.getItem()))) {
                    continue;
                }
                if (enchants.keySet().stream().noneMatch(other -> other.conflictsWith(enchantment))) {
                    options.add(enchantment);
                }
            }
            if (!options.isEmpty()) {
                enchants.put(options.get(ThreadLocalRandom.current().nextInt(options.size())), 1);
                player.sendActionBar(plugin.lang().get("perk-message.treasure-scholar"));
            }
        }
        if (has(player, Perk.ARCANE_FOCUS) && roll(0.2)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.giveExpLevels(1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent event) {
        if (event.getAmount() > 0 && has(event.getPlayer(), Perk.EXPERIENCE_WELL)) {
            double more = event.getAmount() * 0.1;
            int whole = (int) more;
            event.setAmount(event.getAmount() + whole + (roll(more - whole) ? 1 : 0));
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        int discount = (has(player, Perk.ANVIL_ADEPT) ? 1 : 0) + (has(player, Perk.ANVIL_SAGE) ? 1 : 0);
        int cost = event.getView().getRepairCost();
        if (discount > 0 && cost > 1 && event.getResult() != null && !event.getResult().isEmpty()) {
            event.getView().setRepairCost(Math.max(1, cost - discount));
        }
    }

    // ============================================================ Alchemy

    void onBrew(Player player, BrewEvent event) {
        if (has(player, Perk.POTENT) && roll(0.04)) {
            List<ItemStack> results = new ArrayList<>();
            for (ItemStack result : event.getResults()) {
                if (result != null && !result.isEmpty()) {
                    results.add(result);
                }
            }
            if (!results.isEmpty()) {
                ItemStack copy = results.get(ThreadLocalRandom.current().nextInt(results.size())).clone();
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        bonus(player, copy, event.getBlock().getLocation().add(0.5, 1.2, 0.5)));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrewStart(BrewingStartEvent event) {
        Player player = plugin.gather().brewer(event.getBlock());
        if (player != null && has(player, Perk.QUICK_BREW)) {
            event.setBrewingTime(Math.max(1, (int) Math.round(event.getBrewingTime() * 0.85)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrewFuel(BrewingStandFuelEvent event) {
        Player player = plugin.gather().brewer(event.getBlock());
        if (player != null && has(player, Perk.FUEL_EFFICIENT) && event.getFuelPower() == 20) {
            event.setFuelPower(25);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        switch (item.getType()) {
            case BREAD, BAKED_POTATO, PUMPKIN_PIE, BEETROOT_SOUP, MUSHROOM_STEW, RABBIT_STEW, COOKIE -> {
                if (has(player, Perk.HEARTY_MEALS)) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 2));
                        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 1));
                    });
                }
            }
            case POTION -> potion(player, item);
            default -> {
            }
        }
    }

    /** Long Lasting / Master Alchemist stretch the drunk potion's effects; Alchemist's Thirst adds Regeneration. */
    private void potion(Player player, ItemStack item) {
        double factor = 1 + (has(player, Perk.LONG_LASTING) ? 0.15 : 0) + (has(player, Perk.MASTER_ALCHEMIST) ? 0.15 : 0);
        boolean thirst = has(player, Perk.ALCHEMISTS_THIRST);
        if (factor <= 1 && !thirst) {
            return;
        }
        List<PotionEffect> effects = new ArrayList<>();
        if (item.getItemMeta() instanceof PotionMeta meta) {
            if (meta.getBasePotionType() != null) {
                effects.addAll(meta.getBasePotionType().getPotionEffects());
            }
            effects.addAll(meta.getCustomEffects());
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (PotionEffect effect : effects) {
                if (factor > 1 && !effect.getType().isInstant() && effect.getDuration() > 0) {
                    PotionEffect current = player.getPotionEffect(effect.getType());
                    if (current != null && current.getAmplifier() == effect.getAmplifier() && current.getDuration() <= effect.getDuration()) {
                        player.addPotionEffect(effect.withDuration((int) (effect.getDuration() * factor)));
                    }
                }
            }
            if (thirst) {
                buff(player, PotionEffectType.REGENERATION, 60);
            }
        });
    }

    /** Iron Stomach: harmful effects last 25% shorter. */
    @EventHandler(ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null
                || event.getCause() == EntityPotionEffectEvent.Cause.PLUGIN || event.getCause() == EntityPotionEffectEvent.Cause.COMMAND
                || (event.getAction() != EntityPotionEffectEvent.Action.ADDED && event.getAction() != EntityPotionEffectEvent.Action.CHANGED)) {
            return;
        }
        PotionEffect effect = event.getNewEffect();
        if (!HARMFUL.contains(effect.getType()) || effect.getDuration() <= 20 || effect.isInfinite() || !has(player, Perk.IRON_STOMACH)) {
            return;
        }
        event.setCancelled(true);
        PotionEffect shorter = effect.withDuration((int) (effect.getDuration() * 0.75));
        plugin.getServer().getScheduler().runTask(plugin, () -> player.addPotionEffect(shorter));
    }

    // ============================================================ Tools, hunger, farmland, berries, drops

    /** The Sturdy perks: the tool used for the profession's work sometimes takes no damage. */
    @EventHandler(ignoreCancelled = true)
    public void onToolDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        String name = event.getItem().getType().name();
        Perk perk = null;
        double chance = 0.2;
        if (name.endsWith("_AXE") && recent(player, Profession.WOODCUTTING)) {
            perk = Perk.STURDY_AXE;
        } else if (name.endsWith("_PICKAXE") && recent(player, Profession.MINING)) {
            perk = Perk.STURDY_PICK;
        } else if (name.endsWith("_SHOVEL") && recent(player, Profession.DIGGING)) {
            perk = Perk.STURDY_SPADE;
        } else if (name.equals("SHEARS") && recent(player, Profession.FORAGING)) {
            perk = Perk.STURDY_SHEARS;
        } else if (name.equals("SHEARS") && recent(player, Profession.HUSBANDRY)) {
            perk = Perk.GENTLE_SHEARS;
            chance = 0.25;
        } else if (name.equals("FISHING_ROD") && recent(player, Profession.FISHING)) {
            perk = Perk.STURDY_ROD;
            chance = 0.25;
        }
        if (perk != null && has(player, perk) && roll(chance)) {
            event.setCancelled(true);
        }
    }

    /** Dwarven Endurance / Earth's Embrace: no hunger from mining / digging. */
    @EventHandler(ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (event.getExhaustionReason() != EntityExhaustionEvent.ExhaustionReason.BLOCK_MINED || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if ((recent(player, Profession.MINING) && has(player, Perk.DWARVEN_ENDURANCE))
                || (recent(player, Profession.DIGGING) && has(player, Perk.EARTHS_EMBRACE))) {
            event.setCancelled(true);
        }
    }

    /** Gentle Step: no trampling farmland. */
    @EventHandler(ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.FARMLAND && has(event.getPlayer(), Perk.GENTLE_STEP)) {
            event.setCancelled(true);
        }
    }

    /** Berry Picker: one more berry when picking sweet or glow berries by hand. */
    @EventHandler(ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (!has(event.getPlayer(), Perk.BERRY_PICKER) || event.getItemsHarvested().isEmpty()) {
            return;
        }
        Material type = event.getHarvestedBlock().getType();
        if (type == Material.SWEET_BERRY_BUSH || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT) {
            ItemStack first = event.getItemsHarvested().getFirst();
            event.getItemsHarvested().add(new ItemStack(first.getType()));
        }
    }

    /** Smelter's Touch: raw iron, gold or copper sometimes drops as an ingot. */
    @EventHandler(ignoreCancelled = true)
    public void onOreDrop(BlockDropItemEvent event) {
        if (!event.getBlockState().getType().name().endsWith("_ORE") || !has(event.getPlayer(), Perk.SMELTERS_TOUCH)) {
            return;
        }
        // On an Auto-Smelt pickaxe (MagicEnchants) every raw drop becomes an ingot anyway, so there the perk
        // adds one more of it instead - Auto-Smelt then smelts that one too (Professions 1.8.1).
        boolean autoSmelt = plugin.magicLevel(event.getPlayer().getInventory().getItemInMainHand(), "auto_smelt") > 0;
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            Material ingot = switch (stack.getType()) {
                case RAW_IRON -> Material.IRON_INGOT;
                case RAW_GOLD -> Material.GOLD_INGOT;
                case RAW_COPPER -> Material.COPPER_INGOT;
                default -> null;
            };
            if (ingot != null && roll(0.08)) {
                drop.setItemStack(autoSmelt ? stack.add(1) : stack.withType(ingot));
                drop.getWorld().spawnParticle(Particle.FLAME, drop.getLocation(), 6, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastProfession.remove(id);
        lastActionAt.remove(id);
        bloodthirstAt.remove(id);
        oreSenseAt.remove(id);
        plugin.forget(event.getPlayer());
    }

    void shutdown() {
        outlines.forEach(Entity::remove);
        outlines.clear();
    }
}
