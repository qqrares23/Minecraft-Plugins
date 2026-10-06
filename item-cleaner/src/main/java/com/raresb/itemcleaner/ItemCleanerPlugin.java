package com.raresb.itemcleaner;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.raresb.itemcleaner.common.ConfigCommand;
import com.raresb.itemcleaner.common.ConfigFiles;
import com.raresb.itemcleaner.common.Integrations;
import com.raresb.itemcleaner.common.Lang;
import com.raresb.itemcleaner.common.Worlds;

/**
 * Clears dropped items from the ground every few minutes, warning players in chat first.
 * Fresh drops, death drops, glowing rare drops, renamed items and a few valuables are kept.
 */
public final class ItemCleanerPlugin extends JavaPlugin implements Listener {
    /** Where and when players died, so their drops survive a few clears. */
    private record Death(Location location, long time) {
    }

    private final Deque<Death> deaths = new ArrayDeque<>();
    private final Set<Material> keepMaterials = EnumSet.noneOf(Material.class);
    private final Lang lang = new Lang(this);
    private final ConfigCommand admin = new ConfigCommand(this, "/clearitems admin", () -> {
        loadSettings();
        restartTimer();
    });
    private BukkitTask timer;
    private int secondsLeft;

    @Override
    public void onEnable() {
        ConfigFiles.updateConfig(this);
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        restartTimer();
        Integrations.log(this, "MightyMobs", "its glowing rare drops are kept", "Professions", "its glowing rare finds are kept");
    }

    @Override
    public void onDisable() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private void loadSettings() {
        reloadConfig();
        lang.load();
        keepMaterials.clear();
        for (String name : getConfig().getStringList("keep-materials")) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                getLogger().warning("Unknown material in keep-materials: " + name);
            } else {
                keepMaterials.add(material);
            }
        }
        if (keepMaterials.contains(Material.SHULKER_BOX)) {
            for (Material material : Material.values()) {
                if (!material.isLegacy() && material.name().endsWith("_SHULKER_BOX")) {
                    keepMaterials.add(material);
                }
            }
        }
    }

    private int intervalSeconds() {
        return Math.max(1, getConfig().getInt("interval-minutes", 5)) * 60;
    }

    /** One tick per second counts down to the next clear and sends the warnings on the way. */
    private void restartTimer() {
        if (timer != null) {
            timer.cancel();
        }
        secondsLeft = intervalSeconds();
        timer = getServer().getScheduler().runTaskTimer(this, () -> {
            secondsLeft--;
            if (secondsLeft <= 0) {
                clear();
                secondsLeft = intervalSeconds();
            } else if (secondsLeft < intervalSeconds() && getConfig().getIntegerList("warning-seconds").contains(secondsLeft)) {
                warn(secondsLeft);
            }
        }, 20, 20);
    }

    private void warn(int seconds) {
        if (!getConfig().getBoolean("messages.warnings", true)) {
            return;
        }
        broadcast(lang.get("warning", "time", duration(seconds)));
        if (seconds <= 10 && getConfig().getBoolean("messages.sound", true)) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (!Worlds.enabled(this, player.getWorld())) {
                    continue;
                }
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.2f);
            }
        }
    }

    /** Removes every dropped item that isn't protected. Returns how many stacks were removed. */
    private int clear() {
        long now = System.currentTimeMillis();
        long protectMillis = getConfig().getLong("protect-death-drops-minutes", 5) * 60_000L;
        while (!deaths.isEmpty() && now - deaths.peekFirst().time() > protectMillis) {
            deaths.pollFirst();
        }
        int minAgeTicks = getConfig().getInt("min-age-seconds", 30) * 20;
        double radiusSquared = Math.pow(getConfig().getDouble("death-drop-radius", 6), 2);
        boolean keepGlowing = getConfig().getBoolean("keep-glowing", true);
        boolean keepNamed = getConfig().getBoolean("keep-named", true);

        int removed = 0;
        for (World world : getServer().getWorlds()) {
            if (!Worlds.enabled(this, world)) {
                continue;
            }
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (item.getTicksLived() < minAgeTicks || (keepGlowing && item.isGlowing())) {
                    continue;
                }
                ItemStack stack = item.getItemStack();
                if (keepMaterials.contains(stack.getType())
                        || (keepNamed && stack.hasItemMeta() && stack.getItemMeta().hasCustomName())
                        || nearDeath(item.getLocation(), radiusSquared)) {
                    continue;
                }
                item.remove();
                removed++;
            }
        }
        if (removed > 0 && getConfig().getBoolean("messages.result", true)) {
            broadcast(lang.get("cleared", "count", lang.count("unit.item", removed)));
        }
        getLogger().info("Cleared " + removed + " dropped item stacks.");
        return removed;
    }

    /** Chat message to the players in the worlds this plugin works in (and the console). */
    private void broadcast(Component message) {
        for (Player player : getServer().getOnlinePlayers()) {
            if (Worlds.enabled(this, player.getWorld())) {
                player.sendMessage(message);
            }
        }
        getServer().getConsoleSender().sendMessage(message);
    }

    private boolean nearDeath(Location location, double radiusSquared) {
        for (Death death : deaths) {
            if (death.location().getWorld() == location.getWorld() && death.location().distanceSquared(location) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!event.getKeepInventory() && !event.getDrops().isEmpty()) {
            deaths.addLast(new Death(event.getPlayer().getLocation(), System.currentTimeMillis()));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Next clear in " + secondsLeft + "s (every " + getConfig().getInt("interval-minutes")
                    + " min). /clearitems now | interval <minutes> | admin <reload|config|toggle|lang>", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "now" -> {
                int removed = clear();
                secondsLeft = intervalSeconds();
                sender.sendMessage(Component.text("Cleared " + removed + " item stacks; timer restarted.", NamedTextColor.GREEN));
            }
            case "interval" -> {
                int minutes;
                try {
                    minutes = Integer.parseInt(args.length > 1 ? args[1] : "");
                } catch (NumberFormatException e) {
                    minutes = 0;
                }
                if (minutes < 1 || minutes > 60) {
                    sender.sendMessage(Component.text("Usage: /clearitems interval <1-60>", NamedTextColor.RED));
                    return true;
                }
                getConfig().set("interval-minutes", minutes);
                saveConfig();
                restartTimer();
                sender.sendMessage(Component.text("Items are now cleared every " + minutes + " min.", NamedTextColor.GREEN));
            }
            case "reload" -> admin.handle(sender, new String[]{"reload"});
            case "admin" -> {
                if (!admin.handle(sender, Arrays.copyOfRange(args, 1, args.length))) {
                    sender.sendMessage(Component.text("/clearitems admin <reload|config|toggle|lang> ...", NamedTextColor.RED));
                }
            }
            default -> sender.sendMessage(Component.text("/clearitems [now|interval <minutes>|admin ...]", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return ConfigCommand.filter(List.of("now", "interval", "reload", "admin"), args[0]);
        }
        if (args[0].equalsIgnoreCase("admin")) {
            return admin.complete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("interval")) {
            return List.of("1", "5");
        }
        return List.of();
    }

    /** "1 minute" / "1 minut", "30 seconds" / "30 de secunde". */
    private String duration(int seconds) {
        if (seconds % 60 == 0) {
            return lang.count("unit.minute", seconds / 60);
        }
        return lang.count("unit.second", seconds);
    }
}
