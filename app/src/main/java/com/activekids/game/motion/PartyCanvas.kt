package com.activekids.game.motion

import android.graphics.Canvas
import android.graphics.Paint
import com.activekids.game.GameSpace

internal class PartyCanvas {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun rect(canvas: Canvas, color: Int, l: Float, t: Float, r: Float, b: Float, corner: Float = 0f) {
        paint.color = color
        canvas.drawRoundRect(l, t, r, b, corner, corner, paint)
    }

    fun shade(canvas: Canvas, color: Int) = rect(canvas, color, 0f, 0f, GameSpace.WIDTH, GameSpace.HEIGHT)

    fun circle(canvas: Canvas, color: Int, point: Point, radius: Float) {
        paint.color = color
        canvas.drawCircle(point.x, point.y, radius, paint)
    }

    fun line(canvas: Canvas, color: Int, a: Point, b: Point, width: Float) {
        paint.color = color
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(a.x, a.y, b.x, b.y, paint)
    }

    fun bar(canvas: Canvas, left: Float, top: Float, width: Float, fraction: Float, color: Int) {
        rect(canvas, 0xB7182538.toInt(), left, top, left + width, top + 22f, 11f)
        if (fraction > 0f) rect(canvas, color, left, top, left + width * fraction.coerceAtMost(1f), top + 22f, 11f)
    }

    fun poseGuide(canvas: Canvas, pattern: ArmPattern, hip: Point, scale: Float, color: Int) {
        val shoulder = Point(hip.x + pattern.lean * scale, hip.y - scale)
        val width = scale * 0.11f
        val head = Point(shoulder.x, shoulder.y - scale * 0.33f)
        circle(canvas, color, head, width * 1.4f)
        line(canvas, color, shoulder, hip, width)
        for ((side, offset) in listOf(-1 to pattern.left, 1 to pattern.right)) {
            val joint = shoulder.copy(x = shoulder.x + side * scale * 0.35f)
            val hand = Point(shoulder.x + offset.x * scale, shoulder.y + offset.y * scale)
            line(canvas, color, shoulder, joint, width)
            line(canvas, color, joint, hand, width)
            circle(canvas, color, hand, width * 2f)
        }
    }
}
