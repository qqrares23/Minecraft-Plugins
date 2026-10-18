# Professions

Everyone has all 12 professions – Woodcutting, Mining, Digging, Farming, Foraging, Fishing,
Hunting, Husbandry, Smelting, Enchanting, Alchemy, Smithing – and levels them by doing the work.

- Levels 1-50: more drops (extra logs, double ore/harvest), faster fishing, rare finds that fit the
  block (flint from gravel, diamonds deep down, quartz in the Nether...).
- At levels 10/20/30/40/50 each profession offers two perks; players pick one (switchable).
- Blocks placed by players give no XP. Spawner mobs give reduced Hunting XP.
- Counts blocks broken by Timberella, VeinMiner and WeaponSkills' gathering skills.

## Commands

| Command | Permission |
|---|---|
| `/professions` (aliases `/prof`, `/labour`) – menu | `professions.use` |
| `/professions admin setlevel <player> <profession\|all> <level>` | `professions.admin` |
| `/professions admin addxp <player> <profession> <amount>` · `reset <player> [profession]` · `info <player>` | `professions.admin` |
| `/professions admin reload \| config \| toggle \| lang` | `professions.admin` |

## Permissions

`professions.use`, `professions.profession.<profession>`, `professions.profession.*` – granted by
default. `professions.admin` – ops.

## Settings (config.yml)

| Key | What |
|---|---|
| `language`, `worlds` | Language and world filter |
| `professions.<profession>.enabled` | On/off (hidden from the menu, no XP or bonuses) |
| `professions.<profession>.xp-multiplier` / `.find-multiplier` | Per-profession XP and find rates |
| `max-level`, `xp-base`, `xp-per-level`, `xp-multiplier` | Levelling (`xp-multiplier: 2.0` = double-XP weekend) |
| `spawner-xp-fraction` | Hunting XP from spawner mobs |
| `messages.xp-action-bar`, `messages.rare-finds` | Turn the XP bar text / rare-find chat off |

## Data keys

`professions:xp_<profession>`, `professions:perk_<profession>_<tier>` – on players;
`professions:placed` – on chunks (player-placed blocks).
