package com.ryme.speedrun.api;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Universal API for speedrun reset mods, timers, and external tools.
 */
public final class SpeedrunAPI {
    private static final List<Consumer<ResetEvent>> RESET_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<CompletionEvent>> COMPLETION_LISTENERS = new CopyOnWriteArrayList<>();

    private SpeedrunAPI() {}

    public record ResetEvent(int attempt, long seed, MinecraftServer server) {}
    public record CompletionEvent(int attempt, long seed, long elapsedMillis, String formattedTime, MinecraftServer server) {}

    /**
     * Register a callback fired whenever a run reset is triggered.
     */
    public static void onReset(Consumer<ResetEvent> listener) {
        RESET_LISTENERS.add(listener);
    }

    /**
     * Register a callback fired when the run finishes (e.g. Ender Dragon defeated).
     */
    public static void onComplete(Consumer<CompletionEvent> listener) {
        COMPLETION_LISTENERS.add(listener);
    }

    /**
     * Trigger a new attempt with a specified world seed.
     */
    public static void triggerReset(long seed, MinecraftServer server) {
        com.ryme.speedrun.SpeedrunTracker.handleExternalReset(seed, server);
    }

    /**
     * Trigger a new attempt using the current server overworld seed.
     */
    public static void triggerReset(MinecraftServer server) {
        long seed = 0L;
        if (server != null && server.overworld() != null) {
            seed = server.overworld().getSeed();
        }
        triggerReset(seed, server);
    }

    public static void dispatchReset(int attempt, long seed, MinecraftServer server) {
        ResetEvent event = new ResetEvent(attempt, seed, server);
        for (Consumer<ResetEvent> listener : RESET_LISTENERS) {
            try {
                listener.accept(event);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static void dispatchComplete(int attempt, long seed, long elapsedMillis, String formattedTime, MinecraftServer server) {
        CompletionEvent event = new CompletionEvent(attempt, seed, elapsedMillis, formattedTime, server);
        for (Consumer<CompletionEvent> listener : COMPLETION_LISTENERS) {
            try {
                listener.accept(event);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static int getAttempts() {
        return com.ryme.speedrun.SpeedrunTracker.getAttempts();
    }

    public static void setAttempts(int count) {
        com.ryme.speedrun.SpeedrunTracker.setAttempts(count);
    }

    public static int incrementAttempts() {
        return com.ryme.speedrun.SpeedrunTracker.incrementAttempts();
    }

    public static long getSeed() {
        return com.ryme.speedrun.SpeedrunTracker.getCurrentSeed();
    }

    public static boolean isTimerRunning() {
        return com.ryme.speedrun.SpeedrunTracker.isTimerRunning();
    }

    public static long getElapsedMillis() {
        return com.ryme.speedrun.SpeedrunTracker.getElapsedMillis();
    }

    public static String getFormattedTime() {
        return com.ryme.speedrun.SpeedrunTracker.formatTimer();
    }
}
