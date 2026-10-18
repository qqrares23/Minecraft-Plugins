package com.raresb.weaponskills;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Mining (pickaxe) and woodcutting (axe) skills. Blocks are broken with {@code player.breakBlock},
 * so they drop, wear the tool and count for other plugins exactly as if the player mined them.
 */
final class GatherSkills implements Listener {
    private static final int BLOCKS_PER_TICK = 8;

    private final WeaponSkillsPlugin plugin;
    private final Skills skills;
    private final List<Entity> temporaryEntities = new ArrayList<>();
    /** Lumber Frenzy: until (ms), whether evolved, and logs counted for skill XP. */
    private final Map<UUID, Long> frenzyUntil = new HashMap<>();
    private final Map<UUID, Boolean> frenzyEvolved = new HashMap<>();
    private final Map<UUID, Integer> frenzyXp = new HashMap<>();

    GatherSkills(WeaponSkillsPlugin plugin, Skills skills) {
        this.plugin = plugin;
        this.skills = skills;
    }

    // ---------------------------------------------------------------- Pickaxe

    /** Bores a 3x3 tunnel ahead of the player, floor level with their feet. */
    boolean tunnelBore(Player player) {
        BlockFace facing = player.getFacing();
        Block feet = player.getLocation().getBlock();
        int depth = skills.evolved(player, Skill.TUNNEL_BORE) ? 5 : 3;
        List<Block> blocks = new ArrayList<>();
        for (int forward = 1; forward <= depth; forward++) {
            for (int side = -1; side <= 1; side++) {
                for (int up = 0; up <= 2; up++) {
                    // Sideways is the facing direction turned a quarter: (x, z) -> (-z, x).
                    Block block = feet.getRelative(facing.getModX() * forward - facing.getModZ() * side, up,
                            facing.getModZ() * forward + facing.getModX() * side);
                    if (minable(player, block)) {
                        blocks.add(block);
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            plugin.flash(player, plugin.lang().get("nothing-to-dig"));
            return false;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.8f, 0.6f);
        breakOverTime(player, Skill.TUNNEL_BORE, blocks, block -> minable(player, block));
        return true;
    }

    void minersRush(Player player) {
        boolean evolved = skills.evolved(player, Skill.MINERS_RUSH);
        int ticks = (200 + 10 * plugin.progress().bonusLevel(player, Skill.MINERS_RUSH)) * (evolved ? 3 : 2) / 2;
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, ticks, evolved ? 2 : 1));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.6f);
        player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0);
    }

    /** Breaks a ball of rock around the block the player looks at. */
    boolean shatter(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || !minable(player, target)) {
            plugin.flash(player, plugin.lang().get("look-at-minable"));
            return false;
        }
        int radius = skills.evolved(player, Skill.SHATTER) ? 3 : 2;
        List<Block> blocks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = target.getRelative(x, y, z);
                    if (x * x + y * y + z * z <= radius * radius + 1 && minable(player, block)) {
                        blocks.add(block);
                    }
                }
            }
        }
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        blocks.sort(Comparator.comparingDouble(block -> block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(center)));
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.6, 0.6, 0.6, 0);
        breakOverTime(player, Skill.SHATTER, blocks, block -> minable(player, block));
        return true;
    }

    /** Something the held pickaxe is the right tool for. Never containers, spawners or unbreakable blocks. */
    private static boolean minable(Player player, Block block) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        return !block.getType().isAir() && !block.isLiquid() && block.getType().getHardness() >= 0
                && block.isPreferredTool(tool) && !(block.getState(false) instanceof TileState);
    }

    // ------------------------------------------------------------ Woodcutting

    /**
     * Fells every natural tree in a 10x10 area around the player (evolved: 16x16) and replants a
     * sapling where each trunk stood. Logs that don't touch naturally grown leaves (houses, log
     * fences...) are left alone.
     */
    boolean clearCut(Player player) {
        boolean evolved = skills.evolved(player, Skill.CLEAR_CUT);
        int radius = evolved ? 8 : 5;
        int cap = evolved ? 600 : 300;
        Block center = player.getLocation().getBlock();
        Set<Block> seen = new HashSet<>();
        List<Block> logs = new ArrayList<>();
        Map<Block, Material> replant = new HashMap<>();
        for (int x = -radius; x < radius && logs.size() < cap; x++) {
            for (int z = -radius; z < radius && logs.size() < cap; z++) {
                for (int y = -3; y <= 6 && logs.size() < cap; y++) {
                    Block block = center.getRelative(x, y, z);
                    if (!Tag.LOGS.isTagged(block.getType()) || seen.contains(block)) {
                        continue;
                    }
                    List<Block> tree = tree(block, seen, cap - logs.size(), false);
                    logs.addAll(tree);
                    for (Block log : tree) {
                        Material sapling = saplingFor(log.getType());
                        if (sapling != null && canHold(sapling, log.getRelative(BlockFace.DOWN).getType())) {
                            replant.put(log, sapling); // a trunk base standing on soil
                        }
                    }
                }
            }
        }
        if (logs.isEmpty()) {
            plugin.flash(player, plugin.lang().get("no-trees"));
            return false;
        }
        // Bottom-up, so trunks fall from the base.
        logs.sort(Comparator.comparingInt(Block::getY));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_AXE_STRIP, 1f, 0.6f);
        world.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 0.6f);
        Fx.shockwave(plugin, player.getLocation().add(0, 0.2, 0), radius, 10, Particle.DUST, Fx.dust(0x8D6E63, 1.3f));
        breakOverTime(player, Skill.CLEAR_CUT, logs, block -> Tag.LOGS.isTagged(block.getType()));
        // Replant once the felling is done.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            int planted = 0;
            for (Map.Entry<Block, Material> entry : replant.entrySet()) {
                Block spot = entry.getKey();
                if (spot.getType().isAir() && canHold(entry.getValue(), spot.getRelative(BlockFace.DOWN).getType())) {
                    spot.setType(entry.getValue());
                    spot.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, spot.getLocation().add(0.5, 0.3, 0.5), 4, 0.3, 0.2, 0.3);
                    planted++;
                }
            }
            if (planted > 0 && player.isOnline()) {
                plugin.flash(player, plugin.lang().get("replanted", "count", planted));
            }
        }, logs.size() / BLOCKS_PER_TICK + 10L);
        return true;
    }

    /** The sapling (or fungus) that grows into a tree with this log, or null. */
    private static Material saplingFor(Material log) {
        String name = log.name();
        if (name.startsWith("STRIPPED_")) {
            return null;
        }
        return switch (log) {
            case MANGROVE_LOG -> Material.MANGROVE_PROPAGULE;
            case CRIMSON_STEM -> Material.CRIMSON_FUNGUS;
            case WARPED_STEM -> Material.WARPED_FUNGUS;
            default -> name.endsWith("_LOG") ? Material.matchMaterial(name.substring(0, name.length() - 4) + "_SAPLING") : null;
        };
    }

    private static boolean canHold(Material sapling, Material soil) {
        if (sapling == Material.CRIMSON_FUNGUS || sapling == Material.WARPED_FUNGUS) {
            return Tag.NYLIUM.isTagged(soil);
        }
        return Tag.DIRT.isTagged(soil);
    }

    /**
     * The connected logs starting at {@code start} (at most {@code cap}). Unless {@code always},
     * returns nothing if none of them touches naturally grown leaves.
     */
    private static List<Block> tree(Block start, Set<Block> seen, int cap, boolean always) {
        List<Block> logs = new ArrayList<>();
        boolean natural = always;
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && logs.size() < cap) {
            Block log = queue.poll();
            logs.add(log);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block next = log.getRelative(x, y, z);
                        if (Tag.LOGS.isTagged(next.getType())) {
                            if (seen.add(next)) {
                                queue.add(next);
                            }
                        } else if (!natural && next.getBlockData() instanceof Leaves leaves && !leaves.isPersistent()) {
                            natural = true;
                        }
                    }
                }
            }
        }
        return natural ? logs : List.of();
    }

    /** Grows every sapling (and nether fungus) around the player into a tree, as if bone-mealed until it grows. */
    boolean groveGrowth(Player player) {
        boolean evolved = skills.evolved(player, Skill.GROVE_GROWTH);
        int radius = evolved ? 12 : 8;
        Block center = player.getLocation().getBlock();
        List<Block> saplings = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -4; y <= 4; y++) {
                    Block block = center.getRelative(x, y, z);
                    Material type = block.getType();
                    if (Tag.SAPLINGS.isTagged(type) || type == Material.CRIMSON_FUNGUS || type == Material.WARPED_FUNGUS) {
                        saplings.add(block);
                    }
                }
            }
        }
        if (saplings.isEmpty()) {
            plugin.flash(player, plugin.lang().get("no-saplings"));
            return false;
        }
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.2f, 0.8f);
        world.playSound(player.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, 1f, 0.7f);
        Fx.shockwave(plugin, player.getLocation().add(0, 0.2, 0), radius, 12, Particle.HAPPY_VILLAGER, null);
        int grown = 0;
        for (Block sapling : saplings) {
            Material type = sapling.getType();
            for (int tries = 0; tries < 30 && sapling.getType() == type; tries++) {
                if (!sapling.applyBoneMeal(BlockFace.UP)) {
                    break; // no room to grow, or no light
                }
            }
            if (sapling.getType() != type) {
                grown++;
                world.spawnParticle(Particle.HAPPY_VILLAGER, sapling.getLocation().add(0.5, 1, 0.5), 15, 0.6, 1, 0.6);
            }
        }
        for (int i = 0; i < Math.min(grown, 5); i++) {
            plugin.progress().onHit(player, Skill.GROVE_GROWTH);
        }
        plugin.flash(player, plugin.lang().get("saplings-grown", "grown", grown, "total", saplings.size()));
        return true;
    }

    /** For a while, every log the player chops drops one more, and they chop faster. */
    void lumberFrenzy(Player player) {
        boolean evolved = skills.evolved(player, Skill.LUMBER_FRENZY);
        int ticks = (evolved ? 600 : 400) + 10 * plugin.progress().bonusLevel(player, Skill.LUMBER_FRENZY);
        frenzyUntil.put(player.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        frenzyEvolved.put(player.getUniqueId(), evolved);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, ticks, 1, false, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.5f);
        player.getWorld().spawnParticle(Particle.COMPOSTER, player.getLocation().add(0, 1, 0), 30, 0.5, 0.7, 0.5);
    }

    /** Lumber Frenzy: extra logs from every log the player breaks (evolved: two extra). */
    @EventHandler(ignoreCancelled = true)
    public void onLogDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        Long until = frenzyUntil.get(player.getUniqueId());
        if (until == null || until < System.currentTimeMillis() || !Tag.LOGS.isTagged(event.getBlockState().getType())) {
            return;
        }
        int extra = frenzyEvolved.getOrDefault(player.getUniqueId(), false) ? 2 : 1;
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            if (Tag.LOGS.isTagged(stack.getType())) {
                stack.setAmount(stack.getAmount() + extra);
                drop.setItemStack(stack);
                if (frenzyXp.merge(player.getUniqueId(), 1, Integer::sum) % 8 == 0) {
                    plugin.progress().onHit(player, Skill.LUMBER_FRENZY);
                }
                return;
            }
        }
    }

    void forget(Player player) {
        frenzyUntil.remove(player.getUniqueId());
        frenzyEvolved.remove(player.getUniqueId());
        frenzyXp.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------------ Shared

    /**
     * Breaks the blocks a few per tick, as the player, with the tool they hold. Stops if the player
     * leaves or no longer holds the skill's tool (it broke, or they switched items).
     */
    private void breakOverTime(Player player, Skill skill, List<Block> blocks, Predicate<Block> stillValid) {
        Skill.Weapon tool = skill.weapon() == Skill.Weapon.WOODCUTTING ? Skill.Weapon.AXE : skill.weapon();
        new BukkitRunnable() {
            int next;
            int broken;

            @Override
            public void run() {
                for (int n = 0; n < BLOCKS_PER_TICK && next < blocks.size(); n++) {
                    if (!player.isOnline() || Skill.Weapon.of(player.getInventory().getItemInMainHand().getType()) != tool) {
                        cancel();
                        return;
                    }
                    Block block = blocks.get(next++);
                    if (stillValid.test(block) && player.breakBlock(block) && ++broken % 8 == 0) {
                        plugin.progress().onHit(player, skill); // skill XP for the work done (capped per use)
                    }
                }
                if (next >= blocks.size()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    void shutdown() {
        temporaryEntities.forEach(Entity::remove);
        temporaryEntities.clear();
    }
}
