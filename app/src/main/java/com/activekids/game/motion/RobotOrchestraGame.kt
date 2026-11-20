package com.activekids.game.motion

import android.graphics.Canvas
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.settings.AppSettings
import kotlin.math.abs

internal data class OrchestraNote(val pad: Int, val beat: Int, val arrives: Long)

internal class RobotOrchestraLogic(private val beatMs: Long, private val windowMs: Long) {
    private val leadMs = 2 * beatMs
    private var elapsed = 0L
    private var nextBeat = 0
    private val touching = BooleanArray(2)
    private val pending = mutableListOf<OrchestraNote>()
    val notes: List<OrchestraNote> get() = pending

    fun progress(note: OrchestraNote) = (1f - (note.arrives - elapsed).toFloat() / leadMs).coerceIn(0f, 1f)

    fun advance(dt: Long, contact: List<Boolean>): List<OrchestraNote> {
        elapsed += dt
        while (nextBeat * beatMs <= elapsed) {
            pending += OrchestraNote(nextBeat % 2, nextBeat, leadMs + nextBeat * beatMs)
            nextBeat++
        }
        pending.removeAll { elapsed - it.arrives > windowMs }
        val hits = mutableListOf<OrchestraNote>()
        contact.forEachIndexed { index, pressed ->
            if (pressed && !touching[index]) {
                val matched = pending.firstOrNull { it.pad == index && abs(elapsed - it.arrives) <= windowMs }
                if (matched != null) {
                    pending.remove(matched)
                    hits += matched
                }
            }
            touching[index] = pressed
        }
        return hits
    }
}

internal class RobotOrchestraGame(settings: AppSettings, sound: SoundFx) :
    TimedMotionGame(settings.copy(maxPlayers = settings.maxPlayers.coerceAtMost(2)), sound) {
    override val kind = GameKind.ROBOT_ORCHESTRA
    override val scoreSound = null
    private val rhythm = RobotOrchestraLogic(
        (settings.orchestraBeatSeconds * 1000).toLong(), (settings.orchestraWindowSeconds * 1000).toLong(),
    )
    private val radiusFraction = settings.orchestraPadPercent / 100f
    private val shapes = PartyCanvas()
    private var owners = emptyList<Int>()
    private var pads = emptyList<Pad>()
    private var hits = emptySet<Int>()
    private val colors = listOf(0xFFFFA86E.toInt(), 0xFFB0A2FF.toInt())
    private val instruments = listOf("Drums", "Melody")

    private data class Pad(val center: Point, val radius: Float, val pressed: Boolean)

    override fun readyToPlay(poses: Map<Int, MotionPose>): Boolean =
        if (owners.isEmpty()) poses.isNotEmpty() else owners.all(poses::containsKey)

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        if (owners.isEmpty()) {
            val leftToRight = poses.entries.sortedBy { it.value.hipCenter.x }.map { it.key }
            owners = listOf(leftToRight.first(), leftToRight.last())
        }
        pads = owners.mapIndexed { index, owner ->
            val pose = poses.getValue(owner)
            val center = pose.reachPoint(if (index == 0) -1 else 1)
            val radius = (pose.torsoHeight * radiusFraction).coerceAtMost(135f)
            Pad(center, radius, pose.handsNear(center, radius))
        }
        val notes = rhythm.advance(dtMs, pads.map { it.pressed })
        if (notes.isNotEmpty()) hits = notes.map { it.pad }.toSet()
        notes.forEach {
            scores[owners[it.pad]]++
            cue(when {
                it.pad == 0 -> SoundFx.Sfx.DRUM
                it.beat % 4 == 1 -> SoundFx.Sfx.NOTE_LOW
                else -> SoundFx.Sfx.NOTE_HIGH
            })
        }
        return notes.isNotEmpty()
    }

    override fun drawPlay(canvas: Canvas) {
        shapes.shade(canvas, 0x781A1533)
        label(canvas, "Tap each pad when a note reaches it", GameSpace.WIDTH / 2f, 246f, 62f)
        pads.forEachIndexed { index, pad ->
            val color = colors[index]
            val padText = if (owners.distinct().size == 2) "P${owners[index] + 1} ${instruments[index]}" else instruments[index]
            val upper = Point(pad.center.x, pad.center.y - 260f)
            shapes.line(canvas, 0x55FFFFFF, upper, pad.center, 8f)
            shapes.circle(canvas, color, pad.center, pad.radius)
            shapes.circle(canvas, if (pad.pressed) color else 0xFF262641.toInt(), pad.center, pad.radius * 0.76f)
            rhythm.notes.filter { it.pad == index }.forEach { note ->
                val progress = rhythm.progress(note)
                shapes.circle(canvas, 0xFFFFF5DC.toInt(),
                    pad.center.copy(y = upper.y + (pad.center.y - upper.y) * progress), pad.radius * 0.45f)
            }
            label(canvas, padText, pad.center.x, pad.center.y + pad.radius + 54f, 47f, color)
            if (pad.pressed && index in hits) label(canvas, "♪", pad.center.x, pad.center.y + 21f, 63f, 0xFF20203A.toInt())
        }
    }
}
