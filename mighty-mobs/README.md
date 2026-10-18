# MightyMobs

Tougher, more varied hostile mobs.

- **Mighty mobs:** a share of natural spawns get 1-2 abilities (Tank, Swift, Berserker, Venomous,
  Frost, Vampire, Explosive, Leaper, Regenerating, Blinker, Necromancer), more health, more XP and a
  chance of rare drops.
- **Health display** over every mob: `Name ❤ 8/10`.
- **Special spawns:** chicken/spider jockeys, charged creepers, skeleton/zombie horsemen, armored
  brutes, baby zombie packs, slime towers, bat bombers, witch covens, the Killer Bunny.
- **Blood Moon:** some nights far more mighty mobs and special spawns, double XP, no sleeping, a
  boss bar and a top-hunter announcement at dawn.
- Mounts of special spawns despawn like normal mobs (unless a player saddles, leashes, name-tags
  or rides them).

## Commands (all need `mightymobs.admin`, ops by default)

| Command | What |
|---|---|
| `/mm status` | Current settings |
| `/mm chance <percent>` · `/mm doublechance <percent>` | Mighty mob / second ability chance |
| `/mm ability <name> on\|off` | Turn an ability on or off |
| `/mm special on\|off` · `/mm special <type> <percent\|spawn>` | Special spawns |
| `/mm spawn <mob> [abilities...]` | Spawn a mighty mob where you look |
| `/mm bloodmoon [status\|start\|stop\|chance <percent>]` | Blood Moon |
| `/mm reload \| config \| toggle \| lang` | Shared admin commands |

## Settings (config.yml)

| Key | What |
|---|---|
| `language`, `worlds` | Language; worlds where mighty mobs, special spawns and Blood Moons happen |
| `ignored-mobs` | Mob types that never become mighty or special |
| `spawn-chance`, `double-ability-chance`, `spawn-reasons` | How often |
| `health-multiplier`, `buffed-damage-multiplier`, `xp-multiplier`, `xp.*` | Strength and rewards |
| `drops.*` | Themed and rare drops |
| `abilities.<ability>` | Per-ability on/off |
| `health-display.enabled` / `.always-visible` / `.all-worlds` | Health bars |
| `special-spawns.*` | Per-type chances |
| `blood-moon.*` (incl. `blood-moon.worlds`) | Blood Moon chance, strength, worlds |

## Data keys

Worlds: `mightymobs:blood_moon_active` (other plugins can check it). Mobs: `mightymobs:abilities`,
`mightymobs:special`, `mightymobs:mount`, `mightymobs:base_name`.
