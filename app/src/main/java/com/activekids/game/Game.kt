package com.activekids.game

import android.graphics.Canvas
import com.activekids.audio.SoundFx
import com.activekids.game.bubblepop.BubblePopGame
import com.activekids.game.bubblepop.HandPoint
import com.activekids.game.motion.FreezeDanceGame
import com.activekids.game.motion.MeteorShieldGame
import com.activekids.game.motion.MirrorLeaderGame
import com.activekids.game.motion.PenguinParadeGame
import com.activekids.game.motion.PoseMatchGame
import com.activekids.game.motion.RobotOrchestraGame
import com.activekids.game.motion.ShapeGateGame
import com.activekids.game.motion.StarReachGame
import com.activekids.game.motion.TrailDashGame
import com.activekids.game.motion.TreasureTeamGame
import com.activekids.pose.FrameTransform
import com.activekids.pose.PoseLandmarks
import com.activekids.pose.TrackedPlayer
import com.activekids.settings.AppSettings

/** Games draw into a fixed 16:9 space; each screen scales it to fit (letterboxed). */
object GameSpace {
    const val WIDTH = 1920f
    const val HEIGHT = 1080f
}

data class GameSummary(
    val phase: String,
    val secondsLeft: Int,
    val scores: List<Int>,
    val mistakes: Int = 0,
    val goal: String? = null,
)

interface Game {
    val kind: GameKind
    fun update(dtMs: Long, players: List<TrackedPlayer>, transform: FrameTransform?, settings: AppSettings)
    fun draw(canvas: Canvas)
    fun summary(): GameSummary
}

enum class GameKind(val title: String, val description: String) {
    BUBBLE_POP("Bubble Pop", "Bubbles float up. Pop them with your hands!"),
    STAR_REACH("Star Reach", "Reach for stars in your player color."),
    POSE_MATCH("Pose Match", "Copy the pose on screen and hold it to score."),
    TRAIL_DASH("Trail Dash", "Solo. Start centered, step sideways to change lanes, and hop over logs."),
    FREEZE_DANCE("Freeze Dance", "1–4 players. Dance to the beat and hold still when it stops."),
    PENGUIN_PARADE("Penguin Parade", "Solo. Keep your feet planted and lean to steer toward fish."),
    METEOR_SHIELD("Meteor Shield", "1–2 players. Block meteors with your hands before they hit the moon base."),
    ROBOT_ORCHESTRA("Robot Orchestra", "1–2 players. Tap the drum and melody pads as notes arrive."),
    SHAPE_GATE("Shape Gate", "1–2 players. Fit the glowing outline before the gate arrives."),
    TREASURE_TEAM("Treasure Team", "Two players. Hold the lever while your teammate grabs the treasure."),
    MIRROR_LEADER("Mirror Leader", "2–4 players. Record three moves for your friends to copy, then swap leaders.");

    fun create(settings: AppSettings, sound: SoundFx): Game {
        val game = when (this) {
            BUBBLE_POP -> BubblePopGame(settings, sound)
            STAR_REACH -> StarReachGame(settings, sound)
            POSE_MATCH -> PoseMatchGame(settings, sound)
            TRAIL_DASH -> TrailDashGame(settings, sound)
            FREEZE_DANCE -> FreezeDanceGame(settings, sound)
            PENGUIN_PARADE -> PenguinParadeGame(settings, sound)
            METEOR_SHIELD -> MeteorShieldGame(settings, sound)
            ROBOT_ORCHESTRA -> RobotOrchestraGame(settings, sound)
            SHAPE_GATE -> ShapeGateGame(settings, sound)
            TREASURE_TEAM -> TreasureTeamGame(settings, sound)
            MIRROR_LEADER -> MirrorLeaderGame(settings, sound)
        }
        return ObjectiveGame(game, settings, sound)
    }
}

object Hands {
    private val PAIRS = listOf(
        PoseLandmarks.LEFT_INDEX to PoseLandmarks.LEFT_WRIST,
        PoseLandmarks.RIGHT_INDEX to PoseLandmarks.RIGHT_WRIST,
    )

    /** One point per visible hand: the index fingertip if visible, otherwise the wrist. */
    fun points(player: TrackedPlayer, transform: FrameTransform, minVisibility: Float): List<HandPoint> =
        PAIRS.mapNotNull { (tip, wrist) ->
            val lm = player.landmarks
            val chosen = lm.getOrNull(tip)?.takeIf { it.visibility >= minVisibility }
                ?: lm.getOrNull(wrist)?.takeIf { it.visibility >= minVisibility }
            chosen?.let { HandPoint(player.slot, transform.mapX(it.x), transform.mapY(it.y)) }
        }
}
