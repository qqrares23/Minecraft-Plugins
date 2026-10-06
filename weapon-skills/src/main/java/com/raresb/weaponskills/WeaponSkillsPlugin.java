package com.raresb.weaponskills;

import io.papermc.paper.registry.RegistryAccess;
import com.raresb.weaponskills.common.ConfigCommand;
import com.raresb.weaponskills.common.ConfigFiles;
import com.raresb.weaponskills.common.Integrations;
import com.raresb.weaponskills.common.Lang;
import com.raresb.weaponskills.common.Perms;
import com.raresb.weaponskills.common.Worlds;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class WeaponSkillsPlugin extends JavaPlugin implements Listener {
    private Combat combat;
    private Skills skills;
    private Progress progress;
    private Loadout loadout;
    private SkillMenu menu;
    private Guide guide;
    private Dodge dodge;
    private Augments augments;
    private Combos combos;
    private final Map<UUID, Map<Skill, Long>> readyAt = new HashMap<>();
    /** Skills that came off cooldown recently (shown with a check mark for a moment). */
    private final Map<UUID, Map<Skill, Long>> readySince = new HashMap<>();
    /** Don't overwrite a skill message on the action bar until this time. */
    private final Map<UUID, Long> messageUntil = new HashMap<>();
    /** Players currently seeing the cooldown bar, so it can be cleared when no longer needed. */
    private final Set<UUID> showingBar = new HashSet<>();
    /** MagicEnchants enchantments by id (Skill Surge, Arcane Echo, Cooldown Siphon); empty when that plugin isn't installed. */
    private final Map<String, java.util.Optional<Enchantment>> magicEnchants = new HashMap<>();
    /** Player data read by the Hud plugin: running cooldowns ("Name=readyAtMillis;...") and a use counter. */
    private NamespacedKey cooldownsKey;
    private NamespacedKey skillsUsedKey;
    /** Also for the Hud sidebar: the held weapon and its mastery ("Sword|12"). */
    private NamespacedKey heldKey;
    /** Faster attacks from sword/axe mastery (a transient modifier, never saved on the player). */
    private NamespacedKey masterySpeedKey;

    private final Lang lang = new Lang(this);
    private final ConfigCommand config = new ConfigCommand(this, "/skills admin", this::loadSettings);
    private static WeaponSkillsPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        ConfigFiles.updateConfig(this);
        lang.load();
        Perms.register(this, "weaponskills.weapon", java.util.Arrays.stream(Skill.Weapon.values()).map(Skill.Weapon::id).toList(), "weapon's skills");
        Perms.register(this, "weaponskills.skill", java.util.Arrays.stream(Skill.values()).map(Skill::id).toList(), "skill");
        cooldownsKey = new NamespacedKey(this, "cooldowns");
        skillsUsedKey = new NamespacedKey(this, "skills_used");
        heldKey = new NamespacedKey(this, "held");
        masterySpeedKey = new NamespacedKey(this, "mastery_attack_speed");
        progress = new Progress(this);
        loadout = new Loadout(this);
        combat = new Combat(this);
        augments = new Augments(this);
        combos = new Combos(this);
        skills = new Skills(this, combat);
        menu = new SkillMenu(this);
        guide = new Guide(this);
        dodge = new Dodge(this);
        getServer().getPluginManager().registerEvents(dodge, this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(skills.extra(), this);
        getServer().getPluginManager().registerEvents(skills.ranged(), this);
        getServer().getPluginManager().registerEvents(skills.more(), this);
        getServer().getPluginManager().registerEvents(skills.gather(), this);
        getServer().getPluginManager().registerEvents(augments, this);
        getServer().getScheduler().runTaskTimer(this, combos::tick, 10, 10);
        getServer().getScheduler().runTaskTimer(this, augments::cleanUp, 200, 200);
        getServer().getScheduler().runTaskTimer(this, this::tickCooldownBar, 4, 4);
        getServer().getScheduler().runTaskTimer(this, menu::refreshOpenMenus, 20, 20);
        getServer().getScheduler().runTaskTimer(this, () -> getServer().getOnlinePlayers().forEach(player -> {
            publishHeld(player);
            applyMasterySpeed(player);
        }), 20, 20);
        // The Skill Book item is gone (/skills opens the menu): remove old copies.
        getServer().getOnlinePlayers().forEach(menu::removeBooks);
        Integrations.log(this, "Hud", "sidebar shows cooldowns and held weapon, custom menu icons",
                "Paths", "skill hits trigger path passives", "MagicEnchants", "Skill Surge and Soul Fragments",
                "Professions", "gathering skills give profession XP", "Timberella", "Clear Cut works with tree felling");
    }

    private void loadSettings() {
        reloadConfig();
        lang.load();
        if (progress != null) {
            progress.clearLevelCache();
        }
    }

    Lang lang() {
        return lang;
    }

    /** Text from the language file for enum descriptions; the enum's own text if the key is missing. */
    static String text(String key, String fallback) {
        return instance != null && instance.lang.has(key) ? instance.lang.raw(key) : fallback;
    }

    boolean worldEnabled(Player player) {
        return Worlds.enabled(this, player.getWorld());
    }

    /** weapons.<id>.enabled and the permission weaponskills.weapon.<id>. */
    boolean weaponAllowed(Player player, Skill.Weapon weapon) {
        return getConfig().getBoolean("weapons." + weapon.id() + ".enabled", true) && player.hasPermission("weaponskills.weapon." + weapon.id());
    }

    /** The skill's weapon is allowed, skills.<id>.enabled and the permission weaponskills.skill.<id>. */
    boolean skillAllowed(Player player, Skill skill) {
        return weaponAllowed(player, skill.weapon()) && getConfig().getBoolean("skills." + skill.id() + ".enabled", true)
                && player.hasPermission("weaponskills.skill." + skill.id());
    }

    boolean augmentsEnabled() {
        return getConfig().getBoolean("augments.enabled", true);
    }

    @Override
    public void onDisable() {
        if (skills != null) {
            skills.shutdown();
        }
    }

    Progress progress() {
        return progress;
    }

    Loadout loadout() {
        return loadout;
    }

    Augments augments() {
        return augments;
    }

    Combos combos() {
        return combos;
    }

    Skills skills() {
        return skills;
    }

    Combat combat() {
        return combat;
    }

    /** Takes a fraction of the remaining cooldown off a skill (Reaping). */
    void reduceCooldown(Player player, Skill skill, double fraction) {
        Map<Skill, Long> cooldowns = readyAt.get(player.getUniqueId());
        Long ready = cooldowns == null ? null : cooldowns.get(skill);
        long now = System.currentTimeMillis();
        if (ready != null && ready > now) {
            cooldowns.put(skill, ready - (long) ((ready - now) * fraction));
            publishCooldowns(player);
        }
    }

    double skillDamage(Skill skill) {
        return getConfig().getDouble("skills." + skill.id() + ".damage", skill.defaultDamage());
    }

    double skillCooldown(Skill skill) {
        return getConfig().getDouble("skills." + skill.id() + ".cooldown", skill.defaultCooldown());
    }

    /** The cooldown in seconds for this player, shortened by their level in the skill. */
    double effectiveCooldown(Player player, Skill skill) {
        double cooldown = skillCooldown(skill) * progress.cooldownMultiplier(player, skill) * augments.cooldownMultiplier(player, skill);
        if (skill.weapon() == Skill.Weapon.WOODCUTTING) {
            // Timbersong (a MagicEnchants enchantment on the axe): -10% per level.
            cooldown *= 1 - 0.1 * magicLevel(player.getInventory().getItemInMainHand(), "timbersong");
        }
        return cooldown;
    }

    /** Milliseconds until the skill is ready again (0 if ready). */
    long cooldownRemaining(Player player, Skill skill) {
        Long ready = readyAt.getOrDefault(player.getUniqueId(), Map.of()).get(skill);
        return ready == null ? 0 : Math.max(0, ready - System.currentTimeMillis());
    }

    /** Writes the player's running cooldowns into their data, where the Hud plugin's sidebar reads them. */
    void publishCooldowns(Player player) {
        long now = System.currentTimeMillis();
        StringBuilder running = new StringBuilder();
        if (dodge.readyAt(player) > now) {
            running.append(lang.plain("dodge-name")).append('=').append(dodge.readyAt(player)).append(';');
        }
        for (Map.Entry<Skill, Long> entry : readyAt.getOrDefault(player.getUniqueId(), Map.of()).entrySet()) {
            if (entry.getValue() > now) {
                running.append(entry.getKey().displayName()).append('=').append(entry.getValue()).append(';');
            }
        }
        if (running.isEmpty()) {
            player.getPersistentDataContainer().remove(cooldownsKey);
        } else {
            player.getPersistentDataContainer().set(cooldownsKey, PersistentDataType.STRING, running.toString());
        }
    }

    /** Writes the held weapon and its mastery into the player's data (only when it changes). */
    private void publishHeld(Player player) {
        Skill.Weapon weapon = heldWeapon(player);
        String now = weapon == null ? null : weapon.displayName() + "|" + loadout.mastery(player, weapon);
        String before = player.getPersistentDataContainer().get(heldKey, PersistentDataType.STRING);
        if (now == null && before != null) {
            player.getPersistentDataContainer().remove(heldKey);
        } else if (now != null && !now.equals(before)) {
            player.getPersistentDataContainer().set(heldKey, PersistentDataType.STRING, now);
        }
    }

    /**
     * Attack speed bonus from mastery for the held sword or axe: attack-speed.per-mastery per point,
     * up to attack-speed.max-bonus (0.002 and 0.30: +10% at 50 mastery, +30% from 150). A higher
     * attack speed is a proportionally shorter cooldown between full-strength hits.
     */
    double masteryAttackSpeed(Player player, Skill.Weapon weapon) {
        if (weapon != Skill.Weapon.SWORD && weapon != Skill.Weapon.AXE) {
            return 0;
        }
        return Math.min(getConfig().getDouble("attack-speed.max-bonus", 0.30),
                loadout.mastery(player, weapon) * getConfig().getDouble("attack-speed.per-mastery", 0.002));
    }

    private void applyMasterySpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed == null) {
            return;
        }
        double bonus = masteryAttackSpeed(player, Skill.Weapon.of(player.getInventory().getItemInMainHand().getType()));
        AttributeModifier current = speed.getModifier(masterySpeedKey);
        if (current != null && Math.abs(current.getAmount() - bonus) < 1.0e-6) {
            return;
        }
        if (current != null) {
            speed.removeModifier(masterySpeedKey);
        }
        if (bonus > 0) {
            speed.addTransientModifier(new AttributeModifier(masterySpeedKey, bonus, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    /** Switching items: update the attack speed on the next tick, when the new item is in hand. */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().runTask(this, () -> {
            if (player.isOnline()) {
                applyMasterySpeed(player);
            }
        });
    }

    // --- Triggering ---

    /** The swap-hands key (F) fires the skill for the held weapon and what the player is doing. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("weaponskills.use")) {
            return;
        }
        Skill skill = skillFor(player);
        if (skill == null || !worldEnabled(player) || !weaponAllowed(player, skill.weapon())) {
            return; // not holding a (usable) weapon: F swaps hands as usual
        }
        event.setCancelled(true);
        // Look-up/look-down/ultimate keys are pressed while looking steeply up or down: those casts aim straight ahead.
        Skill.Trigger key = isBlockingWithShield(player) ? null : trigger(player);
        cast(player, skill, key == Skill.Trigger.LOOKING_UP || key == Skill.Trigger.LOOKING_DOWN || key == Skill.Trigger.ULTIMATE);
    }


    /** Uses a skill: cooldown check, the skill itself, cooldown, augments, XP. */
    private void cast(Player player, Skill skill, boolean levelled) {
        if (!skillAllowed(player, skill)) {
            flash(player, lang.get("skill-off", "skill", skill.displayName()));
            return;
        }
        long now = System.currentTimeMillis();
        Map<Skill, Long> cooldowns = readyAt.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Skill.class));
        long ready = cooldowns.getOrDefault(skill, 0L);
        if (ready > now) {
            flash(player, lang.get("cooldown", "skill", skill.icon() + " " + skill.displayName(),
                    "seconds", String.format(Locale.ROOT, "%.1f", (ready - now) / 1000.0)));
            return;
        }
        long cooldownMillis = (long) (effectiveCooldown(player, skill) * 1000);
        Aim.setLevelled(player, levelled && !skill.freeAim());
        boolean used;
        try {
            used = skills.use(player, skill);
        } catch (Throwable t) {
            // A skill error must not reach Paper: on 2026-10-01 one (most likely a StackOverflowError from
            // recursing events) broke Paper's own error logging and crashed the server. Log everything
            // needed to find the skill, and put it on cooldown so it can't be spammed.
            getLogger().log(Level.SEVERE, "Skill " + skill.name() + " (" + skill.weapon() + ") failed for "
                    + player.getName() + " at " + player.getLocation().toVector() + " in "
                    + player.getWorld().getName() + ", holding " + player.getInventory().getItemInMainHand().getType(), t);
            if (t instanceof BootstrapMethodError) {
                // Paper's "Could not pass event" logging failed to link and stays broken for this run:
                // the next exception in ANY plugin's listener would crash the server.
                getLogger().severe("Paper's event error handler is now broken - restart the server soon.");
            }
            cooldowns.put(skill, now + cooldownMillis);
            flash(player, lang.get("skill-failed", "skill", skill.icon() + " " + skill.displayName()));
            return;
        }
        if (!used) {
            return; // e.g. no arrow or no target: no cooldown
        }
        // Non-short-circuit "|": the augments' after-use effects must run even when Arcane Echo fires.
        if (augments.afterUse(player, skill) | arcaneEcho(player)) {
            flash(player, lang.get("skill-echo", "skill", skill.icon() + " " + skill.displayName()));
        } else {
            cooldowns.put(skill, now + cooldownMillis);
            flash(player, lang.get("skill-used", "skill", skill.icon() + " " + skill.displayName()));
            // The white cooldown sweep on the hotbar slot - only on items with no right-click use of
            // their own: a cooldown on a bow, crossbow, trident, shield or spear would stop it from working.
            Skill.Weapon weapon = skill.weapon();
            if (weapon == Skill.Weapon.SWORD || weapon == Skill.Weapon.AXE || weapon == Skill.Weapon.MACE
                    || weapon == Skill.Weapon.PICKAXE) {
                player.setCooldown(player.getInventory().getItemInMainHand(), (int) (cooldownMillis / 50));
            }
        }
        progress.onUse(player, skill);
        publishCooldowns(player);
        player.getPersistentDataContainer().set(skillsUsedKey, PersistentDataType.INTEGER,
                player.getPersistentDataContainer().getOrDefault(skillsUsedKey, PersistentDataType.INTEGER, 0) + 1);
    }

    /** Shows a message on the action bar and keeps the cooldown bar from overwriting it briefly. */
    void flash(Player player, Component message) {
        player.sendActionBar(message);
        messageUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1500);
    }

    /** The skill F would fire right now, from the held weapon, what the player is doing, and their loadout. */
    Skill skillFor(Player player) {
        if (isBlockingWithShield(player)) {
            Skill.Trigger slot = player.isSneaking() ? Skill.Trigger.BLOCKING_SNEAKING : Skill.Trigger.BLOCKING;
            return loadout.get(player, Skill.Weapon.SHIELD, slot);
        }
        Skill.Weapon weapon = heldWeapon(player);
        if (weapon == null) {
            return null;
        }
        Skill.Trigger trigger = trigger(player);
        Skill skill = weapon.slots().contains(trigger) ? loadout.get(player, weapon, trigger) : null;
        if (skill == null && trigger == Skill.Trigger.ULTIMATE && weapon.slots().contains(Skill.Trigger.SNEAKING)) {
            skill = loadout.get(player, weapon, Skill.Trigger.SNEAKING); // ultimate not unlocked yet
        }
        if (skill == null) {
            skill = loadout.get(player, weapon, Skill.Trigger.STANDING); // this weapon has no such slot
        }
        return skill;
    }

    /** The weapon the held item counts as. An axe counts as Woodcutting while the player looks at a log or leaves. */
    private static Skill.Weapon heldWeapon(Player player) {
        Skill.Weapon weapon = Skill.Weapon.of(player.getInventory().getItemInMainHand().getType());
        if (weapon == Skill.Weapon.AXE) {
            Block target = player.getTargetBlockExact(6);
            if (target != null && (Tag.LOGS.isTagged(target.getType()) || Tag.LEAVES.isTagged(target.getType())
                    || Tag.SAPLINGS.isTagged(target.getType()))) {
                return Skill.Weapon.WOODCUTTING;
            }
        }
        return weapon;
    }

    private static boolean isBlockingWithShield(Player player) {
        return (player.isBlocking() || player.isHandRaised()) && player.getActiveItem().getType() == Material.SHIELD;
    }

    private static Skill.Trigger trigger(Player player) {
        if (isAirborne(player)) {
            return Skill.Trigger.AIRBORNE;
        }
        float pitch = player.getLocation().getPitch();
        if (player.isSneaking()) {
            return pitch <= -50 ? Skill.Trigger.ULTIMATE : Skill.Trigger.SNEAKING;
        }
        if (player.isSprinting()) {
            return Skill.Trigger.SPRINTING;
        }
        if (pitch <= -50) {
            return Skill.Trigger.LOOKING_UP;
        }
        if (pitch >= 50) {
            return Skill.Trigger.LOOKING_DOWN;
        }
        return Skill.Trigger.STANDING;
    }

    /** In the air and at least 1.1 blocks above the ground, so pressing F at the top of a normal jump counts. */
    private static boolean isAirborne(Player player) {
        if (Combat.onGround(player) || player.isInWater() || player.isGliding() || player.isFlying()
                || player.isClimbing() || player.isInsideVehicle()) {
            return false;
        }
        RayTraceResult ground = player.getWorld().rayTraceBlocks(player.getLocation(), new Vector(0, -1, 0), 64,
                FluidCollisionMode.ALWAYS, true);
        double height = ground == null ? 64 : player.getLocation().getY() - ground.getHitPosition().getY();
        return height >= 1.1;
    }

    // --- Cooldown bar above the hotbar ---

    /** The equipped skills for what the player holds, plus their shield skills if they carry a shield. */
    private List<Skill> shownSkills(Player player) {
        List<Skill> shown = new ArrayList<>();
        Skill.Weapon weapon = heldWeapon(player);
        if (weapon != null) {
            for (Skill.Trigger slot : weapon.slots()) {
                Skill skill = loadout.get(player, weapon, slot);
                if (skill != null) {
                    shown.add(skill);
                }
            }
        }
        if (player.getInventory().getItemInOffHand().getType() == Material.SHIELD
                || player.getInventory().getItemInMainHand().getType() == Material.SHIELD) {
            for (Skill.Trigger slot : Skill.Weapon.SHIELD.slots()) {
                Skill skill = loadout.get(player, Skill.Weapon.SHIELD, slot);
                if (skill != null) {
                    shown.add(skill);
                }
            }
        }
        return shown;
    }

    private void tickCooldownBar() {
        long now = System.currentTimeMillis();
        for (Player player : getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Map<Skill, Long> cooldowns = readyAt.getOrDefault(id, Map.of());
            Map<Skill, Long> recent = readySince.computeIfAbsent(id, k -> new EnumMap<>(Skill.class));

            // Ding once when a skill comes off cooldown.
            boolean expired = false;
            for (Map.Entry<Skill, Long> entry : new ArrayList<>(cooldowns.entrySet())) {
                if (entry.getValue() <= now) {
                    cooldowns.remove(entry.getKey());
                    recent.put(entry.getKey(), now);
                    expired = true;
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 2f);
                }
            }
            if (expired) {
                publishCooldowns(player);
            }
            recent.values().removeIf(t -> now - t > 2000);

            List<Skill> shown = shownSkills(player);
            boolean relevant = shown.stream().anyMatch(skill -> cooldowns.containsKey(skill) || recent.containsKey(skill));
            if (shown.isEmpty() || !relevant) {
                if (showingBar.remove(id) && messageUntil.getOrDefault(id, 0L) <= now) {
                    player.sendActionBar(Component.empty());
                }
                continue;
            }
            if (messageUntil.getOrDefault(id, 0L) > now) {
                continue; // a skill message is showing
            }
            Skill current = skillFor(player);
            Component bar = Component.empty();
            boolean first = true;
            for (Skill skill : shown) {
                if (!first) {
                    bar = bar.append(Component.text("  "));
                }
                first = false;
                Long ready = cooldowns.get(skill);
                Component status = ready != null
                        ? Component.text((skill == current ? " " : "") + (int) Math.ceil((ready - now) / 1000.0) + "s", NamedTextColor.RED)
                        : Component.text((skill == current ? " " : "") + "✔", NamedTextColor.GREEN);
                if (skill == current) {
                    bar = bar.append(Component.text("[", NamedTextColor.GOLD))
                            .append(Component.text(skill.icon() + " " + skill.shortName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                            .append(status)
                            .append(Component.text("]", NamedTextColor.GOLD));
                } else {
                    bar = bar.append(Component.text(skill.icon(), NamedTextColor.GRAY)).append(status);
                }
            }
            player.sendActionBar(bar);
            showingBar.add(id);
        }
    }

    // --- Parry, Reflect, fall protection, Skill Surge ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getDamager() instanceof Projectile projectile && skills.extra().tryReflect(player, projectile)) {
            event.setCancelled(true);
            return;
        }
        LivingEntity attacker = event.getDamager() instanceof LivingEntity living ? living
                : event.getDamager() instanceof Projectile p && p.getShooter() instanceof LivingEntity shooter ? shooter
                : null;
        if (attacker != null && skills.tryParry(player, attacker)) {
            event.setCancelled(true);
        }
    }

    /** The level of a MagicEnchants enchantment on an item (0 when that plugin isn't installed). */
    int magicLevel(ItemStack item, String id) {
        Enchantment enchantment = magicEnchants.computeIfAbsent(id, key -> java.util.Optional.ofNullable(
                RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(Key.key("magicenchants", key)))).orElse(null);
        return enchantment == null || item == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    /** Arcane Echo (a MagicEnchants enchantment): 5% per level that a cast skill doesn't go on cooldown. */
    private boolean arcaneEcho(Player player) {
        int level = magicLevel(player.getInventory().getItemInMainHand(), "arcane_echo");
        return level > 0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.05 * level;
    }

    /** Skill Surge (a MagicEnchants enchantment): every hit shortens the attacker's skill cooldowns. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSurgeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        int level = magicLevel(player.getInventory().getItemInMainHand(), "skill_surge");
        Map<Skill, Long> cooldowns = readyAt.get(player.getUniqueId());
        if (level > 0 && cooldowns != null && player.getAttackCooldown() >= 0.8f) {
            cooldowns.replaceAll((skill, ready) -> ready - 200L * level);
            publishCooldowns(player);
        }
    }

    /** Cooldown Siphon (a MagicEnchants enchantment): every kill takes 1 s per level off the killer's skill cooldowns. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSiphonKill(org.bukkit.event.entity.EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        int level = magicLevel(killer.getInventory().getItemInMainHand(), "cooldown_siphon");
        Map<Skill, Long> cooldowns = readyAt.get(killer.getUniqueId());
        if (level > 0 && cooldowns != null && !cooldowns.isEmpty()) {
            cooldowns.replaceAll((skill, ready) -> ready - 1000L * level);
            publishCooldowns(killer);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && event.getEntity() instanceof Player player && combat.isFallProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        readyAt.remove(event.getPlayer().getUniqueId());
        dodge.forget(event.getPlayer());
        Aim.forget(event.getPlayer());
        publishCooldowns(event.getPlayer()); // cooldowns don't carry over a relog
        readySince.remove(event.getPlayer().getUniqueId());
        messageUntil.remove(event.getPlayer().getUniqueId());
        showingBar.remove(event.getPlayer().getUniqueId());
        combat.forget(event.getPlayer());
        skills.forget(event.getPlayer());
        progress.forget(event.getPlayer());
        augments.forget(event.getPlayer());
        loadout.forget(event.getPlayer());
    }

    // --- Commands ---

    private void admin(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("weaponskills.admin")) {
            sender.sendMessage(lang.get("no-permission"));
            return;
        }
        if (config.handle(sender, java.util.Arrays.copyOfRange(args, 1, args.length))) {
            return;
        }
        String usage = "/" + label + " admin setlevel <player> <skill|weapon|all> <level> | reset <player> | info <player>"
                + " | resetcooldowns <player> | reload | config <get|set|reset|list> | toggle <feature> | lang <code>";
        if (args.length < 2) {
            sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "info" -> {
                Player target = args.length >= 3 ? getServer().getPlayer(args[2]) : null;
                if (target == null) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                sender.sendMessage(Component.text(target.getName() + " - weapon skills", NamedTextColor.GOLD));
                for (Skill.Weapon weapon : Skill.Weapon.values()) {
                    StringBuilder line = new StringBuilder("  " + weapon.displayName() + " (mastery " + loadout.mastery(target, weapon) + "): ");
                    boolean first = true;
                    for (Skill skill : Skill.values()) {
                        int level = progress.level(target, skill);
                        if (skill.weapon() == weapon && level > 1) {
                            line.append(first ? "" : ", ").append(skill.displayName()).append(' ').append(level);
                            first = false;
                        }
                    }
                    sender.sendMessage(Component.text(line.toString(), NamedTextColor.GRAY));
                }
            }
            case "resetcooldowns" -> {
                Player target = args.length >= 3 ? getServer().getPlayer(args[2]) : null;
                if (target == null) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                readyAt.remove(target.getUniqueId());
                publishCooldowns(target);
                sender.sendMessage(Component.text("Skill cooldowns reset for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "setlevel" -> {
                Player target = args.length >= 5 ? getServer().getPlayer(args[2]) : null;
                List<Skill> chosen = args.length >= 5 ? parseSkills(args[3]) : List.of();
                Integer level = null;
                try {
                    level = args.length >= 5 ? Integer.parseInt(args[4]) : null;
                } catch (NumberFormatException ignored) {
                    // handled below
                }
                if (target == null || chosen.isEmpty() || level == null) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                int clamped = Math.max(1, Math.min(progress.maxLevel(), level));
                chosen.forEach(skill -> progress.setLevel(target, skill, clamped));
                sender.sendMessage(Component.text("Set " + args[3] + " to level " + clamped + " for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "reset" -> {
                Player target = args.length >= 3 ? getServer().getPlayer(args[2]) : null;
                if (target == null) {
                    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
                    return;
                }
                for (Skill skill : Skill.values()) {
                    progress.setLevel(target, skill, 1);
                }
                readyAt.remove(target.getUniqueId());
                publishCooldowns(target);
                sender.sendMessage(Component.text("Reset all skills for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
        }
    }

    /** A skill id, a weapon name (all its skills) or "all". */
    private static List<Skill> parseSkills(String id) {
        if (id.equalsIgnoreCase("all")) {
            return List.of(Skill.values());
        }
        Skill skill = Skill.fromId(id);
        if (skill != null) {
            return List.of(skill);
        }
        List<Skill> weaponSkills = new ArrayList<>();
        for (Skill s : Skill.values()) {
            if (s.weapon().id().equalsIgnoreCase(id)) {
                weaponSkills.add(s);
            }
        }
        return weaponSkills;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equals("skills") || !sender.hasPermission("weaponskills.admin")) {
            return List.of();
        }
        if (args.length >= 3 && ConfigCommand.SUBCOMMANDS.contains(args[1].toLowerCase(Locale.ROOT))) {
            return config.complete(java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        List<String> options = new ArrayList<>();
        switch (args.length) {
            case 1 -> options.add("admin");
            case 2 -> {
                options.addAll(List.of("setlevel", "reset", "info", "resetcooldowns"));
                options.addAll(ConfigCommand.SUBCOMMANDS);
            }
            case 3 -> getServer().getOnlinePlayers().forEach(p -> options.add(p.getName()));
            case 4 -> {
                options.add("all");
                for (Skill.Weapon weapon : Skill.Weapon.values()) {
                    options.add(weapon.id());
                }
                for (Skill skill : Skill.values()) {
                    options.add(skill.id());
                }
            }
            default -> {
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("weaponskills-reload")) {
            config.handle(sender, new String[]{"reload"});
            return true;
        }
        if (command.getName().equals("guide")) {
            if (sender instanceof Player player) {
                guide.open(player);
            } else {
                sender.sendMessage("Only players can read the guide.");
            }
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            admin(sender, label, args);
            return true;
        }
        if (sender instanceof Player player) {
            if (!player.hasPermission("weaponskills.use")) {
                player.sendMessage(lang.get("no-permission"));
                return true;
            }
            menu.open(player);
            return true;
        }
        sender.sendMessage(Component.text("Weapon Skills", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" - press the swap-hands key (F):", NamedTextColor.GRAY)));
        Skill.Weapon current = null;
        for (Skill skill : Skill.values()) {
            if (skill.weapon() != current) {
                current = skill.weapon();
                sender.sendMessage(Component.text(current.displayName(), NamedTextColor.YELLOW));
            }
            String slot = skill.defaultSlot() != null ? skill.defaultSlot().description()
                    : "unlocks at " + skill.unlockMastery() + " mastery";
            sender.sendMessage(Component.text("  " + slot + ": ", NamedTextColor.GRAY)
                    .append(Component.text(skill.displayName(), NamedTextColor.WHITE))
                    .append(Component.text(String.format(Locale.ROOT, " - %.0fs cooldown", skillCooldown(skill)), NamedTextColor.DARK_GRAY)));
        }
        return true;
    }
}
