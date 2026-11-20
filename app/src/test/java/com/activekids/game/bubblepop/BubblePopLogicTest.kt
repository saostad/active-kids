package com.activekids.game.bubblepop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BubblePopLogicTest {
    private fun config(
        countdownMs: Long = 0,
        roundMs: Long = 10_000,
        spawnPerSecond: Float = 2f,
        players: Int = 2,
    ) = BubblePopConfig(
        roundMs = roundMs,
        countdownMs = countdownMs,
        spawnPerSecond = spawnPerSecond,
        bubbleRadius = 50f,
        handRadius = 20f,
        riseMs = 100_000, // slow, so bubbles stay on screen during the test
        players = players,
    )

    private fun logic(c: BubblePopConfig = config()) = BubblePopLogic(c, 1920f, 1080f, Random(42))

    @Test
    fun spawnsAtConfiguredRate() {
        val game = logic(config(spawnPerSecond = 2f))
        repeat(11) { game.update(100, emptyList()) } // 1.1 s at 2/s, kept off the exact boundary
        assertEquals(2, game.bubbles.size)
    }

    @Test
    fun handTouchingBubblePopsItAndScoresThatPlayer() {
        val game = logic()
        repeat(6) { game.update(100, emptyList()) } // one bubble after 600 ms
        val bubble = game.bubbles.single()

        val events = game.update(1, listOf(HandPoint(slot = 1, x = bubble.x + bubble.radius + 19f, y = bubble.y)))

        assertTrue(game.bubbles.isEmpty())
        assertEquals(listOf(0, 1), game.scores.toList())
        assertEquals(listOf(BubbleEvent.Popped(1, bubble.id)), events)
    }

    @Test
    fun handOutsideReachDoesNotPop() {
        val game = logic()
        repeat(6) { game.update(100, emptyList()) }
        val bubble = game.bubbles.single()
        // Reach is bubble radius + hand radius; stay just outside it.
        game.update(1, listOf(HandPoint(0, bubble.x + bubble.radius + 21f, bubble.y)))
        assertEquals(1, game.bubbles.size)
        assertEquals(0, game.scores[0])
    }

    @Test
    fun handFromUnknownSlotIsIgnored() {
        val game = logic(config(players = 1))
        repeat(6) { game.update(100, emptyList()) }
        val bubble = game.bubbles.single()
        game.update(1, listOf(HandPoint(3, bubble.x, bubble.y)))
        assertEquals(1, game.bubbles.size)
    }

    @Test
    fun countdownTicksEachSecondThenGo() {
        val game = logic(config(countdownMs = 3000))
        val events = (1..30).flatMap { game.update(100, emptyList()) }
        assertEquals(
            listOf(BubbleEvent.CountdownTick(3), BubbleEvent.CountdownTick(2), BubbleEvent.CountdownTick(1), BubbleEvent.Go),
            events,
        )
        assertEquals(BubblePhase.PLAYING, game.phase)
        assertTrue("no bubbles during countdown", game.bubbles.isEmpty())
    }

    @Test
    fun roundEndsClearsBubblesAndStopsSpawning() {
        val game = logic(config(roundMs = 1000))
        val events = (1..10).flatMap { game.update(100, emptyList()) }
        assertEquals(BubbleEvent.Finished, events.last())
        assertEquals(BubblePhase.FINISHED, game.phase)
        assertTrue(game.bubbles.isEmpty())
        repeat(20) { game.update(100, emptyList()) }
        assertTrue(game.bubbles.isEmpty())
        assertEquals(0, game.secondsLeft)
    }
}
