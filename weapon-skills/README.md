# WeaponSkills

Special attacks for weapons and tools. Hold a weapon and press **F** (swap-hands key); what the
player is doing picks the skill: standing, mid-air, sprinting, sneaking, looking up/down, or
sneaking + looking up (ultimate). Shields: block + F. Sneak + hotbar key 1-4 casts extra binds.

- 85 skills for sword, axe, shield, bow, crossbow, trident, mace, spear, pickaxe, woodcutting (axe
  aimed at a tree), hoe, shovel and fishing rod.
- Skill levels 1-50 from use; mastery (sum of levels) unlocks more skills and ultimates.
- Evolutions at level 10, augments (pick 1 of 2) at levels 30/40/50.
- Dodge roll (double-tap sneak), cross-weapon combos, faster attacks from sword/axe mastery.
- `/skills` menu to equip skills into slots, `/guide` book that lists every skill.

## Commands

| Command | Permission |
|---|---|
| `/skills` – menu | `weaponskills.use` |
| `/guide` – the guide book | `weaponskills.use` |
| `/skills admin setlevel <player> <skill\|weapon\|all> <level>` | `weaponskills.admin` |
| `/skills admin reset <player>` · `resetcooldowns <player>` · `info <player>` | `weaponskills.admin` |
| `/skills admin reload \| config \| toggle \| lang` (see the main README) | `weaponskills.admin` |

## Permissions

`weaponskills.use` (all players), `weaponskills.dodge`, `weaponskills.weapon.<weapon>` (e.g.
`weaponskills.weapon.mace`), `weaponskills.skill.<skill>` (e.g. `weaponskills.skill.ragnarok`),
wildcards `weaponskills.weapon.*` / `weaponskills.skill.*` – all granted by default.
`weaponskills.admin` – ops.

## Settings (config.yml)

| Key | What |
|---|---|
| `language`, `worlds` | Language and world filter |
| `weapons.<weapon>.enabled` | Turn a whole weapon's skills off (F then works normally) |
| `skills.<skill>.cooldown` / `.damage` / `.enabled` | Per-skill balance and on/off |
| `progression.*` | Max level, XP, damage/cooldown per level, evolve level |
| `dodge.*` | Dodge roll on/off, cooldown, invulnerability frames |
| `combos.enabled`, `augments.enabled`, `shift-keys.enabled` | Turn systems off |
| `attack-speed.*` | Attack speed bonus from mastery |
| `hit-passive-mobs`, `durability-cost`, `durability-chance` | Targeting and tool wear |

## Data keys (for other plugins)

`weaponskills:xp_<skill>`, `weaponskills:cooldowns` (`Name=readyAtMillis;...`),
`weaponskills:held` (`Sword|12`), `weaponskills:skills_used` – on players.
