# raresb's survival plugins

Seven Paper plugins that add RPG-style progression to a survival server. Each one works on its
own; installed together they link up (see the table at the bottom).

| Plugin | What it adds | Folder |
|---|---|---|
| **WeaponSkills** | 85 special attacks for 13 weapons and tools (press F), levels, mastery, evolutions, augments, dodge roll, cross-weapon combos, `/guide` book | [`weapon-skills/`](weapon-skills/README.md) |
| **Paths** | 12 combat classes – pick two; choose passives, an M2 skill and talents | [`paths/`](paths/README.md) |
| **Professions** | 12 professions levelled by doing the work: better yields, rare finds, 120 perks | [`professions/`](professions/README.md) |
| **MagicEnchants** | 31 real enchantments (table, loot, librarians, anvils) | [`magic-enchants/`](magic-enchants/README.md) |
| **MightyMobs** | Mobs with abilities, health bars, special spawns, Blood Moon nights | [`mighty-mobs/`](mighty-mobs/README.md) |
| **Hud** | Sidebar, damage numbers, hit combo, `/stats`, resource pack hosting | [`hud/`](hud/README.md) |
| **ItemCleaner** | Clears dropped items on a timer, with warnings | [`item-cleaner/`](item-cleaner/README.md) |

## Requirements

- **Paper 26.3** (built against `26.3.build.140-beta`), **Java 25**.
- No database and no other plugins needed. Player data lives in each player's file
  (PersistentDataContainer), so it moves with the world.

## Installing

1. Drop the jars you want into `plugins/` and start the server once. Each plugin writes its
   `config.yml` and `lang/en.yml` + `lang/ro.yml`.
2. Pick the language: `language: en` (default) or `ro` in each `config.yml`, or the in-game
   `... lang <code>` command. To translate, copy `lang/en.yml` to `lang/<code>.yml` and edit it.
3. Optional: the custom menu icons come from a resource pack (see [Hud](hud/README.md)).

Updating: replace the jar and restart. New settings and new language keys are **added** to your
existing files automatically; values you changed are never touched.

## Commands every plugin shares

Admin commands use the same shape everywhere (each plugin's base command is listed in its README):

| Command | What it does |
|---|---|
| `<base> reload` | Re-read config.yml and the language file |
| `<base> config get <key>` | Show a setting (tab-completes every key) |
| `<base> config set <key> <value>` | Change a setting – type-checked, saved, applied at once |
| `<base> config reset <key>` | Back to the default |
| `<base> config list [section]` | List settings |
| `<base> toggle <feature> [on\|off]` | Flip any on/off setting (`toggle combos`, `toggle paths.lancer`) |
| `<base> lang <code>` | Switch the language |

Bases: `/skills admin`, `/paths admin`, `/professions admin`, `/enchants admin`, `/mm`, `/hud`, `/clearitems admin`.

## Shared conventions

- **Worlds:** every plugin has `worlds: { mode: blacklist|whitelist, list: [...] }` to keep it
  out of (or only in) certain worlds – e.g. a lobby or minigame world.
- **Permissions:** players get everything by default. Each feature also has its own node (all
  `default: true`), so a permissions plugin such as LuckPerms can take single features away –
  e.g. deny `paths.path.pyromancer` or `weaponskills.weapon.mace`. Wildcards: `paths.path.*`,
  `professions.profession.*`, `weaponskills.weapon.*`, `weaponskills.skill.*`.
- **Messages:** all player-facing text is in `lang/*.yml` in [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
  format (`<gold>`, `<bold>`, `<#ff8800>`...). Placeholders like `<player>` are filled in by the plugin.
  Long descriptions (skills, perks, abilities) are plain text because they are word-wrapped in menus.
- **Startup log:** each plugin prints which companion plugins it found.

## How they work together

| Combination | Effect |
|---|---|
| WeaponSkills + Paths | Skill hits count as path hits (on-hit passives trigger, path XP) |
| WeaponSkills + MagicEnchants | Skill Surge shortens skill cooldowns; Soul Fragments give skill XP; enchantments trigger on skill hits |
| WeaponSkills + Professions | Gathering skills (Tunnel Bore, Clear Cut, Harvest Wave...) give profession XP |
| Hud + any | Sidebar sections for skills, paths, professions and enchantments; custom menu icons |
| MightyMobs + MagicEnchants | Rare book drops can hold the custom enchantments |
| ItemCleaner + MightyMobs / Professions | Glowing rare drops are never cleared |
| Professions + Timberella / VeinMiner | Every extra log or ore they break counts |

The plugins only talk to each other through shared data keys, never hard dependencies.

## Building from source

Each folder has a `build.sh` that builds inside Docker (`gradle:9.8.0-jdk25-noble`), so no JDK is
needed: `cd weapon-skills && ./build.sh` → `build/libs/WeaponSkills-<version>.jar`.

The shared classes (language files, config updater, admin commands, worlds, permissions) live in
`common/src/` and are copied into every plugin as `<plugin package>.common` by `common/sync.sh`.
Edit them there, run `sh common/sync.sh`, then rebuild the plugins.

## Documentation

- [`docs/DEVELOPER_DOCS.md`](docs/DEVELOPER_DOCS.md) – how everything is built and wired together, data keys, build/deploy, gotchas
- [`docs/ROADMAP.md`](docs/ROADMAP.md) – feature roadmap
