# MagicEnchants

31 custom enchantments registered as **real** enchantments: they come from the enchanting table,
loot chests, fishing, librarian trades and mob drops, combine on anvils and show in tooltips.

| Group | Enchantments |
|---|---|
| Swords, axes, spears | Bleeding, Venom, Frostbite, Lifesteal, Executioner, Thunderstrike, Cleaving, Soul Harvest, Chain Lightning, Momentum, Skill Surge, *Blood Pact*, *Glass Blade* (curses) |
| Spears | Pinning, Skyfall, Vanguard |
| Armor | Thorns of Frost, Second Wind, Evasion, Spring Heels, Night Vision |
| Bows / crossbows | Homing, Barrage, Explosive Tips, Hunter's Mark, Ricochet, Harpoon, Scatter Shot |
| Tools | Auto-Smelt, Magnet, Excavator |

This is a **Paper plugin** (`paper-plugin.yml` with a bootstrapper): enchantments are registered
before the worlds load. Watch the log for `Loaded 31 magic enchantments.` after updates.
**Removing the plugin strips these enchantments from items** – turn single ones off instead.

## Commands

| Command | Permission |
|---|---|
| `/enchants` – menu of every enchantment (text list on the console) | `magicenchants.menu` |
| `/enchants admin give <player> <enchantment> [level]` – enchanted book | `magicenchants.admin` |
| `/enchants admin list` · `reload \| config \| toggle \| lang` | `magicenchants.admin` |

## Settings (config.yml)

`language`, `worlds` (effects only work there; items keep the enchantments everywhere), and per
enchantment:

```yaml
enchantments:
  bleeding:
    enabled: true          # false = no effect, can't be found any more (items keep it)
    max-level: 3           # restart needed
    weight: 5              # 10 common ... 1 very rare; restart needed
    enchanting-table: true # restart needed
    loot: true             # restart needed
    trades: true           # restart needed
```

`enabled`, `worlds` and `language` apply immediately; the rest needs a restart because it is part
of the registration.
