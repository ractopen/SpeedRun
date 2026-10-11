# SpeedRun?

A lightweight attempt counter, stopwatch, and fast reset mod for Fabric.

![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0C0?style=flat&logo=fabric&logoColor=black)
![Minecraft](https://img.shields.io/badge/Minecraft-26.1_--_26.3-238636?style=flat&logo=minecraft&logoColor=white)
![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat&logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blueviolet?style=flat)

---

## Features

- **Fast Reset**: Button in the singleplayer pause menu and configurable keybind to quickly roll a new world.
- **Stopwatch**: Live In-Game Time (IGT) and Real Time Attack (RTA) counter with millisecond precision.
- **Input Detection**: Timer starts on the tick you make your first move or action.
- **Split Tracking**: Automatically logs timestamps when entering the Nether and The End.
- **Per-World Stats**: Saves attempts, deaths, and splits directly into each world's save.
- **HUD & Menus**: Smooth 60+ FPS HUD above the hotbar and unblurred timer in pause/options menus.
- **World Templates**: Configurable difficulty, gamemode, and rules (with a one-click leaderboard preset).
- **TAB Placeholders**: Integrates with [TAB](https://modrinth.com/plugin/tab) (`%attempt%`, `%deaths%`, `%speedrun_timer%`, `%seed%`, etc.).
- **Mod Menu Support**: Clean in-game settings screen to adjust all features.

---

## Commands

All commands are unified under `/speedrun`:

| Command | Description |
| :--- | :--- |
| `/speedrun` | Overview and help |
| `/speedrun stats` | View current world attempts, deaths, time, and splits |
| `/speedrun stop` | Stop timer and lock final time for the current world |
| `/speedrun reset` | Reset current run |
| `/speedrun timer get` | Show timer breakdown |
| `/speedrun timer mode <IGT\|RTA\|BOTH>` | Change stopwatch display mode |
| `/speedrun attempts set <number>` | Set attempt counter |
| `/speedrun rules reset` | Reset world template back to official rules |
| `/speedrun menu` | Open settings GUI (or use `/speedrun-menu`) |

---

## Integrations

- **[WorldReset](https://modrinth.com/mod/worldreset)**: Enables sub-second in-memory world resets without returning to title.
- **[Mod Menu](https://modrinth.com/mod/modmenu)**: Config screen access via the mod list or pause menu.
- **[TAB](https://modrinth.com/plugin/tab)**: Exposes `%attempt%`, `%deaths%`, `%speedrun_split_nether%`, `%speedrun_split_end%`, `%speedrun_timer%`, `%speedrun_rta%`, `%speedrun_igt%`, and `%seed%`.

---

## Configuration

Config file located at `config/speedrun.json`. All options can also be adjusted in-game via **Mod Menu** or `/speedrun menu`.

---

## License

Released under the [MIT License](LICENSE).
