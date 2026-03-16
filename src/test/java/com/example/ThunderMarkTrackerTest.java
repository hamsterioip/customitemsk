package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ThunderMarkTrackerTest {

    private Map<UUID, Integer> marks;

    @BeforeEach
    void setUp() {
        marks = Collections.synchronizedMap(new HashMap<>());
    }

    // -------------------------------------------------------------------------
    // recordHit
    // -------------------------------------------------------------------------

    @Test
    void firstHit_marksOne() {
        UUID id = UUID.randomUUID();
        int count = ThunderMarkTracker.recordHit(marks, id);
        assertEquals(1, count);
    }

    @Test
    void secondHit_marksTwo() {
        UUID id = UUID.randomUUID();
        ThunderMarkTracker.recordHit(marks, id);
        int count = ThunderMarkTracker.recordHit(marks, id);
        assertEquals(2, count);
    }

    @Test
    void thirdHit_marksThree() {
        UUID id = UUID.randomUUID();
        ThunderMarkTracker.recordHit(marks, id);
        ThunderMarkTracker.recordHit(marks, id);
        int count = ThunderMarkTracker.recordHit(marks, id);
        assertEquals(3, count);
    }

    // -------------------------------------------------------------------------
    // shouldTriggerLightning
    // -------------------------------------------------------------------------

    @Test
    void oneHit_noLightning() {
        assertFalse(ThunderMarkTracker.shouldTriggerLightning(1));
    }

    @Test
    void twoHits_noLightning() {
        assertFalse(ThunderMarkTracker.shouldTriggerLightning(2));
    }

    @Test
    void threeHits_triggersLightning() {
        assertTrue(ThunderMarkTracker.shouldTriggerLightning(3));
    }

    @Test
    void moreThanThreeHits_triggersLightning() {
        assertTrue(ThunderMarkTracker.shouldTriggerLightning(4));
        assertTrue(ThunderMarkTracker.shouldTriggerLightning(10));
    }

    @Test
    void threshold_isThree() {
        assertEquals(3, ThunderMarkTracker.LIGHTNING_THRESHOLD);
    }

    // -------------------------------------------------------------------------
    // clearMarks
    // -------------------------------------------------------------------------

    @Test
    void clearMarks_removesEntity() {
        UUID id = UUID.randomUUID();
        ThunderMarkTracker.recordHit(marks, id);
        ThunderMarkTracker.recordHit(marks, id);
        ThunderMarkTracker.clearMarks(marks, id);
        assertEquals(0, ThunderMarkTracker.getMarkCount(marks, id));
    }

    @Test
    void hitAfterClear_startsFromOne() {
        UUID id = UUID.randomUUID();
        ThunderMarkTracker.recordHit(marks, id);
        ThunderMarkTracker.recordHit(marks, id);
        ThunderMarkTracker.clearMarks(marks, id);
        int count = ThunderMarkTracker.recordHit(marks, id);
        assertEquals(1, count);
    }

    @Test
    void clearNonExistentEntity_isNoOp() {
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> ThunderMarkTracker.clearMarks(marks, id));
    }

    // -------------------------------------------------------------------------
    // Multiple entities tracked independently
    // -------------------------------------------------------------------------

    @Test
    void multipleEntities_trackedIndependently() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        ThunderMarkTracker.recordHit(marks, a);
        ThunderMarkTracker.recordHit(marks, a);
        ThunderMarkTracker.recordHit(marks, b);

        assertEquals(2, ThunderMarkTracker.getMarkCount(marks, a));
        assertEquals(1, ThunderMarkTracker.getMarkCount(marks, b));
    }

    @Test
    void clearOneEntity_doesNotAffectOther() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        ThunderMarkTracker.recordHit(marks, a);
        ThunderMarkTracker.recordHit(marks, a);
        ThunderMarkTracker.recordHit(marks, b);

        ThunderMarkTracker.clearMarks(marks, a);

        assertEquals(0, ThunderMarkTracker.getMarkCount(marks, a));
        assertEquals(1, ThunderMarkTracker.getMarkCount(marks, b));
    }

    @Test
    void fullLightningCycle_thenRestarts() {
        UUID id = UUID.randomUUID();

        // First cycle — reaches lightning
        ThunderMarkTracker.recordHit(marks, id);
        ThunderMarkTracker.recordHit(marks, id);
        int triggerCount = ThunderMarkTracker.recordHit(marks, id);
        assertTrue(ThunderMarkTracker.shouldTriggerLightning(triggerCount));
        ThunderMarkTracker.clearMarks(marks, id);

        // Second cycle — starts fresh
        int afterClear = ThunderMarkTracker.recordHit(marks, id);
        assertEquals(1, afterClear);
        assertFalse(ThunderMarkTracker.shouldTriggerLightning(afterClear));
    }
}
