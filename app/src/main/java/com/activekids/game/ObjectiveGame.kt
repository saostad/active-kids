package com.activekids.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.activekids.audio.SoundFx
import com.activekids.game.motion.MirrorLeaderLogic
import com.activekids.game.motion.PenguinParadeLogic
import com.activekids.pose.FrameTransform
import com.activekids.pose.TrackedPlayer
import com.activekids.settings.AppSettings
import com.activekids.settings.GoalSetting

internal enum class RoundResult(val title: String, val reason: String) {
    WON("Goal reached!", "You won this round."),
    TIME_OUT("Round lost", "Time ran out before you reached the goal."),
    TOO_MANY_MISTAKES("Round lost", "You reached the mistake limit."),
    PLAYER_LEFT("Round lost", "A player stayed out of view too long."),
}

internal data class RoundGoal(val target: Int, val shared: Boolean, val mistakeLimit: Int?, val returnMs: Long)

internal class ObjectiveLogic(private val goal: RoundGoal) {
    var points = 0
        private set
    var remainingMistakes = goal.mistakeLimit
        private set
    var result: RoundResult? = null
        private set
    private var started = false
    private var waitingMs = 0L
    val returnSecondsLeft: Long? get() = if (waitingMs == 0L) null else (goal.returnMs - waitingMs + 999L) / 1000L

    fun advance(summary: GameSummary, dtMs: Long) {
        if (result != null) return
        if (summary.phase == "Playing") started = true
        waitingMs = if (started && summary.phase == "Waiting for player") {
            (waitingMs + dtMs).coerceAtMost(goal.returnMs)
        } else 0L
        points = if (goal.shared) summary.scores.sum() else summary.scores.maxOrNull() ?: 0
        remainingMistakes = goal.mistakeLimit?.let { (it - summary.mistakes).coerceAtLeast(0) }
        result = when {
            remainingMistakes == 0 -> RoundResult.TOO_MANY_MISTAKES
            points >= goal.target -> RoundResult.WON
            waitingMs >= goal.returnMs -> RoundResult.PLAYER_LEFT
            summary.secondsLeft == 0 -> RoundResult.TIME_OUT
            else -> null
        }
    }
}

internal fun GameKind.roundGoal(settings: AppSettings): RoundGoal {
    val duration = settings.roundSeconds * 1000L
    fun ms(seconds: Float) = (seconds * 1000).toLong()
    fun opportunities(first: Long, gap: Long) = if (first > duration) 1 else (1 + (duration - first) / gap).toInt()
    val limit = when (this) {
        GameKind.BUBBLE_POP -> ((settings.roundSeconds - settings.bubbleRiseSeconds / 2) * settings.bubblesPerSecond).toInt()
        GameKind.POSE_MATCH -> opportunities(ms(settings.poseHoldSeconds), settings.posePromptSeconds * 1000L)
        GameKind.TRAIL_DASH -> opportunities(ms(settings.runnerApproachSeconds), ms(settings.runnerSpacingSeconds))
        GameKind.FREEZE_DANCE -> (duration / ms(settings.danceSeconds + settings.freezeSeconds)).toInt()
        GameKind.PENGUIN_PARADE -> {
            val travel = ms(settings.penguinTravelSeconds)
            opportunities(travel, PenguinParadeLogic.rowSpacingMs(travel, settings.penguinSteerSpeed))
        }
        GameKind.METEOR_SHIELD -> opportunities(ms(settings.meteorTravelSeconds), ms(settings.meteorSpacingSeconds))
        GameKind.ROBOT_ORCHESTRA -> opportunities(ms(settings.orchestraBeatSeconds) * 2, ms(settings.orchestraBeatSeconds))
        GameKind.SHAPE_GATE -> (duration / ms(settings.gateTravelSeconds)).toInt()
        GameKind.TREASURE_TEAM -> (duration / ms(settings.treasureHoldSeconds)).toInt()
        GameKind.MIRROR_LEADER -> {
            val beats = duration / MirrorLeaderLogic.playableBeatMs(settings)
            val moves = MirrorLeaderLogic.MOVES
            (beats / (2 * moves) * moves + (beats % (2 * moves) - moves).coerceAtLeast(0)).toInt()
        }
        GameKind.STAR_REACH -> Int.MAX_VALUE
    }
    return RoundGoal(
        target = minOf(GoalSetting.valueOf(name).target(settings), limit.coerceAtLeast(1)),
        shared = this == GameKind.METEOR_SHIELD || this == GameKind.ROBOT_ORCHESTRA || this == GameKind.MIRROR_LEADER,
        mistakeLimit = when (this) {
            GameKind.TRAIL_DASH, GameKind.PENGUIN_PARADE, GameKind.METEOR_SHIELD -> settings.mistakeLimit
            else -> null
        },
        returnMs = settings.playerReturnSeconds * 1000L,
    )
}

internal class ObjectiveGame(private val game: Game, settings: AppSettings, private val sound: SoundFx) : Game {
    override val kind get() = game.kind
    private val goal = kind.roundGoal(settings)
    private val objective = ObjectiveLogic(goal)
    private var current = game.summary()
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF142238.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    override fun update(dtMs: Long, players: List<TrackedPlayer>, transform: FrameTransform?, settings: AppSettings) {
        if (objective.result != null) return
        game.update(dtMs, players, transform, settings)
        current = game.summary()
        objective.advance(current, dtMs)
        val result = objective.result
        if (result != null && settings.soundOn) {
            sound.play(if (result == RoundResult.WON) SoundFx.Sfx.FINISH else SoundFx.Sfx.LOSS)
        }
    }

    private val progress get() = "${if (goal.shared) "Team goal" else "Goal"}: ${objective.points} / ${goal.target}"
    private val lives get() = objective.remainingMistakes?.let { "Mistakes left: $it" }

    override fun summary() = current.copy(
        phase = objective.result?.title ?: current.phase,
        secondsLeft = if (objective.result == null) current.secondsLeft else 0,
        goal = listOfNotNull(progress, lives, returnPrompt).joinToString("  •  "),
    )

    override fun draw(canvas: Canvas) {
        val result = objective.result
        if (result == null) {
            game.draw(canvas)
            canvas.drawRoundRect(27f, 30f, 520f, 107f, 28f, 28f, panel)
            label(canvas, progress, 52f, 83f, 43f, Paint.Align.LEFT)
            lives?.let {
                canvas.drawRoundRect(1480f, 30f, 1890f, 107f, 28f, 28f, panel)
                label(canvas, it, 1865f, 83f, 43f, Paint.Align.RIGHT)
            }
            returnPrompt?.let { label(canvas, it, GameSpace.WIDTH / 2f, 815f, 65f) }
            return
        }

        val center = GameSpace.WIDTH / 2f
        canvas.drawRect(0f, 0f, GameSpace.WIDTH, GameSpace.HEIGHT, panel)
        label(canvas, kind.title, center, 204f, 58f)
        val color = if (result == RoundResult.WON) 0xFF91F8C0.toInt() else 0xFFFFBD82.toInt()
        label(canvas, result.title, center, 390f, 132f, color = color)
        label(canvas, result.reason, center, 510f, 65f)
        label(canvas, progress, center, 625f, 72f)
        current.scores.forEachIndexed { slot, score ->
            val x = GameSpace.WIDTH * (slot + 1) / (current.scores.size + 1)
            label(canvas, "P${slot + 1}: $score", x, 845f, 62f, color = Palette.player(slot))
        }
    }

    private val returnPrompt get() = objective.returnSecondsLeft?.let { "Return to the camera in ${it}s" }

    private fun label(
        canvas: Canvas, value: String, x: Float, y: Float, size: Float,
        align: Paint.Align = Paint.Align.CENTER, color: Int = Color.WHITE,
    ) {
        text.textAlign = align
        text.textSize = size
        text.color = color
        canvas.drawText(value, x, y, text)
    }
}
