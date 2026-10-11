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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeedrunTracker implements ModInitializer {
    public static final String MOD_ID = "speedrun-tracker";
    private static final File SAVE_FILE = new File("config/speedrun_attempts.txt");

    // Persistent per-world speedrun stats attachment via Fabric Data Attachment API
    public record WorldRunStats(int attempts, int deaths, long netherSplitMillis, long endSplitMillis, boolean stopped, long finalIgtTicks, long finalRtaMillis) {
        public static final Codec<WorldRunStats> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.fieldOf("attempts").forGetter(WorldRunStats::attempts),
                Codec.INT.fieldOf("deaths").forGetter(WorldRunStats::deaths),
                Codec.LONG.fieldOf("nether_split").forGetter(WorldRunStats::netherSplitMillis),
                Codec.LONG.fieldOf("end_split").forGetter(WorldRunStats::endSplitMillis),
                Codec.BOOL.optionalFieldOf("stopped", false).forGetter(WorldRunStats::stopped),
                Codec.LONG.optionalFieldOf("final_igt_ticks", 0L).forGetter(WorldRunStats::finalIgtTicks),
                Codec.LONG.optionalFieldOf("final_rta_millis", 0L).forGetter(WorldRunStats::finalRtaMillis)
            ).apply(instance, WorldRunStats::new)
        );
    }

    public static final AttachmentType<WorldRunStats> WORLD_STATS_ATTACHMENT = AttachmentRegistry.createPersistent(
        Identifier.fromNamespaceAndPath(MOD_ID, "world_stats"),
        WorldRunStats.CODEC
    );

    // Vanilla GameRule registered via Fabric Game Rule API
    public static final GameRule<Boolean> SPEEDRUN_RULE = GameRuleBuilder.forBoolean(true)
        .category(GameRuleCategory.MISC)
        .buildAndRegister(Identifier.fromNamespaceAndPath(MOD_ID, "speedrun_enabled"));

    public static SpeedrunConfig config;
    private static int attempts = 1;
    private static int deaths = 0;
    private static long netherSplitMillis = 0L;
    private static long endSplitMillis = 0L;
    private static boolean worldStopped = false;
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
    private static volatile boolean userLeft = false;

    @Override
    public void onInitialize() {
        config = SpeedrunConfig.load();
        loadAttempts();

        // WorldReset integration
        if (FabricLoader.getInstance().isModLoaded("worldreset")) {
            initWorldResetHook();
        }

        // Register built-in presets datapack / resource pack using Fabric Resource Loader
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(container -> {
            net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
                Identifier.fromNamespaceAndPath(MOD_ID, "speedrun_presets"),
                container,
                net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED
            );
        });

        // Server startup and world detection
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ServerLevel overworld = server.overworld();
                if (overworld != null) {
                    currentSeed = overworld.getSeed();

                    if (!FabricLoader.getInstance().isModLoaded("worldreset") && config.behavior.autoIncrementOnNewWorld) {
                        long gameTime = overworld.getGameTime();
                        WorldRunStats stats = ((AttachmentTarget) overworld).getAttached(WORLD_STATS_ATTACHMENT);

                        if (stats != null && stats.stopped()) {
                            // If this specific world was stopped, restore stopped state and do not run timer
                            attempts = stats.attempts();
                            deaths = stats.deaths();
                            netherSplitMillis = stats.netherSplitMillis();
                            endSplitMillis = stats.endSplitMillis();
                            igtTicks = stats.finalIgtTicks();
                            startNano = System.nanoTime();
                            rtaOffsetNanos = TimeUnit.MILLISECONDS.toNanos(stats.finalRtaMillis());
                            endNano = startNano; // locks getElapsedMillis()
                            worldStopped = true;
                            timerRunning.set(false);
                            hasPlayerMoved = true;
                        } else if (gameTime < 40) {
                            attempts++;
                            saveAttempts();
                            resetRun(server, overworld.getSeed());
                        } else {
                            // Resume on rejoining existing world: load per-world stats from Fabric Data Attachment if present
                            if (stats != null) {
                                attempts = stats.attempts();
                                deaths = stats.deaths();
                                netherSplitMillis = stats.netherSplitMillis();
                                endSplitMillis = stats.endSplitMillis();
                            }
                            igtTicks = Math.max(0, gameTime);
                            startNano = System.nanoTime();
                            rtaOffsetNanos = TimeUnit.MILLISECONDS.toNanos(igtTicks * 50L);
                            worldStopped = false;
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

        // Player join event: ensure fresh worlds trigger reset and indicator if server started early
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.JOIN.register((player) -> {
            MinecraftServer server = player.level().getServer();
            if (server != null && server.overworld() != null) {
                WorldRunStats stats = ((AttachmentTarget) server.overworld()).getAttached(WORLD_STATS_ATTACHMENT);
                if (stats != null && stats.stopped()) {
                    return; // World is stopped, don't trigger new run
                }
                long gameTime = server.overworld().getGameTime();
                if (!timerRunning.get() && !worldStopped && gameTime < 40) {
                    attempts++;
                    saveAttempts();
                    resetRun(server, server.overworld().getSeed());
                }
                // Reset spawn capture so movement detection uses player's actual spawn point
                spawnPosCaptured = false;
            }
        });

        // Track player death & respawn statistics via Fabric Entity Events API
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && timerRunning.get()) {
                deaths++;
                MinecraftServer server = newPlayer.level().getServer();
                if (server != null && config != null && config.display.chatBroadcast) {
                    server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§c§l☠ §7Player §e" + newPlayer.getScoreboardName() + " §7died (Run Deaths: §c" + deaths + "§7)"),
                        false
                    );
                }
            }
        });

        // Fabric API: Player connection events (RTA management when joining/leaving)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            userLeft = false;
        });

        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            userLeft = true;
        });

        // Server stopped lifecycle
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            worldInitialized = false;
            hasPlayerMoved = false;
            spawnPosCaptured = false;
            timerRunning.set(false);
            userLeft = true;
        });

        // Ender Dragon defeat
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (config.behavior.stopOnDragonKill && entity instanceof EnderDragon && timerRunning.get()) {
                endNano = System.nanoTime();
                timerRunning.set(false);
                String timeStr = formatTimer();
                MinecraftServer srv = entity.level().getServer();

                if (srv != null && srv.overworld() != null) {
                    persistWorldStats(srv.overworld());
                }

                SpeedrunAPI.dispatchComplete(attempts, currentSeed, getElapsedMillis(), timeStr, srv);

                if (srv != null && config.display.chatBroadcast) {
                    String winMsg = "§6§l» §aSpeedrun finished: §e" + timeStr + " §8(Attempt #" + attempts + ")";
                    srv.getPlayerList().broadcastSystemMessage(Component.literal(winMsg), false);
                }

                if (srv != null && config.behavior.victorySound) {
                    for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
                        player.level().playSound(
                            null,
                            player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                            net.minecraft.sounds.SoundSource.PLAYERS,
                            1.0f, 1.0f
                        );
                    }
                }
            }
        });

        // Fabric Entity Events API: Dimension Change (Nether / End split milestones)
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            if (!timerRunning.get()) return;
            var destKey = destination.dimension();
            MinecraftServer srv = destination.getServer();

            if (destKey == Level.NETHER && netherSplitMillis == 0L) {
                netherSplitMillis = getElapsedMillis();
                if (srv != null && srv.overworld() != null) persistWorldStats(srv.overworld());
                if (srv != null && config != null && config.display.chatBroadcast) {
                    String splitMsg = "§5§l» §dNether Split: §e" + formatTimeMillis(netherSplitMillis) + " §8(Attempt #" + attempts + ")";
                    srv.getPlayerList().broadcastSystemMessage(Component.literal(splitMsg), false);
                }
            } else if (destKey == Level.END && endSplitMillis == 0L) {
                endSplitMillis = getElapsedMillis();
                if (srv != null && srv.overworld() != null) persistWorldStats(srv.overworld());
                if (srv != null && config != null && config.display.chatBroadcast) {
                    String splitMsg = "§3§l» §bThe End Split: §e" + formatTimeMillis(endSplitMillis) + " §8(Attempt #" + attempts + ")";
                    srv.getPlayerList().broadcastSystemMessage(Component.literal(splitMsg), false);
                }
            }
        });

        // Fabric Interaction Events API: Trigger instantaneous start upon first block/entity/item action
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            triggerMovementStart();
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            triggerMovementStart();
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            triggerMovementStart();
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            triggerMovementStart();
            return InteractionResult.PASS;
        });

        // Action bar ticker
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (config != null && !config.enabled) return;
            if (!timerRunning.get()) return;

            // Movement detection: IGT waits at 00:00.000 until first player movement or intentional input
            // RTA runs immediately from world load and is never interrupted by movement
            if (!hasPlayerMoved) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!spawnPosCaptured) {
                        spawnX = player.getX();
                        spawnY = player.getY();
                        spawnZ = player.getZ();
                        spawnYRot = player.getYRot();
                        spawnXRot = player.getXRot();
                        spawnPosCaptured = true;
                    } else {
                        // Measure horizontal planar movement (ignore initial vertical gravity settling)
                        double dx = player.getX() - spawnX;
                        double dz = player.getZ() - spawnZ;
                        double horizontalDistSq = dx * dx + dz * dz;
                        float rotDiff = Math.abs(player.getYRot() - spawnYRot) + Math.abs(player.getXRot() - spawnXRot);

                        // Require intentional input: horizontal movement > 0.05 blocks, looking around > 3 degrees, crouching, sprinting, or swinging
                        if (horizontalDistSq > 0.0025 || rotDiff > 3.0f || player.isCrouching() || player.isSprinting() || player.isSwinging()) {
                            hasPlayerMoved = true;
                            break;
                        }
                    }
                }
            }

            if (hasPlayerMoved) {
                igtTicks++;
            }

            if (config == null || config.display.zeroOverhead || !config.display.actionBar) return;

            // Only send server action bar packets on dedicated servers; singleplayer client uses 60+ FPS HudElementRegistry
            if (FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) return;

            tickCounter++;
            if (tickCounter % config.timer.updateRateTicks != 0) return;

            String barText = "§6Attempt #" + attempts + " §8| " + formatHudTimer();
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

                // Enforce template settings on WorldReset reset
                if (server != null && config != null && config.template != null) {
                    try {
                        net.minecraft.world.Difficulty diff = net.minecraft.world.Difficulty.byName(config.template.difficulty.toLowerCase());
                        if (diff != null) {
                            server.setDifficulty(diff, true);
                        }
                    } catch (Throwable ignored) {}
                    try {
                        net.minecraft.world.level.GameType gt = net.minecraft.world.level.GameType.byName(config.template.gameMode.toLowerCase());
                        if (gt != null) {
                            player.setGameMode(gt);
                        }
                    } catch (Throwable ignored) {}
                }

                if (config.display.chatBroadcast && server != null) {
                    String msg = "§6§l» §eStarting Attempt #" + attempts + " §8(Seed: §7" + currentSeed + "§8)";
                    server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
                }
            });
        } catch (Throwable t) {
            System.err.println("[Speedrun] WorldReset hook error: " + t.getMessage());
        }
    }

    private static void triggerMovementStart() {
        if (!hasPlayerMoved && timerRunning.get()) {
            hasPlayerMoved = true;
        }
    }

    public static void persistWorldStats(ServerLevel overworld) {
        if (overworld != null) {
            try {
                ((AttachmentTarget) overworld).setAttached(
                    WORLD_STATS_ATTACHMENT,
                    new WorldRunStats(attempts, deaths, netherSplitMillis, endSplitMillis, worldStopped, igtTicks, getElapsedMillis())
                );
            } catch (Throwable ignored) {}
        }
    }

    public static void stopRunForCurrentWorld(MinecraftServer server) {
        if (!worldStopped) {
            endNano = System.nanoTime();
            worldStopped = true;
            timerRunning.set(false);
            if (server != null && server.overworld() != null) {
                persistWorldStats(server.overworld());
            }
        }
    }

    private static void resetRun(MinecraftServer server, long seed) {
        startNano = System.nanoTime();
        endNano = 0L;
        rtaOffsetNanos = 0L;
        igtTicks = 0L;
        deaths = 0;
        netherSplitMillis = 0L;
        endSplitMillis = 0L;
        worldStopped = false;
        currentSeed = seed;
        timerRunning.set(true);
        hasPlayerMoved = false;
        spawnPosCaptured = false;
        userLeft = false;

        if (server != null && server.overworld() != null) {
            persistWorldStats(server.overworld());
        }

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

    public static int getDeaths() {
        return deaths;
    }

    public static void setDeaths(int count) {
        deaths = count;
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
                tab.getPlaceholderManager().registerServerPlaceholder("%deaths%", 1000, () -> String.valueOf(deaths));
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_deaths%", 1000, () -> String.valueOf(deaths));
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_split_nether%", 1000, () -> netherSplitMillis > 0 ? formatTimeMillis(netherSplitMillis) : "--:--");
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_split_end%", 1000, () -> endSplitMillis > 0 ? formatTimeMillis(endSplitMillis) : "--:--");
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_timer%", 500, SpeedrunTracker::formatTimer);
                tab.getPlaceholderManager().registerServerPlaceholder("%timer%", 500, SpeedrunTracker::formatTimer);
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_rta%", 500, SpeedrunTracker::formatRTA);
                tab.getPlaceholderManager().registerServerPlaceholder("%speedrun_igt%", 500, SpeedrunTracker::formatIGT);
                tab.getPlaceholderManager().registerServerPlaceholder("%seed%", 2000, () -> String.valueOf(currentSeed));
            }
        } catch (Throwable ignored) {}
    }

    public static long getNetherSplitMillis() {
        return netherSplitMillis;
    }

    public static long getEndSplitMillis() {
        return endSplitMillis;
    }

    public static boolean isWorldStopped() {
        return worldStopped;
    }

    public static String formatHudTimer() {
        String mode = (config != null && config.timer != null && config.timer.mode != null)
            ? config.timer.mode.toUpperCase() : "BOTH";
        if ("IGT".equals(mode)) {
            return "§b" + formatIGT();
        } else if ("RTA".equals(mode)) {
            return "§f" + formatRTA();
        }
        return "§b" + formatIGT() + " §8| §f" + formatRTA();
    }

    public static String formatMenuTimer() {
        String mode = (config != null && config.timer != null && config.timer.mode != null)
            ? config.timer.mode.toUpperCase() : "BOTH";
        if ("IGT".equals(mode)) {
            return "§6Attempt #" + attempts + " §8| §bIGT: " + formatIGT();
        } else if ("RTA".equals(mode)) {
            return "§6Attempt #" + attempts + " §8| §fRTA: " + formatRTA();
        }
        return "§6Attempt #" + attempts + " §8| §bIGT: " + formatIGT() + " §8| §fRTA: " + formatRTA();
    }

    public static String formatTimer() {
        return formatMenuTimer();
    }

    public static String formatActionBarTimer() {
        return formatHudTimer();
    }

    public static boolean isUserLeft() {
        return userLeft;
    }

    public static void setUserLeft(boolean left) {
        userLeft = left;
    }

    public static String formatRTA() {
        if (userLeft) {
            return "null";
        }
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
            // Main unified command: /speedrun
            var speedrunCommand = Commands.literal("speedrun")
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
                .then(Commands.literal("reset")
                    .executes(ctx -> {
                        if (ctx.getSource().getServer() != null) {
                            handleExternalReset(currentSeed, ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("§aRun reset to Attempt #" + attempts), true);
                            return 1;
                        }
                        return 0;
                    })
                )
                // /speedrun attempts
                .then(Commands.literal("attempts")
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
                )
                // /speedrun timer
                .then(Commands.literal("timer")
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
                    .then(Commands.literal("stop")
                        .executes(ctx -> {
                            var source = ctx.getSource();
                            var server = source.getServer();
                            if (server != null) {
                                stopRunForCurrentWorld(server);
                                source.sendSuccess(() -> Component.literal("§6§l» §cSpeedrun Timer STOPPED for this world! §8(IGT: §b" + formatIGT() + " §8| RTA: §f" + formatRTA() + "§8)"), true);
                                return 1;
                            }
                            return 0;
                        })
                    )
                    .then(Commands.literal("reset")
                        .executes(ctx -> {
                            startNano = System.nanoTime();
                            endNano = 0L;
                            rtaOffsetNanos = 0L;
                            igtTicks = 0L;
                            hasPlayerMoved = false;
                            spawnPosCaptured = false;
                            worldStopped = false;
                            timerRunning.set(true);
                            if (ctx.getSource().getServer() != null && ctx.getSource().getServer().overworld() != null) {
                                persistWorldStats(ctx.getSource().getServer().overworld());
                            }
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
                )
                // /speedrun stop (stops the timer and permanently stores final run time in this world's NBT)
                .then(Commands.literal("stop")
                    .executes(ctx -> {
                        var source = ctx.getSource();
                        var server = source.getServer();
                        if (server != null) {
                            stopRunForCurrentWorld(server);
                            source.sendSuccess(() -> Component.literal("§6§l» §cSpeedrun Timer STOPPED for this world! §8(IGT: §b" + formatIGT() + " §8| RTA: §f" + formatRTA() + "§8)"), true);
                            return 1;
                        }
                        return 0;
                    })
                )
                // /speedrun stats (Per-World statistics powered by Fabric Data Attachments)
                .then(Commands.literal("stats")
                    .executes(ctx -> {
                        var source = ctx.getSource();
                        var server = source.getServer();
                        source.sendSuccess(() -> Component.literal("§6§l=== SPEEDRUN STATS (World Seed: " + currentSeed + ") ==="), false);
                        source.sendSuccess(() -> Component.literal("§eStatus: " + (worldStopped ? "§c[STOPPED]" : (timerRunning.get() ? "§a[RUNNING]" : "§7[WAITING]"))), false);
                        source.sendSuccess(() -> Component.literal("§eAttempts: §f#" + attempts), false);
                        source.sendSuccess(() -> Component.literal("§eDeaths: §c" + deaths), false);
                        source.sendSuccess(() -> Component.literal("§eIGT: §b" + formatIGT() + " §8| §eRTA: §f" + formatRTA()), false);
                        source.sendSuccess(() -> Component.literal("§eNether Split: " + (netherSplitMillis > 0 ? "§a" + formatTimeMillis(netherSplitMillis) : "§7--:--")), false);
                        source.sendSuccess(() -> Component.literal("§eThe End Split: " + (endSplitMillis > 0 ? "§a" + formatTimeMillis(endSplitMillis) : "§7--:--")), false);
                        if (server != null && server.overworld() != null) {
                            WorldRunStats stats = ((AttachmentTarget) server.overworld()).getAttached(WORLD_STATS_ATTACHMENT);
                            if (stats != null) {
                                source.sendSuccess(() -> Component.literal("§8[Fabric Data Attachment: Synced with World NBT]"), false);
                            }
                        }
                        return 1;
                    })
                )
                // /speedrun menu / config
                .then(Commands.literal("menu")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("§6§l» §eOpening Speedrun Settings... (Use §b/speedrun-menu §eor configure via §bMod Menu§e)"), false);
                        return 1;
                    })
                )
                // /speedrun rules
                .then(Commands.literal("rules")
                    .then(Commands.literal("reset")
                        .executes(ctx -> {
                            config.template.resetToSpeedrunDefaults();
                            config.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§aWorld template reset to Leaderboard defaults (Easy, Survival, No Cheats)"), true);
                            return 1;
                        })
                    )
                );

            dispatcher.register(speedrunCommand);
        });
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§lSpeedRun? §7v" + SpeedrunTracker.config.template.difficulty.toLowerCase()), false);
        source.sendSuccess(() -> Component.literal("§e/speedrun §7- Overview and settings"), false);
        source.sendSuccess(() -> Component.literal("§e/speedrun stop §7- Stop timer & lock final time for current world"), false);
        source.sendSuccess(() -> Component.literal("§e/speedrun stats §7- View per-world attempts, deaths & splits"), false);
        source.sendSuccess(() -> Component.literal("§e/speedrun timer §7[get|set|reset|stop|mode] §7- Stopwatch control"), false);
        source.sendSuccess(() -> Component.literal("§e/speedrun attempts §7[set|reset] §7- Attempt counter"), false);
        source.sendSuccess(() -> Component.literal("§e/speedrun rules reset §7- Reset to official leaderboard rules"), false);
        source.sendSuccess(() -> Component.literal("§e/speedrun menu §7(or §e/speedrun config§7) - Open Settings GUI"), false);
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
