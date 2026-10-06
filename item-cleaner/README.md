# ItemCleaner

Clears dropped items from the ground every few minutes, with chat warnings first. Fresh drops,
death drops, glowing rare drops, renamed items and a list of valuables are kept.

## Commands (permission `itemcleaner.admin`, ops by default)

| Command | What |
|---|---|
| `/clearitems` | Time until the next clear |
| `/clearitems now` | Clear now (restarts the timer) |
| `/clearitems interval <1-60>` | Minutes between clears |
| `/clearitems admin reload \| config \| toggle \| lang` | Shared admin commands |

## Settings (config.yml)

| Key | What |
|---|---|
| `language`, `worlds` | Language; worlds that get cleared (and get the messages) |
| `interval-minutes`, `warning-seconds` | Timer and warnings |
| `min-age-seconds` | Items younger than this are kept |
| `protect-death-drops-minutes`, `death-drop-radius` | Death drops are kept |
| `keep-glowing`, `keep-named`, `keep-materials` | What else is kept |
| `messages.warnings` / `.result` / `.sound` | Turn chat warnings, the result message or the ping off |
