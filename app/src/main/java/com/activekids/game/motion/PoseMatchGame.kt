package com.activekids.game.motion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.game.Palette
import com.activekids.settings.AppSettings
import kotlin.math.abs

internal enum class PosePrompt(val label: String, val demoHand: Point) {
    HANDS_UP("Hands up", Point(44f, -70f)),
    ARMS_OUT("Arms out", Point(72f, 0f)),
    HANDS_LOW("Hands low", Point(33f, 62f));

    fun matches(pose: MotionPose, tolerance: Float): Boolean {
        val left = pose.left.hand ?: return false
        val right = pose.right.hand ?: return false
        val leftDrop = left.y - pose.left.shoulder.y
        val rightDrop = right.y - pose.right.shoulder.y
        val minExtension = pose.torsoHeight * (0.5f - tolerance)
        return when (this) {
            HANDS_UP -> leftDrop <= -minExtension && rightDrop <= -minExtension
            ARMS_OUT -> abs(leftDrop) <= pose.torsoHeight * tolerance &&
                abs(rightDrop) <= pose.torsoHeight * tolerance &&
                pose.left.shoulder.x - left.x >= minExtension && right.x - pose.right.shoulder.x >= minExtension
            HANDS_LOW -> leftDrop >= pose.torsoHeight * (1f - tolerance) &&
                rightDrop >= pose.torsoHeight * (1f - tolerance)
        }
    }
}

internal class PoseMatchLogic(
    private val promptMs: Long,
    private val holdMs: Long,
    private val tolerance: Float,
    private val scores: IntArray,
) {
    private var elapsedMs = 0L
    private var index = 0
    private val heldMs = LongArray(scores.size)
    val prompt: PosePrompt get() = PosePrompt.entries[index]

    fun progress(slot: Int): Float = heldMs[slot] / holdMs.toFloat()

    fun update(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        var scored = false
        val step = dtMs.coerceAtMost(promptMs - elapsedMs)
        for (slot in heldMs.indices) {
            if (heldMs[slot] == holdMs) continue
            val pose = poses[slot]
            if (pose == null || !prompt.matches(pose, tolerance)) {
                heldMs[slot] = 0
                continue
            }
            heldMs[slot] = (heldMs[slot] + step).coerceAtMost(holdMs)
            if (heldMs[slot] == holdMs) {
                scores[slot]++
                scored = true
            }
        }

        elapsedMs += dtMs
        if (elapsedMs >= promptMs) {
            index = ((index + elapsedMs / promptMs) % PosePrompt.entries.size).toInt()
            elapsedMs %= promptMs
            heldMs.fill(0)
        }
        return scored
    }
}

internal class PoseMatchGame(settings: AppSettings, sound: SoundFx) : TimedMotionGame(settings, sound) {
    override val kind = GameKind.POSE_MATCH

    private val logic = PoseMatchLogic(
        promptMs = settings.posePromptSeconds * 1000L,
        holdMs = (settings.poseHoldSeconds * 1000).toLong(),
        tolerance = settings.poseTolerancePercent / 100f,
        scores = scores,
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND
    }

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>) = logic.update(dtMs, poses)

    override fun drawPlay(canvas: Canvas) {
        for (slot in scores.indices) {
            val x = GameSpace.WIDTH * (slot + 1) / (scores.size + 1)
            paint.color = 0xCC131A26.toInt()
            canvas.drawRoundRect(x - 96f, 175f, x + 96f, 192f, 8f, 8f, paint)
            paint.color = Palette.player(slot)
            val progress = logic.progress(slot)
            if (progress > 0f) {
                canvas.drawRoundRect(x - 96f, 175f, x - 96f + progress * 192f, 192f, 8f, 8f, paint)
            }
        }

        val center = GameSpace.WIDTH / 2f
        paint.color = 0xDA131A26.toInt()
        canvas.drawRoundRect(center - 365f, 225f, center + 365f, 415f, 32f, 32f, paint)
        drawGuide(canvas, center - 242f, 322f)
        label(canvas, logic.prompt.label, center + 73f, 326f, 70f)
        label(canvas, "Hold it to score", center + 73f, 383f, 42f)
    }

    private fun drawGuide(canvas: Canvas, x: Float, y: Float) {
        val hand = logic.prompt.demoHand
        paint.color = Color.WHITE
        canvas.drawCircle(x, y - 47f, 15f, paint)
        canvas.drawLine(x, y - 21f, x, y + 25f, paint)
        canvas.drawLine(x - 16f, y, x + 16f, y, paint)
        for (side in listOf(-1, 1)) {
            canvas.drawLine(x + side * 16f, y, x + side * hand.x, y + hand.y, paint)
            canvas.drawLine(x, y + 25f, x + side * 23f, y + 63f, paint)
        }
    }
}
