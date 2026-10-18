# Minecraft Server – Feature Roadmap

Ideas for the next rounds of work on the server's custom plugins
(WeaponSkills, Paths, Professions, MagicEnchants, MightyMobs, Hud).
Tick items off as they get built.

> **RAM:** the VM has 6.6 GB and Paper now gets 4 GB (`MEMORY: 4G`). Only ~1.4 GB of the VM
> is left, so any further increase needs more VM RAM first.

---

## ✅ Done so far

- [x] **Hud plugin** – sidebar (`/sidebar`), damage numbers (`/damagenumbers`), `/stats`.
- [x] **Sidebar covers every system** – paths, combo, cooldowns, held weapon + mastery, professions being worked on, magic enchantments on your gear.
- [x] **Mining skills** – Tunnel Bore, Miner's Rush, Shatter (pickaxe), next to Tremor Sense.
- [x] **Woodcutting skills** – reworked in 1.9.1 to work alongside Timberella: Clear Cut (fell + replant every tree in 10×10), Grove Growth (grow all nearby saplings), Lumber Frenzy (extra logs + Haste).
- [x] **Resource pack** – 312 icons (skills, weapons, paths, professions, enchantments, path abilities, profession perks; weapon, path, ability and perk icons hand-drawn as pixel art), offered on join, used in all menus.
- [x] **`/enchants` menu** – every magic enchantment with what it goes on and what it does.
- [x] **Drop rework – professions:** finds now match the activity and the block (a tree drops its own sapling and wood, not glow berries); more logs per log as Woodcutting levels up.
- [x] **Dodge roll + combo system** with a finisher at x10.
- [x] **Romanian** for all player-facing text (names stay English).
- [x] **Level cap 50** for skills and paths; levels above 20 give no extra strength yet (room for future progression).
- [x] **All planned weapon skills** (WeaponSkills 1.9.0) – 23 new weapon skills + 4 ultimates (bow, crossbow, trident, mace), tool skills for hoe, shovel and fishing rod; 74 skills in total.
- [x] **Paths 2.0** – 7 new paths (11 in total); in every path the player picks the on-hit passive, the always-on passive and the M2 skill (2 choices each), plus a talent at levels 5/10/15/20.
- [x] **Profession perks** (Professions 1.3.0) – pick 1 of 2 perks at levels 10/20/30/40/50 for all 11 professions (110 perks, kept small).
- [x] **Blood Moon event** (MightyMobs 1.7.0) – random announced nights with 35% mighty mobs, ×3 special spawns, ×2 monster cap, ×2 XP, better rare drops, no sleeping, top hunter at dawn.
- [x] **Spear** (WeaponSkills 1.10.0) – 11 spear skills incl. the Gungnir ultimate; look-up/look-down keys now aim straight ahead for every aimed skill.
- [x] **Real numbers in `/skills`, skill augments (levels 30/40/50), cross-weapon combo marks** (WeaponSkills 1.11.0; the sneak + 1-4 binds from that release were removed in 1.14.0).
- [x] **Lancer path + path titles in chat** (Paths 2.1.0).
- [x] **Smithing profession + 10 perks** (Professions 1.4.0).
- [x] **Spear enchantments** – all weapon enchantments already worked on spears; plus Pinning, Skyfall, Vanguard (MagicEnchants 1.3.0).
- [x] **Player data cache** in WeaponSkills, Paths and Professions (fewer data reads per hit).
- [x] **Drop rework – mobs:** mighty mobs and special spawns drop things that fit them, plus rare drops (enchanted books, diamonds, signature items) at low odds.
- [x] **MagicEnchants 1.5.0** – 23 new enchantments (tridents, maces, shields, fishing rods, hoes, elytra, and magic ones tied to skills, paths, professions, combos, MightyMobs and the Blood Moon), curses removed, `/enchants` split into 9 categories, pixel-art icons for all 52 enchantments.
- [x] **WeaponSkills 1.14.0** – Wind Arrow replaces Grapple Arrow (bow, look-up), sneak + 1-4 binds removed, skill damage now grows slowly to level 50 (today's strongest hits need a max-level skill).
- [x] **Magic paths + staff enchantments** (live 2026-10-05): Paths 2.5.0 – Pyromancer/Frost Warden get a third choice per slot and a Spell slot (sneak + M2); MagicEnchants 1.7.0 – 21 more enchantments (90): staff enchantments with path synergy (Spellweaver, Battle Focus, Wildfire, Inferno Core, Deep Freeze, Brittle), Steed, Trample, Loyal Guard, Shearer's Bounty, Delver, Bountiful, Lure of the Deep, Bloodhound, Crescendo, Anchor, Purifier, Afterimage, Antidote, Crimson Ward, Resonance.
- [x] **MagicEnchants 1.6.0** (live 2026-10-05) – 17 more enchantments: Warding, Steadfast, Last Stand, Fleetfoot, Sure-footed, Clarity, Headhunter's Eye, Vigor, Piercing Bolt, Overcharge, Impaler's Reach, Rally, Shockwave, Packleader, Timbersong, Prospector, Bounty Hunter (69 total).

---

## ⭐ Top picks (suggested order)

- [x] **Dodge roll + combo system** – done (WeaponSkills 1.7.0, Hud 1.3.0).
- [ ] **Bosses + legendary weapons** – gives all the progression something to aim for.
- [x] **Blood Moon event** – done (MightyMobs 1.7.0, `/mm bloodmoon`).
- [x] **Talent choices for paths** – done (Paths 2.0.0), together with choosing every path ability.
- [ ] **Party system** – matters as the server grows toward 20 players.

---

## 1. New paths – all done (11 paths now)

| Path | Weapon | On hit | Always on | Right-click skill |
|---|---|---|---|---|
| **Tidecaller** | Trident | Hits apply "Soaked" (slowed, +damage from lightning) | Swim faster, breathe longer, stronger in rain | **Maelstrom** – whirlpool that pulls enemies in |
| **Juggernaut** | Mace | Hits crack the ground (small splash damage) | Knockback resistance; fall damage turns into extra smash damage | **Seismic Leap** – jump high and crash down |
| **Sentinel** | Shield | Blocking a hit charges "Retaliation" for your next attack | Less damage from arrows and explosions | **Bulwark** – 3 s full block that reflects melee damage |
| **Shadow** | Sword / dagger-style | Hits from behind deal big bonus damage | Quieter footsteps; brief invisibility after a kill | **Vanish** – go invisible, next hit crits |
| **Beastmaster** | Any | Your hits mark targets for your pets | Tamed wolves/cats are stronger and heal faster | **Call of the Wild** – summon 2 temporary wolves |
| **Pyromancer** | Blaze rod "staff" | Hits set targets ablaze | Fire resistance, less fire damage | **Fireball** – fireball that doesn't break blocks |
| **Frost Warden** | Snowball / ice staff | Hits chill; 3 stacks freezes | Frost-walker on water, immune to powder snow | **Blizzard** – area freezing storm |

- [x] Tidecaller
- [x] Juggernaut
- [x] Sentinel
- [x] Shadow
- [x] Beastmaster *(M2 with a bone; XP from your pets)*
- [x] Pyromancer *(magic class – Blaze Rod staff, left-click bolts)*
- [x] Frost Warden *(magic class – Breeze Rod staff, left-click bolts)*

*All done in Paths 2.0.0. Each path offers 2 choices per slot – the table above shows one of them; see `/paths` or DEVELOPER_DOCS §5.2.*

## 2. Deeper path progression

- [x] **Talent choices** – at levels 5/10/15/20 pick 1 of 2 talents; plus every slot has 2 abilities to choose from. *(Paths 2.0.0)*
- [ ] **Path synergies** – bonus for specific pairs:
  - Duelist + Ranger = **Skirmisher** (melee hits speed up your next arrow)
  - Warbringer + Arbalist = **Siege** (stunned targets take extra bolt damage)
- [x] **Rewards for levels 21–50** – skills: augments at 30/40/50 (WeaponSkills 1.11.0); paths: talents at 30/40/50 + veteran damage bonus (Paths 2.2.0).
- [ ] **Prestige** – after level 20, reset a path for a permanent small bonus and a title (*Duelist ★*).
- [x] **Path titles in chat** – e.g. `[Warbringer 15] Rares: hi`. *(Paths 2.1.0)*

## 3. More weapon skills (for the `/skills` loadouts) – all done in WeaponSkills 1.9.0

**Sword**
- [x] Crescent Wave – a sword beam that flies forward
- [x] Counter Stance – take reduced damage for 2 s, then release it back as one big hit
- [x] Thousand Cuts – very fast strikes on one target

**Axe**
- [x] Tomahawk Rain – axes fall in an area
- [x] Lumberjack's Fury – next 5 hits cleave
- [x] Bone Breaker – cripples a target's movement for 5 s

**Bow**
- [x] Rain of Fire – flaming Arrow Rain
- [x] Sniper Mode – zoom, then one heavy shot
- [x] Trap Arrow – plants a snare that roots
- [x] Split Shot – arrow splits mid-air

**Crossbow**
- [x] Gatling – 10 fast bolts
- [x] Chain Bolt – bolts tether enemies together
- [x] Smoke Bolt – blinding cloud

**Trident**
- [x] Poseidon's Call – summon a wave that carries you
- [x] Harpoon Throw – returning throw that drags enemies

**Mace**
- [x] Wind Burst – launch yourself straight up
- [x] Anvil Drop – drop an anvil on the enemy you're looking at

**Shield**
- [x] Shield Throw – bounces between enemies
- [x] Phalanx – nearby allies get your block

**Tool skills**
- [x] Hoe – Harvest Wave (harvest + replant a 5×5 area)
- [x] Shovel – Burrow (dig fast through soft blocks for 5 s)
- [x] Fishing rod – Grapple Hook (pull yourself along)
- [x] Axe (tool use) – Clear Cut, Grove Growth, Lumber Frenzy *(WeaponSkills 1.9.1, "Woodcutting"; replaced Timber Sense and Leaf Storm)*
- [x] Pickaxe – Tunnel Bore, Miner's Rush, Shatter *(WeaponSkills 1.6.0)*

**More ultimates**
- [x] Bow – Storm of Arrows
- [x] Crossbow – Artillery Barrage
- [x] Trident – Tsunami
- [x] Mace – Cataclysm

## 4. New passive systems

- [x] **Combo system** – hit counter (x1…x10) on a bar at the top of the screen; +3% damage per step; x10 finisher gives +50% damage and bonus XP.
- [x] **Dodge roll** – double-tap sneak to roll with brief invulnerability (3 s cooldown).
- [ ] **Stamina bar** *(optional)* – sprinting, rolling and skills cost stamina.
- [ ] **Rage meter** – fills as you deal/take damage; when full, the next skill is empowered.
- [ ] **Kill streaks** – buffs at 5/10/20 kills without dying; server announcement at 20.

## 5. New content & bigger systems

- [ ] **Bosses** – custom fights with phases and a boss bar (e.g. *Bone Colossus*, summoned at an altar with a Soul Fragment item); drop unique weapons.
- [ ] **Legendary weapons** – named items with a built-in unique skill (*Stormbreaker* axe calls lightning, *Whisper* bow with invisible arrows); boss drops or very rare finds.
- [ ] **Rifts / dungeons** – a temporary portal fills an area with mighty mobs and a mini-boss; closes after 10 minutes.
- [x] **Blood Moon event** – random night with far more mighty mobs and special spawns and double XP, announced in advance. *(MightyMobs 1.7.0)*
  - [ ] Follow-ups: double skill/path/profession XP during it (read world key `mightymobs:blood_moon_active`), a Blood Moon line in the Hud sidebar, a `/guide` page, a Blood Moon–only drop or mob.
- [ ] **Quests / bounties** – daily board ("Kill 20 zombies with a sword", "Mine 100 ores") → XP, Soul Fragments, money.
- [ ] **Economy** – EssentialsX money from profession finds and bounties; spend on enchanted books, respecs, cosmetics.
- [ ] **Achievements & titles** – *Slayer of 1000*, *Master Angler*, shown in chat and tab list.
- [ ] **Leaderboards** – `/top professions`, `/top skills`, weekly top-hunter rewards.
- [ ] **Party system** – `/party`: shared XP nearby, no friendly fire, party buffs from War Cry / Guardian Aura.

## 6. Professions

- [x] **Perk choices** – 1 of 2 perks at levels 10/20/30/40/50 for all 11 professions, with icons. *(Professions 1.3.0–1.3.1)*
- [ ] **Daily contracts** – three rotating profession tasks a day for big XP / Soul Fragments (doubles as the quest board).
- [ ] **Mastery titles + leaderboard** – titles at levels 25 and 50 (*Master Miner*), `/top professions`.
- [ ] **Collection log** – every possible find listed in `/professions`, greyed out until found; reward for completing a list.
- [ ] **Profession events** – e.g. a *Gold Rush* weekend (double Mining XP, more gold), *Bountiful Harvest*.
- [ ] **Profession-made items** – high-level crafts (longer potions, lures, better repairs) so players trade.

## 7. Quality of life

- [x] **Scoreboard sidebar** (toggleable) – paths, levels, combo, active cooldowns. *(Hud plugin, `/sidebar`; the combo is a display-only hit counter until the combo system is built)*
- [x] **Damage numbers** – floating numbers on hit (gold for crits). *(Hud plugin, `/damagenumbers`)*
- [x] **Resource pack** – custom icons for skills, paths, professions, enchantments and weapons. *(see DEVELOPER_DOCS §5.6)*
- [x] **All icons pixel art + path talent icons** (2026-10-05: 93 font-symbol icons redrawn, 15 talent icons) – live in the 443-icon pack (`v2026.10.05b`).
- [ ] **Menu art** – custom menu backgrounds, sidebar symbols, item textures (Soul Fragment, legendary weapons).
- [x] **`/stats`** – kills, deaths, highest combo, skills used. *(Hud plugin)*

---

## Open items from earlier

- [x] Raise VM RAM in Proxmox (now 6.6 GB).
- [x] World difficulty back to Normal (it was Hard from the Pumpkin world); mighty/special mobs keep Hard-level damage (×1.5).
- [x] Give Paper more memory: `MEMORY` 2500M → 4G in `~/pumpkin/docker-compose.yml`.
- [ ] Decide the mighty mob spawn chance (currently 8% – change with `/mm chance <percent>`).
- [ ] Update VeinMiner 2.12.2 → 2.12.3 (released 2026-09-30).
- [ ] Switch EssentialsX from dev build #1830 to a stable release once one supports 26.3.
- [ ] Rebalance: in-game testing of the skill evolutions, paths and enchantments.
- [x] Upload the new resource pack (312 icons) – live at `files.catbox.moe/qefwtx.zip` (356 icons).
- [ ] Play-test the new weapon skills, path abilities and talents; tune numbers.
- [ ] Play-test the profession perks (`/professions admin setlevel <you> all 50`, then pick perks in `/professions`).
- [ ] Play-test a Blood Moon night (`/mm bloodmoon start`) and tune `blood-moon.*` in the MightyMobs config.
- [ ] Play-test the new drop rates (profession finds, mighty/special mob drops) and tune them.
- [ ] Check in-game that the resource pack downloads and the icons look right.
- [ ] Set up automatic backups (Proxmox scheduled backups or `itzg/mc-backup`) – there are none yet.
- [x] Upload the 380-icon resource pack – live on GitHub Releases (`qqrares23/minecraft-resource-pack`, 2026-10-04); catbox was down.
- [ ] Play-test the 2026-10-04 release (52 enchantments, `/enchants` categories, Wind Arrow, damage curve) and tune `progression.damage-*`.
- [ ] Clean up Pumpkin leftovers (~700 MB) once the Paper world is trusted.
- [x] Publish the 397-icon resource pack (icons for the 17 new enchantments) – GitHub release `v2026.10.05` (2026-10-05); live on the server.
