package com.activekids.game.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrailDashLogicTest {
    private val center = Point(950f, 700f)
    private val left = Point(810f, 700f)
    private val right = Point(1090f, 700f)
    private val jumping = Point(950f, 630f)

    // Subsequent obstacles deliberately use the left lane and the non-jumpable kind.
    private fun course() = TrailDashLogic(
        approachMs = 3000,
        spacingMs = 1800,
        stepFraction = 0.55f,
        jumpFraction = 0.25f,
        jumpMs = 600,
        random = object : Random() {
            override fun nextBits(bitCount: Int) = 0
        },
    ).apply { calibrate(center, height = 200f) }

    @Test
    fun standingStillHitsTheFirstLogOnlyWhenItArrives() {
        val trail = course()
        assertEquals(0, trail.advance(2999, center))
        assertEquals(null, trail.outcome)
        assertTrue(trail.progress(trail.obstacles.first()) > 0.99f)
        assertEquals(0, trail.advance(1, center))
        assertEquals(TrailOutcome.HIT, trail.outcome)
    }

    @Test
    fun steppingEitherWayCanDodgeTheCenterLog() {
        for ((position, lane) in listOf(left to TrailLane.LEFT, right to TrailLane.RIGHT)) {
            val trail = course()
            trail.advance(2820, center)
            trail.advance(60, position)
            assertEquals(lane, trail.lane)
            assertEquals(1, trail.advance(120, position))
            assertEquals(TrailOutcome.CLEARED, trail.outcome)
        }
    }

    @Test
    fun aTimedHopClearsTheLogButAnEarlyHopExpiresEvenIfStillRaised() {
        val timed = course()
        timed.advance(2700, center)
        timed.advance(50, jumping)
        assertEquals(1, timed.advance(250, center))

        val early = course()
        early.advance(2000, center)
        early.advance(50, jumping)
        assertEquals(0, early.advance(950, jumping))
        assertEquals(TrailOutcome.HIT, early.outcome)
    }

    @Test
    fun jumpingCannotClearABoulderInTheSameLane() {
        val trail = course()
        trail.advance(3000, left)
        val next = trail.obstacles.first()
        assertEquals(TrailObstacleKind.BOULDER, next.kind)
        assertEquals(TrailLane.LEFT, next.lane)
        trail.advance(1500, left)
        trail.advance(50, left.copy(y = jumping.y))
        assertEquals(0, trail.advance(250, left.copy(y = jumping.y)))
        assertEquals(TrailOutcome.HIT, trail.outcome)
    }

    @Test
    fun theLastCountdownPoseDefinesTheCenterUntilTheRoundEnds() {
        val trail = course()
        trail.calibrate(left, height = 200f)
        trail.advance(50, left)
        assertEquals(TrailLane.CENTER, trail.lane)
        trail.calibrate(center, height = 200f)
        trail.advance(50, center)
        assertEquals(TrailLane.RIGHT, trail.lane)
    }
}
