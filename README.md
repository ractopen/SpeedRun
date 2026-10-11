<div align="center">

<img src="src/main/resources/assets/speedrun-tracker/logo.png" alt="SpeedRun?" width="400"/>

<p>Minimalist attempt counter, stopwatch, and fast reset utility for Fabric Minecraft.</p>

[![Release](https://img.shields.io/badge/Release-0.1.0--beta-2563EB?style=for-the-badge)](https://github.com/ractopen/SpeedRun/releases)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0C0?style=for-the-badge&logo=fabric&logoColor=black)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1_%E2%80%A2_26.2_%E2%80%A2_26.3-238636?style=for-the-badge&logo=minecraft&logoColor=white)](#supported-versions)
[![License](https://img.shields.io/badge/License-MIT-blueviolet?style=for-the-badge)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

</div>

---

## Supported Versions

| Minecraft Version | Fabric Loader | Java Runtime | Status |
| :--- | :--- | :--- | :--- |
| **26.3** | `>=0.15.0` | 25 | Verified (Active Target) |
| **26.2** | `>=0.15.0` | 25 | Supported |
| **26.1** | `>=0.15.0` | 25 | Supported |

---

## Features

* Fast Reset button in the singleplayer pause menu.
* Configurable world templates (difficulty, gamemode, cheats, structures).
* "Reset to Speedrun Rules" button to restore standard leaderboard defaults.
* Tracks run attempts across resets and newly generated worlds.
* In-Game Time (IGT) and Real Time Attack (RTA) stopwatch.
* Automatic Ender Dragon defeat detection and timer freeze.
* Action bar HUD displaying current attempt and timer.
* Zero-Overhead mode to turn off tick listeners for zero memory/CPU impact.
* Vanilla UI integration that works seamlessly with GUI resource packs, dark themes, and font mods.
* TAB list placeholders (`%attempt%`, `%speedrun_timer%`, `%seed%`).
* Mod Menu configuration GUI.
* In-game commands (`/attempts`, `/timer`).
* Universal Java API for other mods and reset engines.

---

## Modes

* **Display Mode (Default)**: Shows an action bar HUD with your attempt number and active timer (updates every 10 ticks).
* **Zero-Overhead Mode (`zeroOverhead: true`)**: Bypasses tick events entirely. Zero memory allocations, zero packets. Attempts and seeds are tracked strictly on reset/dragon events and remain queryable via commands, TAB placeholders, and the API.

---

## Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/attempts` | All | Show current attempt, seed, and timer |
| `/attempts set <count>` | OP (Level 2) | Set the attempt count |
| `/attempts reset` | OP (Level 2) | Reset attempts to #1 |
| `/timer` | All | Show active stopwatch time and status |
| `/timer reset` | OP (Level 2) | Reset stopwatch to 00:00 |
| `/timer mode <IGT\|RTA>` | OP (Level 2) | Switch timer between In-Game Time and Real Time |

---

## Configuration

File: `config/speedrun.json`

```json
{
  "display": {
    "actionBar": true,
    "tabList": true,
    "chatBroadcast": true,
    "showResetButtonInMenu": true,
    "zeroOverhead": false
  },
  "timer": {
    "mode": "IGT",
    "updateRateTicks": 10
  },
  "behavior": {
    "autoIncrementOnNewWorld": true,
    "stopOnDragonKill": true,
    "victorySound": true
  },
  "template": {
    "difficulty": "EASY",
    "gameMode": "SURVIVAL",
    "allowCheats": false,
    "generateStructures": true,
    "bonusChest": false
  }
}
```

---

## Integrations

* [WorldReset](https://modrinth.com/mod/worldreset) &mdash; Instant in-memory world resets without leaving to the title screen.
* [TAB](https://modrinth.com/plugin/tab) &mdash; Placeholders for player list header/footer (`%attempt%`, `%speedrun_timer%`, `%speedrun_rta%`, `%speedrun_igt%`, `%seed%`).
* [Mod Menu](https://modrinth.com/mod/modmenu) &mdash; In-game options screen.

---

## Developer API

Any reset mod or tool can hook into `SpeedRun?` via `SpeedrunAPI`:

```java
import com.ryme.speedrun.api.SpeedrunAPI;

// Listen for run resets
SpeedrunAPI.onReset(event -> {
    System.out.println("Attempt #" + event.attempt() + " on seed " + event.seed());
});

// Listen for dragon defeat
SpeedrunAPI.onComplete(event -> {
    System.out.println("Victory: " + event.formattedTime() + " (Attempt #" + event.attempt() + ")");
});

// Trigger a reset from external code
SpeedrunAPI.triggerReset(seed, server);

// Query state
int attempts = SpeedrunAPI.getAttempts();
boolean running = SpeedrunAPI.isTimerRunning();
String time = SpeedrunAPI.getFormattedTime();
```

---

## Version

Current release: `v0.1.0-beta`

---

## License

This project is licensed under the [MIT License](LICENSE).
