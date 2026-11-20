package com.activekids.game.motion

import android.graphics.Canvas
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.game.Palette
import com.activekids.settings.AppSettings
import kotlin.random.Random

internal data class Meteor(val slot: Int, val x: Float, val impact: Long)

internal class MeteorShieldLogic(
    private val travelMs: Long, private val spacingMs: Long, val radius: Float,
    private val random: Random = Random.Default,
) {
    private val pending = mutableListOf<Meteor>()
    val meteors: List<Meteor> get() = pending
    private var elapsed = 0L
    private var nextImpact = travelMs
    private var nextOwner = 0
    var baseHits = 0
        private set

    fun center(meteor: Meteor): Point {
        val progress = 1f - (meteor.impact - elapsed).toFloat() / travelMs
        return Point(meteor.x, -radius + (BASE_Y + radius) * progress.coerceIn(0f, 1f))
    }

    fun advance(dt: Long, zones: Map<Int, ClosedFloatingPointRange<Float>>, hands: Map<Int, List<Point>>): List<Int> {
        if (zones.isEmpty()) return emptyList()
        elapsed += dt
        while (nextImpact - travelMs <= elapsed) {
            val (slot, range) = zones.entries.elementAt(nextOwner % zones.size)
            pending += Meteor(slot, range.start + random.nextFloat() * (range.endInclusive - range.start), nextImpact)
            nextOwner++
            nextImpact += spacingMs
        }
        val cleared = mutableListOf<Int>()
        pending.removeAll { meteor ->
            val point = center(meteor)
            if (elapsed < meteor.impact && hands[meteor.slot].orEmpty().any { it.distanceTo(point) <= radius }) {
                cleared += meteor.slot
                true
            } else if (elapsed >= meteor.impact) {
                baseHits++
                true
            } else false
        }
        return cleared
    }

    companion object {
        const val BASE_Y = 975f
    }
}

internal class MeteorShieldGame(settings: AppSettings, sound: SoundFx) :
    TimedMotionGame(settings.copy(maxPlayers = settings.maxPlayers.coerceAtMost(2)), sound) {
    override val kind = GameKind.METEOR_SHIELD
    private val field = MeteorShieldLogic(
        (settings.meteorTravelSeconds * 1000).toLong(), (settings.meteorSpacingSeconds * 1000).toLong(),
        GameSpace.HEIGHT * settings.meteorRadiusPercent / 100f,
    )
    override val mistakes get() = field.baseHits
    private val shapes = PartyCanvas()
    private var roles: List<Int> = emptyList()

    override fun readyToPlay(poses: Map<Int, MotionPose>): Boolean =
        if (roles.isEmpty()) poses.isNotEmpty() else roles.all(poses::containsKey)

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        if (roles.isEmpty()) roles = poses.entries.sortedBy { it.value.hipCenter.x }.map { it.key }
        val zones = roles.mapIndexed { index, slot ->
            val pose = poses.getValue(slot)
            val inset = field.radius
            val zoneWidth = GameSpace.WIDTH / roles.size
            val min = index * zoneWidth + inset
            val max = (index + 1) * zoneWidth - inset
            val x = pose.shoulderCenter.x
            slot to ((x - pose.torsoHeight).coerceIn(min, max)..(x + pose.torsoHeight).coerceIn(min, max))
        }.toMap()
        val hands = poses.mapValues { (_, pose) -> listOfNotNull(pose.left.hand, pose.right.hand) }
        val cleared = field.advance(dtMs, zones, hands)
        cleared.forEach { scores[it]++ }
        return cleared.isNotEmpty()
    }

    override fun drawPlay(canvas: Canvas) {
        val center = GameSpace.WIDTH / 2f
        shapes.shade(canvas, 0x80201338.toInt())
        if (roles.size == 2) {
            shapes.line(canvas, 0x7799BBFF, Point(center, 245f), Point(center, MeteorShieldLogic.BASE_Y), 8f)
            roles.forEachIndexed { index, slot ->
                label(canvas, "P${slot + 1} shield zone", GameSpace.WIDTH * (index + 0.5f) / 2, 242f, 51f, Palette.player(slot))
            }
        } else label(canvas, "Block meteors with your hands", center, 242f, 56f)
        shapes.rect(canvas, 0xFF142847.toInt(), 0f, MeteorShieldLogic.BASE_Y, GameSpace.WIDTH, GameSpace.HEIGHT, 20f)
        label(canvas, "Moon base  •  Hits: ${field.baseHits}", center, 1049f, 52f)
        field.meteors.forEach { meteor ->
            val point = field.center(meteor)
            val color = Palette.player(meteor.slot)
            shapes.line(canvas, 0xA9F79A73.toInt(), point.copy(y = point.y - field.radius * 2.6f), point, field.radius)
            shapes.circle(canvas, color, point, field.radius)
            shapes.circle(canvas, 0xFFFAE8D6.toInt(), point, field.radius * 0.68f)
            shapes.circle(canvas, 0xFFD98C75.toInt(), Point(point.x - field.radius * 0.24f, point.y), field.radius * 0.17f)
        }
    }
}
