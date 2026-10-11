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
    private static long igtTickStart = 0L;
    private static long igtTickEnd = 0L;
    private static final AtomicBoolean timerRunning = new AtomicBoolean(true);
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

        // Ender Dragon defeat
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (config.behavior.stopOnDragonKill && entity instanceof EnderDragon && timerRunning.get()) {
                endTime = System.currentTimeMillis();
                try {
                    ServerLevel lvl = (ServerLevel) entity.level();
                    igtTickEnd = lvl.getGameTime();
                } catch (Throwable ignored) {}

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

        // Action bar ticker (bypassed entirely in zero-overhead mode)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (config.display.zeroOverhead || !config.display.actionBar || !timerRunning.get()) return;

            tickCounter++;
            if (tickCounter % config.timer.updateRateTicks != 0) return;

            String barText = "§6Attempt #" + attempts + " §8│ §b" + formatTimer() + " §8(" + config.timer.mode + ")";
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
        currentSeed = seed;
        timerRunning.set(true);

        try {
            if (server != null && server.overworld() != null) {
                igtTickStart = server.overworld().getGameTime();
            }
        } catch (Throwable ignored) {
            igtTickStart = 0L;
        }
        igtTickEnd = 0L;

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
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_igt%", 500, SpeedrunTracker::formatTimer);
                tab.getPlaceholderManager().registerServerPlaceholder("%seed%", 2000, () -> String.valueOf(currentSeed));
            }
        } catch (Throwable ignored) {}
    }

    public static String formatTimer() {
        if ("IGT".equalsIgnoreCase(config.timer.mode)) {
            return formatIGT();
        }
        return formatRTA();
    }

    public static String formatRTA() {
        return formatTimeMillis(getElapsedMillis());
    }

    public static String formatIGT() {
        long currentTicks = timerRunning.get()
            ? (igtTickStart > 0 ? (System.currentTimeMillis() - startTime) / 50 : 0)
            : (igtTickEnd - igtTickStart);
        if (currentTicks < 0) currentTicks = 0;
        return formatTimeMillis(currentTicks * 50);
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
                    ctx.getSource().sendSuccess(() -> Component.literal("§bSpeedrun Timer: §f" + formatTimer() + " §8(" + config.timer.mode + ") " + (timerRunning.get() ? "§a(Running)" : "§c(Finished)")), false);
                    return 1;
                })
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        startTime = System.currentTimeMillis();
                        endTime = 0L;
                        timerRunning.set(true);
                        ctx.getSource().sendSuccess(() -> Component.literal("§bSpeedrun Timer reset to 00:00"), true);
                        return 1;
                    })
                )
                .then(Commands.literal("mode")
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .executes(ctx -> {
                            String m = StringArgumentType.getString(ctx, "mode").toUpperCase();
                            if ("IGT".equals(m) || "RTA".equals(m)) {
                                config.timer.mode = m;
                                config.save();
                                ctx.getSource().sendSuccess(() -> Component.literal("§bTimer mode set to: §e" + m), true);
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("§cChoose 'IGT' or 'RTA'"));
                            return 0;
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
