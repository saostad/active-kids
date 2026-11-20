package com.activekids.game.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PartyGamesTest {
    private val neutral = MotionPose(
        left = Arm(Point(800f, 400f), Point(730f, 650f)),
        right = Arm(Point(1000f, 400f), Point(1070f, 650f)),
        hipCenter = Point(900f, 650f),
        torsoHeight = 250f,
    )

    @Test
    fun freezeAwardsOnlyPlayersWhoStayStillAndVisibleForTheWholeWindow() {
        val dance = FreezeDanceLogic(4000, 2000, 0.20f, 150)
        val initial = mapOf(0 to neutral, 1 to neutral, 2 to neutral)
        assertTrue(dance.advance(4000, initial).isEmpty())
        assertTrue(dance.freezing)
        val moved = neutral.copy(left = neutral.left.copy(hand = Point(620f, 650f)))
        dance.advance(900, mapOf(0 to neutral, 1 to moved))
        assertEquals(setOf(0), dance.stillPlayers)
        assertEquals(listOf(0), dance.advance(1100, initial))
        assertFalse(dance.freezing)
    }

    @Test
    fun penguinUsesTorsoLeanForSteeringAndFishAndSnowAreSeparate() {
        val center = PenguinParadeLogic(3000, 0.30f, 1f, Random(11))
        center.calibrate(0.10f)
        assertEquals(1, center.advance(3000, 0.10f))
        assertEquals(0f, center.position, 0.0001f)
        val banking = PenguinParadeLogic(3000, 0.30f, 1f, Random(11))
        banking.advance(0, 0f)
        val bankSide = banking.rows.first().bank
        assertEquals(0, banking.advance(3000, 0.30f * bankSide))
        assertEquals(bankSide.toFloat(), banking.position, 0.0001f)
        assertEquals(1, banking.snowbanks)
    }

    @Test
    fun meteorCanOnlyBeBlockedByItsAssignedDefender() {
        val meteors = MeteorShieldLogic(3000, 2000, 60f, Random(1))
        val zones = linkedMapOf(0 to (420f..420f), 1 to (1350f..1350f))
        meteors.advance(900, zones, emptyMap())
        val meteor = meteors.meteors.single()
        val point = meteors.center(meteor)
        assertTrue(meteors.advance(0, zones, mapOf(1 to listOf(point))).isEmpty())
        assertEquals(listOf(0), meteors.advance(0, zones, mapOf(0 to listOf(point))))
        meteors.advance(4100, zones, emptyMap())
        assertEquals(1, meteors.baseHits)
    }

    @Test
    fun orchestraIgnoresEarlyHoldsAndScoresFreshTouchesInTheBeatWindow() {
        val orchestra = RobotOrchestraLogic(800, 350)
        assertTrue(orchestra.advance(600, listOf(true, false)).isEmpty())
        assertTrue(orchestra.advance(1000, listOf(true, false)).isEmpty())
        assertTrue(orchestra.advance(20, listOf(false, false)).isEmpty())
        assertEquals(listOf(0), orchestra.advance(50, listOf(true, false)).map { it.pad })
        assertTrue(orchestra.advance(50, listOf(true, false)).isEmpty())
        assertEquals(listOf(1), orchestra.advance(600, listOf(true, true)).map { it.pad })
    }

    @Test
    fun bothHalvesMustFitWhenACoopGateArrives() {
        val gates = ShapeGateLogic(4000, 0.30f)
        fun matching() = listOf(gates.shape(0).pose, gates.shape(1).pose)
        assertFalse(gates.advance(3999, matching()))
        assertFalse(gates.advance(1, listOf(gates.shape(0).pose, null)))
        assertEquals(false, gates.lastCleared)
        assertTrue(gates.advance(4000, matching()))
        assertEquals(true, gates.lastCleared)
    }

    @Test
    fun treasureNeedsTheLeverAndAFreshGrabAndThenSwapsRoles() {
        val hunt = TreasureTeamLogic(600)
        assertFalse(hunt.advance(400, leverTouched = true, chestTouched = true))
        assertFalse(hunt.advance(200, leverTouched = true, chestTouched = true))
        assertTrue(hunt.unlocked)
        assertFalse(hunt.advance(100, leverTouched = false, chestTouched = false))
        assertFalse(hunt.unlocked)
        assertFalse(hunt.advance(600, leverTouched = true, chestTouched = false))
        assertTrue(hunt.advance(30, leverTouched = true, chestTouched = true))
        assertEquals(1, hunt.leverPlayer)
        assertEquals(1, hunt.finds)
        assertFalse(hunt.unlocked)
    }

    @Test
    fun mirrorPlaysBackTheThreeLeaderMovesAndRotatesLeaders() {
        val mirror = MirrorLeaderLogic(1500, 0.30f)
        val moves = listOf(GateShape.ARCH.pose, GateShape.WINGS.pose, GateShape.KITE_LEFT.pose)
        moves.forEach { assertTrue(mirror.advance(1500, mapOf(0 to it, 1 to null)).isEmpty()) }
        assertTrue(mirror.copying)
        moves.forEachIndexed { index, pattern ->
            assertEquals(pattern, mirror.reference)
            val player = if (index == 1) moves[0] else pattern
            val awarded = mirror.advance(1500, mapOf(0 to pattern, 1 to player))
            assertEquals(if (index == 1) emptyList<Int>() else listOf(1), awarded)
        }
        assertEquals(1, mirror.leader)
        assertFalse(mirror.copying)
    }
}
