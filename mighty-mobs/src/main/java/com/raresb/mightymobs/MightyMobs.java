package com.raresb.mightymobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Tracks mighty mobs, applies their abilities and keeps their health display current. */
public final class MightyMobs {
    private final MightyMobsPlugin plugin;
    private final NamespacedKey abilitiesKey;
    private final NamespacedKey summonedKey;
    private final NamespacedKey modifierKey;
    /** Set once the health display has been applied to a mob. */
    private final NamespacedKey displayKey;
    /** A name a player gave the mob (JSON text component). */
    private final NamespacedKey baseNameKey;
    /** Mighty mobs currently loaded in a world. */
    private final Set<UUID> loaded = new HashSet<>();

    MightyMobs(MightyMobsPlugin plugin) {
        this.plugin = plugin;
        this.abilitiesKey = new NamespacedKey(plugin, "abilities");
        this.summonedKey = new NamespacedKey(plugin, "summoned");
        this.modifierKey = new NamespacedKey(plugin, "ability");
        this.displayKey = new NamespacedKey(plugin, "hp_display");
        this.baseNameKey = new NamespacedKey(plugin, "base_name");
    }

    // --- Ability data (stored on the entity so it survives restarts) ---

    public Set<Ability> abilities(LivingEntity entity) {
        String raw = entity.getPersistentDataContainer().get(abilitiesKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Ability> result = EnumSet.noneOf(Ability.class);
        for (String id : raw.split(",")) {
            Ability ability = Ability.fromId(id);
            if (ability != null) {
                result.add(ability);
            }
        }
        return result;
    }

    public boolean isMighty(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(abilitiesKey, PersistentDataType.STRING);
    }

    public boolean has(LivingEntity entity, Ability ability) {
        return abilities(entity).contains(ability);
    }

    /** Marks minions summoned by a Necromancer so they never become mighty themselves. */
    public void markSummoned(LivingEntity entity) {
        entity.getPersistentDataContainer().set(summonedKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean isSummoned(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(summonedKey, PersistentDataType.BYTE);
    }

    /** Picks random enabled abilities according to the config. */
    public Set<Ability> rollAbilities() {
        return rollAbilities(plugin.getConfig().getDouble("double-ability-chance", 0.2));
    }

    public Set<Ability> rollAbilities(double doubleChance) {
        List<Ability> pool = new ArrayList<>(plugin.enabledAbilities());
        if (pool.isEmpty()) {
            return Collections.emptySet();
        }
        Collections.shuffle(pool);
        int count = ThreadLocalRandom.current().nextDouble() < doubleChance
                ? Math.min(2, pool.size())
                : 1;
        return EnumSet.copyOf(pool.subList(0, count));
    }

    /** Turns a mob into a mighty mob with the given abilities. */
    public void makeMighty(LivingEntity entity, Set<Ability> abilities) {
        if (abilities.isEmpty()) {
            return;
        }
        String raw = abilities.stream().map(Ability::id).collect(Collectors.joining(","));
        entity.getPersistentDataContainer().set(abilitiesKey, PersistentDataType.STRING, raw);

        double healthBonus = plugin.getConfig().getDouble("health-multiplier", 1.5) - 1;
        if (healthBonus > 0) {
            modify(entity, "mighty", Attribute.MAX_HEALTH, healthBonus);
        }
        if (abilities.contains(Ability.TANK)) {
            modify(entity, "ability", Attribute.MAX_HEALTH, 1.0);
            modify(entity, "ability", Attribute.SCALE, 0.25);
            modify(entity, "ability", Attribute.KNOCKBACK_RESISTANCE, 0.6);
        }
        if (abilities.contains(Ability.SWIFT)) {
            modify(entity, "ability", Attribute.MOVEMENT_SPEED, 0.6);
        }
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            entity.setHealth(maxHealth.getValue());
        }
        // A custom name must not stop the mob from despawning like a normal mob.
        entity.setRemoveWhenFarAway(true);
        entity.setCustomNameVisible(true);
        track(entity);
        updateName(entity);
    }

    /**
     * Adds {@code amount} x base to an attribute using a modifier owned by this plugin.
     * Modifiers with different sources stack (e.g. mighty +50% and Tank +100% health = 2.5x).
     */
    private void modify(LivingEntity entity, String source, Attribute attribute, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        // "ability_<attribute>" keeps the key used by earlier versions for ability modifiers.
        NamespacedKey key = new NamespacedKey(plugin, (source.equals("ability") ? modifierKey.getKey() : source) + "_"
                + attribute.getKey().getKey());
        instance.removeModifier(key);
        instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
    }

    // --- Loaded mighty mobs ---

    public void track(LivingEntity entity) {
        loaded.add(entity.getUniqueId());
    }

    public void untrack(UUID id) {
        loaded.remove(id);
    }

    public Set<UUID> loaded() {
        return loaded;
    }

    // --- Health display ---

    /**
     * Every living mob gets a health display, except players, armor stands and mannequins
     * (player look-alikes, e.g. WeaponSkills' Mirror Image decoys).
     */
    public static boolean showsHealth(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof ArmorStand)
                && !(entity instanceof org.bukkit.entity.Mannequin);
    }

    /**
     * Refreshes the name above the mob: abilities (for mighty mobs), its name and current health.
     * The name is the one a player gave it with a name tag, or the mob type.
     */
    public void updateName(LivingEntity entity) {
        if (entity.isDead() || !showsHealth(entity)) {
            return;
        }
        Set<Ability> abilities = abilities(entity);
        if (abilities.isEmpty() && !healthDisplayFor(entity)) {
            return;
        }
        abilities = abilities(entity);
        double max = maxHealth(entity);
        double health = Math.max(0, entity.getHealth());
        TextColor hpColor = healthColor(health / max);

        Component name = Component.empty();
        for (Ability ability : abilities) {
            name = name.append(Component.text(ability.icon() + plugin.abilityName(ability) + " ", ability.color(), TextDecoration.BOLD));
        }
        name = name.append(baseName(entity).colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text("  ❤ " + hearts(health) + "/" + hearts(max), hpColor));
        entity.customName(name);
        entity.setCustomNameVisible(!abilities.isEmpty() || plugin.getConfig().getBoolean("health-display.always-visible", true));
    }

    /** Health over normal mobs: health-display.enabled, and with all-worlds false only in the plugin's worlds. */
    private boolean healthDisplayFor(LivingEntity entity) {
        var config = plugin.getConfig();
        return config.getBoolean("health-display.enabled", true)
                && (config.getBoolean("health-display.all-worlds", true) || plugin.worldAllowed(entity.getWorld()));
    }

    /** The mob's own name: a name-tag name if it has one, otherwise its type. */
    private Component baseName(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (!data.has(displayKey, PersistentDataType.BYTE)) {
            // First time we show this mob's health. A name it already has was given by a
            // player (name tag) - unless it is a mighty mob, whose name was set by us.
            Component existing = entity.customName();
            if (existing != null && !isMighty(entity)) {
                data.set(baseNameKey, PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(existing));
            }
            data.set(displayKey, PersistentDataType.BYTE, (byte) 1);
        }
        String json = data.get(baseNameKey, PersistentDataType.STRING);
        return json != null ? GsonComponentSerializer.gson().deserialize(json) : Component.translatable(entity.getType());
    }

    /** Remembers a name a player gave the mob, so the health display keeps it. */
    public void setBaseName(LivingEntity entity, Component name) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (name == null) {
            data.remove(baseNameKey);
        } else {
            data.set(baseNameKey, PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(name));
        }
        data.set(displayKey, PersistentDataType.BYTE, (byte) 1);
    }

    public double maxHealth(LivingEntity entity) {
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        return maxHealth != null ? maxHealth.getValue() : Math.max(1, entity.getHealth());
    }

    /**
     * Health in hearts (1 heart = 2 health points), like the player's own health bar,
     * to the nearest half heart. Rounds up so a mob that is still alive never shows 0.
     */
    static String hearts(double hp) {
        int halfHearts = (int) Math.ceil(hp);
        return halfHearts % 2 == 0 ? Integer.toString(halfHearts / 2) : (halfHearts / 2) + ".5";
    }

    private static TextColor healthColor(double fraction) {
        if (fraction > 0.6) {
            return NamedTextColor.GREEN;
        }
        if (fraction > 0.3) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    /** Returns true the first time it is called for this mob and flag, false afterwards. */
    public boolean firstTime(LivingEntity entity, String flag) {
        NamespacedKey key = new NamespacedKey(plugin, "used_" + flag);
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (data.has(key, PersistentDataType.BYTE)) {
            return false;
        }
        data.set(key, PersistentDataType.BYTE, (byte) 1);
        return true;
    }
}
