package com.ryme.speedrun.client;

import com.ryme.speedrun.SpeedrunTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class SpeedrunClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Add "Fast Reset" button to Singleplayer Pause Menu
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof PauseScreen && SpeedrunTracker.config.display.showResetButtonInMenu) {
                int buttonWidth = 100;
                int buttonHeight = 20;
                int x = scaledWidth / 2 - 102 - 105; // Left side of pause menu options
                int y = scaledHeight / 4 + 72 + -16;

                Button fastResetButton = Button.builder(
                    Component.literal("Fast Reset"),
                    btn -> {
                        if (FabricLoader.getInstance().isModLoaded("worldreset")) {
                            // If WorldReset mod is installed, use its sub-second in-game reset
                            if (client.player != null) {
                                client.player.connection.sendCommand("worldreset reset");
                            }
                        } else {
                            // If WorldReset is not installed, fast-trigger singleplayer reset
                            if (client.player != null) {
                                client.player.connection.sendCommand("attempts");
                            }
                            // Disconnect and let user quickly start fresh world
                            if (client.level != null) {
                                screen.onClose();
                            }
                        }
                    }
                ).bounds(Math.max(10, x), y, buttonWidth, buttonHeight).build();

                Screens.getWidgets(screen).add(fastResetButton);
            }
        });

        System.out.println("[Speedrun] Client initialized (Pause menu Fast Reset hook registered)");
    }
}
