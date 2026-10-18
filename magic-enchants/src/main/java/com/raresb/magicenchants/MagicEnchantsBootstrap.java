package com.raresb.magicenchants;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry.EnchantmentCost;
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import io.papermc.paper.registry.tag.TagKey;
import io.papermc.paper.tag.TagEntry;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemType;

/**
 * Registers the enchantments with the game before the worlds load, so they are real
 * enchantments: they appear in the enchanting table, on books, in anvils and in tooltips.
 */
public final class MagicEnchantsBootstrap implements PluginBootstrap {
    /** Swords, axes, spears, bows, crossbows, tridents and maces. */
    private static final TagKey<ItemType> ANY_WEAPON = TagKey.create(RegistryKey.ITEM, Key.key(MagicEnchant.NAMESPACE, "any_weapon"));
    /** Pickaxes, shovels, axes, hoes, shears and fishing rods. */
    private static final TagKey<ItemType> GATHERING = TagKey.create(RegistryKey.ITEM, Key.key(MagicEnchant.NAMESPACE, "gathering"));
    /** Every weapon plus the path staffs (Blaze Rod, Breeze Rod). */
    private static final TagKey<ItemType> COMBAT = TagKey.create(RegistryKey.ITEM, Key.key(MagicEnchant.NAMESPACE, "combat"));

    @Override
    public void bootstrap(BootstrapContext context) {
        EnchantSettings settings = EnchantSettings.load(context.getDataDirectory());
        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {
            for (MagicEnchant enchant : MagicEnchant.values()) {
                RegistryKeySet<ItemType> items = items(event, enchant.target());
                event.registry().register(enchant.key(), builder -> {
                    builder.description(Component.text(enchant.displayName(), enchant.color()))
                            .supportedItems(items)
                            .primaryItems(items)
                            .weight(settings.weight(enchant))
                            .maxLevel(settings.maxLevel(enchant))
                            .minimumCost(EnchantmentCost.of(enchant.minCostBase(), enchant.costPerLevel()))
                            .maximumCost(EnchantmentCost.of(enchant.maxCostBase(), enchant.costPerLevel()))
                            .anvilCost(enchant.anvilCost())
                            .activeSlots(slots(enchant.target()));
                    // No exclusive sets: every enchantment stacks with every other (1.8.0) - except Soulbound and
                    // Curse of Vanishing: Paper deletes vanishing items before Soulbound could keep them (1.9.2).
                    if (enchant == MagicEnchant.SOULBOUND) {
                        builder.exclusiveWith(RegistrySet.keySet(RegistryKey.ENCHANTMENT, EnchantmentKeys.VANISHING_CURSE));
                    }
                });
            }
        }));

        // Our own item tags, for enchantments that go on several kinds of item.
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.preFlatten(RegistryKey.ITEM), event -> {
            event.registrar().setTag(ANY_WEAPON, List.of(
                    TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_SHARP_WEAPON), TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_BOW),
                    TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_CROSSBOW), TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_TRIDENT),
                    TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_MACE)));
            event.registrar().setTag(COMBAT, List.of(
                    TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_SHARP_WEAPON), TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_BOW),
                    TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_CROSSBOW), TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_TRIDENT),
                    TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_MACE), TagEntry.valueEntry(ItemTypeKeys.BLAZE_ROD),
                    TagEntry.valueEntry(ItemTypeKeys.BREEZE_ROD)));
            event.registrar().setTag(GATHERING, List.of(
                    TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_MINING), TagEntry.tagEntry(ItemTypeTagKeys.ENCHANTABLE_FISHING)));
        });

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.preFlatten(RegistryKey.ENCHANTMENT), event -> {
            // Where each one can be found comes from config.yml (enchantments.<id>); disabled ones are in none of these.
            event.registrar().addToTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE, entries(settings::enchantingTable));
            event.registrar().addToTag(EnchantmentTagKeys.NON_TREASURE, entries(e -> settings.enabled(e) && !e.treasure()));
            // Treasure (Soulbound): only found in loot and trades.
            event.registrar().addToTag(EnchantmentTagKeys.TREASURE, entries(e -> settings.enabled(e) && e.treasure()));
            event.registrar().addToTag(EnchantmentTagKeys.ON_RANDOM_LOOT, entries(settings::loot));
            event.registrar().addToTag(EnchantmentTagKeys.TRADEABLE, entries(settings::trades));
            // Every enchantment combines with every other (1.8.0): vanilla's exclusive sets are emptied, so
            // Mending + Infinity, Sharpness + Smite, all Protections, Silk Touch + Fortune, Multishot + Piercing,
            // Riptide + Loyalty/Channeling, Density + Breach etc. all go on the same item (table and anvil).
            for (TagKey<Enchantment> set : List.of(EnchantmentTagKeys.EXCLUSIVE_SET_ARMOR, EnchantmentTagKeys.EXCLUSIVE_SET_BOOTS,
                    EnchantmentTagKeys.EXCLUSIVE_SET_BOW, EnchantmentTagKeys.EXCLUSIVE_SET_CROSSBOW, EnchantmentTagKeys.EXCLUSIVE_SET_DAMAGE,
                    EnchantmentTagKeys.EXCLUSIVE_SET_MINING, EnchantmentTagKeys.EXCLUSIVE_SET_RIPTIDE)) {
                event.registrar().setTag(set, List.of());
            }
        });
    }

    /** The enchantments that pass the test, as tag entries. */
    private static List<TagEntry<Enchantment>> entries(java.util.function.Predicate<MagicEnchant> test) {
        return Arrays.stream(MagicEnchant.values())
                .filter(test)
                .map(e -> TagEntry.valueEntry(e.key()))
                .toList();
    }

    private static RegistryKeySet<ItemType> items(RegistryComposeEvent<Enchantment, ?> event, MagicEnchant.Target target) {
        return switch (target) {
            case WEAPON -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_SHARP_WEAPON); // swords, axes and spears
            case SPEAR -> event.getOrCreateTag(ItemTypeTagKeys.SPEARS);
            case ARMOR -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_ARMOR);
            case HEAD -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_HEAD_ARMOR);
            case CHEST -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_CHEST_ARMOR);
            case LEGS -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_LEG_ARMOR);
            case FEET -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_FOOT_ARMOR);
            case BOW -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_BOW);
            case CROSSBOW -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_CROSSBOW);
            case RANGED -> RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.BOW, ItemTypeKeys.CROSSBOW);
            case MINING -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_MINING);
            case FISHING -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_FISHING);
            case HOE -> event.getOrCreateTag(ItemTypeTagKeys.HOES);
            case TRIDENT -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_TRIDENT);
            case MACE -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_MACE);
            case ELYTRA -> RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.ELYTRA);
            case SHIELD -> RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.SHIELD);
            case ANY_WEAPON -> event.getOrCreateTag(ANY_WEAPON);
            case GATHERING -> event.getOrCreateTag(GATHERING);
            case ANYTHING -> event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_VANISHING); // everything that can lose an item to Curse of Vanishing
            case WOLF_ARMOR -> RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.WOLF_ARMOR);
            case AXE -> event.getOrCreateTag(ItemTypeTagKeys.AXES);
            case PICKAXE -> event.getOrCreateTag(ItemTypeTagKeys.PICKAXES);
            // Horse armor isn't enchantable at the table (books on an anvil); staffs are, through StaffTable.
            case STAFF -> RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.BLAZE_ROD, ItemTypeKeys.BREEZE_ROD);
            case BLAZE_STAFF -> RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.BLAZE_ROD);
            case BREEZE_STAFF -> RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.BREEZE_ROD);
            case COMBAT -> event.getOrCreateTag(COMBAT);
            case HORSE_ARMOR -> RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.LEATHER_HORSE_ARMOR, ItemTypeKeys.COPPER_HORSE_ARMOR,
                    ItemTypeKeys.IRON_HORSE_ARMOR, ItemTypeKeys.GOLDEN_HORSE_ARMOR, ItemTypeKeys.DIAMOND_HORSE_ARMOR,
                    ItemTypeKeys.NETHERITE_HORSE_ARMOR);
            case SHEARS -> RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.SHEARS);
            case SHOVEL -> event.getOrCreateTag(ItemTypeTagKeys.SHOVELS);
            case DIGGER -> RegistrySet.keySet(RegistryKey.ITEM,
                    ItemTypeKeys.WOODEN_PICKAXE, ItemTypeKeys.STONE_PICKAXE, ItemTypeKeys.COPPER_PICKAXE, ItemTypeKeys.IRON_PICKAXE,
                    ItemTypeKeys.GOLDEN_PICKAXE, ItemTypeKeys.DIAMOND_PICKAXE, ItemTypeKeys.NETHERITE_PICKAXE,
                    ItemTypeKeys.WOODEN_SHOVEL, ItemTypeKeys.STONE_SHOVEL, ItemTypeKeys.COPPER_SHOVEL, ItemTypeKeys.IRON_SHOVEL,
                    ItemTypeKeys.GOLDEN_SHOVEL, ItemTypeKeys.DIAMOND_SHOVEL, ItemTypeKeys.NETHERITE_SHOVEL);
        };
    }

    private static EquipmentSlotGroup slots(MagicEnchant.Target target) {
        return switch (target) {
            case ARMOR -> EquipmentSlotGroup.ARMOR;
            case HEAD -> EquipmentSlotGroup.HEAD;
            case CHEST, ELYTRA -> EquipmentSlotGroup.CHEST;
            case LEGS -> EquipmentSlotGroup.LEGS;
            case FEET -> EquipmentSlotGroup.FEET;
            case BOW, CROSSBOW, RANGED, SHIELD -> EquipmentSlotGroup.HAND;
            case ANYTHING -> EquipmentSlotGroup.ANY;
            case WOLF_ARMOR, HORSE_ARMOR -> EquipmentSlotGroup.BODY;
            default -> EquipmentSlotGroup.MAINHAND;
        };
    }
}
