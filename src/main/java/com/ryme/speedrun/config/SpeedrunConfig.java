package com.ryme.speedrun.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class SpeedrunConfig {
    private static final File CONFIG_FILE = new File("config/speedrun.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public DisplayConfig display = new DisplayConfig();
    public TimerConfig timer = new TimerConfig();
    public BehaviorConfig behavior = new BehaviorConfig();
    public WorldTemplateConfig template = new WorldTemplateConfig();

    public static class DisplayConfig {
        public boolean actionBar = true;
        public boolean tabList = true;
        public boolean chatBroadcast = true;
        public boolean showResetButtonInMenu = true;
        public boolean zeroOverhead = false;
    }

    public static class TimerConfig {
        public String mode = "BOTH"; // "BOTH", "IGT", or "RTA"
        public int updateRateTicks = 2; // Update rate in ticks (2 ticks = 10 updates/sec for smooth milliseconds)
        public boolean waitForFirstInput = true; // Wait until player makes first input/movement before starting timer
        public boolean showMilliseconds = true; // Display milliseconds (.xxx)
    }

    public static class BehaviorConfig {
        public boolean autoIncrementOnNewWorld = true;
        public boolean stopOnDragonKill = true;
        public boolean victorySound = true;
    }

    public static class WorldTemplateConfig {
        public String difficulty = "EASY"; // Standard Speedrun Leaderboard Rule: EASY
        public String gameMode = "SURVIVAL";
        public boolean allowCheats = false;
        public boolean generateStructures = true;
        public boolean bonusChest = false;
        public boolean firstLaunchPromptShown = false;

        public void resetToSpeedrunDefaults() {
            this.difficulty = "EASY";
            this.gameMode = "SURVIVAL";
            this.allowCheats = false;
            this.generateStructures = true;
            this.bonusChest = false;
        }
    }

    public static SpeedrunConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                SpeedrunConfig config = GSON.fromJson(reader, SpeedrunConfig.class);
                if (config != null) return config;
            } catch (Exception e) {
                System.err.println("[Speedrun] Error reading config: " + e.getMessage());
            }
        }
        SpeedrunConfig def = new SpeedrunConfig();
        def.save();
        return def;
    }

    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("[Speedrun] Error saving config: " + e.getMessage());
        }
    }
}
