package com.activekids.game.motion

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.settings.AppSettings
import kotlin.math.PI
import kotlin.math.sin

internal class TrailDashGame(settings: AppSettings, sound: SoundFx) : TimedMotionGame(settings.copy(maxPlayers = 1), sound) {
    override val kind = GameKind.TRAIL_DASH
    override val countdownCaption = "Stand in the middle"

    private val trail = TrailDashLogic(
        approachMs = (settings.runnerApproachSeconds * 1000f).toLong(),
        spacingMs = (settings.runnerSpacingSeconds * 1000f).toLong(),
        stepFraction = settings.runnerStepPercent / 100f,
        jumpFraction = settings.runnerJumpPercent / 100f,
        jumpMs = (settings.runnerJumpSeconds * 1000f).toLong(),
    )
    override val mistakes get() = trail.collisions
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val sky = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, 560f, 0xFF142840.toInt(), 0xFF527D78.toInt(), Shader.TileMode.CLAMP)
    }

    override fun readyToPlay(poses: Map<Int, MotionPose>): Boolean {
        val pose = poses[0] ?: return false
        trail.calibrate(pose.hipCenter, pose.torsoHeight)
        return true
    }

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        val cleared = trail.advance(dtMs, poses.getValue(0).hipCenter)
        scores[0] += cleared
        return cleared > 0
    }

    override fun drawPlay(canvas: Canvas) {
        drawTrack(canvas)
        for (obstacle in trail.obstacles.toList().asReversed()) {
            drawObstacle(canvas, obstacle)
        }
        drawRover(canvas)
        when (trail.outcome) {
            null -> label(canvas, "Hop over logs or move aside. Go around boulders.", CENTER, 235f, 50f)
            TrailOutcome.CLEARED -> label(canvas, "Clear! +1", CENTER, 235f, 58f, 0xFF8FFFD2.toInt())
            TrailOutcome.HIT -> label(canvas, "Hit! Keep going", CENTER, 235f, 58f, 0xFFFFCF95.toInt())
        }
    }

    private fun drawTrack(canvas: Canvas) {
        canvas.drawRect(0f, 0f, GameSpace.WIDTH, GameSpace.HEIGHT, sky)
        fill(0xFFFFD68D)
        canvas.drawCircle(1500f, 277f, 83f, paint)
        polygon(canvas, 0xFF386760, 0f, 455f, 300f, 310f, 590f, 435f, 900f, 320f, 1200f, 420f,
            1580f, 344f, GameSpace.WIDTH, 480f, GameSpace.WIDTH, GameSpace.HEIGHT, 0f, GameSpace.HEIGHT)
        fill(0xFF1A504B)
        canvas.drawRect(0f, 495f, GameSpace.WIDTH, GameSpace.HEIGHT, paint)
        for (i in 0..4) {
            val distance = i * 121f
            tree(canvas, 805f - distance, 517f + i * 119f, 65f + i * 43f)
            tree(canvas, 1115f + distance, 541f + i * 112f, 80f + i * 39f)
        }

        polygon(canvas, 0xFF2D3E4D,
            trackX(-1.5f, 0f), HORIZON, trackX(1.5f, 0f), HORIZON,
            trackX(1.5f, TRACK_END), GameSpace.HEIGHT, trackX(-1.5f, TRACK_END), GameSpace.HEIGHT)

        fill(0xFF89AB9A)
        for (edge in listOf(-1.5f, 1.5f)) {
            paint.strokeWidth = 8f
            canvas.drawLine(trackX(edge, 0f), HORIZON, trackX(edge, TRACK_END), GameSpace.HEIGHT, paint)
        }
        val offset = (trail.elapsedMs % 800L) / 800f
        for (divider in listOf(-0.5f, 0.5f)) {
            for (i in 0..5) {
                val from = (i + offset) / 5f
                val to = from + 0.075f
                paint.strokeWidth = 3f + from * 12f
                canvas.drawLine(trackX(divider, from), trackY(from), trackX(divider, to), trackY(to), paint)
            }
        }

        fill(0xFF6E807F)
        paint.strokeWidth = 4f
        canvas.drawLine(trackX(-1.5f, 1f), RUNNER_Y, trackX(1.5f, 1f), RUNNER_Y, paint)
    }

    private fun tree(canvas: Canvas, x: Float, y: Float, size: Float) {
        fill(0xFF184643)
        canvas.drawRect(x - size * 0.08f, y - size / 2f, x + size * 0.08f, y, paint)
        polygon(canvas, 0xFF25655C, x, y - size * 1.24f, x - size * 0.49f, y - size * 0.16f,
            x + size * 0.49f, y - size * 0.16f)
        polygon(canvas, 0xFF34776B, x, y - size * 1.24f, x - size * 0.38f, y - size * 0.42f,
            x + size * 0.36f, y - size * 0.42f)
    }

    private fun drawObstacle(canvas: Canvas, obstacle: TrailObstacle) {
        val progress = trail.progress(obstacle)
        val depth = progress * progress
        val x = trackX(obstacle.lane.offset, depth)
        val y = trackY(depth)
        val size = 22f + depth * 118f
        fill(0x66314247)
        canvas.drawOval(x - size, y - size * 0.16f, x + size, y + size * 0.16f, paint)
        when (obstacle.kind) {
            TrailObstacleKind.LOG -> {
                fill(0xFFC77537)
                canvas.drawRoundRect(x - size, y - size * 0.57f, x + size, y, size * 0.19f, size * 0.19f, paint)
                fill(0xFFFFC278)
                canvas.drawOval(x + size * 0.49f, y - size * 0.57f, x + size, y, paint)
                fill(0xFFA86238)
                canvas.drawOval(x + size * 0.62f, y - size * 0.42f, x + size * 0.88f, y - size * 0.15f, paint)
            }
            TrailObstacleKind.BOULDER -> {
                polygon(canvas, 0xFFB05056, x - size, y, x - size * 0.89f, y - size * 0.84f,
                    x - size * 0.32f, y - size * 1.52f, x + size * 0.47f, y - size * 1.46f,
                    x + size, y - size * 0.76f, x + size * 0.91f, y)
                polygon(canvas, 0xFFE88685, x - size * 0.89f, y - size * 0.84f,
                    x - size * 0.32f, y - size * 1.52f, x + size * 0.47f, y - size * 1.46f,
                    x + size * 0.31f, y - size * 0.83f)
            }
        }
    }

    private fun drawRover(canvas: Canvas) {
        val x = trackX(trail.lane.offset, 1f)
        val hop = if (trail.jumpProgress < 1f) sin(trail.jumpProgress * PI).toFloat() * 165f else 0f
        val y = RUNNER_Y - hop
        val stride = sin(trail.elapsedMs % 420L / 420.0 * 2.0 * PI).toFloat() * 15f
        fill(0xAA192B38)
        canvas.drawOval(x - 74f, RUNNER_Y - 11f, x + 74f, RUNNER_Y + 15f, paint)
        fill(0xFF9DF4D7)
        for (side in listOf(-1, 1)) {
            val footX = x + side * 41f
            val footY = y - 13f + side * stride
            canvas.drawRoundRect(footX - 22f, footY - 30f, footX + 22f, footY, 10f, 10f, paint)
        }
        canvas.drawRoundRect(x - 63f, y - 118f, x + 63f, y - 36f, 24f, 24f, paint)
        canvas.drawRoundRect(x - 84f, y - 201f, x + 84f, y - 95f, 35f, 35f, paint)
        fill(0xFF16364C)
        canvas.drawRoundRect(x - 64f, y - 175f, x + 64f, y - 117f, 19f, 19f, paint)
        fill(0xFFFFDB89)
        canvas.drawCircle(x - 26f, y - 146f, 11f, paint)
        canvas.drawCircle(x + 26f, y - 146f, 11f, paint)
        canvas.drawCircle(x, y - 70f, 10f, paint)
    }

    private val TrailLane.offset get() = (ordinal - TrailLane.CENTER.ordinal).toFloat()
    private fun trackX(laneOffset: Float, depth: Float) = CENTER + laneOffset * (49f + 380f * depth)
    private fun trackY(depth: Float) = HORIZON + (RUNNER_Y - HORIZON) * depth

    private fun fill(color: Long) {
        paint.color = color.toInt()
    }

    private fun polygon(canvas: Canvas, color: Long, vararg points: Float) {
        path.rewind()
        path.moveTo(points[0], points[1])
        for (index in 2 until points.size step 2) path.lineTo(points[index], points[index + 1])
        path.close()
        fill(color)
        canvas.drawPath(path, paint)
    }

    private companion object {
        const val CENTER = GameSpace.WIDTH / 2f
        const val HORIZON = 443f
        const val RUNNER_Y = 953f
        const val TRACK_END = (GameSpace.HEIGHT - HORIZON) / (RUNNER_Y - HORIZON)
    }
}
