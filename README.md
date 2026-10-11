<div align="center">

<img src="https://raw.githubusercontent.com/ractopen/SpeedRun/main/src/main/resources/assets/speedrun-tracker/logo.png" alt="SpeedRun?" width="400"/>

<p>Minimalist attempt counter, stopwatch, and fast reset utility for Fabric Minecraft.</p>

[![Release](https://img.shields.io/badge/Release-0.1.2--beta-2563EB?style=for-the-badge)](https://github.com/ractopen/SpeedRun/releases)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0C0?style=for-the-badge&logo=fabric&logoColor=black)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1_%E2%80%A2_26.2_%E2%80%A2_26.3-238636?style=for-the-badge&logo=minecraft&logoColor=white)](#supported-versions)
[![License](https://img.shields.io/badge/License-MIT-blueviolet?style=for-the-badge)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

</div>


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

## Frequently Asked Questions

<details>
<summary><b>Does this mod work on dedicated servers or LAN?</b></summary>
<br>

SpeedRun? operates seamlessly across singleplayer, LAN, and dedicated Fabric servers:
* **Server-side installation**: The server manages attempt state, timers, world resets, dragon defeat triggers, and can broadcast timer/attempt updates to connected players via the action bar or chat.
* **Client-side installation**: Provides full access to the in-game Mod Menu configuration GUI, keybindings, and client-specific HUD options.
</details>

<details>
<summary><b>What happens if a player joins without the mod installed?</b></summary>
<br>

SpeedRun? is completely client-optional for multiplayer servers:
* **Vanilla Client Compatibility**: Players running unmodded vanilla Minecraft can join the server without any errors or disconnects.
* **HUD & Placeholders**: Vanilla clients receive real-time attempt and stopwatch information via native Minecraft action bar packets, chat messages, and TAB list headers/footers.
* **Controls & Reset Triggers**: Vanilla players can interact using standard in-game commands (`/attempts`, `/timer`), while graphical Mod Menu configuration screens remain exclusive to clients that have the mod loaded.
</details>

---

## Version

Current release: `v0.1.2-beta`

---

## Supported Versions

| Minecraft Version | Fabric Loader | Java Runtime |
| :--- | :--- | :--- |
| **26.3** | `>=0.15.0` | 25 |
| **26.2** | `>=0.15.0` | 25 |
| **26.1** | `>=0.15.0` | 25 |

---

## License

This project is licensed under the [MIT License](LICENSE).
