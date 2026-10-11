package com.ryme.speedrun.integration;

import com.ryme.speedrun.SpeedrunTracker;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SpeedrunModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }

    public static class ConfigScreen extends Screen {
        private final Screen parent;
        private final Runnable onDoneCallback;

        public ConfigScreen(Screen parent) {
            this(parent, null);
        }

        public ConfigScreen(Screen parent, Runnable onDoneCallback) {
            super(Component.literal("Speedrun? Settings"));
            this.parent = parent;
            this.onDoneCallback = onDoneCallback;
        }

        @Override
        protected void init() {
            int colWidth = 150;
            int colGap = 16;
            int totalColsWidth = (colWidth * 2) + colGap;
            int leftX = (this.width - totalColsWidth) / 2;
            int rightX = leftX + colWidth + colGap;

            int topY = 10;
            int masterWidth = 160;

            // Master Mod Enable / Disable Toggle
            addRenderableWidget(Button.builder(
                Component.literal("Mod Status: " + (SpeedrunTracker.config.enabled ? "§aENABLED" : "§cDISABLED")),
                btn -> {
                    SpeedrunTracker.config.enabled = !SpeedrunTracker.config.enabled;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Mod Status: " + (SpeedrunTracker.config.enabled ? "§aENABLED" : "§cDISABLED")));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Master toggle: Enable or disable all SpeedRun? tracking, HUD, and fast reset features.")))
            .bounds((this.width - masterWidth) / 2, topY, masterWidth, 20).build());

            int startY = topY + 24;
            int rowSpacing = 22;

            // ==========================================
            // LEFT COLUMN: Display & Stopwatch
            // ==========================================
            int leftY = startY;
            addRenderableWidget(new StringWidget(leftX, leftY, colWidth, 12, Component.literal("§6§lDisplay & Stopwatch"), this.font));
            leftY += 15;

            // Timer mode (BOTH / IGT / RTA)
            addRenderableWidget(Button.builder(
                Component.literal("Mode: §e" + SpeedrunTracker.config.timer.mode),
                btn -> {
                    if ("BOTH".equalsIgnoreCase(SpeedrunTracker.config.timer.mode)) {
                        SpeedrunTracker.config.timer.mode = "IGT";
                    } else if ("IGT".equalsIgnoreCase(SpeedrunTracker.config.timer.mode)) {
                        SpeedrunTracker.config.timer.mode = "RTA";
                    } else {
                        SpeedrunTracker.config.timer.mode = "BOTH";
                    }
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Mode: §e" + SpeedrunTracker.config.timer.mode));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Toggle displayed stopwatch modes: In-Game Time (IGT), Real Time Attack (RTA), or Both.")))
            .bounds(leftX, leftY, colWidth, 20).build());
            leftY += rowSpacing;

            // Milliseconds toggle (.xxx)
            addRenderableWidget(Button.builder(
                Component.literal("Millis (.xxx): " + (SpeedrunTracker.config.timer.showMilliseconds ? "§aON" : "§7OFF")),
                btn -> {
                    SpeedrunTracker.config.timer.showMilliseconds = !SpeedrunTracker.config.timer.showMilliseconds;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Millis (.xxx): " + (SpeedrunTracker.config.timer.showMilliseconds ? "§aON" : "§7OFF")));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Display sub-second millisecond precision (.xxx) on the live timer.")))
            .bounds(leftX, leftY, colWidth, 20).build());
            leftY += rowSpacing;

            // Action Bar HUD
            addRenderableWidget(Button.builder(
                Component.literal("Action Bar: " + (SpeedrunTracker.config.display.actionBar ? "§aON" : "§cOFF")),
                btn -> {
                    SpeedrunTracker.config.display.actionBar = !SpeedrunTracker.config.display.actionBar;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Action Bar: " + (SpeedrunTracker.config.display.actionBar ? "§aON" : "§cOFF")));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Display the live speedrun timer HUD directly above your hotbar.")))
            .bounds(leftX, leftY, colWidth, 20).build());
            leftY += rowSpacing;

            // TAB list display
            addRenderableWidget(Button.builder(
                Component.literal("TAB List: " + (SpeedrunTracker.config.display.tabList ? "§aON" : "§cOFF")),
                btn -> {
                    SpeedrunTracker.config.display.tabList = !SpeedrunTracker.config.display.tabList;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("TAB List: " + (SpeedrunTracker.config.display.tabList ? "§aON" : "§cOFF")));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Show attempts and live timer in player list. (Note: Minecraft vanilla suppresses TAB in singleplayer).")))
            .bounds(leftX, leftY, colWidth, 20).build());
            leftY += rowSpacing;

            // Chat Broadcast
            addRenderableWidget(Button.builder(
                Component.literal("Chat Broadcast: " + (SpeedrunTracker.config.display.chatBroadcast ? "§aON" : "§7OFF")),
                btn -> {
                    SpeedrunTracker.config.display.chatBroadcast = !SpeedrunTracker.config.display.chatBroadcast;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Chat Broadcast: " + (SpeedrunTracker.config.display.chatBroadcast ? "§aON" : "§7OFF")));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Broadcast completion message and final run time in chat when the dragon dies.")))
            .bounds(leftX, leftY, colWidth, 20).build());
            leftY += rowSpacing;

            // Zero-Overhead Mode
            addRenderableWidget(Button.builder(
                Component.literal("Zero-Overhead: " + (SpeedrunTracker.config.display.zeroOverhead ? "§aON" : "§7OFF")),
                btn -> {
                    SpeedrunTracker.config.display.zeroOverhead = !SpeedrunTracker.config.display.zeroOverhead;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Zero-Overhead: " + (SpeedrunTracker.config.display.zeroOverhead ? "§aON" : "§7OFF")));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Suspend background tick processing when no players are logged in to eliminate server overhead.")))
            .bounds(leftX, leftY, colWidth, 20).build());
            leftY += rowSpacing;

            // ==========================================
            // RIGHT COLUMN: Template & World Rules
            // ==========================================
            int rightY = startY;
            addRenderableWidget(new StringWidget(rightX, rightY, colWidth, 12, Component.literal("§6§lTemplate & Rules"), this.font));
            rightY += 15;

            // Template Difficulty
            Button diffBtn = Button.builder(
                Component.literal("Difficulty: §e" + SpeedrunTracker.config.template.difficulty),
                btn -> {
                    if ("EASY".equalsIgnoreCase(SpeedrunTracker.config.template.difficulty)) {
                        SpeedrunTracker.config.template.difficulty = "NORMAL";
                    } else if ("NORMAL".equalsIgnoreCase(SpeedrunTracker.config.template.difficulty)) {
                        SpeedrunTracker.config.template.difficulty = "HARD";
                    } else {
                        SpeedrunTracker.config.template.difficulty = "EASY";
                    }
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Difficulty: §e" + SpeedrunTracker.config.template.difficulty));
                }
            )
            .tooltip(Tooltip.create(Component.literal("World difficulty for newly generated attempts. Speedrun.com standard rule: Easy.")))
            .bounds(rightX, rightY, colWidth, 20).build();
            addRenderableWidget(diffBtn);
            rightY += rowSpacing;

            // Template Game Mode (SURVIVAL / HARDCORE)
            Button modeBtn = Button.builder(
                Component.literal("Game Mode: §e" + SpeedrunTracker.config.template.gameMode),
                btn -> {
                    if ("SURVIVAL".equalsIgnoreCase(SpeedrunTracker.config.template.gameMode)) {
                        SpeedrunTracker.config.template.gameMode = "HARDCORE";
                    } else {
                        SpeedrunTracker.config.template.gameMode = "SURVIVAL";
                    }
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Game Mode: §e" + SpeedrunTracker.config.template.gameMode));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Game mode for generated worlds (Survival or Hardcore).")))
            .bounds(rightX, rightY, colWidth, 20).build();
            addRenderableWidget(modeBtn);
            rightY += rowSpacing;

            // Template Cheats
            Button cheatBtn = Button.builder(
                Component.literal("Cheats: " + (SpeedrunTracker.config.template.allowCheats ? "§cAllowed" : "§aDisallowed")),
                btn -> {
                    SpeedrunTracker.config.template.allowCheats = !SpeedrunTracker.config.template.allowCheats;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Cheats: " + (SpeedrunTracker.config.template.allowCheats ? "§cAllowed" : "§aDisallowed")));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Allow commands in generated speedrun worlds. Speedrun.com standard rule: Disallowed.")))
            .bounds(rightX, rightY, colWidth, 20).build();
            addRenderableWidget(cheatBtn);
            rightY += rowSpacing;

            // Wait For First Move (Non-toggleable: permanent standard)
            Button waitBtn = Button.builder(
                Component.literal("Wait To Move: §aLocked (ON)"),
                btn -> {}
            )
            .tooltip(Tooltip.create(Component.literal("IGT stays at 00:00.000 until first intentional movement. RTA runs continuously from world load.")))
            .bounds(rightX, rightY, colWidth, 20).build();
            waitBtn.active = false;
            addRenderableWidget(waitBtn);
            rightY += rowSpacing;

            // Menu Fast Reset Button
            addRenderableWidget(Button.builder(
                Component.literal("Pause Menu Reset: " + (SpeedrunTracker.config.display.showResetButtonInMenu ? "§aON" : "§7OFF")),
                btn -> {
                    SpeedrunTracker.config.display.showResetButtonInMenu = !SpeedrunTracker.config.display.showResetButtonInMenu;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Pause Menu Reset: " + (SpeedrunTracker.config.display.showResetButtonInMenu ? "§aON" : "§7OFF")));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Display the Fast Reset button inside the Pause (ESC) menu.")))
            .bounds(rightX, rightY, colWidth, 20).build());
            rightY += rowSpacing;

            // Reset Template to Defaults
            addRenderableWidget(Button.builder(
                Component.literal("§7Reset Rules to Default"),
                btn -> {
                    SpeedrunTracker.config.template.resetToSpeedrunDefaults();
                    SpeedrunTracker.config.save();
                    diffBtn.setMessage(Component.literal("Difficulty: §e" + SpeedrunTracker.config.template.difficulty));
                    modeBtn.setMessage(Component.literal("Game Mode: §e" + SpeedrunTracker.config.template.gameMode));
                    cheatBtn.setMessage(Component.literal("Cheats: " + (SpeedrunTracker.config.template.allowCheats ? "§cAllowed" : "§aDisallowed")));
                }
            )
            .tooltip(Tooltip.create(Component.literal("Reset world template rules (Difficulty: Easy, Survival, Cheats: Off) to standard leaderboard defaults.")))
            .bounds(rightX, rightY, colWidth, 20).build());
            rightY += rowSpacing;

            // ==========================================
            // BOTTOM FOOTER: Done Button
            // ==========================================
            int bottomY = Math.max(leftY, rightY) + 6;
            int doneWidth = 140;
            addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
                if (this.onDoneCallback != null) {
                    this.onDoneCallback.run();
                } else if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(this.parent);
                }
            })
            .tooltip(Tooltip.create(Component.literal("Save configuration and return.")))
            .bounds((this.width - doneWidth) / 2, bottomY, doneWidth, 20).build());
        }

        @Override
        public void onClose() {
            if (this.onDoneCallback != null) {
                this.onDoneCallback.run();
            } else if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(this.parent);
            }
        }
    }
}
