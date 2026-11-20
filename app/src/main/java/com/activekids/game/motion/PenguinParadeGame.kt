package com.activekids.game.motion

import android.graphics.Canvas
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.settings.AppSettings
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

internal data class IceRow(val fish: Int, val bank: Int, val arrives: Long)

internal class PenguinParadeLogic(
    private val travelMs: Long, private val fullLean: Float, private val steerSpeed: Float,
    private val random: Random = Random.Default,
) {
    private val pending = ArrayDeque<IceRow>()
    private val spacingMs = rowSpacingMs(travelMs, steerSpeed)
    val rows: Iterable<IceRow> get() = pending
    private var next = travelMs
    private var elapsed = 0L
    var position = 0f
        private set
    var neutralLean = 0f
        private set
    var snowbanks = 0
        private set

    fun calibrate(lean: Float) {
        if (pending.isEmpty() && elapsed == 0L) neutralLean = lean
    }

    fun progress(row: IceRow) = (1f - (row.arrives - elapsed).toFloat() / travelMs).coerceIn(0f, 1f)

    fun advance(dt: Long, lean: Float): Int {
        position = (position + ((lean - neutralLean) / fullLean).coerceIn(-1f, 1f) * steerSpeed * dt / 1000f).coerceIn(-1f, 1f)
        elapsed += dt
        while (next - travelMs <= elapsed) {
            val fish = if (next == travelMs) 0 else random.nextInt(-1, 2)
            val bank = listOf(-1, 0, 1).filter { it != fish }.random(random)
            pending.addLast(IceRow(fish, bank, next))
            next += spacingMs
        }
        var collected = 0
        while (pending.isNotEmpty() && pending.first().arrives <= elapsed) {
            val row = pending.removeFirst()
            if (abs(position - row.fish) <= ITEM_HALF_WIDTH) collected++
            if (abs(position - row.bank) <= ITEM_HALF_WIDTH) snowbanks++
        }
        return collected
    }

    companion object {
        const val ITEM_HALF_WIDTH = 0.37f

        // A player has time to notice the next row and get across the whole slide.
        fun rowSpacingMs(travelMs: Long, steerSpeed: Float): Long =
            maxOf(travelMs / 2, ceil(2000.0 / steerSpeed).toLong() + travelMs / 4)
    }
}

internal class PenguinParadeGame(settings: AppSettings, sound: SoundFx) : TimedMotionGame(settings.copy(maxPlayers = 1), sound) {
    override val kind = GameKind.PENGUIN_PARADE
    override val countdownCaption = "Stand upright to set your balance"
    private val slope = PenguinParadeLogic(
        (settings.penguinTravelSeconds * 1000).toLong(), settings.penguinLeanPercent / 100f, settings.penguinSteerSpeed,
    )
    override val mistakes get() = slope.snowbanks
    private val shapes = PartyCanvas()

    override fun readyToPlay(poses: Map<Int, MotionPose>): Boolean {
        val pose = poses[0] ?: return false
        slope.calibrate(pose.lean)
        return true
    }

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        val fish = slope.advance(dtMs, poses.getValue(0).lean)
        scores[0] += fish
        return fish > 0
    }

    override fun drawPlay(canvas: Canvas) {
        val cx = GameSpace.WIDTH / 2f
        shapes.shade(canvas, 0xFF245579.toInt())
        shapes.circle(canvas, 0xFF86C9E8.toInt(), Point(360f, 480f), 285f)
        shapes.circle(canvas, 0xFF9CD9EF.toInt(), Point(1540f, 465f), 260f)
        shapes.rect(canvas, 0xFFB8E7F4.toInt(), 0f, 435f, 1920f, 1080f)
        shapes.rect(canvas, 0xFF87CFE7.toInt(), 308f, 435f, 1612f, 1080f, 85f)
        label(canvas, "Lean to steer  •  Snowbanks hit: ${slope.snowbanks}", cx, 247f, 55f)
        for (row in slope.rows.toList().asReversed()) {
            val t = slope.progress(row)
            val y = 375f + t * 565f
            val span = 220f + t * 210f
            val radius = span * PenguinParadeLogic.ITEM_HALF_WIDTH * (0.47f + 0.30f * t)
            val fish = Point(cx + span * row.fish, y)
            val bank = Point(cx + span * row.bank, y)
            shapes.circle(canvas, 0xFFF1FCFF.toInt(), bank, radius)
            shapes.circle(canvas, 0xFFFFFFFF.toInt(), bank.copy(x = bank.x - radius * 0.43f), radius * 0.65f)
            shapes.line(canvas, 0xFFDE714C.toInt(), fish, fish.copy(x = fish.x - radius), radius * 0.38f)
            shapes.circle(canvas, 0xFFFFA36B.toInt(), fish, radius * 0.55f)
            shapes.circle(canvas, 0xFF273F63.toInt(), fish.copy(x = fish.x + radius * 0.2f), radius * 0.10f)
        }
        val penguin = Point(cx + slope.position * 430f, 925f)
        shapes.circle(canvas, 0xFF202B4A.toInt(), penguin, 96f)
        shapes.circle(canvas, 0xFFF4F9FF.toInt(), penguin.copy(y = 952f), 68f)
        shapes.circle(canvas, 0xFF202B4A.toInt(), penguin.copy(y = 827f), 70f)
        for (side in listOf(-1, 1)) {
            shapes.circle(canvas, 0xFFF4F9FF.toInt(), Point(penguin.x + side * 22f, 824f), 18f)
            shapes.circle(canvas, 0xFF202B4A.toInt(), Point(penguin.x + side * 22f, 824f), 7f)
        }
        shapes.circle(canvas, 0xFFFFAD5C.toInt(), penguin.copy(y = 849f), 19f)
    }
}
