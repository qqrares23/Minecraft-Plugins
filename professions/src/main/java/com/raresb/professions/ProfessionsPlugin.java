package com.raresb.professions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
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
import org.bukkit.plugin.java.JavaPlugin;
import com.raresb.professions.common.ConfigCommand;
import com.raresb.professions.common.ConfigFiles;
import com.raresb.professions.common.Integrations;
import com.raresb.professions.common.Lang;
import com.raresb.professions.common.Perms;
import com.raresb.professions.common.Worlds;
import java.util.Arrays;

public final class ProfessionsPlugin extends JavaPlugin implements Listener {
    private PlacedBlocks placed;
    private GatherListener gather;
    private PerkListener perks;
    /** Player data read on every block broken or mob killed, kept in memory (see DataCache). */
    private final DataCache data = new DataCache();
    private final java.util.Map<String, NamespacedKey> keys = new java.util.HashMap<>();
    private final Lang lang = new Lang(this);
    private final ConfigCommand config = new ConfigCommand(this, "/professions admin", this::loadSettings);
    private static ProfessionsPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        ConfigFiles.updateConfig(this);
        loadSettings();
        Perms.register(this, "professions.profession", Arrays.stream(Profession.values()).map(Profession::id).toList(), "profession");
        placed = new PlacedBlocks(this);
        gather = new GatherListener(this);
        perks = new PerkListener(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gather, this);
        getServer().getPluginManager().registerEvents(perks, this);
        SmithingListener smithing = new SmithingListener(this);
        getServer().getPluginManager().registerEvents(smithing, this);
        getServer().getScheduler().runTaskTimer(this, smithing::fieldRepair, 600, 600);
        Integrations.log(this, "Hud", "sidebar shows profession progress, custom menu icons",
                "Timberella", "every log it fells counts", "VeinMiner", "every ore it mines counts",
                "ItemCleaner", "keeps glowing rare finds", "WeaponSkills", "skill-broken blocks count");
    }

    private void loadSettings() {
        reloadConfig();
        lang.load();
    }

    Lang lang() {
        return lang;
    }

    /** Text from the language file for enum descriptions; the enum's own text if the key is missing. */
    static String text(String key, String fallback) {
        return instance != null && instance.lang.has(key) ? instance.lang.raw(key) : fallback;
    }

    static List<String> textList(String key, List<String> fallback) {
        return instance != null && instance.lang.has(key) ? instance.lang.rawList(key) : fallback;
    }

    /** professions.<id>.enabled in config.yml. */
    boolean enabled(Profession profession) {
        return getConfig().getBoolean("professions." + profession.id() + ".enabled", true);
    }

    /** The profession works for this player here: enabled, allowed by permission and in an enabled world. */
    boolean active(Player player, Profession profession) {
        return enabled(profession) && player.hasPermission("professions.use")
                && player.hasPermission("professions.profession." + profession.id()) && Worlds.enabled(this, player.getWorld());
    }

    private double professionSetting(Profession profession, String key) {
        return getConfig().getDouble("professions." + profession.id() + "." + key, 1.0);
    }

    @Override
    public void onDisable() {
        if (perks != null) {
            perks.shutdown();
        }
    }

    PlacedBlocks placed() {
        return placed;
    }

    GatherListener gather() {
        return gather;
    }

    PerkListener perks() {
        return perks;
    }

    // --- Perks (one of two per tier, saved in the player's data) ---

    private NamespacedKey perkKey(Profession profession, int tier) {
        return keys.computeIfAbsent("perk_" + profession.id() + "_" + tier, n -> new NamespacedKey(this, n));
    }

    /** Drops a player's cached data when they leave. */
    void forget(Player player) {
        data.forget(player);
    }

    /** The perk the player picked at a tier, or null. */
    Perk chosenPerk(Player player, Profession profession, int tier) {
        String stored = data.getString(player, perkKey(profession, tier));
        for (Perk perk : Perk.of(profession, tier)) {
            if (perk.id().equals(stored)) {
                return perk;
            }
        }
        return null;
    }

    void choosePerk(Player player, Perk perk) {
        data.setString(player, perkKey(perk.profession(), perk.tier()), perk.id());
    }

    /** Whether the perk works for the player: picked, and the profession has reached its level. */
    boolean has(Player player, Perk perk) {
        return level(player, perk.profession()) >= perk.tier() && chosenPerk(player, perk.profession(), perk.tier()) == perk;
    }

    /** Perk tiers reached but not picked yet. */
    int openPerks(Player player, Profession profession) {
        int level = storedLevel(player, profession);
        int open = 0;
        for (int tier : Perk.TIERS) {
            if (level >= tier && chosenPerk(player, profession, tier) == null) {
                open++;
            }
        }
        return open;
    }

    // --- XP and levels (saved in the player's own data) ---

    private NamespacedKey key(Profession profession) {
        return keys.computeIfAbsent("xp_" + profession.id(), n -> new NamespacedKey(this, n));
    }

    int maxLevel() {
        return getConfig().getInt("max-level", 50);
    }

    int xpForNext(int level) {
        return getConfig().getInt("xp-base", 50) + getConfig().getInt("xp-per-level", 15) * level;
    }

    double xp(Player player, Profession profession) {
        return data.getDouble(player, key(profession));
    }

    /** The level that counts for bonuses and perks: 0 while the profession doesn't work for the player (see active). */
    int level(Player player, Profession profession) {
        return active(player, profession) ? storedLevel(player, profession) : 0;
    }

    /** The level from the player's XP, whether or not the profession is active. */
    int storedLevel(Player player, Profession profession) {
        double xp = xp(player, profession);
        int level = 1;
        while (level < maxLevel() && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
        }
        return level;
    }

    /** XP inside the current level and XP needed for the next (0 at max level). */
    double[] progress(Player player, Profession profession) {
        double xp = xp(player, profession);
        int level = 1;
        while (level < maxLevel() && xp >= xpForNext(level)) {
            xp -= xpForNext(level);
            level++;
        }
        return level >= maxLevel() ? new double[] {0, 0} : new double[] {xp, xpForNext(level)};
    }

    /** MagicEnchants enchantments looked up by id (empty when that plugin isn't installed). */
    private final java.util.Map<String, java.util.Optional<org.bukkit.enchantments.Enchantment>> magicEnchants = new java.util.HashMap<>();

    /** The level of a MagicEnchants enchantment on an item (0 when that plugin isn't installed). */
    int magicLevel(org.bukkit.inventory.ItemStack item, String id) {
        var enchantment = magicEnchants.computeIfAbsent(id, key -> java.util.Optional.ofNullable(io.papermc.paper.registry.RegistryAccess
                .registryAccess().getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                .get(net.kyori.adventure.key.Key.key("magicenchants", key)))).orElse(null);
        return enchantment == null || item == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    /** Gives profession XP, shows it above the hotbar and celebrates level-ups. */
    void addXp(Player player, Profession profession, double amount) {
        if (amount <= 0 || !active(player, profession)) {
            return;
        }
        int before = storedLevel(player, profession);
        if (before >= maxLevel()) {
            return;
        }
        amount *= getConfig().getDouble("xp-multiplier", 1.0) * professionSetting(profession, "xp-multiplier");
        // Scholar (a MagicEnchants enchantment on the tool): +10% profession XP per level.
        amount *= 1 + 0.1 * magicLevel(player.getInventory().getItemInMainHand(), "scholar");
        if (profession == Profession.WOODCUTTING) {
            // Timbersong (a MagicEnchants enchantment on the axe): +15% Woodcutting XP per level.
            amount *= 1 + 0.15 * magicLevel(player.getInventory().getItemInMainHand(), "timbersong");
        }
        if (profession == Profession.FISHING) {
            // Lure of the Deep (MagicEnchants, fishing rod): +15% Fishing XP per level.
            amount *= 1 + 0.15 * magicLevel(player.getInventory().getItemInMainHand(), "lure_of_the_deep");
        }
        if (profession == Profession.HUNTING) {
            // Bloodhound (MagicEnchants, sword/axe/spear): +15% Hunting XP per level.
            amount *= 1 + 0.15 * magicLevel(player.getInventory().getItemInMainHand(), "bloodhound");
        }
        data.setDouble(player, key(profession), xp(player, profession) + amount);
        int after = storedLevel(player, profession);
        double[] progress = progress(player, profession);
        if (getConfig().getBoolean("messages.xp-action-bar", true)) {
            player.sendActionBar(Component.text(profession.icon() + " +" + format(amount) + " " + profession.displayName() + " XP", profession.color())
                    .append(progress[1] > 0 ? Component.text("  (" + (int) progress[0] + "/" + (int) progress[1] + ")", NamedTextColor.GRAY)
                            : lang.get("xp-max")));
        }
        if (after > before) {
            player.showTitle(Title.title(
                    Component.text(profession.icon() + " " + profession.displayName(), profession.color(), TextDecoration.BOLD),
                    lang.get(after >= maxLevel() ? "level-up.subtitle-max" : "level-up.subtitle", "level", after),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(500))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0);
            player.sendMessage(lang.get("level-up.chat", "profession", Component.text(profession.displayName(), profession.color()), "level", after));
            for (int tier : Perk.TIERS) {
                if (tier > before && tier <= after) {
                    player.sendMessage(lang.get("level-up.new-perk", "profession", profession.displayName()));
                }
            }
        }
    }

    private static String format(double amount) {
        return amount == Math.floor(amount) ? Integer.toString((int) amount) : String.format(Locale.ROOT, "%.1f", amount);
    }

    // --- Perks shared by several professions ---

    /** Chance (0-1) for a random find: {@code base} at level 1, growing by {@code perLevel}. */
    double findChance(Player player, Profession profession, double base, double perLevel) {
        double chance = base + perLevel * (level(player, profession) - 1);
        Perk boost = switch (profession) {
            case WOODCUTTING -> Perk.FOREST_BOUNTY;
            case MINING -> Perk.MASTER_MINER;
            case DIGGING -> Perk.ARCHAEOLOGIST;
            case FARMING -> Perk.GOLDEN_TOUCH;
            case FORAGING -> Perk.KEEN_EYE;
            case FISHING -> Perk.TREASURE_SENSE;
            default -> null;
        };
        if (profession == Profession.MINING) {
            // Prospector (a MagicEnchants enchantment on the pickaxe): x1.25 Mining find chance per level.
            chance *= 1 + 0.25 * magicLevel(player.getInventory().getItemInMainHand(), "prospector");
        }
        if (profession == Profession.DIGGING) {
            // Delver (MagicEnchants, shovel): x1.25 Digging find chance per level.
            chance *= 1 + 0.25 * magicLevel(player.getInventory().getItemInMainHand(), "delver");
        }
        if (profession == Profession.FARMING) {
            // Bountiful (MagicEnchants, hoe): x1.25 Farming find chance per level.
            chance *= 1 + 0.25 * magicLevel(player.getInventory().getItemInMainHand(), "bountiful");
        }
        return boost != null && has(player, boost) ? chance * 1.3 : chance;
    }

    /** Chance for double drops: 0.6% per level (30% at level 50). */
    double doubleChance(Player player, Profession profession) {
        if (!active(player, profession)) {
            return 0;
        }
        double chance = 0.006 * level(player, profession);
        Perk bonus = switch (profession) {
            case MINING -> Perk.PROSPECTOR;
            case DIGGING -> Perk.DEEP_DIGGER;
            case FARMING -> Perk.BUMPER_CROP;
            case HUNTING -> Perk.APEX;
            case HUSBANDRY -> Perk.FLEECE;
            default -> null;
        };
        if (bonus != null && has(player, bonus)) {
            chance += bonus == Perk.FLEECE ? 0.10 : 0.05;
        }
        return chance;
    }

    static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    /** Woodcutting: chance for an extra log, 2% per level (every log doubles at level 50). */
    double extraLogChance(Player player) {
        return Math.min(1, 0.02 * level(player, Profession.WOODCUTTING));
    }

    /** Woodcutting: chance for a third log, 2% per level above 30 (40% at level 50). */
    double thirdLogChance(Player player) {
        return Math.max(0, 0.02 * (level(player, Profession.WOODCUTTING) - 30)) + (has(player, Perk.HEARTWOOD) ? 0.1 : 0);
    }

    /** Rolls a find that fits the {@code source} block from the table and drops it at {@code at} with a sparkle and a sound. */
    void tryFind(Player player, Profession profession, List<Finds.Find> table, double chance, Location at, Material source) {
        if (!active(player, profession) || !roll(chance * professionSetting(profession, "find-multiplier"))) {
            return;
        }
        Finds.Find find = Finds.roll(table, level(player, profession), source);
        if (find != null) {
            dropFind(player, find.item().apply(source), find.rarity(), at);
        }
    }

    void dropFind(Player player, ItemStack item, Finds.Rarity rarity, Location at) {
        Item dropped = at.getWorld().dropItemNaturally(at, item);
        dropped.setGlowing(rarity == Finds.Rarity.RARE || rarity == Finds.Rarity.VERY_RARE);
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 12, 0.3, 0.3, 0.3, 0);
        at.getWorld().spawnParticle(Particle.END_ROD, at, 6, 0.2, 0.3, 0.2, 0.03);
        switch (rarity) {
            case COMMON, UNCOMMON -> at.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            case RARE -> {
                at.getWorld().playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 20, 0.3, 0.4, 0.3, 0.2);
                if (getConfig().getBoolean("messages.rare-finds", true)) {
                    player.sendMessage(lang.get("find.rare", "item", item.effectiveName()));
                }
            }
            case VERY_RARE -> {
                at.getWorld().playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 50, 0.4, 0.6, 0.4, 0.3);
                if (getConfig().getBoolean("messages.rare-finds", true)) {
                    player.sendMessage(lang.get("find.very-rare", "item", item.effectiveName()));
                }
            }
        }
    }

    // --- /professions menu ---

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private Profession profession; // null = the list of professions

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24};

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, label, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have professions. Admins: /" + label + " admin ...");
            return true;
        }
        if (!player.hasPermission("professions.use")) {
            player.sendMessage(lang.get("no-permission"));
            return true;
        }
        openMenu(player);
        return true;
    }

    // --- Admin commands ---

    private void admin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("professions.admin")) {
            sender.sendMessage(lang.get("no-permission"));
            return;
        }
        if (config.handle(sender, Arrays.copyOfRange(args, 1, args.length))) {
            return;
        }
        String usage = "/" + label + " admin setlevel <player> <profession|all> <level> | addxp <player> <profession> <amount>"
                + " | reset <player> [profession] | info <player> | reload | config <get|set|reset|list> | toggle <feature> | lang <code>";
        if (args.length < 2) {
            sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "info" -> {
                Player target = args.length > 2 ? getServer().getPlayer(args[2]) : null;
                if (target == null) {
                    sender.sendMessage(Component.text("Usage: /" + label + " admin info <online player>", NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(target.getName() + " - professions", NamedTextColor.GOLD));
                for (Profession profession : Profession.values()) {
                    StringBuilder perks = new StringBuilder();
                    for (int tier : Perk.TIERS) {
                        Perk perk = chosenPerk(target, profession, tier);
                        if (perk != null) {
                            perks.append(perks.length() == 0 ? "" : ", ").append(perk.displayName());
                        }
                    }
                    sender.sendMessage(Component.text("  " + profession.displayName() + " " + storedLevel(target, profession)
                            + (active(target, profession) ? "" : " (inactive)") + (perks.length() == 0 ? "" : " - " + perks), NamedTextColor.GRAY));
                }
            }
            case "setlevel" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                Player target = getServer().getPlayer(args[2]);
                List<Profession> professions = parseProfessions(args[3]);
                Integer level = parseInt(args[4]);
                if (target == null || professions.isEmpty() || level == null) {
                    sender.sendMessage(Component.text("Unknown player, profession or level.", NamedTextColor.RED));
                    return;
                }
                int clamped = Math.max(1, Math.min(maxLevel(), level));
                for (Profession profession : professions) {
                    setXp(target, profession, totalXpFor(clamped));
                }
                sender.sendMessage(Component.text("Set " + args[3] + " to level " + clamped + " for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "addxp" -> {
                if (args.length < 5) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                Player target = getServer().getPlayer(args[2]);
                List<Profession> professions = parseProfessions(args[3]);
                Integer amount = parseInt(args[4]);
                if (target == null || professions.size() != 1 || amount == null) {
                    sender.sendMessage(Component.text("Unknown player, profession or amount.", NamedTextColor.RED));
                    return;
                }
                addXp(target, professions.get(0), amount / getConfig().getDouble("xp-multiplier", 1.0));
                sender.sendMessage(Component.text("Gave " + amount + " " + professions.get(0).displayName() + " XP to " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                Player target = getServer().getPlayer(args[2]);
                List<Profession> professions = args.length >= 4 ? parseProfessions(args[3]) : List.of(Profession.values());
                if (target == null || professions.isEmpty()) {
                    sender.sendMessage(Component.text("Unknown player or profession.", NamedTextColor.RED));
                    return;
                }
                professions.forEach(profession -> setXp(target, profession, 0));
                sender.sendMessage(Component.text("Reset " + (args.length >= 4 ? args[3] : "all professions") + " for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
        }
    }

    /** Total XP needed to reach a level from level 1. */
    private double totalXpFor(int level) {
        double total = 0;
        for (int n = 1; n < level; n++) {
            total += xpForNext(n);
        }
        return total;
    }

    private void setXp(Player player, Profession profession, double xp) {
        data.setDouble(player, key(profession), xp);
    }

    private static List<Profession> parseProfessions(String id) {
        if (id.equalsIgnoreCase("all")) {
            return List.of(Profession.values());
        }
        for (Profession profession : Profession.values()) {
            if (profession.id().equalsIgnoreCase(id)) {
                return List.of(profession);
            }
        }
        return List.of();
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("professions.admin")) {
            return List.of();
        }
        List<String> options = switch (args.length) {
            case 1 -> List.of("admin");
            case 2 -> {
                List<String> subs = new ArrayList<>(List.of("setlevel", "addxp", "reset", "info"));
                subs.addAll(ConfigCommand.SUBCOMMANDS);
                yield args[0].equalsIgnoreCase("admin") ? subs : List.of();
            }
            case 3 -> ConfigCommand.SUBCOMMANDS.contains(args[1].toLowerCase(Locale.ROOT))
                    ? config.complete(Arrays.copyOfRange(args, 1, args.length))
                    : getServer().getOnlinePlayers().stream().map(Player::getName).toList();
            case 4 -> {
                if (ConfigCommand.SUBCOMMANDS.contains(args[1].toLowerCase(Locale.ROOT))) {
                    yield config.complete(Arrays.copyOfRange(args, 1, args.length));
                }
                List<String> ids = new ArrayList<>();
                ids.add("all");
                for (Profession profession : Profession.values()) {
                    ids.add(profession.id());
                }
                yield ids;
            }
            default -> args.length > 1 && ConfigCommand.SUBCOMMANDS.contains(args[1].toLowerCase(Locale.ROOT))
                    ? config.complete(Arrays.copyOfRange(args, 1, args.length)) : List.of();
        };
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private static final int MENU_SIZE = 54;
    private static final int BACK_SLOT = 0;
    /** Perk tiers 10..50: the label row, choice A and choice B. */
    private static final int[] TIER_LABELS = {20, 21, 22, 23, 24};
    private static final int[] PERK_A = {29, 30, 31, 32, 33};
    private static final int[] PERK_B = {38, 39, 40, 41, 42};

    private void openMenu(Player player) {
        Holder holder = new Holder();
        holder.inventory = getServer().createInventory(holder, MENU_SIZE, lang.get("menu.title"));
        drawMenu(holder, player);
        player.openInventory(holder.inventory);
    }

    private void drawMenu(Holder holder, Player player) {
        Inventory inventory = holder.inventory;
        ItemStack pane = named(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < MENU_SIZE; i++) {
            inventory.setItem(i, pane);
        }
        if (holder.profession == null) {
            List<Profession> professions = shown(player);
            for (int i = 0; i < professions.size() && i < SLOTS.length; i++) {
                inventory.setItem(SLOTS[i], icon(player, professions.get(i)));
            }
            inventory.setItem(40, named(Material.BOOK, lang.item("menu.about.name"), lang.lore("menu.about.lore")));
            return;
        }
        Profession profession = holder.profession;
        int level = storedLevel(player, profession);
        inventory.setItem(BACK_SLOT, named(Material.ARROW, lang.item("menu.back"), List.of()));
        inventory.setItem(4, icon(player, profession));
        inventory.setItem(13, named(Material.ENCHANTED_BOOK, lang.item("menu.perks.name"), lang.lore("menu.perks.lore")));
        for (int i = 0; i < Perk.TIERS.length; i++) {
            int tier = Perk.TIERS[i];
            boolean reached = level >= tier;
            inventory.setItem(TIER_LABELS[i], named(reached ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    lang.item(reached ? "menu.tier-reached" : "menu.tier", "level", tier), List.of()));
            Perk[] pair = Perk.of(profession, tier);
            inventory.setItem(PERK_A[i], perkButton(player, pair[0], level));
            inventory.setItem(PERK_B[i], perkButton(player, pair[1], level));
        }
    }

    private ItemStack perkButton(Player player, Perk perk, int level) {
        boolean reached = level >= perk.tier();
        boolean chosen = chosenPerk(player, perk.profession(), perk.tier()) == perk;
        List<Component> lore = new ArrayList<>(wrap(perk.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (!reached) {
            lore.add(lang.item("menu.perk-locked", "level", perk.tier()));
        } else if (chosen) {
            lore.add(lang.item("menu.perk-chosen"));
        } else {
            lore.add(lang.item("menu.perk-pick"));
        }
        ItemStack icon = named(reached ? perk.icon() : Material.GRAY_DYE, text(perk.displayName(),
                chosen ? NamedTextColor.LIGHT_PURPLE : reached ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true), lore);
        icon.editMeta(meta -> meta.setEnchantmentGlintOverride(chosen));
        if (reached) {
            customIcon(icon, player, "perk/" + perk.id());
        }
        return icon;
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

    private ItemStack icon(Player player, Profession profession) {
        int level = storedLevel(player, profession);
        double[] progress = progress(player, profession);
        List<Component> lore = new ArrayList<>(wrap(profession.howToLevel(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(lang.item("menu.level", "level", level, "max", maxLevel()));
        if (progress[1] > 0) {
            int filled = (int) Math.round(10 * progress[0] / progress[1]);
            lore.add(text("■".repeat(filled), NamedTextColor.GREEN).append(text("■".repeat(10 - filled), NamedTextColor.DARK_GRAY))
                    .append(text("  " + (int) progress[0] + "/" + (int) progress[1] + " XP", NamedTextColor.DARK_GRAY)));
        } else {
            lore.add(lang.item("menu.max-level"));
        }
        lore.add(Component.empty());
        lore.add(lang.item("menu.bonuses"));
        for (String perk : profession.perks()) {
            lore.add(text(" " + perk, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.addAll(currentNumbers(player, profession, level));
        int open = openPerks(player, profession);
        if (open > 0) {
            lore.add(Component.empty());
            lore.add(lang.item("menu.open-perks", "count", lang.count("unit.perk", open)));
        }
        ItemStack icon = named(profession.iconItem(), text(profession.icon() + " " + profession.displayName(), profession.color())
                .decoration(TextDecoration.BOLD, true).append(text("  Lvl " + level, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, false)), lore);
        icon.editMeta(meta -> meta.setMaxStackSize(99));
        icon.setAmount(Math.min(99, level));
        customIcon(icon, player, "profession/" + profession.id());
        return icon;
    }

    /** The perk numbers at the player's current level. */
    private List<Component> currentNumbers(Player player, Profession profession, int level) {
        if (!active(player, profession)) {
            return List.of(lang.item("menu.inactive"));
        }
        String doubles = pct(doubleChance(player, profession));
        String unlocked = lang.raw("stat.unlocked");
        return switch (profession) {
            case WOODCUTTING -> List.of(stat("find-log", pct(findChance(player, profession, 0.01, 0.001))),
                    stat("extra-log", pct(extraLogChance(player))), stat("third-log", level > 30 ? pct(thirdLogChance(player)) : lang.raw("stat.from-31")));
            case MINING -> List.of(stat("find-block", pct(findChance(player, profession, 0.005, 0.0006))), stat("double-ore", doubles),
                    stat("haste", level >= 25 ? unlocked : lang.raw("stat.at-25")));
            case DIGGING -> List.of(stat("find-block", pct(findChance(player, profession, 0.008, 0.0008))), stat("double-harvest", doubles));
            case FARMING -> List.of(stat("find-harvest", pct(findChance(player, profession, 0.015, 0.001))), stat("double-harvest", doubles),
                    stat("replant", level >= 20 ? unlocked : lang.raw("stat.at-20")));
            case FORAGING -> List.of(stat("find-plant", pct(findChance(player, profession, 0.02, 0.001))));
            case FISHING -> List.of(stat("bite-time", "-" + pct(GatherListener.fishingSpeedup(level))), stat("extra-fish", pct(0.01 * level)),
                    stat("treasure", pct(findChance(player, profession, 0.03, 0.002))));
            case HUNTING -> List.of(stat("double-loot", doubles), stat("head", pct(0.0002 * level)), stat("extra-xp", "+" + (2 * level) + "%"));
            case HUSBANDRY -> List.of(stat("twins", pct(0.006 * level)), stat("breed-cooldown", "-" + pct(0.01 * level)), stat("extra-wool", doubles));
            case SMELTING -> List.of(stat("extra-output", pct(0.004 * level)));
            case ENCHANTING -> List.of(stat("refund", pct(0.008 * level)));
            case ALCHEMY -> List.of(stat("keep-ingredient", pct(0.008 * level)));
            case SMITHING -> List.of(stat("repair", "+" + pct(SmithingListener.repairBonus(this, player))));
        };
    }

    private Component stat(String key, String value) {
        return lang.item("stat.line", "name", lang.raw("stat." + key), "value", value);
    }

    /** Professions shown in the menu: enabled ones the player has permission for. */
    private List<Profession> shown(Player player) {
        return Arrays.stream(Profession.values())
                .filter(p -> enabled(p) && player.hasPermission("professions.profession." + p.id())).toList();
    }

    private static String pct(double chance) {
        return String.format(Locale.ROOT, "%.1f%%", chance * 100);
    }

    private static Component text(String content, TextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Set by the Hud plugin while the player has the server resource pack loaded. */
    private static final NamespacedKey HAS_PACK = new NamespacedKey("hud", "pack");

    /** Swaps in the custom icon from the resource pack, for players who have it (and only if the pack has that icon). */
    private void customIcon(ItemStack item, Player player, String model) {
        if (player.getPersistentDataContainer().has(HAS_PACK, PersistentDataType.BYTE) && PackModels.has(this, model)) {
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= MENU_SIZE) {
            return;
        }
        int slot = event.getRawSlot();
        if (holder.profession == null) {
            List<Profession> professions = shown(player);
            for (int i = 0; i < professions.size() && i < SLOTS.length; i++) {
                if (SLOTS[i] == slot) {
                    holder.profession = professions.get(i);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    drawMenu(holder, player);
                }
            }
            return;
        }
        if (slot == BACK_SLOT) {
            holder.profession = null;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            drawMenu(holder, player);
            return;
        }
        for (int i = 0; i < Perk.TIERS.length; i++) {
            if (slot != PERK_A[i] && slot != PERK_B[i]) {
                continue;
            }
            Perk perk = Perk.of(holder.profession, Perk.TIERS[i])[slot == PERK_A[i] ? 0 : 1];
            if (storedLevel(player, holder.profession) < perk.tier()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                player.sendActionBar(lang.get("perk-locked", "level", perk.tier()));
            } else if (chosenPerk(player, holder.profession, perk.tier()) != perk) {
                choosePerk(player, perk);
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.3f);
                player.sendActionBar(lang.get("perk-chosen", "perk", Component.text(perk.displayName(), holder.profession.color())));
            }
            drawMenu(holder, player);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
