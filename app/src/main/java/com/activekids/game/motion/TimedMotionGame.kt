package com.activekids.game.motion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.activekids.audio.SoundFx
import com.activekids.game.Game
import com.activekids.game.GameSpace
import com.activekids.game.GameSummary
import com.activekids.game.Palette
import com.activekids.pose.FrameTransform
import com.activekids.pose.TrackedPlayer
import com.activekids.settings.AppSettings
import kotlin.math.ceil

internal abstract class TimedMotionGame(
    settings: AppSettings,
    private val sound: SoundFx,
) : Game {
    private val countdownMs = settings.countdownSeconds * 1000L
    private val endMs = countdownMs + settings.roundSeconds * 1000L
    private var elapsedMs = 0L
    private var lastTick = 0
    private var waiting = false
    private var soundEnabled = false
    protected val scores = IntArray(settings.maxPlayers)

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(8f, 0f, 3f, Color.BLACK)
    }
    private val scrim = Paint().apply { color = 0xBB000000.toInt() }
    private val countdown get() = elapsedMs < countdownMs
    private val finished get() = elapsedMs >= endMs
    private val secondsLeft get() = seconds(endMs - maxOf(countdownMs, elapsedMs))

    final override fun update(
        dtMs: Long,
        players: List<TrackedPlayer>,
        transform: FrameTransform?,
        settings: AppSettings,
    ) {
        if (finished) return
        soundEnabled = settings.soundOn
        val poses = if (transform == null) emptyMap() else buildMap {
            players.forEach { player ->
                if (player.slot in scores.indices) {
                    player.motionPose(transform, settings.landmarkVisibility)?.let { put(player.slot, it) }
                }
            }
        }
        waiting = !readyToPlay(poses)
        if (waiting) return

        val previousPlayMs = (elapsedMs - countdownMs).coerceAtLeast(0)
        val wasCountingDown = countdown
        elapsedMs = (elapsedMs + dtMs).coerceAtMost(endMs)
        if (countdown) {
            val tick = seconds(countdownMs - elapsedMs)
            if (tick != lastTick && settings.soundOn) sound.play(SoundFx.Sfx.TICK)
            lastTick = tick
            return
        }

        if (wasCountingDown && settings.soundOn) sound.play(SoundFx.Sfx.GO)
        val scored = updatePlay(elapsedMs - countdownMs - previousPlayMs, poses)
        if (!finished && settings.soundOn && scored) scoreSound?.let(sound::play)
    }

    protected open fun readyToPlay(poses: Map<Int, MotionPose>) = true
    protected open val waitingCaption = "Keep your shoulders and hips visible"
    protected open val waitingTitle = "Step into view"
    protected open val scoreSound: SoundFx.Sfx? = SoundFx.Sfx.POP
    protected open val mistakes: Int get() = 0
    protected fun cue(sfx: SoundFx.Sfx) {
        if (soundEnabled) sound.play(sfx)
    }
    protected abstract fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean
    protected abstract fun drawPlay(canvas: Canvas)

    final override fun summary() = GameSummary(
        phase = when {
            waiting -> "Waiting for player"
            countdown -> "Get ready"
            finished -> "Finished"
            else -> "Playing"
        },
        secondsLeft = secondsLeft,
        scores = scores.toList(),
        mistakes = mistakes,
    )

    final override fun draw(canvas: Canvas) {
        when {
            finished -> {
                canvas.drawRect(0f, 0f, GameSpace.WIDTH, GameSpace.HEIGHT, scrim)
                label(canvas, "Time’s up!", GameSpace.WIDTH / 2f, 320f, 128f)
                scores.forEachIndexed { slot, score ->
                    label(canvas, "Player ${slot + 1}: $score", GameSpace.WIDTH / 2f, 480f + slot * 115f, 82f, Palette.player(slot))
                }
            }
            waiting -> {
                label(canvas, waitingTitle, GameSpace.WIDTH / 2f, 510f, 108f)
                label(canvas, waitingCaption, GameSpace.WIDTH / 2f, 635f, 63f)
            }
            countdown -> {
                label(canvas, seconds(countdownMs - elapsedMs).toString(), GameSpace.WIDTH / 2f, 620f, 290f)
                label(canvas, countdownCaption, GameSpace.WIDTH / 2f, 735f, 80f)
            }
            else -> {
                drawPlay(canvas)
                label(canvas, "%d:%02d".format(secondsLeft / 60, secondsLeft % 60), GameSpace.WIDTH / 2f, 83f, 70f)
                scores.forEachIndexed { slot, score ->
                    val x = GameSpace.WIDTH * (slot + 1) / (scores.size + 1)
                    label(canvas, "P${slot + 1}  $score", x, 155f, 56f, Palette.player(slot))
                }
            }
        }
    }

    protected open val countdownCaption get() = kind.title

    protected fun label(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE) {
        text.textSize = size
        text.color = color
        canvas.drawText(value, x, y, text)
    }

    private fun seconds(ms: Long): Int = ceil(ms / 1000.0).toInt()
}
