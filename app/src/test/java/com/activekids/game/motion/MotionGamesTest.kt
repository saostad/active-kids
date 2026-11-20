package com.activekids.game.motion

import com.activekids.game.GameSpace
import com.activekids.pose.FrameTransform
import com.activekids.pose.Landmark
import com.activekids.pose.PoseLandmarks
import com.activekids.pose.TrackedPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionGamesTest {
    private val resting = MotionPose(
        left = Arm(Point(850f, 450f), Point(850f, 690f)),
        right = Arm(Point(1070f, 450f), Point(1070f, 690f)),
        hipCenter = Point(960f, 690f),
        torsoHeight = 240f,
    )
    private val handsUp = resting.copy(
        left = resting.left.copy(hand = Point(800f, 280f)),
        right = resting.right.copy(hand = Point(1120f, 280f)),
    )
    private val armsOut = resting.copy(
        left = resting.left.copy(hand = Point(620f, 450f)),
        right = resting.right.copy(hand = Point(1300f, 450f)),
    )

    @Test
    fun touchingTheStarMovesTheNextTargetAwayFromTheHand() {
        val first = starTarget(resting, score = 0, sizeFraction = 0.3f)
        assertFalse(first.reachedBy(resting))
        val reaching = resting.copy(left = resting.left.copy(hand = first.center))
        assertTrue(first.reachedBy(reaching))
        assertFalse(starTarget(reaching, score = 1, sizeFraction = 0.3f).reachedBy(reaching))
    }

    @Test
    fun targetsStayOnScreenNearAnEdge() {
        val nearEdge = resting.copy(left = Arm(Point(10f, 30f), null))
        val target = starTarget(nearEdge, score = 0, sizeFraction = 0.3f)
        assertEquals(target.radius, target.center.x, 0.001f)
        assertEquals(target.radius, target.center.y, 0.001f)
    }

    @Test
    fun interruptedHoldsResetAndEachPlayerScoresAtMostOncePerPrompt() {
        val scores = IntArray(2)
        val logic = PoseMatchLogic(promptMs = 3000, holdMs = 800, tolerance = 0.3f, scores)
        assertFalse(logic.update(700, mapOf(0 to handsUp)))
        assertFalse(logic.update(100, emptyMap()))
        assertFalse(logic.update(100, mapOf(0 to handsUp)))
        assertEquals(0.125f, logic.progress(0), 0.001f)
        assertTrue(logic.update(700, mapOf(0 to handsUp, 1 to handsUp)))
        assertEquals(listOf(1, 0), scores.toList())
        assertTrue(logic.update(100, mapOf(0 to handsUp, 1 to handsUp)))
        assertFalse(logic.update(1300, mapOf(0 to handsUp, 1 to handsUp)))
        assertEquals(listOf(1, 1), scores.toList())
        assertEquals(PosePrompt.ARMS_OUT, logic.prompt)
        assertFalse(logic.update(800, mapOf(0 to handsUp)))
        assertTrue(logic.update(800, mapOf(0 to armsOut)))
        assertEquals(listOf(2, 1), scores.toList())
    }

    @Test
    fun bothArmsMustBeVisibleAndArmsOutWorksWithMirroring() {
        val points = MutableList(33) { Landmark(0f, 0f, 0f) }.apply {
            set(PoseLandmarks.LEFT_SHOULDER, Landmark(0.40f, 0.4f, 1f))
            set(PoseLandmarks.RIGHT_SHOULDER, Landmark(0.60f, 0.4f, 1f))
            set(PoseLandmarks.LEFT_HIP, Landmark(0.40f, 0.65f, 1f))
            set(PoseLandmarks.RIGHT_HIP, Landmark(0.60f, 0.65f, 1f))
            set(PoseLandmarks.LEFT_WRIST, Landmark(0.20f, 0.4f, 1f))
            set(PoseLandmarks.RIGHT_WRIST, Landmark(0.80f, 0.4f, 1f))
        }
        for (mirror in listOf(false, true)) {
            val transform = FrameTransform(GameSpace.WIDTH, GameSpace.HEIGHT, 1280f, 960f, mirror)
            val pose = checkNotNull(TrackedPlayer(0, points).motionPose(transform, 0.5f))
            assertTrue(PosePrompt.ARMS_OUT.matches(pose, 0.3f))
            assertFalse(PosePrompt.ARMS_OUT.matches(pose.copy(left = pose.left.copy(hand = null)), 0.3f))
        }
    }
}
