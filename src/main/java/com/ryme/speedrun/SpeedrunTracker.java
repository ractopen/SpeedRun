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
import net.minecraft.commands.CommandSourceStack;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeedrunTracker implements ModInitializer {
    public static final String MOD_ID = "speedrun-tracker";
    private static final File SAVE_FILE = new File("config/speedrun_attempts.txt");

    public static SpeedrunConfig config;
    private static int attempts = 1;
    private static long currentSeed = 0L;
    private static long startNano = System.nanoTime();
    private static long endNano = 0L;
    private static long rtaOffsetNanos = 0L;
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
                            startNano = System.nanoTime();
                            rtaOffsetNanos = TimeUnit.MILLISECONDS.toNanos(igtTicks * 50L);
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
                endNano = System.nanoTime();
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
        startNano = System.nanoTime();
        endNano = 0L;
        rtaOffsetNanos = 0L;
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
        long elapsedNanos = timerRunning.get()
            ? (System.nanoTime() - startNano + rtaOffsetNanos)
            : (endNano > 0 ? (endNano - startNano + rtaOffsetNanos) : rtaOffsetNanos);
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
    }

    public static void setTime(long millis) {
        igtTicks = Math.max(0, millis / 50L);
        startNano = System.nanoTime();
        rtaOffsetNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, millis));
    }

    public static long parseTimeInput(String input) {
        if (input == null || input.isBlank()) return -1L;
        input = input.trim().toLowerCase();
        if (input.endsWith("s")) {
            input = input.substring(0, input.length() - 1);
        }
        try {
            long extraMillis = 0;
            if (input.contains(".")) {
                String[] dotParts = input.split("\\.");
                input = dotParts[0];
                if (dotParts.length > 1) {
                    String msStr = dotParts[1];
                    if (msStr.length() == 1) extraMillis = Long.parseLong(msStr) * 100;
                    else if (msStr.length() == 2) extraMillis = Long.parseLong(msStr) * 10;
                    else if (msStr.length() >= 3) extraMillis = Long.parseLong(msStr.substring(0, 3));
                }
            }
            if (input.contains(":")) {
                String[] parts = input.split(":");
                if (parts.length == 2) {
                    long m = Long.parseLong(parts[0]);
                    long s = Long.parseLong(parts[1]);
                    return (m * 60 + s) * 1000L + extraMillis;
                } else if (parts.length == 3) {
                    long h = Long.parseLong(parts[0]);
                    long m = Long.parseLong(parts[1]);
                    long s = Long.parseLong(parts[2]);
                    return (h * 3600 + m * 60 + s) * 1000L + extraMillis;
                }
            } else {
                long s = Long.parseLong(input);
                return s * 1000L + extraMillis;
            }
        } catch (NumberFormatException ignored) {}
        return -1L;
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

    public static String formatTimeMillis(long millis) {
        boolean showMs = config != null && config.timer != null && config.timer.showMilliseconds;
        return formatTimeMillis(millis, showMs);
    }

    public static String formatTimeMillis(long millis, boolean showMillis) {
        long ms = millis % 1000;
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));
        if (showMillis) {
            if (hours > 0) {
                return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
            }
            return String.format("%02d:%02d.%03d", minutes, seconds, ms);
        } else {
            if (hours > 0) {
                return String.format("%02d:%02d:%02d", hours, minutes, seconds);
            }
            return String.format("%02d:%02d", minutes, seconds);
        }
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
                .then(Commands.literal("help")
                    .executes(ctx -> {
                        sendHelp(ctx.getSource());
                        return 1;
                    })
                )
            );

            dispatcher.register(Commands.literal("timer")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6§l» §bTimer: §f" + formatActionBarTimer() +
                        " " + (timerRunning.get() ? "§a(Running)" : "§c(Finished)") +
                        " §8│ §7Ticks: §f" + igtTicks
                    ), false);
                    return 1;
                })
                .then(Commands.literal("get")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6§l» §bTimer Breakdown: §eIGT: §f" + formatIGT() + " §8(" + igtTicks + " ticks) §8│ §eRTA: §f" + formatRTA() +
                            " §8│ §7Mode: §f" + config.timer.mode +
                            " " + (timerRunning.get() ? "§a(Running)" : "§c(Finished)")
                        ), false);
                        return 1;
                    })
                )
                .then(Commands.literal("set")
                    .then(Commands.argument("time", StringArgumentType.string())
                        .executes(ctx -> {
                            String input = StringArgumentType.getString(ctx, "time");
                            long millis = parseTimeInput(input);
                            if (millis < 0) {
                                ctx.getSource().sendFailure(Component.literal("§cInvalid format! Use seconds (e.g. 90, 90s) or MM:SS (e.g. 01:30)"));
                                return 0;
                            }
                            setTime(millis);
                            ctx.getSource().sendSuccess(() -> Component.literal("§bSpeedrun Timer set to: §e" + formatTimeMillis(millis)), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        startNano = System.nanoTime();
                        endNano = 0L;
                        rtaOffsetNanos = 0L;
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
                .then(Commands.literal("millis")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                            config.timer.showMilliseconds = enabled;
                            config.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§bMilliseconds display: " + (enabled ? "§aEnabled" : "§cDisabled")), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("help")
                    .executes(ctx -> {
                        sendHelp(ctx.getSource());
                        return 1;
                    })
                )
            );

            // /speedrun
            dispatcher.register(Commands.literal("speedrun")
                .executes(ctx -> {
                    sendHelp(ctx.getSource());
                    return 1;
                })
                .then(Commands.literal("help")
                    .executes(ctx -> {
                        sendHelp(ctx.getSource());
                        return 1;
                    })
                )
                .then(Commands.literal("repo")
                    .executes(ctx -> {
                        sendRepoLink(ctx.getSource());
                        return 1;
                    })
                )
            );
        });
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§l=== SpeedRun? Mod Help ==="), false);
        source.sendSuccess(() -> Component.literal("§7A minimalist attempt counter, stopwatch, and fast reset utility for Fabric."), false);
        source.sendSuccess(() -> Component.literal("§eRepository: §b§nhttps://github.com/ractopen/SpeedRun"), false);
        source.sendSuccess(() -> Component.literal("§6Commands:"), false);
        source.sendSuccess(() -> Component.literal(" §e/attempts §7- View attempt counter"), false);
        source.sendSuccess(() -> Component.literal(" §e/attempts set <n> §8| §e/attempts reset §7- Adjust attempt count"), false);
        source.sendSuccess(() -> Component.literal(" §e/timer §7- View active timer"), false);
        source.sendSuccess(() -> Component.literal(" §e/timer get §7- Full IGT and RTA breakdown"), false);
        source.sendSuccess(() -> Component.literal(" §e/timer mode <BOTH|IGT|RTA> §7- Change stopwatch display"), false);
        source.sendSuccess(() -> Component.literal(" §e/timer set <time> §7- Set timer (e.g. 90, 01:30.500)"), false);
        source.sendSuccess(() -> Component.literal(" §e/timer millis <true|false> §7- Toggle millisecond display"), false);
        source.sendSuccess(() -> Component.literal(" §e/timer waitformove <true|false> §7- Toggle wait for first input"), false);
        source.sendSuccess(() -> Component.literal(" §e/timer reset §7- Reset stopwatch to 00:00"), false);
        source.sendSuccess(() -> Component.literal("§6Pause Menu: §7Use the §eFast Reset§7 button to quickly restart runs."), false);
    }

    private static void sendRepoLink(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§l» §eGitHub Repository: §b§nhttps://github.com/ractopen/SpeedRun"), false);
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
