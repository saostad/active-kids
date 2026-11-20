package com.activekids.game.bubblepop

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.activekids.audio.SoundFx
import com.activekids.game.Game
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.game.GameSummary
import com.activekids.game.Hands
import com.activekids.game.Palette
import com.activekids.pose.FrameTransform
import com.activekids.pose.TrackedPlayer
import com.activekids.settings.AppSettings

class BubblePopGame(settings: AppSettings, private val sound: SoundFx) : Game {
    override val kind = GameKind.BUBBLE_POP

    private val logic = BubblePopLogic(
        BubblePopConfig(
            roundMs = settings.roundSeconds * 1000L,
            countdownMs = settings.countdownSeconds * 1000L,
            spawnPerSecond = settings.bubblesPerSecond,
            bubbleRadius = GameSpace.HEIGHT * settings.bubbleSizePercent / 100f,
            handRadius = GameSpace.HEIGHT * settings.handRadiusPercent / 100f,
            riseMs = (settings.bubbleRiseSeconds * 1000).toLong(),
            players = settings.maxPlayers,
        ),
        GameSpace.WIDTH,
        GameSpace.HEIGHT,
    )
    private val handRadius = GameSpace.HEIGHT * settings.handRadiusPercent / 100f
    private var hands: List<HandPoint> = emptyList()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 3f, Color.argb(160, 0, 0, 0))
    }

    override fun update(dtMs: Long, players: List<TrackedPlayer>, transform: FrameTransform?, settings: AppSettings) {
        hands = if (transform == null) emptyList() else players.flatMap { Hands.points(it, transform, settings.landmarkVisibility) }
        for (event in logic.update(dtMs, hands)) {
            if (!settings.soundOn) continue
            when (event) {
                is BubbleEvent.CountdownTick -> sound.play(SoundFx.Sfx.TICK)
                BubbleEvent.Go -> sound.play(SoundFx.Sfx.GO)
                is BubbleEvent.Popped -> sound.play(SoundFx.Sfx.POP)
                BubbleEvent.Finished -> Unit
            }
        }
    }

    override fun summary() = GameSummary(
        phase = when (logic.phase) {
            BubblePhase.COUNTDOWN -> "Get ready"
            BubblePhase.PLAYING -> "Playing"
            BubblePhase.FINISHED -> "Finished"
        },
        secondsLeft = logic.secondsLeft,
        scores = logic.scores.toList(),
    )

    override fun draw(canvas: Canvas) {
        drawBubbles(canvas)
        drawPops(canvas)
        drawHands(canvas)
        drawHud(canvas)
        when (logic.phase) {
            BubblePhase.COUNTDOWN -> drawCenter(canvas, logic.countdownSecondsLeft.toString(), 360f, "Get ready to pop!")
            BubblePhase.FINISHED -> drawResults(canvas)
            BubblePhase.PLAYING -> Unit
        }
    }

    private fun drawBubbles(canvas: Canvas) {
        for (b in logic.bubbles) {
            val c = Palette.bubble(b.colorIndex)
            fill.color = ColorUtils.setAlphaComponent(c, 110)
            canvas.drawCircle(b.x, b.y, b.radius, fill)
            stroke.color = ColorUtils.setAlphaComponent(c, 240)
            stroke.strokeWidth = b.radius * 0.08f
            canvas.drawCircle(b.x, b.y, b.radius, stroke)
            fill.color = Color.argb(200, 255, 255, 255)
            canvas.drawCircle(b.x - b.radius * 0.35f, b.y - b.radius * 0.35f, b.radius * 0.16f, fill)
        }
    }

    private fun drawPops(canvas: Canvas) {
        for (p in logic.pops) {
            val t = p.ageMs / BubblePopLogic.POP_EFFECT_MS.toFloat()
            stroke.color = ColorUtils.setAlphaComponent(Palette.player(p.slot), ((1f - t) * 255).toInt().coerceIn(0, 255))
            stroke.strokeWidth = 14f * (1f - t) + 2f
            canvas.drawCircle(p.x, p.y, p.radius * (1f + t * 0.8f), stroke)
        }
    }

    private fun drawHands(canvas: Canvas) {
        for (h in hands) {
            fill.color = ColorUtils.setAlphaComponent(Palette.player(h.slot), 90)
            canvas.drawCircle(h.x, h.y, handRadius, fill)
            stroke.color = Palette.player(h.slot)
            stroke.strokeWidth = 6f
            canvas.drawCircle(h.x, h.y, handRadius, stroke)
        }
    }

    private fun drawHud(canvas: Canvas) {
        val secs = logic.secondsLeft
        text.textSize = 72f
        text.color = Color.WHITE
        canvas.drawText("%d:%02d".format(secs / 60, secs % 60), GameSpace.WIDTH / 2f, 100f, text)

        val scores = logic.scores
        val slotWidth = 280f
        val startX = GameSpace.WIDTH / 2f - slotWidth * (scores.size - 1) / 2f
        text.textSize = 56f
        scores.forEachIndexed { slot, score ->
            text.color = Palette.player(slot)
            canvas.drawText("P${slot + 1}  $score", startX + slot * slotWidth, 180f, text)
        }
    }

    private fun drawCenter(canvas: Canvas, big: String, bigSize: Float, caption: String) {
        text.color = Color.WHITE
        text.textSize = bigSize
        canvas.drawText(big, GameSpace.WIDTH / 2f, GameSpace.HEIGHT / 2f + bigSize * 0.35f, text)
        text.textSize = 64f
        canvas.drawText(caption, GameSpace.WIDTH / 2f, GameSpace.HEIGHT / 2f + bigSize * 0.35f + 110f, text)
    }

    private fun drawResults(canvas: Canvas) {
        fill.color = Color.argb(170, 0, 0, 0)
        canvas.drawRect(0f, 0f, GameSpace.WIDTH, GameSpace.HEIGHT, fill)
        text.color = Color.WHITE
        text.textSize = 140f
        canvas.drawText("Time's up!", GameSpace.WIDTH / 2f, 360f, text)

        val scores = logic.scores
        val best = scores.maxOrNull() ?: 0
        text.textSize = 84f
        scores.forEachIndexed { slot, score ->
            text.color = Palette.player(slot)
            val crown = if (best > 0 && score == best) "  ★" else ""
            canvas.drawText("Player ${slot + 1}: $score$crown", GameSpace.WIDTH / 2f, 520f + slot * 110f, text)
        }
    }
}
