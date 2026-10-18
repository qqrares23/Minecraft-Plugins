# Hud

- **Sidebar** with the sections that matter right now: active paths, hit combo, skill and M2
  cooldowns, held weapon + mastery, professions that just gained XP, custom enchantments worn.
  Sections only show when their plugin is installed.
- **Damage numbers** that float up from what you hit (only you see yours).
- **Hit combo:** quick hits build up to x10 for bonus damage; the 10th is a finisher.
- **`/stats`** – kills, deaths, best combo, finishers, skills used.
- **Resource pack** offering: custom icons for the `/skills`, `/paths`, `/professions` and
  `/enchants` menus. Players without the pack see vanilla items, so the pack is optional.

## Commands

| Command | Permission |
|---|---|
| `/sidebar` – show/hide | `hud.sidebar` |
| `/damagenumbers` (`/dmgnumbers`) – show/hide | `hud.damagenumbers` |
| `/stats [player]` | `hud.stats` |
| `/hud pack [status\|url <link>\|on\|off\|reload]` | `hud.admin` |
| `/hud reload \| config \| toggle \| lang` | `hud.admin` |

`hud.use` grants the three player permissions (all default true).

## Resource pack setup

1. Upload the pack zip anywhere public (any file host with a direct download link).
2. `/hud pack url <link>` – the server downloads it, saves it as `plugins/Hud/pack.zip`, works out
   the checksum players' clients check, turns the pack on and offers it to everyone online.

Whenever the pack changes, upload the new file (new link) and run the command again.

## Settings (config.yml)

| Key | What |
|---|---|
| `language`, `worlds` | Language; worlds where the sidebar, damage numbers and combo work |
| `sidebar.enabled` / `.default-on` / `.title` (MiniMessage) / `.sections.*` | Sidebar |
| `damage-numbers.enabled` / `.default-on` / `.unit` (hearts or hp) | Damage numbers |
| `combo.*` | Window, max, damage per step, finisher, boss bar |
| `resource-pack.enabled` / `.url` / `.required` / `.prompt` | Pack offering |
