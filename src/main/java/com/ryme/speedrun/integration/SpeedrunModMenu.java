package com.ryme.speedrun.integration;

import com.ryme.speedrun.SpeedrunTracker;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class SpeedrunModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }

    public static class ConfigScreen extends Screen {
        private final Screen parent;

        public ConfigScreen(Screen parent) {
            super(Component.literal("Speedrun? Settings"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int buttonWidth = 240;
            int x = (this.width - buttonWidth) / 2;
            int y = 14;

            // Zero-overhead mode
            addRenderableWidget(Button.builder(
                Component.literal("Zero-Overhead Mode: " + (SpeedrunTracker.config.display.zeroOverhead ? "§aEnabled" : "§7Disabled")),
                btn -> {
                    SpeedrunTracker.config.display.zeroOverhead = !SpeedrunTracker.config.display.zeroOverhead;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Zero-Overhead Mode: " + (SpeedrunTracker.config.display.zeroOverhead ? "§aEnabled" : "§7Disabled")));
                }
            ).bounds(x, y, buttonWidth, 20).build());
            y += 22;

            // Action bar HUD
            addRenderableWidget(Button.builder(
                Component.literal("Action Bar HUD: " + (SpeedrunTracker.config.display.actionBar ? "§aEnabled" : "§cDisabled")),
                btn -> {
                    SpeedrunTracker.config.display.actionBar = !SpeedrunTracker.config.display.actionBar;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Action Bar HUD: " + (SpeedrunTracker.config.display.actionBar ? "§aEnabled" : "§cDisabled")));
                }
            ).bounds(x, y, buttonWidth, 20).build());
            y += 22;

            // TAB list display
            addRenderableWidget(Button.builder(
                Component.literal("TAB List Display: " + (SpeedrunTracker.config.display.tabList ? "§aEnabled" : "§cDisabled")),
                btn -> {
                    SpeedrunTracker.config.display.tabList = !SpeedrunTracker.config.display.tabList;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("TAB List Display: " + (SpeedrunTracker.config.display.tabList ? "§aEnabled" : "§cDisabled")));
                }
            ).bounds(x, y, buttonWidth, 20).build());
            y += 22;

            // Menu fast reset button
            addRenderableWidget(Button.builder(
                Component.literal("Menu Fast Reset Button: " + (SpeedrunTracker.config.display.showResetButtonInMenu ? "§aEnabled" : "§cDisabled")),
                btn -> {
                    SpeedrunTracker.config.display.showResetButtonInMenu = !SpeedrunTracker.config.display.showResetButtonInMenu;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Menu Fast Reset Button: " + (SpeedrunTracker.config.display.showResetButtonInMenu ? "§aEnabled" : "§cDisabled")));
                }
            ).bounds(x, y, buttonWidth, 20).build());
            y += 22;

            // Timer mode (BOTH / IGT / RTA)
            addRenderableWidget(Button.builder(
                Component.literal("Timer Mode: §e" + SpeedrunTracker.config.timer.mode),
                btn -> {
                    if ("BOTH".equalsIgnoreCase(SpeedrunTracker.config.timer.mode)) {
                        SpeedrunTracker.config.timer.mode = "IGT";
                    } else if ("IGT".equalsIgnoreCase(SpeedrunTracker.config.timer.mode)) {
                        SpeedrunTracker.config.timer.mode = "RTA";
                    } else {
                        SpeedrunTracker.config.timer.mode = "BOTH";
                    }
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Timer Mode: §e" + SpeedrunTracker.config.timer.mode));
                }
            ).bounds(x, y, buttonWidth, 20).build());
            y += 22;

            // Wait for first move
            addRenderableWidget(Button.builder(
                Component.literal("Wait For First Move: " + (SpeedrunTracker.config.timer.waitForFirstInput ? "§aEnabled" : "§7Disabled")),
                btn -> {
                    SpeedrunTracker.config.timer.waitForFirstInput = !SpeedrunTracker.config.timer.waitForFirstInput;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Wait For First Move: " + (SpeedrunTracker.config.timer.waitForFirstInput ? "§aEnabled" : "§7Disabled")));
                }
            ).bounds(x, y, buttonWidth, 20).build());
            y += 22;

            // Milliseconds display (.xxx)
            addRenderableWidget(Button.builder(
                Component.literal("Milliseconds (.xxx): " + (SpeedrunTracker.config.timer.showMilliseconds ? "§aEnabled" : "§7Disabled")),
                btn -> {
                    SpeedrunTracker.config.timer.showMilliseconds = !SpeedrunTracker.config.timer.showMilliseconds;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Milliseconds (.xxx): " + (SpeedrunTracker.config.timer.showMilliseconds ? "§aEnabled" : "§7Disabled")));
                }
            ).bounds(x, y, buttonWidth, 20).build());
            y += 22;

            // Template difficulty
            Button diffBtn = Button.builder(
                Component.literal("Template Difficulty: §e" + SpeedrunTracker.config.template.difficulty),
                btn -> {
                    if ("EASY".equalsIgnoreCase(SpeedrunTracker.config.template.difficulty)) {
                        SpeedrunTracker.config.template.difficulty = "NORMAL";
                    } else if ("NORMAL".equalsIgnoreCase(SpeedrunTracker.config.template.difficulty)) {
                        SpeedrunTracker.config.template.difficulty = "HARD";
                    } else {
                        SpeedrunTracker.config.template.difficulty = "EASY";
                    }
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Template Difficulty: §e" + SpeedrunTracker.config.template.difficulty));
                }
            ).bounds(x, y, buttonWidth, 20).build();
            addRenderableWidget(diffBtn);
            y += 22;

            // Template cheats
            Button cheatBtn = Button.builder(
                Component.literal("Template Cheats: " + (SpeedrunTracker.config.template.allowCheats ? "§cAllowed" : "§aDisallowed")),
                btn -> {
                    SpeedrunTracker.config.template.allowCheats = !SpeedrunTracker.config.template.allowCheats;
                    SpeedrunTracker.config.save();
                    btn.setMessage(Component.literal("Template Cheats: " + (SpeedrunTracker.config.template.allowCheats ? "§cAllowed" : "§aDisallowed")));
                }
            ).bounds(x, y, buttonWidth, 20).build();
            addRenderableWidget(cheatBtn);
            y += 24;

            // Reset to default speedrun rules
            addRenderableWidget(Button.builder(
                Component.literal("Reset to Speedrun Rules"),
                btn -> {
                    SpeedrunTracker.config.template.resetToSpeedrunDefaults();
                    SpeedrunTracker.config.save();
                    diffBtn.setMessage(Component.literal("Template Difficulty: §e" + SpeedrunTracker.config.template.difficulty));
                    cheatBtn.setMessage(Component.literal("Template Cheats: " + (SpeedrunTracker.config.template.allowCheats ? "§cAllowed" : "§aDisallowed")));
                }
            ).bounds(x, y, buttonWidth, 20).build());
            y += 24;

            // Done button
            addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(this.parent);
                }
            }).bounds(x, y, buttonWidth, 20).build());
        }

        @Override
        public void onClose() {
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(this.parent);
            }
        }
    }
}
