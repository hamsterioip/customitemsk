package com.example;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WatcherMathTest {

    private static final double EPS = 1e-9;

    /** Normalises a 3-vector. */
    private static double[] norm(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / len, y / len, z / len};
    }

    // -------------------------------------------------------------------------
    // LOOK_DOT_THRESHOLD constant
    // -------------------------------------------------------------------------

    @Test
    void threshold_correspondsToCosOf8Degrees() {
        double expected = Math.cos(Math.toRadians(8.0));
        assertEquals(expected, WatcherMath.LOOK_DOT_THRESHOLD, EPS);
    }

    @Test
    void threshold_isPositive() {
        // cos(8°) ≈ 0.990 — sanity check
        assertTrue(WatcherMath.LOOK_DOT_THRESHOLD > 0.98 && WatcherMath.LOOK_DOT_THRESHOLD < 1.0);
    }

    // -------------------------------------------------------------------------
    // isPlayerLookingAtWatcher — viewing cone
    // -------------------------------------------------------------------------

    @Test
    void directlyAhead_triggersVanish() {
        // Player looking straight at watcher (0° offset)
        assertTrue(WatcherMath.isPlayerLookingAtWatcher(0, 0, 1,  0, 0, 1));
    }

    @Test
    void withinCone_4Degrees_triggersVanish() {
        double angle = Math.toRadians(4.0);
        double[] look = norm(Math.sin(angle), 0, Math.cos(angle));
        assertTrue(WatcherMath.isPlayerLookingAtWatcher(
                look[0], look[1], look[2], 0, 0, 1));
    }

    @Test
    void exactlyAtBoundary_8Degrees_doesNotTrigger() {
        // At exactly 8° the dot product equals the threshold (not strictly greater)
        double angle = Math.toRadians(8.0);
        double[] look = norm(Math.sin(angle), 0, Math.cos(angle));
        // dot == threshold → strict > is false
        assertFalse(WatcherMath.isPlayerLookingAtWatcher(
                look[0], look[1], look[2], 0, 0, 1));
    }

    @Test
    void justOutsideCone_10Degrees_doesNotTrigger() {
        double angle = Math.toRadians(10.0);
        double[] look = norm(Math.sin(angle), 0, Math.cos(angle));
        assertFalse(WatcherMath.isPlayerLookingAtWatcher(
                look[0], look[1], look[2], 0, 0, 1));
    }

    @Test
    void lookingAway_doesNotTrigger() {
        assertFalse(WatcherMath.isPlayerLookingAtWatcher(0, 0, -1,  0, 0, 1));
    }

    @Test
    void lookingPerpendicular_doesNotTrigger() {
        assertFalse(WatcherMath.isPlayerLookingAtWatcher(1, 0, 0,  0, 0, 1));
    }

    @Test
    void watcherBehindPlayer_doesNotTrigger() {
        // "to watcher" points backward relative to look direction
        assertFalse(WatcherMath.isPlayerLookingAtWatcher(0, 0, 1,  0, 0, -1));
    }

    @Test
    void verticalOffset_withinCone_triggersVanish() {
        // Watcher slightly above — still within cone
        double[] to = norm(0, Math.tan(Math.toRadians(4.0)), 1);
        assertTrue(WatcherMath.isPlayerLookingAtWatcher(0, 0, 1, to[0], to[1], to[2]));
    }

    // -------------------------------------------------------------------------
    // ambientIntervalMin
    // -------------------------------------------------------------------------

    @Test
    void ambientIntervalMin_stage1() { assertEquals(200, WatcherMath.ambientIntervalMin(1)); }

    @Test
    void ambientIntervalMin_stage2() { assertEquals(160, WatcherMath.ambientIntervalMin(2)); }

    @Test
    void ambientIntervalMin_stage3() { assertEquals(120, WatcherMath.ambientIntervalMin(3)); }

    @Test
    void ambientIntervalMin_stage4() { assertEquals(80,  WatcherMath.ambientIntervalMin(4)); }

    @Test
    void ambientIntervalMin_stage5_andAbove() {
        assertEquals(40, WatcherMath.ambientIntervalMin(5));
        assertEquals(40, WatcherMath.ambientIntervalMin(99));
    }

    @Test
    void ambientIntervalMin_decreasesWithStage() {
        for (int s = 1; s < 5; s++) {
            assertTrue(WatcherMath.ambientIntervalMin(s) > WatcherMath.ambientIntervalMin(s + 1),
                    "Stage " + s + " min should be greater than stage " + (s + 1));
        }
    }

    // -------------------------------------------------------------------------
    // ambientIntervalRange
    // -------------------------------------------------------------------------

    @Test
    void ambientIntervalRange_allStages_positive() {
        for (int s = 1; s <= 6; s++) {
            assertTrue(WatcherMath.ambientIntervalRange(s) > 0,
                    "Range for stage " + s + " must be positive");
        }
    }

    @Test
    void ambientIntervalRange_stage1() { assertEquals(100, WatcherMath.ambientIntervalRange(1)); }
    @Test
    void ambientIntervalRange_stage5_default() { assertEquals(40, WatcherMath.ambientIntervalRange(5)); }
}
