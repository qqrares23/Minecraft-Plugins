package com.raresb.magicenchants;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import com.raresb.magicenchants.common.ConfigCommand;
import com.raresb.magicenchants.common.ConfigFiles;
import com.raresb.magicenchants.common.Integrations;
import com.raresb.magicenchants.common.Lang;
import com.raresb.magicenchants.common.Worlds;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

public final class MagicEnchantsPlugin extends JavaPlugin {
    private final Map<MagicEnchant, Enchantment> enchantments = new EnumMap<>(MagicEnchant.class);
    /** Marks Soul Fragment items (WeaponSkills reads this key too). */
    private final NamespacedKey soulFragmentKey = new NamespacedKey("magicenchants", "soul_fragment");
    /** Set while this plugin deals bonus damage itself, so that damage doesn't trigger enchantments again. */
    private boolean dealingBonusDamage;
    /** Entity tag (shared by our plugins) set while one of our plugins deals skill/splash/bonus damage: no area effects chain off it. */
    static final String NO_CHAIN = "raresb_no_chain";
    private final Lang lang = new Lang(this);
    private final ConfigCommand admin = new ConfigCommand(this, "/enchants admin", this::loadSettings);
    private EnchantSettings settings;

    @Override
    public void onEnable() {
        ConfigFiles.updateConfig(this);
        loadSettings();
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (MagicEnchant enchant : MagicEnchant.values()) {
            Enchantment enchantment = registry.get(enchant.key());
            if (enchantment == null) {
                getLogger().severe("Enchantment " + enchant.key().key() + " is not registered; its effect is disabled.");
                continue;
            }
            enchantments.put(enchant, enchantment);
        }
        getLogger().info("Loaded " + enchantments.size() + " magic enchantments.");

        var manager = getServer().getPluginManager();
        manager.registerEvents(new WeaponEffects(this), this);
        manager.registerEvents(new ArmorEffects(this), this);
        manager.registerEvents(new RangedEffects(this), this);
        manager.registerEvents(new ToolEffects(this), this);
        manager.registerEvents(new GearEffects(this), this);
        manager.registerEvents(new MagicEffects(this), this);
        manager.registerEvents(new DefenseEffects(this), this);
        manager.registerEvents(new CompanionEffects(this), this);
        manager.registerEvents(new CounterEffects(this), this);
        manager.registerEvents(new TableFocus(this), this);
        manager.registerEvents(new StaffAnvil(this), this);
        EnchantMenu menu = new EnchantMenu(this);
        manager.registerEvents(menu, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("enchants", "List the magic enchantments and what they do.", new BasicCommand() {
                    @Override
                    public void execute(CommandSourceStack source, String[] args) {
                        CommandSender sender = source.getSender();
                        if (args.length > 0 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("magicenchants.admin")) {
                            adminCommand(sender, Arrays.copyOfRange(args, 1, args.length));
                        } else if (!sender.hasPermission("magicenchants.menu")) {
                            sender.sendMessage(lang.get("no-permission"));
                        } else if (sender instanceof Player player) {
                            menu.open(player);
                        } else {
                            listEnchants(sender);
                        }
                    }

                    @Override
                    public Collection<String> suggest(CommandSourceStack source, String[] args) {
                        if (!source.getSender().hasPermission("magicenchants.admin")) {
                            return List.of();
                        }
                        if (args.length <= 1) {
                            return ConfigCommand.filter(List.of("admin"), args.length == 0 ? "" : args[0]);
                        }
                        String[] rest = Arrays.copyOfRange(args, 1, args.length);
                        if (rest.length == 1) {
                            List<String> options = new java.util.ArrayList<>(ConfigCommand.SUBCOMMANDS);
                            options.addAll(List.of("give", "list"));
                            return ConfigCommand.filter(options, rest[0]);
                        }
                        if (rest[0].equalsIgnoreCase("give")) {
                            if (rest.length == 2) {
                                return ConfigCommand.filter(getServer().getOnlinePlayers().stream().map(Player::getName).toList(), rest[1]);
                            }
                            if (rest.length == 3) {
                                return ConfigCommand.filter(Arrays.stream(MagicEnchant.values()).map(EnchantSettings::id).toList(), rest[2]);
                            }
                            return List.of();
                        }
                        return admin.complete(rest);
                    }
                }));
        Integrations.log(this, "WeaponSkills", "Skill Surge, Arcane Echo, Cooldown Siphon, Afterimage, Resonance; Soul Fragments feed skills",
                "Paths", "Pathbound, Battle Focus and the staff enchantments", "Professions", "Scholar, Delver, Bountiful, Lure of the Deep, Bloodhound",
                "Hud", "sidebar lists your enchantments; Combo Keeper, Crescendo",
                "MightyMobs", "Mightslayer, Blood Moon's Gift, Crimson Ward, Anchor, Purifier, rare book drops");
    }

    private void loadSettings() {
        reloadConfig();
        lang.load();
        settings = EnchantSettings.of((YamlConfiguration) getConfig());
    }

    Lang lang() {
        return lang;
    }

    EnchantSettings settings() {
        return settings;
    }

    boolean enabledIn(org.bukkit.World world) {
        return Worlds.enabled(this, world);
    }

    private void adminCommand(CommandSender sender, String[] args) {
        if (admin.handle(sender, args)) {
            if (args.length > 0 && !args[0].equalsIgnoreCase("lang")) {
                sender.sendMessage(Component.text("Note: max-level, weight, enchanting-table, loot and trades apply after a restart;"
                        + " enabled, worlds and language apply now.", NamedTextColor.GRAY));
            }
            return;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "list" -> listEnchants(sender);
            case "give" -> {
                Player target = args.length > 1 ? getServer().getPlayer(args[1]) : null;
                MagicEnchant enchant = null;
                if (args.length > 2) {
                    try {
                        enchant = MagicEnchant.valueOf(args[2].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        // unknown
                    }
                }
                Enchantment enchantment = enchant == null ? null : enchantments.get(enchant);
                if (target == null || enchantment == null) {
                    sender.sendMessage(Component.text("Usage: /enchants admin give <player> <enchantment> [level] - gives an enchanted book",
                            NamedTextColor.RED));
                    return;
                }
                int level = 1;
                try {
                    level = args.length > 3 ? Integer.parseInt(args[3]) : enchantment.getMaxLevel();
                } catch (NumberFormatException ignored) {
                    // level 1
                }
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                int chosen = Math.max(1, level);
                book.editMeta(org.bukkit.inventory.meta.EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(enchantment, chosen, true));
                target.getInventory().addItem(book).values().forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
                sender.sendMessage(Component.text("Gave " + target.getName() + " a " + enchant.displayName() + " " + chosen + " book.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("/enchants admin reload | config <get|set|reset|list> | toggle <feature>"
                    + " | lang <code> | give <player> <enchantment> [level] | list", NamedTextColor.YELLOW));
        }
    }

    // --- Helpers shared by the effect listeners ---

    /** The registered enchantment, or null if it failed to register. */
    Enchantment enchantment(MagicEnchant enchant) {
        return enchantments.get(enchant);
    }

    int level(ItemStack item, MagicEnchant enchant) {
        Enchantment enchantment = enchantments.get(enchant);
        if (enchantment == null || item == null || item.isEmpty() || !settings.enabled(enchant)) {
            return 0;
        }
        return item.getEnchantmentLevel(enchantment);
    }

    /** The highest level of an enchantment across the armor an entity wears. */
    int armorLevel(LivingEntity entity, MagicEnchant enchant) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return 0;
        }
        int best = 0;
        for (ItemStack piece : equipment.getArmorContents()) {
            best = Math.max(best, level(piece, enchant));
        }
        return best;
    }

    /** Deals damage from an enchantment effect without re-triggering enchantments. */
    void bonusDamage(LivingEntity target, double amount, LivingEntity source) {
        if (dealingBonusDamage) {
            return;
        }
        dealingBonusDamage = true;
        // Shared with our other plugins: no area effects (path splashes) chain off enchantment damage.
        boolean marked = !source.getScoreboardTags().contains(NO_CHAIN);
        if (marked) {
            source.addScoreboardTag(NO_CHAIN);
        }
        try {
            target.setNoDamageTicks(0);
            target.damage(amount, source);
            target.setNoDamageTicks(0); // don't make the target ignore the player's next hit
        } finally {
            dealingBonusDamage = false;
            if (marked) {
                source.removeScoreboardTag(NO_CHAIN);
            }
        }
    }

    boolean isDealingBonusDamage() {
        return dealingBonusDamage;
    }

    ItemStack soulFragment() {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        item.editMeta(meta -> {
            meta.displayName(lang.item("soul-fragment.name"));
            meta.lore(lang.lore("soul-fragment.lore"));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(soulFragmentKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    // --- /enchants (players get the menu, the console this list) ---

    private void listEnchants(CommandSender sender) {
        sender.sendMessage(lang.get("list.header"));
        MagicEnchant.Category current = null;
        for (MagicEnchant enchant : Arrays.stream(MagicEnchant.values()).sorted(java.util.Comparator.comparing(MagicEnchant::category)).toList()) {
            if (enchant.category() != current) {
                current = enchant.category();
                sender.sendMessage(lang.get("list.target", "target", lang.raw("category." + EnchantMenu.id(current) + ".name")));
            }
            Component line = Component.text(" " + enchant.displayName() + roman(settings.maxLevel(enchant)), enchant.color())
                    .append(Component.text(" - ", NamedTextColor.GRAY)).append(summary(enchant).colorIfAbsent(NamedTextColor.GRAY));
            if (!settings.enabled(enchant)) {
                line = line.append(Component.text(" (off)", NamedTextColor.RED));
            }
            sender.sendMessage(line);
        }
    }

    String targetLabel(MagicEnchant.Target target) {
        return lang.raw("target." + target.name().toLowerCase(Locale.ROOT));
    }

    Component summary(MagicEnchant enchant) {
        return lang.get("enchant." + EnchantSettings.id(enchant));
    }

    private static String roman(int maxLevel) {
        return switch (maxLevel) {
            case 1 -> "";
            case 2 -> " I-II";
            case 3 -> " I-III";
            default -> " I-" + maxLevel;
        };
    }
}
