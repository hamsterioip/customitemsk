package com.example;

import java.util.Map;
import java.util.UUID;

/**
 * Tracks Thunder Mark stacks accumulated on entities hit by Storm Bow arrows.
 * Lightning is triggered when a single entity accumulates {@link #LIGHTNING_THRESHOLD} marks.
 *
 * All methods are pure-Java static utilities that operate on an externally supplied map,
 * making them fully unit-testable without any Minecraft runtime.
 */
class ThunderMarkTracker {

    /** Number of hits required to trigger a lightning strike on the target. */
    static final int LIGHTNING_THRESHOLD = 3;

    private ThunderMarkTracker() {}

    /**
     * Records one Storm Arrow hit on the entity and returns the updated mark count.
     * Call {@link #shouldTriggerLightning(int)} on the returned value to decide
     * whether to spawn a lightning bolt.
     */
    static int recordHit(Map<UUID, Integer> marks, UUID entityId) {
        return marks.merge(entityId, 1, Integer::sum);
    }

    /** Returns {@code true} when the mark count has reached the lightning threshold. */
    static boolean shouldTriggerLightning(int markCount) {
        return markCount >= LIGHTNING_THRESHOLD;
    }

    /**
     * Removes all marks for the entity (call after triggering lightning, or on entity death).
     */
    static void clearMarks(Map<UUID, Integer> marks, UUID entityId) {
        marks.remove(entityId);
    }

    /** Returns the current mark count for the entity, or 0 if none recorded. */
    static int getMarkCount(Map<UUID, Integer> marks, UUID entityId) {
        return marks.getOrDefault(entityId, 0);
    }
}
