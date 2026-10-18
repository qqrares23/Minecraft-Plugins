# Paths

Combat classes. Players pick two of 12 paths (Duelist, Warbringer, Ranger, Arbalist, Tidecaller,
Juggernaut, Sentinel, Shadow, Beastmaster, Pyromancer, Frost Warden, Lancer). Every path has three
slots – an on-hit passive, an always-on passive and an M2 (right-click) skill – with two abilities
to choose from in each, and a talent choice at levels 5, 10, 15, 20, 30, 40 and 50.

- Path levels 1-50 from hits and kills with the path's weapon (only while active).
- Two active paths can't share a weapon; dropping a path starts a cooldown, progress is kept.
- Optional chat title with the highest active path: `[Warbringer 15] Name: hi`.

## Commands

| Command | Permission |
|---|---|
| `/paths` – menu | `paths.use` |
| `/paths admin setlevel <player> <path\|all> <level>` | `paths.admin` |
| `/paths admin resetcooldown <player>` (switch + M2 cooldowns) | `paths.admin` |
| `/paths admin setactive <player> <path,path\|none>` · `info <player>` | `paths.admin` |
| `/paths admin reload \| config \| toggle \| lang` | `paths.admin` |

## Permissions

`paths.use`, `paths.path.<path>` (e.g. `paths.path.pyromancer`), `paths.path.*` – granted by
default. A path a player loses access to stops working but stays picked. `paths.admin` – ops.

## Settings (config.yml)

| Key | What |
|---|---|
| `language`, `worlds` | Language and world filter (paths do nothing in other worlds) |
| `max-active-paths` | How many paths at once (default 2) |
| `switch-cooldown-minutes` | Wait after dropping a path |
| `combat-lock-seconds` | Abilities/talents can't be changed this long after combat |
| `paths.<path>.enabled` / `.xp-multiplier` | Per-path on/off and XP rate |
| `talents.<talent>` | Turn single talents off |
| `max-level`, `bonus-cap-level`, `xp-base`, `xp-per-level`, `xp-multiplier` | Levelling |
| `chat-titles` | Chat title on/off (format in `lang/*.yml` → `chat-title`) |

## Data keys

`paths:active`, `paths:xp_<path>`, `paths:ability_<path>_<slot>`, `paths:talent_<path>_<tier>`,
`paths:cooldowns` – on players.
