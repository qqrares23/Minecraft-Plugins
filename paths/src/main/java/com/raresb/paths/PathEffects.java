package com.raresb.paths;

import com.raresb.paths.common.Root;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * The shared machinery of the paths (who a hit belongs to, M2 triggers and cooldowns, talents,
 * path XP) and the abilities of Duelist, Warbringer, Ranger and Arbalist. Tidecaller, Juggernaut,
 * Sentinel and Shadow are in {@link MorePathEffects}; Beastmaster, Pyromancer and Frost Warden in
 * {@link MagicPathEffects}.
 */
final class PathEffects implements Listener {
    private static final float MIN_CHARGE = 0.8f;
    /** Choices can't be changed this long after dealing or taking damage. */
    private static final long COMBAT_MILLIS = 10_000;

    /** How a hit was dealt. */
    enum Kind {
        MELEE, ARROW, TRIDENT, PET, BOLT
    }

    /** Who dealt a hit and with what. */
    record Source(Player player, Kind kind, Path.Weapon weapon, Entity damager) {
    }

    private final PathsPlugin plugin;
    private final MorePathEffects more;
    private final MagicPathEffects magic;
    private final LancerPathEffects lancer;
    final NamespacedKey weaponKey;
    private final NamespacedKey specialKey;
    private final NamespacedKey steadyKey;
    private final NamespacedKey camoKey;
    private final NamespacedKey speedKey;
    private final NamespacedKey attackSpeedKey;
    private final NamespacedKey swiftKey;
    private final NamespacedKey enduranceKey;
    private final NamespacedKey fervorKey;
    /** Unyielding talent: ready again at (ms). */
    private final Map<UUID, Long> unyieldingReady = new HashMap<>();
    private final NamespacedKey ironhideArmorKey;
    private final NamespacedKey ironhideKnockbackKey;
    private final NamespacedKey sunderKey;

    /** M2 cooldowns: player -> path -> ready at (ms). */
    private final Map<UUID, Map<Path, Long>> readyAt = new HashMap<>();
    /** Spell cooldowns (magic paths, sneak + M2): player -> path -> ready at (ms). */
    private final Map<UUID, Map<Path, Long>> spellReadyAt = new HashMap<>();
    /** Player data read by the Hud plugin: running M2 cooldowns ("Name=readyAtMillis;..."). */
    private final NamespacedKey cooldownsKey;
    private final Map<UUID, Long> lastCombat = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    /** Precision: player -> {target, consecutive hits}. */
    private final Map<UUID, UUID> precisionTarget = new HashMap<>();
    private final Map<UUID, Integer> precisionHits = new HashMap<>();
    private final Map<UUID, Long> riposteUntil = new HashMap<>();
    private final Map<UUID, Long> feintUntil = new HashMap<>();
    /** Rend: target -> {stacks, until ms, per-stack bonus}. */
    private final Map<UUID, double[]> rend = new HashMap<>();
    /** Crushing Blow primed until (ms). */
    private final Map<UUID, Long> crushingUntil = new HashMap<>();
    /** Camouflage: seconds spent sneaking still. */
    private final Map<UUID, Integer> stillSeconds = new HashMap<>();
    /** Set while dealing our own splash damage, to avoid re-triggering. */
    private boolean dealingSplash;
    /** Entity tag (shared by our plugins) set while one of our plugins deals skill/splash/bonus damage: no area effects chain off it. */
    static final String NO_CHAIN = "raresb_no_chain";

    PathEffects(PathsPlugin plugin) {
        this.plugin = plugin;
        this.weaponKey = new NamespacedKey(plugin, "weapon");
        this.specialKey = new NamespacedKey(plugin, "special");
        this.steadyKey = new NamespacedKey(plugin, "steady");
        this.camoKey = new NamespacedKey(plugin, "camo");
        this.speedKey = new NamespacedKey(plugin, "fleet_speed");
        this.attackSpeedKey = new NamespacedKey(plugin, "fleet_attack_speed");
        this.swiftKey = new NamespacedKey(plugin, "swift_speed");
        this.enduranceKey = new NamespacedKey(plugin, "talent_endurance");
        this.fervorKey = new NamespacedKey(plugin, "talent_fervor");
        this.ironhideArmorKey = new NamespacedKey(plugin, "ironhide_armor");
        this.ironhideKnockbackKey = new NamespacedKey(plugin, "ironhide_knockback");
        this.sunderKey = new NamespacedKey(plugin, "sunder");
        this.cooldownsKey = new NamespacedKey(plugin, "cooldowns");
        this.more = new MorePathEffects(plugin, this);
        this.magic = new MagicPathEffects(plugin, this);
        this.lancer = new LancerPathEffects(plugin, this);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> plugin.getServer().getOnlinePlayers().forEach(this::refresh), 20, 20);
    }

    MorePathEffects more() {
        return more;
    }

    MagicPathEffects magic() {
        return magic;
    }

    // ============================================================ Shared helpers

    static boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    int power(Player player, Path path) {
        return plugin.power(player, path);
    }

    static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max != null ? max.getValue() : 20;
    }

    static void heal(Player player, double amount) {
        if (amount > 0 && !player.isDead()) {
            player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount));
        }
    }

    static double attackDamage(Player player) {
        AttributeInstance attack = player.getAttribute(Attribute.ATTACK_DAMAGE);
        return attack != null ? attack.getValue() : 1;
    }

    static boolean validTarget(Player player, LivingEntity entity) {
        if (entity.equals(player) || entity.isDead() || !entity.isValid() || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Tameable tame && tame.isTamed() && player.equals(tame.getOwner())) {
            return false;
        }
        return !(entity instanceof Player other) || other.getGameMode() == GameMode.SURVIVAL || other.getGameMode() == GameMode.ADVENTURE;
    }

    List<LivingEntity> around(Player player, Location center, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
            if (validTarget(player, entity)) {
                list.add(entity);
            }
        }
        return list;
    }

    /** The closest valid target in front of the player within {@code range}, or null. */
    LivingEntity inFront(Player player, double range) {
        Vector facing = player.getLocation().getDirection().setY(0).normalize();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : around(player, player.getLocation(), range)) {
            Vector to = entity.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
            double distance = to.lengthSquared();
            if ((distance < 1 || to.normalize().dot(facing) > 0.5) && distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Damage dealt by our own effects, as the player (armor and PvP rules apply). */
    void splash(Player player, LivingEntity target, double damage) {
        // A splash set off by another plugin's skill/splash/enchantment damage would multiply the hits
        // (one AoE skill x every mob around each target): skip it. Our own M2 skills aren't inside such damage.
        if (player.getScoreboardTags().contains(NO_CHAIN)) {
            return;
        }
        dealingSplash = true;
        player.addScoreboardTag(NO_CHAIN);
        try {
            target.setNoDamageTicks(0);
            target.damage(damage, player);
            target.setNoDamageTicks(0); // don't make the target ignore the player's next hit
        } finally {
            dealingSplash = false;
            player.removeScoreboardTag(NO_CHAIN);
        }
    }

    boolean dealingSplash() {
        return dealingSplash;
    }

    static void stun(LivingEntity target, int ticks) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, 1));
        target.getWorld().spawnParticle(Particle.WAX_OFF, target.getEyeLocation().add(0, 0.4, 0), 8, 0.3, 0.1, 0.3, 0);
    }

    static void root(LivingEntity target, int ticks) {
        Root.apply(PathsPlugin.getPlugin(PathsPlugin.class), target, ticks, 6);
    }

    void trail(Entity projectile, Particle particle, int ticks) {
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!projectile.isValid() || (projectile instanceof AbstractArrow a && a.isInBlock()) || ++tick > ticks) {
                    cancel();
                    return;
                }
                projectile.getWorld().spawnParticle(particle, projectile.getLocation(), 2, 0.05, 0.05, 0.05, 0);
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** Server-side ground check: a solid block right below the player's feet. */
    static boolean onGround(Player player) {
        Location feet = player.getLocation();
        for (double dx : new double[] {-0.29, 0.29}) {
            for (double dz : new double[] {-0.29, 0.29}) {
                if (feet.getWorld().getBlockAt((int) Math.floor(feet.getX() + dx), (int) Math.floor(feet.getY() - 0.05),
                        (int) Math.floor(feet.getZ() + dz)).isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    void protectFromFall(Player player, long millis) {
        noFallUntil.merge(player.getUniqueId(), System.currentTimeMillis() + millis, Math::max);
    }

    boolean inCombat(Player player) {
        long lock = plugin.getConfig().getLong("combat-lock-seconds", COMBAT_MILLIS / 1000) * 1000;
        return lastCombat.getOrDefault(player.getUniqueId(), 0L) + lock > System.currentTimeMillis();
    }

    private void markCombat(Player player) {
        lastCombat.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** Sets our modifier to {@code amount} (removes it at 0), touching the attribute only if the value changed. */
    static void setModifier(Player player, Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(key);
        if (current != null && Math.abs(current.getAmount() - amount) < 1.0e-9) {
            return;
        }
        if (current != null) {
            instance.removeModifier(key);
        }
        if (amount != 0) {
            instance.addTransientModifier(new AttributeModifier(key, amount, operation));
        }
    }

    // ============================================================ Who a hit belongs to

    /** Who dealt the damage and how, or null if no player is behind it. */
    Source source(Entity damager) {
        if (damager instanceof Player player) {
            return new Source(player, Kind.MELEE, Path.Weapon.of(player.getInventory().getItemInMainHand().getType()), damager);
        }
        if (damager instanceof Trident trident && trident.getShooter() instanceof Player player) {
            return new Source(player, Kind.TRIDENT, Path.Weapon.TRIDENT, damager);
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            String bolt = projectile.getPersistentDataContainer().get(magic.boltKey(), PersistentDataType.STRING);
            if (bolt != null) {
                return new Source(player, Kind.BOLT, Path.Weapon.valueOf(bolt), damager);
            }
            if (projectile instanceof AbstractArrow arrow) {
                String tag = arrow.getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
                return new Source(player, Kind.ARROW, tag == null ? null : Path.Weapon.valueOf(tag), damager);
            }
        }
        if (damager instanceof Tameable pet && pet.isTamed() && pet.getOwner() instanceof Player owner && owner.isOnline()) {
            return new Source(owner, Kind.PET, Path.Weapon.BONE, damager);
        }
        return null;
    }

    /**
     * The active paths a hit counts for: the path of the weapon used, plus Sentinel for melee hits
     * while carrying a shield.
     */
    List<Path> paths(Source source) {
        List<Path> paths = new ArrayList<>(2);
        Path main = fits(source.weapon(), source.kind()) ? plugin.activeFor(source.player(), source.weapon()) : null;
        if (main != null) {
            paths.add(main);
        }
        if (source.kind() == Kind.MELEE && main != Path.SENTINEL && plugin.isActive(source.player(), Path.SENTINEL)
                && (source.player().getInventory().getItemInOffHand().getType() == Material.SHIELD
                || source.player().getInventory().getItemInMainHand().getType() == Material.SHIELD)) {
            paths.add(Path.SENTINEL);
        }
        return paths;
    }

    /** Whether a hit made this way counts for the weapon's path (a bow swung in melee doesn't). */
    private static boolean fits(Path.Weapon weapon, Kind kind) {
        if (weapon == null) {
            return false;
        }
        return switch (weapon) {
            case SWORD, AXE, MACE, SHIELD, SPEAR -> kind == Kind.MELEE;
            case BOW, CROSSBOW -> kind == Kind.ARROW;
            case TRIDENT -> kind == Kind.MELEE || kind == Kind.TRIDENT;
            case BONE -> kind == Kind.PET;
            case BLAZE_ROD, BREEZE_ROD -> kind == Kind.BOLT || kind == Kind.MELEE;
        };
    }

    // ============================================================ Always-on (every second)

    /** Keeps attribute bonuses in line with the player's paths, choices and health. */
    void refresh(Player player) {
        int fleet = plugin.has(player, Ability.FLEET_FOOT) ? power(player, Path.DUELIST) : -1;
        double fleetPower = fleet < 0 ? 0 : plugin.passivePower(player, Path.DUELIST);
        double moveBonus = fleet < 0 ? 0 : (0.05 + 0.0025 * fleet) * fleetPower;
        if (fleet >= 15 && player.getHealth() < maxHealth(player) / 2) {
            moveBonus *= 2;
        }
        setModifier(player, Attribute.MOVEMENT_SPEED, speedKey, moveBonus, AttributeModifier.Operation.ADD_SCALAR);
        setModifier(player, Attribute.ATTACK_SPEED, attackSpeedKey, fleet < 0 ? 0 : (0.05 + 0.005 * fleet) * fleetPower,
                AttributeModifier.Operation.ADD_SCALAR);

        boolean swift = false;
        for (Path path : plugin.active(player)) {
            swift |= plugin.hasTalent(player, path, Path.Talent.SWIFT);
        }
        setModifier(player, Attribute.MOVEMENT_SPEED, swiftKey, swift ? 0.1 : 0, AttributeModifier.Operation.ADD_SCALAR);
        setModifier(player, Attribute.MAX_HEALTH, enduranceKey, plugin.hasTalentAnywhere(player, Path.Talent.ENDURANCE) ? 4 : 0,
                AttributeModifier.Operation.ADD_NUMBER);
        setModifier(player, Attribute.ATTACK_SPEED, fervorKey, plugin.hasTalentAnywhere(player, Path.Talent.FERVOR) ? 0.1 : 0,
                AttributeModifier.Operation.ADD_SCALAR);

        int iron = plugin.has(player, Ability.IRONHIDE) ? power(player, Path.WARBRINGER) : -1;
        double ironPower = iron < 0 ? 0 : plugin.passivePower(player, Path.WARBRINGER);
        setModifier(player, Attribute.ARMOR, ironhideArmorKey, iron < 0 ? 0 : (2 + 0.1 * iron) * ironPower, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(player, Attribute.KNOCKBACK_RESISTANCE, ironhideKnockbackKey,
                iron < 0 ? 0 : Math.min(1, 0.3 * (iron >= 10 ? 2 : 1) * ironPower), AttributeModifier.Operation.ADD_NUMBER);

        camouflage(player);
        more.refresh(player);
        magic.refresh(player);
        lancer.refresh(player);
    }

    /** Camouflage: two seconds sneaking still turn the Ranger invisible. */
    private void camouflage(Player player) {
        UUID id = player.getUniqueId();
        if (!plugin.has(player, Ability.CAMOUFLAGE) || !player.isSneaking() || player.getVelocity().clone().setY(0).lengthSquared() > 0.003) {
            stillSeconds.remove(id);
            return;
        }
        int seconds = stillSeconds.merge(id, 1, Integer::sum);
        if (seconds >= 2) {
            if (seconds == 2) {
                player.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, player.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4);
                player.sendActionBar(plugin.lang().get("camouflaged"));
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 50, 0, false, false, true));
        }
    }

    void removeModifiers(Player player) {
        for (NamespacedKey key : new NamespacedKey[] {speedKey, swiftKey}) {
            setModifier(player, Attribute.MOVEMENT_SPEED, key, 0, AttributeModifier.Operation.ADD_SCALAR);
        }
        setModifier(player, Attribute.ATTACK_SPEED, attackSpeedKey, 0, AttributeModifier.Operation.ADD_SCALAR);
        setModifier(player, Attribute.MAX_HEALTH, enduranceKey, 0, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(player, Attribute.ATTACK_SPEED, fervorKey, 0, AttributeModifier.Operation.ADD_SCALAR);
        setModifier(player, Attribute.ARMOR, ironhideArmorKey, 0, AttributeModifier.Operation.ADD_NUMBER);
        setModifier(player, Attribute.KNOCKBACK_RESISTANCE, ironhideKnockbackKey, 0, AttributeModifier.Operation.ADD_NUMBER);
        more.removeModifiers(player);
        lancer.removeModifiers(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
        // The shared no-chain tag only lives during one damage call; a crash mid-call could leave it saved.
        event.getPlayer().removeScoreboardTag(NO_CHAIN);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        readyAt.remove(id);
        spellReadyAt.remove(id);
        publishCooldowns(event.getPlayer()); // cooldowns don't carry over a relog
        for (Map<UUID, ?> map : List.of(precisionTarget, precisionHits, crushingUntil, riposteUntil, feintUntil, stillSeconds,
                lastCombat, noFallUntil)) {
            map.remove(id);
        }
        more.forget(event.getPlayer());
        magic.forget(event.getPlayer());
    }

    void shutdown() {
        plugin.getServer().getOnlinePlayers().forEach(this::removeModifiers);
        more.shutdown();
        magic.shutdown();
        lancer.shutdown();
    }

    // ============================================================ M2 skills: cooldowns

    /** The M2 the player picked for a path. */
    Ability skillOf(Player player, Path path) {
        return plugin.ability(player, path, Path.Slot.SKILL);
    }

    /** The cooldowns of a slot: M2 skills or spells. */
    private Map<UUID, Map<Path, Long>> cooldownsOf(Path.Slot slot) {
        return slot == Path.Slot.SPELL ? spellReadyAt : readyAt;
    }

    /** Whether the path's M2 is ready; shows the time left if not. */
    boolean ready(Player player, Path path) {
        return ready(player, path, Path.Slot.SKILL);
    }

    /** Whether the path's M2 or spell is ready; shows the time left if not. */
    boolean ready(Player player, Path path, Path.Slot slot) {
        long ready = cooldownsOf(slot).getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(path, 0L);
        long now = System.currentTimeMillis();
        if (ready > now) {
            player.sendActionBar(Component.text(plugin.ability(player, path, slot).displayName() + " "
                    + String.format(java.util.Locale.ROOT, "%.1fs", (ready - now) / 1000.0), NamedTextColor.RED));
            return false;
        }
        return true;
    }

    /** Starts the M2 cooldown, gives a bit of XP and announces the skill. */
    void startCooldown(Player player, Path path) {
        startCooldown(player, path, Path.Slot.SKILL);
    }

    /** Starts the M2 or spell cooldown, gives a bit of XP and announces it. */
    void startCooldown(Player player, Path path, Path.Slot slot) {
        Ability skill = plugin.ability(player, path, slot);
        double seconds = skill.cooldown();
        if (skill == Ability.HEAVY_BOLT && power(player, path) >= 20) {
            seconds /= 2;
        }
        if (plugin.hasTalent(player, path, Path.Talent.SKILL_HASTE)) {
            seconds *= 0.75;
        }
        if (plugin.hasTalent(player, path, Path.Talent.FOCUS)) {
            seconds *= 0.85;
        }
        // MagicEnchants Battle Focus on the held weapon or staff: -8% per level.
        seconds *= 1 - 0.08 * Math.min(3, plugin.magicLevel(player.getInventory().getItemInMainHand(), "battle_focus"));
        long cooldown = (long) (seconds * 1000);
        Map<Path, Long> cooldowns = cooldownsOf(slot).computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(Path.class));
        cooldowns.put(path, System.currentTimeMillis() + cooldown);
        publishCooldowns(player);
        player.sendActionBar(plugin.lang().get("skill-used", "skill", Component.text(skill.displayName(), path.color())));
        plugin.addXp(player, path, 3);
        scheduleReadyDing(player, path, slot, cooldown);
    }

    private void scheduleReadyDing(Player player, Path path, Path.Slot slot, long cooldown) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            long ready = cooldownsOf(slot).getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(path, 0L);
            if (player.isOnline() && ready > 0 && ready <= System.currentTimeMillis() + 50) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.6f);
                player.sendActionBar(plugin.lang().get("skill-ready", "skill", plugin.ability(player, path, slot).displayName()));
            }
        }, Math.max(1, cooldown / 50));
    }

    /** Shortens a running M2 cooldown: {@code factor} 0 resets it, 0.5 halves what is left. */
    void cutCooldown(Player player, Path path, double factor) {
        Map<Path, Long> cooldowns = readyAt.get(player.getUniqueId());
        if (cooldowns == null || !cooldowns.containsKey(path)) {
            return;
        }
        long now = System.currentTimeMillis();
        long left = cooldowns.get(path) - now;
        if (left <= 0) {
            return;
        }
        cooldowns.put(path, now + (long) (left * factor));
        publishCooldowns(player);
        scheduleReadyDing(player, path, Path.Slot.SKILL, (long) (left * factor));
    }

    void resetCooldowns(Player player) {
        readyAt.remove(player.getUniqueId());
        spellReadyAt.remove(player.getUniqueId());
        publishCooldowns(player);
    }

    /** Writes the player's running M2 cooldowns into their data, where the Hud plugin's sidebar reads them. */
    private void publishCooldowns(Player player) {
        long now = System.currentTimeMillis();
        StringBuilder running = new StringBuilder();
        for (Path.Slot slot : List.of(Path.Slot.SKILL, Path.Slot.SPELL)) {
            for (Map.Entry<Path, Long> entry : cooldownsOf(slot).getOrDefault(player.getUniqueId(), Map.of()).entrySet()) {
                if (entry.getValue() > now) {
                    running.append(plugin.ability(player, entry.getKey(), slot).displayName()).append('=').append(entry.getValue()).append(';');
                }
            }
        }
        if (running.isEmpty()) {
            player.getPersistentDataContainer().remove(cooldownsKey);
        } else {
            player.getPersistentDataContainer().set(cooldownsKey, PersistentDataType.STRING, running.toString());
        }
    }

    // ============================================================ M2 skills: triggers

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable()) {
            return; // opening doors, chests...
        }
        Path.Weapon weapon = Path.Weapon.of(player.getInventory().getItemInMainHand().getType());
        if (weapon != Path.Weapon.TRIDENT && sentinelTrigger(player)) {
            cast(player, Path.SENTINEL);
            return;
        }
        Path path = plugin.activeFor(player, weapon);
        if (path == null) {
            return;
        }
        switch (weapon) {
            case SWORD, MACE, BONE -> cast(player, path);
            case BLAZE_ROD, BREEZE_ROD -> castStaff(player, path);
            case AXE -> {
                if (clicked == null || !strippable(clicked.getType())) {
                    cast(player, path); // otherwise let the axe strip logs / scrape copper
                }
            }
            case TRIDENT, SPEAR -> {
                if (player.isSneaking()) {
                    event.setUseItemInHand(org.bukkit.event.Event.Result.DENY); // no throw / spear charge
                    cast(player, path);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Path.Weapon weapon = Path.Weapon.of(player.getInventory().getItemInMainHand().getType());
        if (weapon != Path.Weapon.TRIDENT && sentinelTrigger(player)) {
            cast(player, Path.SENTINEL);
            return;
        }
        Path path = plugin.activeFor(player, weapon);
        if (path == null) {
            return;
        }
        switch (weapon) {
            case SWORD, AXE, MACE -> cast(player, path); // bones on mobs: taming, feeding
            case BLAZE_ROD, BREEZE_ROD -> castStaff(player, path);
            case TRIDENT, SPEAR -> {
                if (player.isSneaking()) {
                    cast(player, path);
                }
            }
            default -> {
            }
        }
    }

    /** Sentinel's M2: sneaking and right-clicking with a shield in either hand. */
    private boolean sentinelTrigger(Player player) {
        return player.isSneaking() && plugin.isActive(player, Path.SENTINEL)
                && (player.getInventory().getItemInMainHand().getType() == Material.SHIELD
                || player.getInventory().getItemInOffHand().getType() == Material.SHIELD);
    }

    private static boolean strippable(Material type) {
        return (Tag.LOGS.isTagged(type) && !type.name().startsWith("STRIPPED_")) || type.name().contains("COPPER")
                || type == Material.BAMBOO_BLOCK;
    }

    /** Uses the path's M2 if it is ready; the cooldown only starts if the skill went off. */
    private void cast(Player player, Path path) {
        if (!ready(player, path)) {
            return;
        }
        Ability skill = skillOf(player, path);
        boolean done = switch (skill) {
            case LUNGE -> {
                lunge(player);
                yield true;
            }
            case FEINT -> {
                feint(player);
                yield true;
            }
            case CRUSHING_BLOW -> {
                primeCrushingBlow(player);
                yield true;
            }
            case BATTLE_LEAP -> {
                battleLeap(player);
                yield true;
            }
            case MAELSTROM, GEYSER, SEISMIC_LEAP, GRAVITY_CRUSH, BULWARK, SHIELD_CHARGE, VANISH, SMOKE_BOMB -> more.skill(player, skill);
            case CALL_OF_THE_WILD, RALLY, FIREBALL, FLAME_WAVE, BLIZZARD, FROST_NOVA, FIRE_PILLAR, GLACIAL_LANCE -> magic.skill(player, skill);
            case CHARGE_LINE, WAR_BANNER -> lancer.skill(player, skill);
            default -> false; // bow and crossbow skills go off when shooting
        };
        if (done) {
            startCooldown(player, path);
        }
    }

    /** Staffs: right-click = M2, sneak + right-click = the spell. */
    private void castStaff(Player player, Path path) {
        if (!player.isSneaking() || path.abilities(Path.Slot.SPELL).isEmpty()) {
            cast(player, path);
            return;
        }
        if (!ready(player, path, Path.Slot.SPELL)) {
            return;
        }
        if (magic.spell(player, plugin.ability(player, path, Path.Slot.SPELL))) {
            startCooldown(player, path, Path.Slot.SPELL);
        }
    }

    // ============================================================ Shooting

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectile() instanceof AbstractArrow arrow)
                || event.getBow() == null) {
            return;
        }
        Path.Weapon weapon = Path.Weapon.of(event.getBow().getType());
        if (weapon != Path.Weapon.BOW && weapon != Path.Weapon.CROSSBOW) {
            return;
        }
        arrow.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, weapon.name());
        if (weapon == Path.Weapon.BOW && plugin.isActive(player, Path.RANGER)) {
            shootRanger(event, player, arrow);
        }
        if (weapon == Path.Weapon.CROSSBOW && plugin.isActive(player, Path.ARBALIST)) {
            shootArbalist(player, arrow);
        }
    }

    private void shootRanger(EntityShootBowEvent event, Player player, AbstractArrow arrow) {
        int level = power(player, Path.RANGER);
        double passive = plugin.passivePower(player, Path.RANGER);
        if (plugin.has(player, Ability.EAGLE_EYE)) {
            // Faster arrows, and a chance to keep the arrow.
            arrow.setVelocity(arrow.getVelocity().multiply(1 + (0.1 + 0.005 * level) * passive));
            double save = (0.1 + 0.005 * level) * (level >= 15 ? 2 : 1) * passive;
            if (roll(save) && player.getGameMode() != GameMode.CREATIVE && event.getConsumable() != null
                    && arrow.getPickupStatus() == AbstractArrow.PickupStatus.ALLOWED) {
                // Give the arrow back; the one in flight can't be picked up, so nothing is duplicated.
                org.bukkit.inventory.ItemStack refund = event.getConsumable().clone();
                refund.setAmount(1);
                arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
                plugin.getServer().getScheduler().runTask(plugin, () -> player.getInventory().addItem(refund));
            }
        }
        if (plugin.has(player, Ability.CAMOUFLAGE) && stillSeconds.getOrDefault(player.getUniqueId(), 0) >= 2) {
            arrow.getPersistentDataContainer().set(camoKey, PersistentDataType.BYTE, (byte) 1);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            stillSeconds.remove(player.getUniqueId());
        }
        if (player.isSneaking() && event.getForce() >= 0.9f && ready(player, Path.RANGER)) {
            Ability skill = skillOf(player, Path.RANGER);
            arrow.getPersistentDataContainer().set(specialKey, PersistentDataType.STRING, skill.id());
            double power = plugin.skillPower(player, Path.RANGER);
            if (skill == Ability.POWER_SHOT) {
                arrow.setDamage(arrow.getDamage() * (1 + (0.6 + 0.02 * level) * power));
                arrow.setPierceLevel(level >= 10 ? 5 : 3);
                arrow.setCritical(true);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 0.8f);
                trail(arrow, Particle.END_ROD, 40);
            } else {
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 0.8f);
                trail(arrow, Particle.WHITE_ASH, 40);
            }
            startCooldown(player, Path.RANGER);
        }
    }

    private void shootArbalist(Player player, AbstractArrow arrow) {
        int level = power(player, Path.ARBALIST);
        double passive = plugin.passivePower(player, Path.ARBALIST);
        if (plugin.has(player, Ability.STEADY_HANDS)) {
            Vector horizontal = player.getVelocity().clone().setY(0);
            if (player.isSneaking() || horizontal.lengthSquared() < 0.003) {
                arrow.getPersistentDataContainer().set(steadyKey, PersistentDataType.BYTE, (byte) 1);
                arrow.setDamage(arrow.getDamage() * (1 + (0.1 + 0.005 * level) * passive));
            }
        }
        if (plugin.has(player, Ability.DEADEYE)) {
            arrow.setVelocity(arrow.getVelocity().multiply(1 + 0.2 * passive));
            arrow.setGravity(false);
            if (level >= 10) {
                arrow.setPierceLevel(arrow.getPierceLevel() + 1);
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> arrow.setGravity(true), 5);
        }
        if (player.isSneaking() && ready(player, Path.ARBALIST)) {
            Ability skill = skillOf(player, Path.ARBALIST);
            arrow.getPersistentDataContainer().set(specialKey, PersistentDataType.STRING, skill.id());
            if (skill == Ability.HEAVY_BOLT) {
                arrow.setDamage(arrow.getDamage() * (1 + (0.4 + 0.02 * level) * plugin.skillPower(player, Path.ARBALIST)));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.5f);
                trail(arrow, Particle.LARGE_SMOKE, 40);
            } else {
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.3f);
                trail(arrow, Particle.FIREWORK, 40);
            }
            startCooldown(player, Path.ARBALIST);
        }
    }

    /** Steady Hands also has a chance to load the crossbow without using ammo. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoadCrossbow(EntityLoadCrossbowEvent event) {
        if (event.getEntity() instanceof Player player && plugin.has(player, Ability.STEADY_HANDS)) {
            int level = power(player, Path.ARBALIST);
            if (roll((0.1 + 0.005 * level) * (level >= 15 ? 2 : 1) * plugin.passivePower(player, Path.ARBALIST))) {
                event.setConsumeItem(false);
            }
        }
    }

    @EventHandler
    public void onArrowImpact(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player player)) {
            return;
        }
        String special = arrow.getPersistentDataContainer().get(specialKey, PersistentDataType.STRING);
        Ability skill = special == null ? null : Ability.fromId(special);
        if (skill == null) {
            return;
        }
        Location at = event.getHitEntity() != null ? event.getHitEntity().getLocation() : arrow.getLocation();
        World world = at.getWorld();
        LivingEntity hit = event.getHitEntity() instanceof LivingEntity living ? living : null;
        switch (skill) {
            case POWER_SHOT -> {
                if (hit != null) {
                    Vector push = arrow.getVelocity().clone().setY(0).normalize().multiply(1.2).setY(0.35);
                    plugin.getServer().getScheduler().runTask(plugin, () -> hit.setVelocity(hit.getVelocity().add(push)));
                    if (power(player, Path.RANGER) >= 20) {
                        hit.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
                        hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    }
                    world.spawnParticle(Particle.CRIT, at.clone().add(0, 1, 0), 20, 0.3, 0.4, 0.3, 0.3);
                }
            }
            case SNARE_SHOT -> {
                int ticks = (int) (60 * plugin.skillPower(player, Path.RANGER));
                List<LivingEntity> caught = new ArrayList<>();
                if (hit != null && validTarget(player, hit)) {
                    caught.add(hit);
                }
                if (power(player, Path.RANGER) >= 10) {
                    for (LivingEntity near : around(player, at, 3)) {
                        if (!caught.contains(near)) {
                            caught.add(near);
                        }
                    }
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> caught.forEach(e -> root(e, ticks)));
                world.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 40, 1, 0.4, 1, 0, Material.COBWEB.createBlockData());
                world.playSound(at, Sound.BLOCK_TRIPWIRE_CLICK_ON, 1.2f, 0.6f);
            }
            case HEAVY_BOLT -> {
                int level = power(player, Path.ARBALIST);
                world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.5, 0), 1);
                world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND, 1f, 0.8f);
                if (hit != null) {
                    Vector push = arrow.getVelocity().clone().setY(0).normalize().multiply(2.0).setY(0.5);
                    plugin.getServer().getScheduler().runTask(plugin, () -> hit.setVelocity(push));
                    stun(hit, 40);
                }
                if (level >= 10) {
                    for (LivingEntity near : around(player, at, 3)) {
                        if (!near.equals(hit)) {
                            splash(player, near, arrow.getDamage() * 0.5 * plugin.skillPower(player, Path.ARBALIST));
                            stun(near, 20);
                        }
                    }
                    world.spawnParticle(Particle.DUST_PLUME, at, 30, 1.5, 0.2, 1.5, 0.05);
                }
            }
            case CLUSTER_BOLT -> {
                int pieces = power(player, Path.ARBALIST) >= 10 ? 8 : 5;
                double damage = Math.max(2, arrow.getDamage() * 0.5) * plugin.skillPower(player, Path.ARBALIST);
                Location top = at.clone().add(0, 3.5, 0);
                world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
                world.spawnParticle(Particle.FIREWORK, top, 20, 0.3, 0.3, 0.3, 0.1);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (int i = 0; i < pieces; i++) {
                        double angle = Math.PI * 2 * i / pieces;
                        Arrow bomblet = world.spawnArrow(top, new Vector(Math.cos(angle) * 0.35, -1, Math.sin(angle) * 0.35), 0.9f, 0f);
                        bomblet.setShooter(player);
                        bomblet.setDamage(damage);
                        bomblet.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    }
                });
            }
            default -> {
            }
        }
    }

    // ============================================================ Duelist & Warbringer skills

    /** Lunge: dash forward and pierce the first enemy. */
    private void lunge(Player player) {
        int level = power(player, Path.DUELIST);
        boolean upgraded = level >= 10;
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        player.setVelocity(direction.clone().multiply(upgraded ? 2.1 : 1.6).setY(0.15));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        double damage = attackDamage(player) * (1 + ((upgraded ? 0.5 : 0.2) + 0.02 * level) * plugin.skillPower(player, Path.DUELIST));
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || ++tick > 7) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 4, 0.2, 0.3, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(0x5DADE2), 1.2f));
                for (LivingEntity target : around(player, player.getLocation(), 1.8)) {
                    splash(player, target, damage);
                    world.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
                    world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.2f);
                    player.setVelocity(new Vector());
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    /** Feint: hop back, daze the enemy in front, and make the next sword hit a critical one. */
    private void feint(Player player) {
        Vector back = player.getLocation().getDirection().setY(0).normalize().multiply(-1.1).setY(0.3);
        LivingEntity target = inFront(player, 5);
        player.setVelocity(back);
        if (target != null) {
            stun(target, (int) ((power(player, Path.DUELIST) >= 10 ? 60 : 30) * plugin.skillPower(player, Path.DUELIST)));
        }
        feintUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 0.8f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 8, 0.3, 0.1, 0.3, 0.02);
    }

    private void primeCrushingBlow(Player player) {
        crushingUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.4f);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                Long until = crushingUntil.get(player.getUniqueId());
                if (!player.isOnline() || until == null || until < System.currentTimeMillis() || ++tick > 60) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1.2, 0), 3, 0.3, 0.4, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(0xC0392B), 1.4f));
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    /** Battle Leap: jump at the spot the player looks at and land with a blow. */
    private void battleLeap(Player player) {
        Block aimed = player.getTargetBlockExact(12);
        Location target = aimed != null ? aimed.getLocation().add(0.5, 1, 0.5)
                : player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(8));
        Vector flat = target.toVector().subtract(player.getLocation().toVector()).setY(0);
        double distance = flat.length();
        Vector velocity = distance < 0.5 ? new Vector(0, 0.9, 0) : flat.normalize().multiply(Math.min(1.6, 0.3 + distance * 0.12)).setY(0.75);
        player.setVelocity(velocity);
        protectFromFall(player, 4000);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.2f);
        int level = power(player, Path.WARBRINGER);
        double damage = attackDamage(player) * (1 + 0.03 * level) * plugin.skillPower(player, Path.WARBRINGER);
        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || ++tick > 60) {
                    cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 3, 0.2, 0.3, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(0xC0392B), 1.2f));
                if (tick > 4 && (onGround(player) || player.isInWater())) {
                    cancel();
                    Location center = player.getLocation();
                    double radius = level >= 10 ? 5 : 3.5;
                    world.playSound(center, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 0.8f);
                    world.spawnParticle(Particle.EXPLOSION, center, 1);
                    world.spawnParticle(Particle.DUST_PLUME, center, 40, radius / 2, 0.2, radius / 2, 0.05);
                    for (LivingEntity victim : around(player, center, radius)) {
                        splash(player, victim, damage);
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        if (level >= 10) {
                            stun(victim, 30);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    // ============================================================ Hits

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (dealingSplash || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Source source = source(event.getDamager());
        if (source == null) {
            return;
        }
        Player player = source.player();
        markCombat(player);
        // Rend bonus applies to damage from any player.
        double[] stacks = rend.get(target.getUniqueId());
        if (stacks != null) {
            if (stacks[1] < System.currentTimeMillis()) {
                rend.remove(target.getUniqueId());
            } else {
                event.setDamage(event.getDamage() * (1 + stacks[0] * stacks[2]));
            }
        }
        // Bloodrage works for any melee hit, whatever the weapon.
        if (source.kind() == Kind.MELEE && plugin.has(player, Ability.BLOODRAGE)) {
            int level = power(player, Path.WARBRINGER);
            double missing = 1 - player.getHealth() / maxHealth(player);
            event.setDamage(event.getDamage() * (1 + missing * (0.3 + 0.005 * level) * plugin.passivePower(player, Path.WARBRINGER)));
            if (level >= 15 && player.getHealth() < maxHealth(player) * 0.3) {
                heal(player, event.getDamage() * 0.05);
            }
        }
        boolean charged = source.kind() != Kind.MELEE || player.getAttackCooldown() >= MIN_CHARGE;
        magic.onAnyHit(event, source, target); // Beastmaster marks: any hit by the player
        List<Path> paths = paths(source);
        for (Path path : paths) {
            switch (path) {
                case DUELIST -> duelistHit(event, player, target, charged);
                case WARBRINGER -> warbringerHit(event, player, target, charged);
                case RANGER -> rangerHit(event, player, (AbstractArrow) source.damager(), target);
                case ARBALIST -> arbalistHit(event, player, target);
                case TIDECALLER, JUGGERNAUT, SENTINEL, SHADOW -> more.hit(path, event, source, target, charged);
                case BEASTMASTER, PYROMANCER, FROST_WARDEN -> magic.hit(path, event, source, target, charged);
                case LANCER -> lancer.hit(event, player, target, charged);
            }
            if (event.isCancelled()) {
                return;
            }
        }
        for (Path path : paths) {
            if (plugin.hasTalent(player, path, Path.Talent.MIGHT)) {
                event.setDamage(event.getDamage() * 1.1);
            }
            if (plugin.hasTalent(player, path, Path.Talent.LEGEND)) {
                event.setDamage(event.getDamage() * 1.1);
            }
            event.setDamage(event.getDamage() * plugin.veteranBonus(player, path));
            if (plugin.hasTalent(player, path, Path.Talent.LIFESTEAL)) {
                heal(player, event.getDamage() * 0.05);
            }
            double xp = switch (source.kind()) {
                case MELEE -> path == Path.SENTINEL ? 0 : charged ? 1 : 0;
                case ARROW, TRIDENT, BOLT -> 1.5;
                case PET -> 1;
            };
            plugin.addXp(player, path, xp);
        }
    }

    private void duelistHit(EntityDamageByEntityEvent event, Player player, LivingEntity target, boolean charged) {
        int level = power(player, Path.DUELIST);
        double power = plugin.hitPower(player, Path.DUELIST);
        World world = target.getWorld();
        Location body = target.getLocation().add(0, target.getHeight() / 2, 0);
        UUID id = player.getUniqueId();
        if (plugin.has(player, Ability.PRECISION)) {
            if (event.isCritical()) {
                event.setDamage(event.getDamage() * (1 + (0.1 + 0.005 * level) * power));
            }
            if (charged) {
                int hits = target.getUniqueId().equals(precisionTarget.get(id)) ? precisionHits.getOrDefault(id, 0) + 1 : 1;
                precisionTarget.put(id, target.getUniqueId());
                if (hits >= (level >= 5 ? 3 : 4)) {
                    hits = 0;
                    event.setDamage(event.getDamage() * (1 + (0.5 + 0.01 * level) * power));
                    world.spawnParticle(Particle.CRIT, body, 25, 0.3, 0.4, 0.3, 0.3);
                    world.spawnParticle(Particle.SWEEP_ATTACK, body, 1);
                    world.playSound(body, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.3f);
                    if (level >= 20) {
                        heal(player, 2);
                        world.spawnParticle(Particle.HEART, player.getLocation().add(0, 2.1, 0), 1);
                    }
                }
                precisionHits.put(id, hits);
            }
        }
        if (plugin.has(player, Ability.RIPOSTE) && riposteUntil.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            riposteUntil.remove(id);
            event.setDamage(event.getDamage() * (1 + (0.4 + 0.01 * level) * power));
            world.spawnParticle(Particle.ENCHANTED_HIT, body, 15, 0.3, 0.4, 0.3, 0.2);
            world.playSound(body, Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.6f);
            if (level >= 10) {
                heal(player, 2);
            }
        }
        if (feintUntil.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            feintUntil.remove(id);
            event.setDamage(event.getDamage() * (1 + 0.5 * plugin.skillPower(player, Path.DUELIST)));
            world.spawnParticle(Particle.CRIT, body, 20, 0.3, 0.4, 0.3, 0.3);
            world.playSound(body, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.1f);
        }
    }

    private void warbringerHit(EntityDamageByEntityEvent event, Player player, LivingEntity target, boolean charged) {
        int level = power(player, Path.WARBRINGER);
        double power = plugin.hitPower(player, Path.WARBRINGER);
        World world = target.getWorld();
        Location body = target.getLocation().add(0, target.getHeight() / 2, 0);
        Long primed = crushingUntil.remove(player.getUniqueId());
        if (primed != null && primed >= System.currentTimeMillis()) {
            double skill = plugin.skillPower(player, Path.WARBRINGER);
            event.setDamage(event.getDamage() * (1 + (0.8 + 0.03 * level) * skill));
            stun(target, 40);
            world.spawnParticle(Particle.EXPLOSION, body, 1);
            world.playSound(body, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.9f);
            if (level >= 10) {
                double splashDamage = event.getDamage() * 0.5;
                for (LivingEntity near : around(player, target.getLocation(), 3)) {
                    if (!near.equals(target)) {
                        splash(player, near, splashDamage);
                    }
                }
                world.spawnParticle(Particle.DUST_PLUME, target.getLocation(), 30, 1.5, 0.2, 1.5, 0.05);
            }
        }
        if (!charged) {
            return;
        }
        if (plugin.has(player, Ability.REND)) {
            int maxStacks = level >= 5 ? 4 : 3;
            double[] stacks = rend.getOrDefault(target.getUniqueId(), new double[] {0, 0, 0});
            if (stacks[1] < System.currentTimeMillis()) {
                stacks[0] = 0;
            }
            stacks[0] = Math.min(maxStacks, stacks[0] + 1);
            stacks[1] = System.currentTimeMillis() + 4000;
            stacks[2] = (0.04 + 0.002 * level) * power;
            rend.put(target.getUniqueId(), stacks);
            world.spawnParticle(Particle.DAMAGE_INDICATOR, body, (int) stacks[0] * 2, 0.3, 0.3, 0.3, 0.05);
        }
        if (plugin.has(player, Ability.SUNDER) && roll((0.15 + 0.005 * level) * power)) {
            AttributeInstance armor = target.getAttribute(Attribute.ARMOR);
            if (armor != null && armor.getModifier(sunderKey) == null) {
                armor.addTransientModifier(new AttributeModifier(sunderKey, level >= 10 ? -6 : -4, AttributeModifier.Operation.ADD_NUMBER));
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (target.isValid()) {
                        armor.removeModifier(sunderKey);
                    }
                }, 100);
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
            world.playSound(body, Sound.ITEM_SHIELD_BREAK, 0.8f, 0.7f);
            world.spawnParticle(Particle.BLOCK, body, 15, 0.3, 0.3, 0.3, 0, Material.IRON_BLOCK.createBlockData());
        }
    }

    private void rangerHit(EntityDamageByEntityEvent event, Player shooter, AbstractArrow arrow, LivingEntity target) {
        int level = power(shooter, Path.RANGER);
        double power = plugin.hitPower(shooter, Path.RANGER);
        if (plugin.has(shooter, Ability.HEADHUNTER)) {
            double distance = shooter.getWorld().equals(target.getWorld()) ? shooter.getLocation().distance(target.getLocation()) : 0;
            if (distance > 12) {
                double bonus = Math.min(0.4, (distance - 12) * 0.02) * (1 + 0.02 * level) * power;
                event.setDamage(event.getDamage() * (1 + bonus));
                target.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 10, 0.2, 0.2, 0.2, 0.2);
                if (level >= 5 && distance > 20) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                }
            }
        }
        if (plugin.has(shooter, Ability.VENOM_TIPS) && arrow.isCritical()) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, (int) ((40 + 2 * level) * power), level >= 10 ? 1 : 0));
            target.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0);
        }
        if (arrow.getPersistentDataContainer().has(camoKey, PersistentDataType.BYTE)) {
            event.setDamage(event.getDamage() * (1 + (level >= 10 ? 0.6 : 0.3) * plugin.passivePower(shooter, Path.RANGER)));
            target.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 15, 0.2, 0.2, 0.2, 0.3);
        }
    }

    private void arbalistHit(EntityDamageByEntityEvent event, Player shooter, LivingEntity target) {
        int level = power(shooter, Path.ARBALIST);
        double power = plugin.hitPower(shooter, Path.ARBALIST);
        if (plugin.has(shooter, Ability.ARMOR_PIERCER)) {
            AttributeInstance armor = target.getAttribute(Attribute.ARMOR);
            if (armor != null && armor.getValue() > 0) {
                event.setDamage(event.getDamage() + armor.getValue() * (0.15 + 0.005 * level) * power);
                target.getWorld().spawnParticle(Particle.ENCHANTED_HIT, target.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.2);
            }
            if (level >= 5 && target instanceof Player victim && victim.isBlocking()) {
                victim.setCooldown(Material.SHIELD, 60);
                victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
            }
        }
        if (plugin.has(shooter, Ability.CONCUSSION) && roll((0.15 + 0.005 * level) * power)) {
            stun(target, level >= 10 ? 50 : 30);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 1.4f);
        }
    }

    // ============================================================ Getting hurt

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        onHurtPaths(event, player);
        if (!event.isCancelled()) {
            unyielding(event, player);
        }
    }

    /** Unyielding (level-50 talent): once every 3 minutes a fatal hit leaves the player at one heart. */
    private void unyielding(EntityDamageEvent event, Player player) {
        if (event.getFinalDamage() < player.getHealth() || !plugin.hasTalentAnywhere(player, Path.Talent.UNYIELDING)
                || event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.KILL) {
            return;
        }
        long now = System.currentTimeMillis();
        if (unyieldingReady.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        unyieldingReady.put(player.getUniqueId(), now + 180_000);
        event.setCancelled(true);
        player.setHealth(Math.min(2, maxHealth(player)));
        player.setNoDamageTicks(20);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
        player.sendActionBar(plugin.lang().get("unyielding"));
    }

    private void onHurtPaths(EntityDamageEvent event, Player player) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && noFallUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) {
            event.setCancelled(true);
            return;
        }
        Entity attacker = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        if (attacker != null) {
            markCombat(player);
        }
        boolean melee = attacker instanceof LivingEntity;
        for (Path path : plugin.active(player)) {
            if (plugin.hasTalent(player, path, Path.Talent.GUARD)) {
                event.setDamage(event.getDamage() * 0.9);
            }
        }
        if (melee && plugin.has(player, Ability.RIPOSTE)) {
            riposteUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3000);
        }
        if (melee && plugin.has(player, Ability.EN_GARDE)
                && Path.Weapon.of(player.getInventory().getItemInMainHand().getType()) == Path.Weapon.SWORD) {
            int level = power(player, Path.DUELIST);
            double passive = plugin.passivePower(player, Path.DUELIST);
            if (level >= 10 && roll(0.15 * passive)) {
                event.setCancelled(true);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.5f);
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 1);
                return;
            }
            event.setDamage(event.getDamage() * (1 - Math.min(0.5, (0.08 + 0.004 * level) * passive)));
        }
        more.onHurt(event, player, attacker);
        if (!event.isCancelled()) {
            magic.onHurt(event, player, attacker);
        }
        if (!event.isCancelled()) {
            lancer.onHurt(event, player);
        }
        if (plugin.has(player, Ability.FLEET_FOOT) && power(player, Path.DUELIST) >= 15) {
            plugin.getServer().getScheduler().runTask(plugin, () -> refresh(player));
        }
    }

    // ============================================================ Kills

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        rend.remove(entity.getUniqueId());
        Source source = entity.getLastDamageCause() instanceof EntityDamageByEntityEvent damage ? source(damage.getDamager()) : null;
        if (source == null && entity.getKiller() != null) {
            Player killer = entity.getKiller();
            source = new Source(killer, Kind.MELEE, Path.Weapon.of(killer.getInventory().getItemInMainHand().getType()), killer);
        }
        if (source == null) {
            return;
        }
        Player player = source.player();
        double xp = entity instanceof Enemy || entity instanceof Player ? 5 : 2;
        for (Path path : paths(source)) {
            plugin.addXp(player, path, xp);
            if (path == Path.WARBRINGER && plugin.has(player, Ability.CRUSHING_BLOW) && power(player, Path.WARBRINGER) >= 20) {
                cutCooldown(player, Path.WARBRINGER, 0);
            }
            if (plugin.hasTalent(player, path, Path.Talent.KILL_RESET)) {
                cutCooldown(player, path, 0.5);
            }
            if (plugin.hasTalent(player, path, Path.Talent.SECOND_WIND)) {
                heal(player, 4);
            }
        }
        more.onKill(source, entity);
        magic.onKill(source, entity);
    }
}
