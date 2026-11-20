package com.activekids.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.core.graphics.withSave
import com.activekids.core.NoticeLevel
import com.activekids.pose.PoseLandmarks
import kotlin.math.min

/** Draws the camera picture, skeletons and the current game, scaled to fit any screen. */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val engine: GameEngine) : View(context) {
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }
    private val joint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 3f, Color.argb(160, 0, 0, 0))
    }
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 30f
        color = Color.rgb(253, 224, 71)
    }
    private val dest = RectF()
    private val backgroundPaint = Paint().apply { color = Color.BLACK }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        engine.attach(this)
    }

    override fun onDetachedFromWindow() {
        engine.detach(this)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        val scale = min(width / GameSpace.WIDTH, height / GameSpace.HEIGHT)
        canvas.save()
        canvas.translate((width - GameSpace.WIDTH * scale) / 2f, (height - GameSpace.HEIGHT * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, GameSpace.WIDTH, GameSpace.HEIGHT)

        val s = engine.settings
        val frame = engine.currentFrame
        if (frame != null) {
            val t = engine.transformFor(frame)
            bitmapPaint.alpha = s.cameraBrightnessPercent * 255 / 100
            dest.set(t.destLeft, t.destTop, t.destRight, t.destBottom)
            canvas.withSave {
                if (t.mirror) scale(-1f, 1f, GameSpace.WIDTH / 2f, 0f)
                drawBitmap(frame.bitmap, null, dest, bitmapPaint)
            }

            if (s.showSkeleton) {
                for (p in engine.players) {
                    val color = Palette.player(p.slot)
                    bone.color = color
                    joint.color = color
                    val lm = p.landmarks
                    for ((a, b) in PoseLandmarks.CONNECTIONS) {
                        val la = lm.getOrNull(a) ?: continue
                        val lb = lm.getOrNull(b) ?: continue
                        if (la.visibility < s.landmarkVisibility || lb.visibility < s.landmarkVisibility) continue
                        canvas.drawLine(t.mapX(la.x), t.mapY(la.y), t.mapX(lb.x), t.mapY(lb.y), bone)
                    }
                    lm.getOrNull(PoseLandmarks.NOSE)?.takeIf { it.visibility >= s.landmarkVisibility }?.let {
                        canvas.drawCircle(t.mapX(it.x), t.mapY(it.y), 26f, joint)
                    }
                }
            }
        }

        val game = engine.game
        if (game != null) game.draw(canvas) else drawIdle(canvas, frame != null)
        drawFallbackBadges(canvas)
        canvas.restore()
    }

    private fun drawIdle(canvas: Canvas, hasCamera: Boolean) {
        val cx = GameSpace.WIDTH / 2f
        text.textSize = 150f
        canvas.drawText("Active Kids", cx, 430f, text)
        text.textSize = 64f
        val line = when {
            !hasCamera -> "Waiting for the camera…"
            engine.players.isEmpty() -> "Step in front of the camera!"
            else -> "${engine.players.size} player${if (engine.players.size == 1) "" else "s"} ready. Pick a game on the phone."
        }
        canvas.drawText(line, cx, 560f, text)
    }

    /** Degraded modes are always visible on the play screen, not only on the phone. */
    private fun drawFallbackBadges(canvas: Canvas) {
        val fallbacks = engine.notices.items.value.filter { it.fallback || it.level == NoticeLevel.ERROR }
        fallbacks.take(3).forEachIndexed { i, n ->
            canvas.drawText("⚠ ${n.message}", 24f, GameSpace.HEIGHT - 24f - i * 40f, badge)
        }
    }
}
