package com.activekids.game.motion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.ColorUtils
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.game.Palette
import com.activekids.settings.AppSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class StarTarget(val center: Point, val radius: Float) {
    fun reachedBy(pose: MotionPose): Boolean =
        pose.left.hand?.distanceTo(center)?.let { it <= radius } == true ||
            pose.right.hand?.distanceTo(center)?.let { it <= radius } == true
}

internal fun starTarget(pose: MotionPose, score: Int, sizeFraction: Float): StarTarget {
    val position = score % 4
    val onLeft = position == 0 || position == 3
    val high = position % 2 == 0
    val shoulder = if (onLeft) pose.left.shoulder else pose.right.shoulder
    val radius = (pose.torsoHeight * sizeFraction).coerceAtMost(GameSpace.HEIGHT / 2f)
    val dx = pose.torsoHeight * if (onLeft) -0.7f else 0.7f
    val dy = pose.torsoHeight * if (high) -0.6f else 0.6f
    return StarTarget(
        center = Point(
            (shoulder.x + dx).coerceIn(radius, GameSpace.WIDTH - radius),
            (shoulder.y + dy).coerceIn(radius, GameSpace.HEIGHT - radius),
        ),
        radius = radius,
    )
}

internal class StarReachGame(settings: AppSettings, sound: SoundFx) : TimedMotionGame(settings, sound) {
    override val kind = GameKind.STAR_REACH

    private val sizeFraction = settings.starSizePercent / 100f
    private val targets = arrayOfNulls<StarTarget>(scores.size)
    private val poses = arrayOfNulls<MotionPose>(scores.size)
    private val touchingTarget = BooleanArray(scores.size)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val star = Path()

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        var scored = false
        for (slot in scores.indices) {
            val pose = poses[slot]
            this.poses[slot] = pose
            if (pose == null) {
                targets[slot] = null
                continue
            }
            var target = starTarget(pose, scores[slot], sizeFraction)
            if (dtMs > 0) {
                if (!touchingTarget[slot] && target.reachedBy(pose)) {
                    scores[slot]++
                    scored = true
                    target = starTarget(pose, scores[slot], sizeFraction)
                }
                // A wrist already inside the next star has to leave before it can score again.
                touchingTarget[slot] = target.reachedBy(pose)
            }
            targets[slot] = target
        }
        return scored
    }

    override fun drawPlay(canvas: Canvas) {
        if (targets.all { it == null }) {
            label(canvas, "Show your shoulders and hips", GameSpace.WIDTH / 2f, 325f, 65f)
            return
        }
        targets.forEachIndexed { slot, target ->
            if (target == null) return@forEachIndexed
            val color = Palette.player(slot)
            val (x, y) = target.center
            val radius = target.radius
            paint.style = Paint.Style.FILL
            paint.color = ColorUtils.setAlphaComponent(color, 85)
            canvas.drawCircle(x, y, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = radius * 0.08f
            paint.color = color
            canvas.drawCircle(x, y, radius, paint)

            star.rewind()
            for (i in 0 until 10) {
                val angle = i * PI / 5 - PI / 2
                val vertexRadius = radius * if (i % 2 == 0) 0.85f else 0.38f
                val vx = x + cos(angle).toFloat() * vertexRadius
                val vy = y + sin(angle).toFloat() * vertexRadius
                if (i == 0) star.moveTo(vx, vy) else star.lineTo(vx, vy)
            }
            star.close()
            paint.style = Paint.Style.FILL
            canvas.drawPath(star, paint)

            val pose = poses[slot] ?: return@forEachIndexed
            for (hand in listOfNotNull(pose.left.hand, pose.right.hand)) {
                canvas.drawCircle(hand.x, hand.y, 18f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(hand.x, hand.y, 7f, paint)
                paint.color = color
            }
        }
    }
}
