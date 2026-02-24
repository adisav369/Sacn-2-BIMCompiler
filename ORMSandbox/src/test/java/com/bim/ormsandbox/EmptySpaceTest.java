package com.bim.ormsandbox;

import com.bim.orm.EmptySpace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-PHANTOM-1 automated gate — replaces the manual witness.
 *
 * NORTH_WALL capacity = SH living room width = 8869mm.
 * Items: Piano(1371mm) + Sofa(2000mm) + Loveseat(1600mm) = 4971mm used.
 * Remaining = 8869 − 4971 = 3898mm.
 * (PROGRESS.md brief listed 1898 — off-by-2000 typo; 3898 is correct.)
 */
@DisplayName("EmptySpace — W-PHANTOM-1 automated gate")
class EmptySpaceTest {

    @Test
    @DisplayName("W-PHANTOM-1: NORTH_WALL Piano+Sofa+Loveseat fit within 8869mm capacity")
    void north_wall_piano_sofa_loveseat_fit() {
        EmptySpace es = EmptySpace.of("NORTH_WALL", 8869);
        es = es.place(1371).place(2000).place(1600);
        assertFalse(es.isOverflow());
        assertEquals(3898, es.remainingMm(), 1.0);
    }

    @Test
    @DisplayName("EmptySpace: overflow detected when placed exceeds capacity")
    void overflowDetected() {
        EmptySpace es = EmptySpace.of("TEST_WALL", 1000);
        es = es.place(600).place(600);
        assertTrue(es.isOverflow());
    }

    @Test
    @DisplayName("EmptySpace: place() is immutable — returns new instance")
    void placeIsImmutable() {
        EmptySpace original = EmptySpace.of("WALL", 5000);
        EmptySpace placed = original.place(1000);
        assertEquals(0, original.usedMm(), 0.001);
        assertEquals(1000, placed.usedMm(), 0.001);
    }
}
