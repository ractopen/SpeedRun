package com.ryme.speedrun.client;

import com.ryme.speedrun.SpeedrunTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class SpeedrunClient implements ClientModInitializer {
    public static boolean autoResetPending = false;
    public static KeyMapping fastResetKeyMapping;

    @Override
    public void onInitializeClient() {
        // Register client keymapping for Fast Reset (unbound by default)
        fastResetKeyMapping = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.speedrun.fast_reset",
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.MISC
        ));

        // Listen for Fast Reset keybind in-game
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (fastResetKeyMapping.consumeClick()) {
                if (client.level != null && SpeedrunTracker.config != null && SpeedrunTracker.config.enabled) {
                    executeReset(client);
                }
            }
        });

        // Fabric API: client disconnect event (mark userLeft = true so RTA says null)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SpeedrunTracker.setUserLeft(true);
        });

        // Fabric API: client connection join event (reset userLeft when joining a world)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            SpeedrunTracker.setUserLeft(false);
        });

        // Fabric API: 60+ FPS client-side HUD element attached directly above hotbar
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.HOTBAR,
            Identifier.fromNamespaceAndPath("speedrun-tracker", "live_hud"),
            (extractor, deltaTracker) -> {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level != null && mc.gui != null && mc.gui.screen() == null && mc.gui.hud != null && !mc.gui.hud.isHidden()
                    && SpeedrunTracker.config != null && SpeedrunTracker.config.enabled && SpeedrunTracker.config.display.actionBar) {
                    int screenWidth = mc.getWindow().getGuiScaledWidth();
                    int screenHeight = mc.getWindow().getGuiScaledHeight();
                    int x = screenWidth / 2;
                    int y = screenHeight - 56;

                    extractor.centeredText(mc.font, "§6Attempt #" + SpeedrunTracker.getAttempts() + " §8| " + SpeedrunTracker.formatHudTimer(), x, y, 0xFFFFFFFF);
                }
            }
        );

        // Add bottom timer overlay and pause screen Fast Reset button
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Step 1: Render crisp, unblurred bottom timer on all in-game menus (Pause, Options, Config, etc.)
            if (client.level != null && SpeedrunTracker.config != null && SpeedrunTracker.config.enabled && SpeedrunTracker.config.display.actionBar) {
                Component timerComp = Component.literal(SpeedrunTracker.formatMenuTimer());
                int textWidth = Screens.getFont(screen).width(timerComp);
                int timerX = (scaledWidth - textWidth) / 2;
                int timerY = scaledHeight - 40;

                StringWidget timerWidget = new StringWidget(timerX, timerY, textWidth, 12, timerComp, Screens.getFont(screen));
                Screens.getWidgets(screen).add(timerWidget);

                ScreenEvents.afterTick(screen).register(s -> {
                    Component updated = Component.literal(SpeedrunTracker.formatMenuTimer());
                    int w = Screens.getFont(screen).width(updated);
                    timerWidget.setWidth(w);
                    timerWidget.setX((scaledWidth - w) / 2);
                    timerWidget.setMessage(updated);
                });
            }

            // Step 2: Inject Fast Reset button into PauseScreen safely below Disconnect (Row 5)
            if (screen instanceof PauseScreen pauseScreen && client.hasSingleplayerServer()) {
                if (SpeedrunTracker.config != null && SpeedrunTracker.config.enabled && SpeedrunTracker.config.display.showResetButtonInMenu) {
                    // Use Fabric Screen API (Screens.getWidgets) to locate the disconnect button reliably without reflection
                    Button disconnectBtn = null;
                    String returnToMenu = Component.translatable("menu.returnToMenu").getString();
                    String disconnect = Component.translatable("menu.disconnect").getString();
                    for (var widget : Screens.getWidgets(pauseScreen)) {
                        if (widget instanceof Button btn) {
                            String label = btn.getMessage() != null ? btn.getMessage().getString() : "";
                            if (returnToMenu.equals(label) || disconnect.equals(label) || label.contains("Quit") || label.contains("Disconnect")) {
                                disconnectBtn = btn;
                                break;
                            }
                        }
                    }

                    int buttonWidth;
                    int buttonHeight = 20;
                    int btnX;
                    int btnY;

                    if (disconnectBtn != null) {
                        buttonWidth = disconnectBtn.getWidth();
                        btnX = disconnectBtn.getX();
                        btnY = disconnectBtn.getY() + 24; // Row 5: below Disconnect, completely free of World Options overlap
                    } else {
                        buttonWidth = 204;
                        btnX = (scaledWidth - buttonWidth) / 2;
                        btnY = scaledHeight / 4 + 144;
                    }

                    Button fastResetButton = Button.builder(
                        Component.literal("§eFast Reset"),
                        btn -> {
                            if (SpeedrunTracker.config != null && !SpeedrunTracker.config.template.firstLaunchPromptShown) {
                                net.minecraft.client.gui.screens.ConfirmScreen prompt = new net.minecraft.client.gui.screens.ConfirmScreen(
                                    (boolean useDefault) -> {
                                        SpeedrunTracker.config.template.firstLaunchPromptShown = true;
                                        SpeedrunTracker.config.save();
                                        if (useDefault) {
                                            executeReset(client);
                                        } else {
                                            client.setScreenAndShow(new com.ryme.speedrun.integration.SpeedrunModMenu.ConfigScreen(
                                                screen,
                                                () -> executeReset(client)
                                            ));
                                        }
                                    },
                                    Component.literal("World Template"),
                                    Component.literal("Use standard leaderboard rules, or customize settings?"),
                                    Component.literal("Default (Leaderboard)"),
                                    Component.literal("Customize")
                                );
                                client.setScreenAndShow(prompt);
                                return;
                            }

                            executeReset(client);
                        }
                    )
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("Abandon run and generate a new world. (Keybind can be configured in Controls > Key Binds; unbound by default).")
                    ))
                    .bounds(btnX, btnY, buttonWidth, buttonHeight)
                    .build();

                    Screens.getWidgets(screen).add(fastResetButton);
                }
            }

            // Step 2: Once disconnected to TitleScreen, trigger fresh world creation
            if (screen instanceof TitleScreen && autoResetPending) {
                CreateWorldScreen.openFresh(client, null);
            }

            // Step 3: When CreateWorldScreen initializes, inject template and auto-click create
            if (screen instanceof CreateWorldScreen createWorldScreen && autoResetPending) {
                autoResetPending = false;
                applyTemplateAndCreateWorld(createWorldScreen);
            }
        });

        // Register client commands (/speedrun menu and /speedrun config open settings GUI)
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("speedrun-menu")
                .executes(ctx -> {
                    openConfigScreen();
                    return 1;
                })
            );
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("speedrun")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("menu")
                    .executes(ctx -> {
                        openConfigScreen();
                        return 1;
                    })
                )
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("config")
                    .executes(ctx -> {
                        openConfigScreen();
                        return 1;
                    })
                )
            );
        });

        System.out.println("[Speedrun] Client initialized (Pause menu Fast Reset, Auto-Reset, and Settings GUI command registered)");
    }

    private static void executeReset(net.minecraft.client.Minecraft client) {
        if (FabricLoader.getInstance().isModLoaded("worldreset")) {
            // If WorldReset mod is installed, use its sub-second in-game reset
            if (client.player != null) {
                client.player.connection.sendCommand("worldreset reset");
            }
        } else {
            // Native auto-create world pipeline
            autoResetPending = true;
            if (client.level != null) {
                client.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
            } else {
                CreateWorldScreen.openFresh(client, null);
            }
        }
    }

    private static void openConfigScreen() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                mc.setScreenAndShow(new com.ryme.speedrun.integration.SpeedrunModMenu.ConfigScreen(null));
            });
        }
    }

    private static void applyTemplateAndCreateWorld(CreateWorldScreen createWorldScreen) {
        WorldCreationUiState uiState = createWorldScreen.getUiState();
        if (uiState != null && SpeedrunTracker.config != null && SpeedrunTracker.config.template != null) {
            var template = SpeedrunTracker.config.template;

            // Name world with attempt number
            try {
                int nextAttempt = SpeedrunTracker.getAttempts() + 1;
                uiState.setName("Speedrun Attempt #" + nextAttempt);
            } catch (Throwable ignored) {}

            try {
                Difficulty diff = Difficulty.byName(template.difficulty.toLowerCase());
                if (diff != null) {
                    uiState.setDifficulty(diff);
                }
            } catch (Throwable ignored) {}

            try {
                if ("HARDCORE".equalsIgnoreCase(template.gameMode)) {
                    uiState.setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE);
                } else {
                    uiState.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
                }
            } catch (Throwable ignored) {}

            uiState.setAllowCommands(template.allowCheats);
            uiState.setGenerateStructures(template.generateStructures);
            uiState.setBonusChest(template.bonusChest);
        }

        // Attempt world creation via reflection, key event, or button fallback
        try {
            java.lang.reflect.Method onCreateMethod = CreateWorldScreen.class.getDeclaredMethod("onCreate");
            onCreateMethod.setAccessible(true);
            onCreateMethod.invoke(createWorldScreen);
            return;
        } catch (Throwable ignored) {}

        try {
            net.minecraft.client.input.KeyEvent enterEvent = new net.minecraft.client.input.KeyEvent(40, 40, 0);
            if (createWorldScreen.keyPressed(enterEvent)) {
                return;
            }
        } catch (Throwable ignored) {}

        String createLabel = Component.translatable("selectWorld.create").getString();
        for (var widget : Screens.getWidgets(createWorldScreen)) {
            if (widget instanceof Button btn) {
                Component msg = btn.getMessage();
                if (msg != null && (createLabel.equals(msg.getString()) || msg.getString().contains("Create"))) {
                    try {
                        java.lang.reflect.Field onPressField = Button.class.getDeclaredField("onPress");
                        onPressField.setAccessible(true);
                        Button.OnPress handler = (Button.OnPress) onPressField.get(btn);
                        if (handler != null) {
                            handler.onPress(btn);
                        }
                    } catch (Throwable ignored) {}
                    break;
                }
            }
        }
    }
}
