package com.activekids.game.bubblepop

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.random.Random

/** All sizes are in game-space units (see GameSpace); times in milliseconds. */
data class BubblePopConfig(
    val roundMs: Long,
    val countdownMs: Long,
    val spawnPerSecond: Float,
    val bubbleRadius: Float,
    val handRadius: Float,
    val riseMs: Long,
    val players: Int,
)

class Bubble(val id: Long, val x: Float, var y: Float, val radius: Float, val speedPerMs: Float, val colorIndex: Int)

class PopEffect(val x: Float, val y: Float, val radius: Float, val slot: Int, var ageMs: Long = 0)

data class HandPoint(val slot: Int, val x: Float, val y: Float)

enum class BubblePhase { COUNTDOWN, PLAYING, FINISHED }

sealed interface BubbleEvent {
    data class CountdownTick(val secondsLeft: Int) : BubbleEvent
    data object Go : BubbleEvent
    data class Popped(val slot: Int, val bubbleId: Long) : BubbleEvent
    data object Finished : BubbleEvent
}

/** Bubble Pop rules with no Android dependencies, so they can be unit tested. */
class BubblePopLogic(
    private val config: BubblePopConfig,
    private val width: Float,
    private val height: Float,
    private val random: Random = Random.Default,
) {
    var phase: BubblePhase = if (config.countdownMs > 0) BubblePhase.COUNTDOWN else BubblePhase.PLAYING
        private set
    private var phaseElapsedMs = 0L
    private var spawnCarry = 0f
    private var nextId = 0L
    private var lastTick = Int.MAX_VALUE

    private val _bubbles = mutableListOf<Bubble>()
    val bubbles: List<Bubble> get() = _bubbles
    private val _pops = mutableListOf<PopEffect>()
    val pops: List<PopEffect> get() = _pops
    val scores = IntArray(config.players)

    val countdownSecondsLeft: Int
        get() = if (phase == BubblePhase.COUNTDOWN) ceil((config.countdownMs - phaseElapsedMs) / 1000.0).toInt() else 0

    val secondsLeft: Int
        get() = when (phase) {
            BubblePhase.COUNTDOWN -> ceil(config.roundMs / 1000.0).toInt()
            BubblePhase.PLAYING -> ceil((config.roundMs - phaseElapsedMs).coerceAtLeast(0) / 1000.0).toInt()
            BubblePhase.FINISHED -> 0
        }

    fun update(dtMs: Long, hands: List<HandPoint>): List<BubbleEvent> {
        val events = mutableListOf<BubbleEvent>()
        _pops.forEach { it.ageMs += dtMs }
        _pops.removeAll { it.ageMs > POP_EFFECT_MS }

        when (phase) {
            BubblePhase.COUNTDOWN -> {
                phaseElapsedMs += dtMs
                if (phaseElapsedMs >= config.countdownMs) {
                    phase = BubblePhase.PLAYING
                    phaseElapsedMs = 0
                    events += BubbleEvent.Go
                } else {
                    val left = countdownSecondsLeft
                    if (left < lastTick) {
                        lastTick = left
                        events += BubbleEvent.CountdownTick(left)
                    }
                }
            }
            BubblePhase.PLAYING -> {
                phaseElapsedMs += dtMs
                spawn(dtMs)
                move(dtMs)
                collide(hands, events)
                if (phaseElapsedMs >= config.roundMs) {
                    phase = BubblePhase.FINISHED
                    _bubbles.clear()
                    events += BubbleEvent.Finished
                }
            }
            BubblePhase.FINISHED -> Unit
        }
        return events
    }

    private fun spawn(dtMs: Long) {
        spawnCarry += dtMs * config.spawnPerSecond / 1000f
        while (spawnCarry >= 1f) {
            spawnCarry -= 1f
            val r = config.bubbleRadius * (0.8f + 0.4f * random.nextFloat())
            val x = r + random.nextFloat() * (width - 2 * r).coerceAtLeast(0f)
            val speed = (height + 2 * r) / config.riseMs * (0.85f + 0.3f * random.nextFloat())
            _bubbles += Bubble(nextId++, x, height + r, r, speed, random.nextInt(BUBBLE_COLORS))
        }
    }

    private fun move(dtMs: Long) {
        _bubbles.forEach { it.y -= it.speedPerMs * dtMs }
        _bubbles.removeAll { it.y + it.radius < 0 }
    }

    private fun collide(hands: List<HandPoint>, events: MutableList<BubbleEvent>) {
        for (hand in hands) {
            if (hand.slot !in scores.indices) continue
            val hit = _bubbles.firstOrNull { hypot(it.x - hand.x, it.y - hand.y) < it.radius + config.handRadius } ?: continue
            _bubbles.remove(hit)
            scores[hand.slot]++
            _pops += PopEffect(hit.x, hit.y, hit.radius, hand.slot)
            events += BubbleEvent.Popped(hand.slot, hit.id)
        }
    }

    companion object {
        const val POP_EFFECT_MS = 350L
        const val BUBBLE_COLORS = 6
    }
}
