package com.example;

/**
 * Pure-math helpers for WatcherEntity logic.
 * No Minecraft or Fabric dependencies — fully unit-testable.
 */
class WatcherMath {

    private WatcherMath() {}

    /** Cosine of 8 degrees — the half-angle of the viewing cone that triggers vanish. */
    static final double LOOK_DOT_THRESHOLD = Math.cos(Math.toRadians(8.0));

    /**
     * Returns {@code true} if the player's crosshair lands within the 8-degree cone
     * centred on the watcher.
     *
     * @param lookX/Y/Z      normalised look direction of the player
     * @param toWatcherX/Y/Z normalised vector from the player's eye to the watcher's eye
     */
    static boolean isPlayerLookingAtWatcher(
            double lookX,     double lookY,     double lookZ,
            double toWatcherX, double toWatcherY, double toWatcherZ) {
        double dot = lookX * toWatcherX + lookY * toWatcherY + lookZ * toWatcherZ;
        return dot > LOOK_DOT_THRESHOLD;
    }

    /**
     * Returns the minimum ambient sound interval (ticks) for the given stage.
     * The actual interval is {@code ambientIntervalMin(stage) + random(ambientIntervalRange(stage))}.
     */
    static int ambientIntervalMin(int stage) {
        return switch (stage) {
            case 1 -> 200;
            case 2 -> 160;
            case 3 -> 120;
            case 4 -> 80;
            default -> 40;
        };
    }

    /** Returns the random range (exclusive) added on top of the minimum interval. */
    static int ambientIntervalRange(int stage) {
        return switch (stage) {
            case 1 -> 100;
            case 2 -> 80;
            case 3 -> 60;
            case 4 -> 60;
            default -> 40;
        };
    }
}
