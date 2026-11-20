package com.activekids.game.motion

import android.graphics.Canvas
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.game.Palette
import com.activekids.settings.AppSettings

internal enum class GateShape(val title: String, val pose: ArmPattern) {
    ARCH("Arch", ArmPattern(Point(-0.55f, -0.90f), Point(0.55f, -0.90f), 0f)),
    KITE_LEFT("Kite left", ArmPattern(Point(-0.86f, -0.65f), Point(0.96f, 0.45f), -0.20f)),
    WINGS("Wings", ArmPattern(Point(-1.22f, 0.07f), Point(1.22f, 0.07f), 0f)),
    KITE_RIGHT("Kite right", ArmPattern(Point(-0.96f, 0.45f), Point(0.86f, -0.65f), 0.20f)),
}

internal class ShapeGateLogic(private val travelMs: Long, private val tolerance: Float) {
    private var elapsed = 0L
    private var gate = 0
    val progress get() = elapsed.toFloat() / travelMs
    var lastCleared: Boolean? = null
        private set

    fun shape(index: Int): GateShape = GateShape.entries[(gate + index) % GateShape.entries.size]

    fun fits(index: Int, pattern: ArmPattern?) = pattern?.matches(shape(index).pose, tolerance) == true

    fun advance(dt: Long, patterns: List<ArmPattern?>): Boolean {
        elapsed += dt
        if (elapsed < travelMs) return false
        val cleared = patterns.isNotEmpty() && patterns.indices.all { fits(it, patterns[it]) }
        lastCleared = cleared
        elapsed %= travelMs
        gate = (gate + patterns.size.coerceAtLeast(1)) % GateShape.entries.size
        return cleared
    }
}

internal class ShapeGateGame(settings: AppSettings, sound: SoundFx) :
    TimedMotionGame(settings.copy(maxPlayers = settings.maxPlayers.coerceAtMost(2)), sound) {
    override val kind = GameKind.SHAPE_GATE
    private val gates = ShapeGateLogic((settings.gateTravelSeconds * 1000).toLong(), settings.gateTolerancePercent / 100f)
    private val shapes = PartyCanvas()
    private var roles: List<Int> = emptyList()
    private var tracked: Map<Int, MotionPose> = emptyMap()

    override fun readyToPlay(poses: Map<Int, MotionPose>) =
        if (roles.isEmpty()) poses.isNotEmpty() else roles.all(poses::containsKey)

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        if (roles.isEmpty()) roles = poses.entries.sortedBy { it.value.hipCenter.x }.map { it.key }
        tracked = poses
        val cleared = gates.advance(dtMs, roles.map { poses.getValue(it).armPattern() })
        if (cleared) roles.forEach { scores[it]++ }
        return cleared
    }

    override fun drawPlay(canvas: Canvas) {
        val center = GameSpace.WIDTH / 2f
        val width = 750f + gates.progress * 1130f
        val height = 440f + gates.progress * 330f
        shapes.shade(canvas, 0x66261A42)
        val l = center - width / 2f
        val r = center + width / 2f
        val t = 640f - height / 2f
        val b = 640f + height / 2f
        val color = 0xC4A58EFA.toInt()
        shapes.line(canvas, color, Point(l, t), Point(r, t), 20f)
        shapes.line(canvas, color, Point(l, t), Point(l, b), 20f)
        shapes.line(canvas, color, Point(r, t), Point(r, b), 20f)
        val cue = when (gates.lastCleared) {
            true -> "Gate cleared! Fit the next shape"
            false -> "Missed the last gate. Try this shape"
            null -> "Fit the shape before the gate arrives"
        }
        label(canvas, cue, center, 240f, 55f)
        shapes.bar(canvas, center - 400f, 275f, 800f, 1f - gates.progress, color)
        roles.forEachIndexed { index, slot ->
            val pose = tracked[slot] ?: return@forEachIndexed
            val shape = gates.shape(index)
            val fits = gates.fits(index, pose.armPattern())
            val guideColor = if (fits) 0xFF94FEC5.toInt() else Palette.player(slot)
            shapes.poseGuide(canvas, shape.pose, pose.hipCenter, pose.torsoHeight, guideColor)
            val prefix = if (roles.size == 2) "P${slot + 1}  " else ""
            label(canvas, prefix + shape.title + if (fits) " ✓" else "", GameSpace.WIDTH * (index + 1) / (roles.size + 1), 1028f, 56f, guideColor)
        }
    }
}
