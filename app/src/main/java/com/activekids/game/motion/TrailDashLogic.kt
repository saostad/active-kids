package com.activekids.game.motion

import kotlin.random.Random

internal enum class TrailLane { LEFT, CENTER, RIGHT }
internal enum class TrailObstacleKind { LOG, BOULDER }
internal enum class TrailOutcome { CLEARED, HIT }

internal data class TrailObstacle(val lane: TrailLane, val kind: TrailObstacleKind, val arrivesAtMs: Long)

internal class TrailDashLogic(
    private val approachMs: Long,
    private val spacingMs: Long,
    private val stepFraction: Float,
    private val jumpFraction: Float,
    private val jumpMs: Long,
    private val random: Random = Random.Default,
) {
    private var origin: Point? = null
    private var torsoHeight = 0f
    private var nextArrivalMs = approachMs
    private var jumpStartedAtMs = -jumpMs
    private var wasRaised = false
    private val pending = ArrayDeque<TrailObstacle>()
    val obstacles: Iterable<TrailObstacle> get() = pending

    var elapsedMs = 0L
        private set
    var lane = TrailLane.CENTER
        private set
    var outcome: TrailOutcome? = null
        private set
    var collisions = 0
        private set

    val jumpProgress get() = (elapsedMs - jumpStartedAtMs).toFloat() / jumpMs

    fun calibrate(hipCenter: Point, height: Float) {
        // Use the last pose from the countdown so the player has time to stand centered.
        if (pending.isEmpty() && elapsedMs == 0L) {
            origin = hipCenter
            torsoHeight = height
        }
    }

    fun progress(obstacle: TrailObstacle): Float =
        (1f - (obstacle.arrivesAtMs - elapsedMs).toFloat() / approachMs).coerceIn(0f, 1f)

    /** Advances the track only while a pose is available. Returns the number of obstacles cleared. */
    fun advance(dtMs: Long, hipCenter: Point): Int {
        val start = checkNotNull(origin)
        elapsedMs += dtMs
        val shift = (hipCenter.x - start.x) / torsoHeight
        lane = when {
            shift <= -stepFraction -> TrailLane.LEFT
            shift >= stepFraction -> TrailLane.RIGHT
            else -> TrailLane.CENTER
        }
        val raised = start.y - hipCenter.y >= torsoHeight * jumpFraction
        if (raised && !wasRaised) jumpStartedAtMs = elapsedMs
        wasRaised = raised

        while (nextArrivalMs - approachMs <= elapsedMs) {
            val first = nextArrivalMs == approachMs
            val obstacleLane = if (first) TrailLane.CENTER else TrailLane.entries[random.nextInt(TrailLane.entries.size)]
            val kind = if (first || random.nextBoolean()) TrailObstacleKind.LOG else TrailObstacleKind.BOULDER
            pending.addLast(TrailObstacle(obstacleLane, kind, nextArrivalMs))
            nextArrivalMs += spacingMs
        }

        var cleared = 0
        while (pending.isNotEmpty() && pending.first().arrivesAtMs <= elapsedMs) {
            val obstacle = pending.removeFirst()
            val airborne = jumpProgress < 1f
            if (obstacle.lane != lane || (obstacle.kind == TrailObstacleKind.LOG && airborne)) {
                outcome = TrailOutcome.CLEARED
                cleared++
            } else {
                collisions++
                outcome = TrailOutcome.HIT
            }
        }
        return cleared
    }
}
