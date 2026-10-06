package com.raresb.weaponskills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The skills menu (/skills): a page listing the weapons, and a page per weapon to see its skills,
 * equip them into trigger slots, and feed them Soul Fragments.
 */
final class SkillMenu implements Listener {
    private static final int SIZE = 54;
    private static final int[] WEAPON_SLOTS = {19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33, 34};
    private static final int FIRST_LOADOUT_SLOT = 10;
    /** The sneak + 1-4 key slots, on the row under the F slots. */
    /** Augment page: per tier (row 1-3) a label and the two choices. */
    private static final int[] TIER_LABEL_SLOTS = {10, 19, 28};
    private static final int[][] TIER_OPTION_SLOTS = {{12, 14}, {21, 23}, {30, 32}};
    private static final int FIRST_POOL_SLOT = 28;
    private static final int BACK_SLOT = 0;
    private static final int HELP_SLOT = 49;
    private static final int SOUL_FRAGMENT_XP = 25;

    private final WeaponSkillsPlugin plugin;
    private final NamespacedKey bookKey;
    private final NamespacedKey soulFragmentKey = new NamespacedKey("magicenchants", "soul_fragment");

    SkillMenu(WeaponSkillsPlugin plugin) {
        this.plugin = plugin;
        this.bookKey = new NamespacedKey(plugin, "skill_book");
    }

    /** One open menu: which page it shows and which skill is selected for equipping. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private Skill.Weapon weapon; // null = the main page
        private Skill selected;
        private Skill augmentSkill; // set = the augment page for this skill

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    void open(Player player) {
        Holder holder = new Holder();
        holder.inventory = plugin.getServer().createInventory(holder, SIZE, plugin.lang().get("menu.title"));
        draw(holder, player);
        player.openInventory(holder.inventory);
    }

    /** Refreshes every open skills menu (levels, cooldowns). Called once a second. */
    void refreshOpenMenus() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
                draw(holder, player);
            }
        }
    }

    private void draw(Holder holder, Player player) {
        Inventory inventory = holder.inventory;
        ItemStack pane = named(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, pane);
        }
        if (holder.augmentSkill != null) {
            drawAugments(inventory, player, holder.augmentSkill);
        } else if (holder.weapon == null) {
            drawMain(inventory, player);
        } else {
            drawWeapon(inventory, player, holder);
        }
    }

    // --- Main page: the weapons ---

    private void drawMain(Inventory inventory, Player player) {
        inventory.setItem(4, summary(player));
        List<Skill.Weapon> weapons = shownWeapons(player);
        for (int i = 0; i < weapons.size() && i < WEAPON_SLOTS.length; i++) {
            Skill.Weapon weapon = weapons.get(i);
            int total = 0;
            int unlocked = 0;
            for (Skill skill : Skill.values()) {
                if (skill.weapon() == weapon) {
                    total++;
                    if (plugin.loadout().unlocked(player, skill)) {
                        unlocked++;
                    }
                }
            }
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.lang().item("menu.mastery", "points", plugin.loadout().mastery(player, weapon)));
            lore.add(plugin.lang().item("menu.unlocked", "unlocked", unlocked, "total", total));
            Skill next = nextUnlock(player, weapon);
            if (next != null) {
                lore.add(plugin.lang().item("menu.next-unlock", "skill", next.displayName(), "mastery", next.unlockMastery()));
            }
            lore.add(Component.empty());
            lore.add(plugin.lang().item("menu.click-weapon"));
            ItemStack button = named(weapon.icon(), text(weapon.displayName(), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true), lore);
            customIcon(button, player, "weapon/" + weapon.id());
            inventory.setItem(WEAPON_SLOTS[i], button);
        }
        inventory.setItem(HELP_SLOT, help());
    }

    private Skill nextUnlock(Player player, Skill.Weapon weapon) {
        Skill next = null;
        for (Skill skill : Skill.values()) {
            if (skill.weapon() == weapon && !plugin.loadout().unlocked(player, skill)
                    && (next == null || skill.unlockMastery() < next.unlockMastery())) {
                next = skill;
            }
        }
        return next;
    }

    // --- Weapon page: loadout slots and the skill pool ---

    private void drawWeapon(Inventory inventory, Player player, Holder holder) {
        Skill.Weapon weapon = holder.weapon;
        inventory.setItem(BACK_SLOT, named(Material.ARROW, plugin.lang().item("menu.back"), List.of()));
        List<Component> weaponLore = new ArrayList<>();
        weaponLore.add(plugin.lang().item("menu.mastery-points", "points", plugin.loadout().mastery(player, weapon)));
        weaponLore.add(plugin.lang().item("menu.mastery-explain"));
        double attackSpeed = plugin.masteryAttackSpeed(player, weapon);
        if (weapon == Skill.Weapon.SWORD || weapon == Skill.Weapon.AXE) {
            weaponLore.add(plugin.lang().item("menu.attack-speed", "bonus", Math.round(attackSpeed * 100)));
        }
        Skill next = nextUnlock(player, weapon);
        if (next != null) {
            weaponLore.add(plugin.lang().item("menu.next-unlock", "skill", next.displayName(), "mastery", next.unlockMastery()));
        }
        Combos.Mark mark = Combos.Mark.of(weapon);
        if (mark != null && plugin.getConfig().getBoolean("combos.enabled", true)) {
            weaponLore.add(Component.empty());
            weaponLore.add(plugin.lang().item("menu.combo-mark", "mark", mark.displayName()));
            weaponLore.addAll(plugin.lang().lore("menu.combo-explain"));
            weaponLore.add(text(mark.description(), NamedTextColor.YELLOW));
        }
        ItemStack header = named(weapon.icon(), plugin.lang().item("menu.weapon-header", "weapon", weapon.displayName()), weaponLore);
        customIcon(header, player, "weapon/" + weapon.id());
        inventory.setItem(4, header);

        // Row 2: the F trigger slots.
        int slotIndex = FIRST_LOADOUT_SLOT;
        for (Skill.Trigger trigger : weapon.slots()) {
            inventory.setItem(slotIndex++, loadoutIcon(player, holder, weapon, trigger));
        }
        drawPool(inventory, player, holder, weapon);
    }

    /** One loadout slot: what is equipped there, with a hint while a skill is selected. */
    private ItemStack loadoutIcon(Player player, Holder holder, Skill.Weapon weapon, Skill.Trigger trigger) {
        {
            Skill equipped = plugin.loadout().get(player, weapon, trigger);
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.lang().item("menu.slot", "slot", trigger.description()));
            ItemStack icon;
            if (equipped == null) {
                lore.add(plugin.lang().item("menu.slot-empty"));
                icon = named(Material.GRAY_STAINED_GLASS_PANE, plugin.lang().item("menu.slot-empty-name"), lore);
            } else {
                lore.addAll(skillLore(player, equipped));
                icon = named(equipped.iconItem(), skillName(player, equipped), lore);
                customIcon(icon, player, "skill/" + equipped.id());
                decorate(icon, player, equipped);
            }
            if (holder.selected != null) {
                icon.editMeta(meta -> {
                    List<Component> withHint = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                    withHint.add(Component.empty());
                    withHint.add(plugin.lang().item(holder.selected.fits(trigger) ? "menu.equip-here" : "menu.cant-equip-here",
                            "skill", holder.selected.displayName()));
                    meta.lore(withHint);
                });
            }
            return icon;
        }
    }

    private void drawPool(Inventory inventory, Player player, Holder holder, Skill.Weapon weapon) {
        // Rows 4-5: every skill of the weapon.
        int poolIndex = FIRST_POOL_SLOT;
        for (Skill skill : Skill.values()) {
            if (skill.weapon() != weapon) {
                continue;
            }
            if (poolIndex % 9 == 8) {
                poolIndex += 2; // keep a border column on both sides
            }
            boolean unlocked = plugin.loadout().unlocked(player, skill);
            List<Component> lore = new ArrayList<>();
            ItemStack icon;
            if (!plugin.skillAllowed(player, skill)) {
                lore.addAll(wrap(skill.description(), NamedTextColor.DARK_GRAY));
                lore.add(Component.empty());
                lore.add(plugin.lang().item("menu.skill-off"));
                icon = named(Material.BARRIER, text("✖ " + skill.displayName(), NamedTextColor.DARK_GRAY), lore);
            } else if (!unlocked) {
                lore.addAll(wrap(skill.description(), NamedTextColor.GRAY));
                lore.add(Component.empty());
                lore.add(plugin.lang().item("menu.locked", "weapon", weapon.displayName(), "mastery", skill.unlockMastery()));
                lore.add(plugin.lang().item("menu.you-have", "mastery", plugin.loadout().mastery(player, weapon)));
                icon = named(Material.GRAY_DYE, text("✖ " + skill.displayName(), NamedTextColor.DARK_GRAY), lore);
            } else {
                lore.addAll(skillLore(player, skill));
                Skill.Trigger slot = plugin.loadout().slotOf(player, skill);
                lore.add(slot != null ? plugin.lang().item("menu.equipped", "slot", slot.description()) : plugin.lang().item("menu.not-equipped"));
                lore.add(Component.empty());
                lore.add(plugin.lang().item(skill == holder.selected ? "menu.selected" : "menu.click-select"));
                if (plugin.getServer().getPluginManager().getPlugin("MagicEnchants") != null) {
                    lore.add(plugin.lang().item("menu.feed", "xp", SOUL_FRAGMENT_XP));
                }
                if (plugin.augmentsEnabled()) {
                    int pending = plugin.augments().pendingChoices(player, skill);
                    lore.add(pending > 0 ? plugin.lang().item("menu.augments-pending", "count", pending) : plugin.lang().item("menu.augments"));
                }
                icon = named(skill.iconItem(), skillName(player, skill), lore);
                customIcon(icon, player, "skill/" + skill.id());
                decorate(icon, player, skill);
                if (skill == holder.selected) {
                    icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                }
            }
            inventory.setItem(poolIndex++, icon);
        }
        inventory.setItem(HELP_SLOT, named(Material.BOOK, plugin.lang().item("menu.equip-help.name"), plugin.lang().lore("menu.equip-help.lore")));
    }

    // --- Augment page: pick 1 of 2 augments at skill levels 30, 40 and 50 ---

    private void drawAugments(Inventory inventory, Player player, Skill skill) {
        inventory.setItem(BACK_SLOT, named(Material.ARROW, plugin.lang().item("menu.back-to", "weapon", skill.weapon().displayName()), List.of()));
        ItemStack header = named(skill.iconItem(), skillName(player, skill), skillLore(player, skill));
        customIcon(header, player, "skill/" + skill.id());
        inventory.setItem(4, header);
        int level = plugin.progress().level(player, skill);
        for (int tier = 0; tier < Augment.TIER_LEVELS.length; tier++) {
            int at = Augment.TIER_LEVELS[tier];
            boolean open = plugin.augments().tierOpen(player, skill, tier);
            Augment chosen = plugin.augments().chosen(player, skill, tier);
            inventory.setItem(TIER_LABEL_SLOTS[tier], named(open ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                    plugin.lang().item(open ? "menu.augment-tier-open" : "menu.augment-tier", "level", at),
                    List.of(open ? plugin.lang().item(chosen == null ? "menu.augment-pick-one" : "menu.augment-change")
                            : plugin.lang().item("menu.augment-skill-level", "level", level))));
            List<Augment> options = Augment.options(skill, tier);
            for (int i = 0; i < options.size(); i++) {
                Augment augment = options.get(i);
                boolean picked = augment == chosen;
                List<Component> lore = new ArrayList<>();
                lore.addAll(wrap(augment.description(), NamedTextColor.GRAY));
                lore.add(Component.empty());
                lore.add(!open ? plugin.lang().item("menu.augment-locked", "level", at)
                        : picked ? plugin.lang().item("menu.augment-chosen") : plugin.lang().item("menu.augment-click"));
                ItemStack icon = named(open ? augment.icon() : Material.GRAY_DYE,
                        text(augment.displayName(), picked ? NamedTextColor.GREEN : open ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.BOLD, true), lore);
                if (open) {
                    customIcon(icon, player, "augment/" + augment.id());
                }
                if (picked) {
                    icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                }
                inventory.setItem(TIER_OPTION_SLOTS[tier][i], icon);
            }
        }
        inventory.setItem(HELP_SLOT, named(Material.BOOK, plugin.lang().item("menu.augment-help.name"), plugin.lang().lore("menu.augment-help.lore")));
    }

    private void handleAugmentClick(Player player, Holder holder, int slot) {
        Skill skill = holder.augmentSkill;
        if (slot == BACK_SLOT) {
            holder.augmentSkill = null;
            click(player);
            draw(holder, player);
            return;
        }
        for (int tier = 0; tier < TIER_OPTION_SLOTS.length; tier++) {
            for (int i = 0; i < TIER_OPTION_SLOTS[tier].length; i++) {
                if (TIER_OPTION_SLOTS[tier][i] != slot) {
                    continue;
                }
                Augment augment = Augment.options(skill, tier).get(i);
                if (plugin.augments().choose(player, skill, tier, augment)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.3f);
                    player.sendActionBar(plugin.lang().get("augment-chosen", "skill", skill.displayName(), "augment", augment.displayName()));
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                    player.sendActionBar(plugin.lang().get("unlocks-at", "level", Augment.TIER_LEVELS[tier]));
                }
                draw(holder, player);
                return;
            }
        }
    }

    private Component skillName(Player player, Skill skill) {
        boolean ready = plugin.cooldownRemaining(player, skill) <= 0;
        TextColor color = skill.kind() == Skill.Kind.ULTIMATE ? NamedTextColor.LIGHT_PURPLE : ready ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
        return text(skill.icon() + " " + skill.displayName(), color).decoration(TextDecoration.BOLD, true)
                .append(text("  Lvl " + plugin.progress().level(player, skill), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, false));
    }

    private List<Component> skillLore(Player player, Skill skill) {
        Progress progress = plugin.progress();
        int level = progress.level(player, skill);
        int[] xp = progress.levelProgress(player, skill);
        long remaining = plugin.cooldownRemaining(player, skill);
        List<Component> lore = new ArrayList<>();
        lore.addAll(wrap(skill.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(plugin.lang().item("menu.level", "level", level, "max", progress.maxLevel()));
        if (xp[1] > 0) {
            lore.add(progressBar(xp[0], xp[1]).append(text("  " + xp[0] + "/" + xp[1] + " XP", NamedTextColor.DARK_GRAY)));
        } else {
            lore.add(plugin.lang().item("menu.max-level"));
        }
        if (skill.dealsDamage()) {
            int bonus = (int) Math.round((progress.damageMultiplier(player, skill) * plugin.augments().damageMultiplier(player, skill) - 1) * 100);
            if (skill.weapon().flatDamage() || Skill.Weapon.of(player.getInventory().getItemInMainHand().getType()) == skill.weapon()) {
                double hearts = plugin.skills().damage(player, skill) / 2;
                String sign = bonus >= 0 ? "+" : "";
                lore.add(plugin.lang().item("menu.damage-hearts", "hearts", String.format(Locale.ROOT, "%.1f", hearts), "bonus", sign + bonus));
            } else {
                String sign = bonus >= 0 ? "+" : "";
                lore.add(plugin.lang().item("menu.damage-multiplier", "multiplier", String.format(Locale.ROOT, "%.2f", plugin.skillDamage(skill)),
                        "bonus", sign + bonus));
                lore.add(plugin.lang().item("menu.damage-hint", "weapon", skill.weapon().displayName().toLowerCase(Locale.ROOT)));
            }
        } else {
            lore.add(plugin.lang().item("menu.longer-effect"));
        }
        lore.add(plugin.lang().item("menu.cooldown", "seconds", String.format(Locale.ROOT, "%.1f", plugin.effectiveCooldown(player, skill))));
        int evolveLevel = plugin.getConfig().getInt("progression.evolve-level", 10);
        boolean evolved = level >= evolveLevel;
        lore.add(plugin.lang().item(evolved ? "menu.evolved" : "menu.evolves-at", "level", evolveLevel));
        lore.addAll(wrap(Evolutions.of(skill), evolved ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.DARK_GRAY));
        for (int tier = 0; plugin.augmentsEnabled() && tier < Augment.TIER_LEVELS.length; tier++) {
            Augment chosen = plugin.augments().chosen(player, skill, tier);
            int at = Augment.TIER_LEVELS[tier];
            if (chosen != null) {
                lore.add(plugin.lang().item("menu.augment-line", "augment", chosen.displayName(), "description", chosen.description()));
            } else if (plugin.augments().tierOpen(player, skill, tier)) {
                lore.add(plugin.lang().item("menu.augment-open", "level", at));
            } else {
                lore.add(plugin.lang().item("menu.augment-at", "level", at));
            }
        }
        lore.add(remaining > 0
                ? plugin.lang().item("menu.ready-in", "seconds", (int) Math.ceil(remaining / 1000.0))
                : plugin.lang().item("menu.ready"));
        return lore;
    }

    /** Ready skills glow; the stack size shows the level. */
    private void decorate(ItemStack icon, Player player, Skill skill) {
        boolean ready = plugin.cooldownRemaining(player, skill) <= 0;
        icon.editMeta(meta -> {
            meta.setEnchantmentGlintOverride(ready);
            meta.setMaxStackSize(99);
        });
        icon.setAmount(Math.max(1, plugin.progress().level(player, skill)));
    }

    private static Component progressBar(int have, int need) {
        int filled = need == 0 ? 10 : (int) Math.round(10.0 * have / need);
        return text("■".repeat(filled), NamedTextColor.GREEN).append(text("■".repeat(10 - filled), NamedTextColor.DARK_GRAY));
    }

    private ItemStack summary(Player player) {
        int total = 0;
        for (Skill skill : Skill.values()) {
            total += plugin.progress().level(player, skill);
        }
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.lang().item("menu.summary.total", "total", total, "max", Skill.values().length * plugin.progress().maxLevel()));
        lore.addAll(plugin.lang().lore("menu.summary.lore"));
        ItemStack head = named(Material.PLAYER_HEAD, plugin.lang().item("menu.summary.name", "player", player.getName()), lore);
        head.editMeta(org.bukkit.inventory.meta.SkullMeta.class, meta -> meta.setOwningPlayer(player));
        return head;
    }

    private ItemStack help() {
        return named(Material.BOOK, plugin.lang().item("menu.help.name"), plugin.lang().lore("menu.help.lore"));
    }

    /** Weapons on the main page: enabled ones the player has permission for. */
    private List<Skill.Weapon> shownWeapons(Player player) {
        return java.util.Arrays.stream(Skill.Weapon.values()).filter(w -> plugin.weaponAllowed(player, w)).toList();
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

    private static Component text(String content, TextColor color) {
        return Component.text(content, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Set by the Hud plugin while the player has the server resource pack loaded. */
    private static final NamespacedKey HAS_PACK = new NamespacedKey("hud", "pack");

    /** Swaps in the custom icon from the resource pack, for players who have it (and only if the pack has that icon). */
    private void customIcon(ItemStack item, Player player, String model) {
        if (player.getPersistentDataContainer().has(HAS_PACK, PersistentDataType.BYTE) && PackModels.has(plugin, model)) {
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

    // --- Clicks ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder holder) {
            event.setCancelled(true); // the menu is read-only
            if (event.getWhoClicked() instanceof Player player && event.getRawSlot() >= 0 && event.getRawSlot() < SIZE) {
                handleMenuClick(player, holder, event.getRawSlot(), event.isShiftClick(), event.isRightClick());
            }
            return;
        }
    }

    private void handleMenuClick(Player player, Holder holder, int slot, boolean shift, boolean right) {
        if (holder.augmentSkill != null) {
            handleAugmentClick(player, holder, slot);
            return;
        }
        if (holder.weapon == null) {
            List<Skill.Weapon> weapons = shownWeapons(player);
            for (int i = 0; i < WEAPON_SLOTS.length && i < weapons.size(); i++) {
                if (WEAPON_SLOTS[i] == slot) {
                    holder.weapon = weapons.get(i);
                    holder.selected = null;
                    click(player);
                    draw(holder, player);
                }
            }
            return;
        }
        Skill.Weapon weapon = holder.weapon;
        if (slot == BACK_SLOT) {
            holder.weapon = null;
            holder.selected = null;
            click(player);
            draw(holder, player);
            return;
        }
        // A trigger slot: equip the selected skill there.
        int loadoutIndex = slot - FIRST_LOADOUT_SLOT;
        Skill.Trigger clickedSlot = loadoutIndex >= 0 && loadoutIndex < weapon.slots().size() ? weapon.slots().get(loadoutIndex) : null;
        if (clickedSlot != null) {
            Skill.Trigger trigger = clickedSlot;
            if (holder.selected == null) {
                player.sendActionBar(plugin.lang().get("select-first"));
            } else if (plugin.loadout().assign(player, holder.selected, trigger)) {
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.2f);
                player.sendActionBar(plugin.lang().get("equipped", "skill", holder.selected.displayName(), "slot", trigger.description()));
                holder.selected = null;
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
                player.sendActionBar(plugin.lang().get("cant-equip", "skill", holder.selected.displayName()));
            }
            draw(holder, player);
            return;
        }
        // A skill in the pool: select it, or feed it a Soul Fragment.
        Skill skill = poolSkillAt(weapon, slot);
        if (skill == null) {
            return;
        }
        if (!plugin.loadout().unlocked(player, skill) || !plugin.skillAllowed(player, skill)) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }
        if (shift) {
            feedSoulFragment(player, skill);
        } else if (right && plugin.augmentsEnabled()) {
            holder.augmentSkill = skill;
            holder.selected = null;
            click(player);
        } else {
            holder.selected = holder.selected == skill ? null : skill;
            click(player);
        }
        draw(holder, player);
    }

    private static Skill poolSkillAt(Skill.Weapon weapon, int slot) {
        int poolIndex = FIRST_POOL_SLOT;
        for (Skill skill : Skill.values()) {
            if (skill.weapon() != weapon) {
                continue;
            }
            if (poolIndex % 9 == 8) {
                poolIndex += 2;
            }
            if (poolIndex++ == slot) {
                return skill;
            }
        }
        return null;
    }

    private void feedSoulFragment(Player player, Skill skill) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(soulFragmentKey, PersistentDataType.BYTE)) {
                if (!plugin.progress().addXp(player, skill, SOUL_FRAGMENT_XP)) {
                    player.sendActionBar(plugin.lang().get("feed-max", "skill", skill.displayName()));
                    return;
                }
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(i, item.getAmount() > 0 ? item : null);
                player.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1.2f, 1f);
                player.sendActionBar(plugin.lang().get("fed", "xp", SOUL_FRAGMENT_XP, "skill", skill.displayName()));
                return;
            }
        }
        player.sendActionBar(plugin.lang().get("no-soul-fragment"));
    }

    private static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    // --- The old Skill Book item (removed; /skills opens the menu) ---

    boolean isBook(ItemStack item) {
        return item != null && !item.isEmpty() && item.getType() == Material.ENCHANTED_BOOK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(bookKey, PersistentDataType.BYTE);
    }

    /** Takes any Skill Books from earlier versions out of the player's inventory and ender chest. */
    void removeBooks(Player player) {
        for (Inventory inventory : new Inventory[] {player.getInventory(), player.getEnderChest()}) {
            ItemStack[] contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) {
                if (isBook(contents[i])) {
                    inventory.setItem(i, null);
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        removeBooks(event.getPlayer());
    }
}
