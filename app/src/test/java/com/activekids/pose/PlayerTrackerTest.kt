package com.activekids.pose

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTrackerTest {
    /** A minimal pose whose torso centre is at (x, y). */
    private fun pose(x: Float, y: Float = 0.5f): List<Landmark> =
        List(33) { Landmark(x, y, 1f) }

    private fun slotsByX(players: List<TrackedPlayer>) = players.associate { it.landmarks[0].x to it.slot }

    @Test
    fun newPlayersGetSlotsLeftToRight() {
        val tracker = PlayerTracker()
        val result = tracker.assign(listOf(pose(0.8f), pose(0.2f)), maxSlots = 3)
        assertEquals(mapOf(0.2f to 0, 0.8f to 1), slotsByX(result))
    }

    @Test
    fun slotsStayStableWhenDetectionOrderSwaps() {
        val tracker = PlayerTracker()
        tracker.assign(listOf(pose(0.2f), pose(0.8f)), 3)
        val result = tracker.assign(listOf(pose(0.78f), pose(0.22f)), 3)
        assertEquals(mapOf(0.22f to 0, 0.78f to 1), slotsByX(result))
    }

    @Test
    fun returningPlayerGetsTheirSlotBack() {
        val tracker = PlayerTracker()
        tracker.assign(listOf(pose(0.2f), pose(0.8f)), 3)
        tracker.assign(listOf(pose(0.8f)), 3) // left player steps out
        val result = tracker.assign(listOf(pose(0.25f), pose(0.8f)), 3)
        assertEquals(mapOf(0.25f to 0, 0.8f to 1), slotsByX(result))
    }

    @Test
    fun extraPeopleBeyondMaxSlotsAreDropped() {
        val tracker = PlayerTracker()
        val result = tracker.assign(listOf(pose(0.1f), pose(0.5f), pose(0.9f)), maxSlots = 2)
        assertEquals(listOf(0, 1), result.map { it.slot })
    }
}
