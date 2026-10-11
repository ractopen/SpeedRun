package com.ryme.speedrun;

import com.ryme.speedrun.api.SpeedrunAPI;
import com.ryme.speedrun.config.SpeedrunConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeedrunTracker implements ModInitializer {
    public static final String MOD_ID = "speedrun-tracker";
    private static final File SAVE_FILE = new File("config/speedrun_attempts.txt");

    public static SpeedrunConfig config;
    private static int attempts = 1;
    private static long currentSeed = 0L;
    private static long startTime = System.currentTimeMillis();
    private static long endTime = 0L;
    private static long igtTicks = 0L;
    private static final AtomicBoolean timerRunning = new AtomicBoolean(false);
    private static boolean hasPlayerMoved = false;
    private static double spawnX = 0, spawnY = 0, spawnZ = 0;
    private static float spawnYRot = 0, spawnXRot = 0;
    private static boolean spawnPosCaptured = false;
    private static int tickCounter = 0;
    private static boolean worldInitialized = false;

    @Override
    public void onInitialize() {
        config = SpeedrunConfig.load();
        loadAttempts();

        // WorldReset integration
        if (FabricLoader.getInstance().isModLoaded("worldreset")) {
            initWorldResetHook();
        }

        // Server startup and world detection
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ServerLevel overworld = server.overworld();
                if (overworld != null) {
                    currentSeed = overworld.getSeed();

                    if (!FabricLoader.getInstance().isModLoaded("worldreset") && config.behavior.autoIncrementOnNewWorld) {
                        long gameTime = overworld.getGameTime();
                        if (gameTime < 40 && !worldInitialized) {
                            attempts++;
                            saveAttempts();
                            resetRun(server, overworld.getSeed());
                        } else if (!worldInitialized) {
                            // Resume on rejoining existing world
                            igtTicks = Math.max(0, gameTime);
                            startTime = System.currentTimeMillis() - (igtTicks * 50L);
                            timerRunning.set(true);
                            hasPlayerMoved = true;
                        }
                    }
                }
            } catch (Throwable t) {
                System.err.println("[Speedrun] Seed error: " + t.getMessage());
            }

            worldInitialized = true;
            if (FabricLoader.getInstance().isModLoaded("tab")) {
                initTabIntegration();
            }
        });

        // Server stopped lifecycle (cleans up state so new worlds trigger reset properly)
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            worldInitialized = false;
            hasPlayerMoved = false;
            spawnPosCaptured = false;
            timerRunning.set(false);
        });

        // Ender Dragon defeat
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (config.behavior.stopOnDragonKill && entity instanceof EnderDragon && timerRunning.get()) {
                endTime = System.currentTimeMillis();
                timerRunning.set(false);
                String timeStr = formatTimer();
                MinecraftServer srv = entity.level().getServer();

                SpeedrunAPI.dispatchComplete(attempts, currentSeed, getElapsedMillis(), timeStr, srv);

                if (srv != null && config.display.chatBroadcast) {
                    String winMsg = "§6§l» §aSpeedrun finished: §e" + timeStr + " §8(Attempt #" + attempts + ")";
                    srv.getPlayerList().broadcastSystemMessage(Component.literal(winMsg), false);
                }
            }
        });

        // Action bar ticker
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!timerRunning.get()) return;

            // Movement detection if waitForFirstInput is enabled
            if (config.timer.waitForFirstInput && !hasPlayerMoved) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!spawnPosCaptured) {
                        spawnX = player.getX();
                        spawnY = player.getY();
                        spawnZ = player.getZ();
                        spawnYRot = player.getYRot();
                        spawnXRot = player.getXRot();
                        spawnPosCaptured = true;
                    } else {
                        double dx = player.getX() - spawnX;
                        double dy = player.getY() - spawnY;
                        double dz = player.getZ() - spawnZ;
                        double distSq = dx * dx + dy * dy + dz * dz;
                        float rotDiff = Math.abs(player.getYRot() - spawnYRot) + Math.abs(player.getXRot() - spawnXRot);
                        if (distSq > 0.001 || rotDiff > 1.0f || player.isCrouching() || player.isSprinting() || player.isSwinging()) {
                            hasPlayerMoved = true;
                            break;
                        }
                    }
                }
            }

            if (!config.timer.waitForFirstInput || hasPlayerMoved) {
                igtTicks++;
            }

            if (config.display.zeroOverhead || !config.display.actionBar) return;

            tickCounter++;
            if (tickCounter % config.timer.updateRateTicks != 0) return;

            String barText;
            if (config.timer.waitForFirstInput && !hasPlayerMoved) {
                barText = "§6Attempt #" + attempts + " §8│ §eWaiting to move... §8│ §f" + formatRTA() + " §8(RTA)";
            } else {
                barText = "§6Attempt #" + attempts + " §8│ " + formatActionBarTimer();
            }
            Component barComponent = Component.literal(barText);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(barComponent, true);
            }
        });

        registerCommands();
    }

    private void initWorldResetHook() {
        try {
            me.libreh.worldreset.api.PlayerResetEvents.AFTER_PLAYER_RESET.register((ServerPlayer player) -> {
                attempts++;
                saveAttempts();

                ServerLevel lvl = player.level();
                MinecraftServer server = lvl.getServer();
                currentSeed = lvl.getSeed();
                resetRun(server, currentSeed);

                if (config.display.chatBroadcast && server != null) {
                    String msg = "§6§l» §eStarting Attempt #" + attempts + " §8(Seed: §7" + currentSeed + "§8)";
                    server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
                }
            });
        } catch (Throwable t) {
            System.err.println("[Speedrun] WorldReset hook error: " + t.getMessage());
        }
    }

    private static void resetRun(MinecraftServer server, long seed) {
        startTime = System.currentTimeMillis();
        endTime = 0L;
        igtTicks = 0L;
        currentSeed = seed;
        timerRunning.set(true);
        hasPlayerMoved = !config.timer.waitForFirstInput;
        spawnPosCaptured = false;

        SpeedrunAPI.dispatchReset(attempts, seed, server);
    }

    public static void handleExternalReset(long seed, MinecraftServer server) {
        attempts++;
        saveAttempts();
        resetRun(server, seed);
        if (server != null && config != null && config.display.chatBroadcast) {
            String msg = "§6§l» §eStarting Attempt #" + attempts + " §8(Seed: §7" + seed + "§8)";
            server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
        }
    }

    public static void triggerReset(long seed, MinecraftServer server) {
        SpeedrunAPI.triggerReset(seed, server);
    }

    public static int getAttempts() {
        return attempts;
    }

    public static void setAttempts(int count) {
        attempts = count;
        saveAttempts();
    }

    public static int incrementAttempts() {
        attempts++;
        saveAttempts();
        return attempts;
    }

    public static boolean isTimerRunning() {
        return timerRunning.get();
    }

    public static long getCurrentSeed() {
        return currentSeed;
    }

    public static long getElapsedMillis() {
        if (!timerRunning.get() && endTime > 0) {
            return Math.max(0, endTime - startTime);
        }
        return Math.max(0, System.currentTimeMillis() - startTime);
    }

    private void initTabIntegration() {
        registerTabPlaceholders();
        try {
            me.neznamy.tab.api.TabAPI tab = me.neznamy.tab.api.TabAPI.getInstance();
            if (tab != null) {
                tab.getEventBus().register(me.neznamy.tab.api.event.plugin.TabLoadEvent.class, event -> registerTabPlaceholders());
            }
        } catch (Throwable ignored) {}
    }

    private static void registerTabPlaceholders() {
        try {
            me.neznamy.tab.api.TabAPI tab = me.neznamy.tab.api.TabAPI.getInstance();
            if (tab != null) {
                tab.getPlaceholderManager().registerServerPlaceholder("%attempt%", 1000, () -> String.valueOf(attempts));
                tab.getPlaceholderManager().registerServerPlaceholder("%attempts%", 1000, () -> String.valueOf(attempts));
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_timer%", 500, SpeedrunTracker::formatTimer);
                tab.getPlaceholderManager().registerServerPlaceholder("%timer%", 500, SpeedrunTracker::formatTimer);
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_rta%", 500, SpeedrunTracker::formatRTA);
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_igt%", 500, SpeedrunTracker::formatIGT);
                tab.getPlaceholderManager().registerServerPlaceholder("%seed%", 2000, () -> String.valueOf(currentSeed));
            }
        } catch (Throwable ignored) {}
    }

    public static String formatTimer() {
        String mode = config.timer.mode != null ? config.timer.mode.toUpperCase() : "BOTH";
        if ("IGT".equals(mode)) {
            return formatIGT();
        } else if ("RTA".equals(mode)) {
            return formatRTA();
        }
        return formatIGT() + " / " + formatRTA();
    }

    public static String formatActionBarTimer() {
        String mode = config.timer.mode != null ? config.timer.mode.toUpperCase() : "BOTH";
        if ("IGT".equals(mode)) {
            return "§b" + formatIGT() + " §8(IGT)";
        } else if ("RTA".equals(mode)) {
            return "§f" + formatRTA() + " §8(RTA)";
        }
        return "§b" + formatIGT() + " §8(IGT) §8│ §f" + formatRTA() + " §8(RTA)";
    }

    public static String formatRTA() {
        return formatTimeMillis(getElapsedMillis());
    }

    public static String formatIGT() {
        return formatTimeMillis(igtTicks * 50L);
    }

    private static String formatTimeMillis(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("attempts")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("§6Current Attempt: §e#" + attempts + " §8(Seed: §7" + currentSeed + "§8) §8│ §bTimer: §f" + formatTimer()), false);
                    return 1;
                })
                .then(Commands.literal("set")
                    .then(Commands.argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            attempts = IntegerArgumentType.getInteger(ctx, "number");
                            saveAttempts();
                            ctx.getSource().sendSuccess(() -> Component.literal("§6Attempt counter set to: §e#" + attempts), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        attempts = 1;
                        saveAttempts();
                        ctx.getSource().sendSuccess(() -> Component.literal("§6Attempt counter reset to §e#1"), true);
                        return 1;
                    })
                )
            );

            dispatcher.register(Commands.literal("timer")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("§bSpeedrun Timer: §f" + formatActionBarTimer() + " " + (timerRunning.get() ? "§a(Running)" : "§c(Finished)")), false);
                    return 1;
                })
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        startTime = System.currentTimeMillis();
                        endTime = 0L;
                        igtTicks = 0L;
                        hasPlayerMoved = !config.timer.waitForFirstInput;
                        spawnPosCaptured = false;
                        timerRunning.set(true);
                        ctx.getSource().sendSuccess(() -> Component.literal("§bSpeedrun Timer reset to 00:00"), true);
                        return 1;
                    })
                )
                .then(Commands.literal("mode")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .executes(ctx -> {
                            String m = StringArgumentType.getString(ctx, "mode").toUpperCase();
                            if ("IGT".equals(m) || "RTA".equals(m) || "BOTH".equals(m)) {
                                config.timer.mode = m;
                                config.save();
                                ctx.getSource().sendSuccess(() -> Component.literal("§bTimer mode set to: §e" + m), true);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("§cChoose 'BOTH', 'IGT', or 'RTA'"));
                            return 0;
                        })
                    )
                )
                .then(Commands.literal("waitformove")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                            config.timer.waitForFirstInput = enabled;
                            config.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§bWait for first move: " + (enabled ? "§aEnabled" : "§cDisabled")), true);
                            return 1;
                        })
                    )
                )
            );
        });
    }

    private static void loadAttempts() {
        try {
            if (SAVE_FILE.exists()) {
                String str = Files.readString(SAVE_FILE.toPath()).trim();
                attempts = Integer.parseInt(str);
            }
        } catch (Exception ignored) {
            attempts = 1;
        }
    }

    private static void saveAttempts() {
        try {
            SAVE_FILE.getParentFile().mkdirs();
            Files.writeString(SAVE_FILE.toPath(), String.valueOf(attempts));
        } catch (Exception e) {
            System.err.println("[Speedrun] Could not save attempts: " + e.getMessage());
        }
    }
}
