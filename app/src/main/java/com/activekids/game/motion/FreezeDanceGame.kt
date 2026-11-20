package com.activekids.game.motion

import android.graphics.Canvas
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.game.Palette
import com.activekids.settings.AppSettings

internal class FreezeDanceLogic(
    private val danceMs: Long, private val freezeMs: Long, private val tolerance: Float, private val graceMs: Long,
) {
    private val eligible = mutableMapOf<Int, MotionPose>()
    private val lastVisible = mutableMapOf<Int, Pair<Long, MotionPose>>()
    val stillPlayers: Set<Int> get() = eligible.keys
    var freezing = false
        private set
    private var elapsed = 0L
    private var clock = 0L
    val remaining get() = 1f - elapsed.toFloat() / if (freezing) freezeMs else danceMs
    val beat get() = (elapsed * 8 / danceMs).toInt()

    fun advance(dt: Long, poses: Map<Int, MotionPose>): List<Int> {
        clock += dt
        val visible = poses.filterValues { it.left.hand != null && it.right.hand != null }
        visible.forEach { (slot, pose) -> lastVisible[slot] = clock to pose }
        lastVisible.entries.removeAll { clock - it.value.first > graceMs }
        elapsed += dt
        if (freezing) {
            eligible.entries.removeAll { (slot, starting) ->
                val current = visible[slot]
                val limit = starting.torsoHeight * tolerance
                if (current == null) slot !in lastVisible else current.hipCenter.distanceTo(starting.hipCenter) > limit ||
                    current.left.shoulder.distanceTo(starting.left.shoulder) > limit ||
                    current.right.shoulder.distanceTo(starting.right.shoulder) > limit ||
                    !handStayed(current.left.hand, starting.left.hand, limit) ||
                    !handStayed(current.right.hand, starting.right.hand, limit)
            }
            if (elapsed < freezeMs) return emptyList()
            val winners = eligible.keys.toList()
            eligible.clear()
            elapsed -= freezeMs
            freezing = false
            return winners
        }
        if (elapsed >= danceMs) {
            freezing = true
            elapsed -= danceMs
            eligible.putAll(lastVisible.mapValues { it.value.second })
        }
        return emptyList()
    }

    private fun handStayed(current: Point?, starting: Point?, limit: Float) =
        current != null && starting != null && current.distanceTo(starting) <= limit
}

internal class FreezeDanceGame(settings: AppSettings, sound: SoundFx) : TimedMotionGame(settings, sound) {
    override val kind = GameKind.FREEZE_DANCE
    override val scoreSound = null
    private val dance = FreezeDanceLogic(
        (settings.danceSeconds * 1000).toLong(), (settings.freezeSeconds * 1000).toLong(), settings.freezeTolerancePercent / 100f,
        (settings.freezeTrackingGraceSeconds * 1000).toLong(),
    )
    private val shapes = PartyCanvas()
    private var started = false
    private var lastBeat = -1
    private var winners = emptyList<Int>()
    private val melody = listOf(
        SoundFx.Sfx.DRUM, SoundFx.Sfx.NOTE_LOW, SoundFx.Sfx.DRUM, SoundFx.Sfx.NOTE_HIGH,
        SoundFx.Sfx.DRUM, SoundFx.Sfx.NOTE_HIGH, SoundFx.Sfx.DRUM, SoundFx.Sfx.NOTE_LOW,
    )

    override fun readyToPlay(poses: Map<Int, MotionPose>) = (started || poses.isNotEmpty()).also { started = it }

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        val wasFrozen = dance.freezing
        val awarded = dance.advance(dtMs, poses)
        if (dance.freezing) {
            if (!wasFrozen) cue(SoundFx.Sfx.FREEZE)
            lastBeat = -1
        } else {
            if (wasFrozen) winners = awarded
            if (dance.beat != lastBeat) {
                lastBeat = dance.beat
                cue(melody[lastBeat])
            }
        }
        awarded.forEach { scores[it]++ }
        return awarded.isNotEmpty()
    }

    override fun drawPlay(canvas: Canvas) {
        val cx = GameSpace.WIDTH / 2f
        val color = if (dance.freezing) 0xFF8FE7FF.toInt() else 0xFFFFCB74.toInt()
        shapes.rect(canvas, 0xD6202844.toInt(), 420f, 270f, 1500f, 710f, 62f)
        label(canvas, if (dance.freezing) "FREEZE" else "DANCE", cx, 493f, 172f, color)
        val subtitle = when {
            dance.freezing -> "Hold still until the bar empties"
            winners.isEmpty() -> "Move to the beat!"
            else -> "+1 for " + winners.joinToString { "P${it + 1}" }
        }
        label(canvas, subtitle, cx, 594f, 59f)
        shapes.bar(canvas, 540f, 631f, 840f, dance.remaining, color)
        if (dance.freezing) {
            dance.stillPlayers.forEach { slot ->
                label(canvas, "P${slot + 1} still", 250f + slot * 470f, 840f, 65f, Palette.player(slot))
            }
        }
    }
}
