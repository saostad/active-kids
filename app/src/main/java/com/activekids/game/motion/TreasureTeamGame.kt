package com.activekids.game.motion

import android.graphics.Canvas
import com.activekids.audio.SoundFx
import com.activekids.game.GameKind
import com.activekids.game.GameSpace
import com.activekids.game.Palette
import com.activekids.settings.AppSettings

internal class TreasureTeamLogic(private val holdMs: Long) {
    private var heldMs = 0L
    private var chestWasTouched = false
    var leverPlayer = 0
        private set
    var finds = 0
        private set
    val progress get() = heldMs.toFloat() / holdMs
    val unlocked get() = heldMs == holdMs

    fun advance(dt: Long, leverTouched: Boolean, chestTouched: Boolean): Boolean {
        heldMs = if (leverTouched) (heldMs + dt).coerceAtMost(holdMs) else 0L
        val claimed = unlocked && chestTouched && !chestWasTouched
        chestWasTouched = chestTouched
        if (claimed) {
            heldMs = 0
            finds++
            leverPlayer = 1 - leverPlayer
        }
        return claimed
    }
}

internal class TreasureTeamGame(settings: AppSettings, sound: SoundFx) : TimedMotionGame(settings.copy(maxPlayers = 2), sound) {
    override val kind = GameKind.TREASURE_TEAM
    override val waitingTitle = "Two players needed"
    override val waitingCaption = if (settings.maxPlayers < 2) "Set Players at once to 2 or more in Settings"
        else "Both players step into view"
    private val treasure = TreasureTeamLogic((settings.treasureHoldSeconds * 1000).toLong())
    private val radiusFraction = settings.treasureReachPercent / 100f
    private val shapes = PartyCanvas()
    private var lever: Target? = null
    private var chest: Target? = null

    private data class Target(val slot: Int, val point: Point, val radius: Float, val touched: Boolean)

    override fun readyToPlay(poses: Map<Int, MotionPose>): Boolean {
        val ready = poses.containsKey(0) && poses.containsKey(1)
        if (!ready) treasure.advance(0, leverTouched = false, chestTouched = false)
        return ready
    }

    override fun updatePlay(dtMs: Long, poses: Map<Int, MotionPose>): Boolean {
        locateTargets(poses)
        val claimed = treasure.advance(dtMs, lever?.touched == true, chest?.touched == true)
        if (claimed) {
            scores[0]++
            scores[1]++
            locateTargets(poses)
        }
        return claimed
    }

    private fun locateTargets(poses: Map<Int, MotionPose>) {
        fun target(slot: Int): Target {
            val pose = poses.getValue(slot)
            val other = poses.getValue(1 - slot)
            val side = if (pose.hipCenter.x <= other.hipCenter.x) -1 else 1
            val point = pose.reachPoint(side)
            val radius = (pose.torsoHeight * radiusFraction).coerceAtMost(135f)
            return Target(slot, point, radius, pose.handsNear(point, radius))
        }
        lever = target(treasure.leverPlayer)
        chest = target(1 - treasure.leverPlayer)
    }

    override fun drawPlay(canvas: Canvas) {
        val l = lever ?: return
        val c = chest ?: return
        val center = GameSpace.WIDTH / 2f
        shapes.shade(canvas, 0x60141634)
        label(canvas, if (treasure.unlocked) "Keep holding  •  Grab the treasure!" else "Hold the lever to unlock the chest", center, 250f, 54f)
        label(canvas, "Treasure found: ${treasure.finds}", center, 318f, 49f, 0xFFFFCF70.toInt())
        shapes.line(canvas, 0x88FFD497.toInt(), l.point, c.point, 9f)

        val leverColor = Palette.player(l.slot)
        shapes.circle(canvas, leverColor, l.point, l.radius)
        shapes.line(canvas, 0xFF253655.toInt(), l.point.copy(y = l.point.y + l.radius * 0.55f),
            l.point.copy(y = l.point.y + l.radius * if (l.touched) 0.12f else -0.57f), l.radius * 0.19f)
        shapes.bar(canvas, l.point.x - l.radius, l.point.y + l.radius + 20f, l.radius * 2, treasure.progress, leverColor)
        label(canvas, "P${l.slot + 1} HOLD", l.point.x, l.point.y + l.radius + 95f, 46f, leverColor)

        val chestColor = if (treasure.unlocked) 0xFFFFCD68.toInt() else 0xFF937993.toInt()
        shapes.rect(canvas, chestColor, c.point.x - c.radius, c.point.y - c.radius * 0.67f,
            c.point.x + c.radius, c.point.y + c.radius * 0.67f, c.radius * 0.22f)
        shapes.line(canvas, 0xFF54394D.toInt(), c.point.copy(x = c.point.x - c.radius), c.point.copy(x = c.point.x + c.radius), c.radius * 0.14f)
        shapes.circle(canvas, 0xFFFFF1BC.toInt(), c.point, c.radius * 0.21f)
        label(canvas, "P${c.slot + 1} ${if (treasure.unlocked) "GRAB" else "LOCKED"}",
            c.point.x, c.point.y + c.radius + 95f, 46f, Palette.player(c.slot))
    }
}
