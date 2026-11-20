package com.activekids.game

import android.os.SystemClock
import android.view.Choreographer
import com.activekids.audio.SoundFx
import com.activekids.core.NoticeCenter
import com.activekids.pose.FrameTransform
import com.activekids.pose.PlayerTracker
import com.activekids.pose.PoseFrame
import com.activekids.pose.TrackedPlayer
import com.activekids.settings.AppSettings
import com.activekids.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicReference

data class EngineStatus(
    val trackingFps: Float = 0f,
    val latencyMs: Long = 0,
    val playersSeen: Int = 0,
    val game: GameKind? = null,
    val summary: GameSummary? = null,
)

/**
 * Runs the game loop once per display frame on the main thread, for every attached screen
 * (TV and/or phone). Pose frames arrive from the tracking thread via [submitFrame].
 */
class GameEngine(
    private val settingsRepo: SettingsRepository,
    private val sound: SoundFx,
    val notices: NoticeCenter,
) : Choreographer.FrameCallback {
    private val latest = AtomicReference<PoseFrame?>(null)
    private val tracker = PlayerTracker()
    private val views = LinkedHashSet<GameView>()
    private var running = false
    private var lastFrameNanos = 0L
    private var lastFrameTs = Long.MIN_VALUE

    var currentFrame: PoseFrame? = null
        private set
    var players: List<TrackedPlayer> = emptyList()
        private set
    var game: Game? = null
        private set
    val settings: AppSettings get() = settingsRepo.settings.value

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status
    private var statsStartMs = 0L
    private var statsFrames = 0

    /** Thread-safe; called from the tracking thread. */
    fun submitFrame(frame: PoseFrame) = latest.set(frame)

    fun startGame(kind: GameKind) {
        tracker.reset()
        game = kind.create(settings, sound)
        publishStatus(_status.value.trackingFps)
    }

    fun stopGame() {
        game = null
        publishStatus(_status.value.trackingFps)
    }

    fun transformFor(frame: PoseFrame) =
        FrameTransform(GameSpace.WIDTH, GameSpace.HEIGHT, frame.width.toFloat(), frame.height.toFloat(), settings.mirrorView)

    internal fun attach(view: GameView) {
        views += view
        if (!running) {
            running = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    internal fun detach(view: GameView) {
        views -= view
        if (views.isEmpty() && running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dtMs = if (lastFrameNanos == 0L) 16L else ((frameTimeNanos - lastFrameNanos) / 1_000_000L).coerceIn(0L, MAX_STEP_MS)
        lastFrameNanos = frameTimeNanos
        val s = settings

        val frame = latest.get()
        if (frame != null && frame.timestampMs != lastFrameTs) {
            lastFrameTs = frame.timestampMs
            currentFrame = frame
            players = tracker.assign(frame.poses, s.maxPlayers)
            statsFrames++
        }

        val g = game
        if (g != null) {
            try {
                g.update(dtMs, players, currentFrame?.let(::transformFor), s)
            } catch (e: RuntimeException) {
                game = null
                notices.error("game-crash", "${g.kind.title} hit a problem and was stopped.", "game", "update failed", e, mapOf("game" to g.kind.name))
            }
        }
        views.forEach { it.invalidate() }

        val now = SystemClock.uptimeMillis()
        if (statsStartMs == 0L) statsStartMs = now
        if (now - statsStartMs >= STATS_WINDOW_MS) {
            publishStatus(statsFrames * 1000f / (now - statsStartMs))
            statsFrames = 0
            statsStartMs = now
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun publishStatus(fps: Float) {
        val g = game
        _status.value = EngineStatus(
            trackingFps = fps,
            latencyMs = currentFrame?.latencyMs ?: 0,
            playersSeen = players.size,
            game = g?.kind,
            summary = g?.summary(),
        )
    }

    private companion object {
        /** Cap on one simulation step so a stalled frame doesn't teleport bubbles. */
        const val MAX_STEP_MS = 100L
        const val STATS_WINDOW_MS = 500L
    }
}
