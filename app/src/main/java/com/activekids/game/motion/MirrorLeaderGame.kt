package com.activekids.game.motion

import android.graphics.Canvas
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.game.Palette
import com.activekids.settings.AppSettings

internal class MirrorLeaderLogic(private val beatMs: Long, private val tolerance: Float) {
    private val captured = mutableListOf<ArmPattern?>()
    private var elapsed = 0L
    var leader: Int? = null
        private set
    var step = 0
        private set
    var copying = false
        private set
    val reference get() = if (copying) captured[step] else null
    val remaining get() = 1f - elapsed.toFloat() / beatMs

    fun advance(dt: Long, poses: Map<Int, ArmPattern?>): List<Int> {
        if (poses.size < 2) return emptyList()
        if (leader == null) leader = poses.keys.min()
        elapsed += dt
        if (elapsed < beatMs) return emptyList()
        elapsed %= beatMs
        val winners = if (copying) {
            val expected = reference
            if (expected == null) emptyList() else poses.entries.filter { (slot, pose) ->
                slot != leader && pose?.matches(expected, tolerance) == true
            }.map { it.key }
        } else {
            captured += poses[leader]
            emptyList()
        }
        step++
        if (step == MOVES) {
            step = 0
            if (copying) {
                captured.clear()
                val roster = poses.keys.sorted()
                leader = roster.firstOrNull { it > checkNotNull(leader) } ?: roster.first()
            }
            copying = !copying
        }
        return winners
    }

    companion object {
        const val MOVES = 3

        fun playableBeatMs(settings: AppSettings): Long = minOf(
            (settings.mirrorBeatSeconds * 1000).toLong(),
            settings.roundSeconds * 1000L / (2 * MOVES),
        )
    }
}

internal class MirrorLeaderGame(settings: AppSettings, sound: SoundFx) : TimedMotionGame(settings, sound) {
    override val kind = GameKind.MIRROR_LEADER
    override val waitingTitle = "Two players needed"
    override val waitingCaption = if (settings.maxPlayers < 2) "Set Players at once to 2 or more in Settings"
        else "Both players step into view"
    private val acts = MirrorLeaderLogic(MirrorLeaderLogic.playableBeatMs(settings), settings.mirrorMatchPercent / 100f)
    private val shapes = PartyCanvas()
    private var lastLead: ArmPattern? = null
    private var winners = emptyList<Int>()

    override fun readyToPlay(poses: Map<Int, MotionPose>) = poses.size >= 2

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        val positions = poses.mapValues { it.value.armPattern() }
        val previousStep = acts.step
        val wasCopying = acts.copying
        val matches = acts.advance(dtMs, positions)
        lastLead = positions[acts.leader]
        matches.forEach { scores[it]++ }
        if (previousStep != acts.step || wasCopying != acts.copying) {
            winners = matches
            if (matches.isEmpty()) cue(SoundFx.Sfx.TICK)
        }
        return matches.isNotEmpty()
    }

    override fun drawPlay(canvas: Canvas) {
        val center = GameSpace.WIDTH / 2f
        val slot = acts.leader ?: return
        val color = Palette.player(slot)
        shapes.shade(canvas, 0x76201D38)
        val step = "${acts.step + 1}/${MirrorLeaderLogic.MOVES}"
        val headline = if (acts.copying) "Everyone else: copy  $step" else "P${slot + 1}: make a move  $step"
        label(canvas, headline, center, 244f, 63f, color)
        shapes.bar(canvas, center - 375f, 272f, 750f, acts.remaining, color)
        val pose = if (acts.copying) acts.reference else lastLead
        val caption = when {
            acts.copying && pose == null -> "Move missed. Get ready for the next one."
            !acts.copying && pose == null -> "Leader, keep both hands visible"
            winners.isNotEmpty() -> "+1 for " + winners.joinToString { "P${it + 1}" }
            acts.copying -> "Match the outline when the bar ends"
            else -> "Hold your move until the beat"
        }
        if (pose != null) {
            shapes.rect(canvas, 0xCF1B2341.toInt(), 612f, 323f, 1308f, 945f, 52f)
            shapes.poseGuide(canvas, pose, Point(center, 742f), 165f, color)
        }
        label(canvas, caption, center, 1025f, 49f)
    }
}
