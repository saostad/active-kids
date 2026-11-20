package com.activekids.display

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import com.activekids.core.NoticeCenter
import com.activekids.game.GameEngine
import com.activekids.game.GameView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Full-screen game on the TV (USB-C → HDMI or wireless display). */
class GamePresentation(outer: Context, display: Display, private val engine: GameEngine) : Presentation(outer, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(GameView(context, engine))
    }
}

/** Watches for an external display and shows the game on it while the phone keeps the controls. */
class TvDisplayController(
    private val activity: Activity,
    private val engine: GameEngine,
    private val notices: NoticeCenter,
) : DisplayManager.DisplayListener {
    data class TvState(val connected: Boolean = false, val name: String? = null, val size: String? = null)

    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private val _state = MutableStateFlow(TvState())
    val state: StateFlow<TvState> = _state
    private var presentation: GamePresentation? = null

    fun start() {
        displayManager.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        displayManager.unregisterDisplayListener(this)
        presentation?.dismiss()
        presentation = null
        _state.value = TvState()
    }

    override fun onDisplayAdded(displayId: Int) = refresh()
    override fun onDisplayRemoved(displayId: Int) = refresh()
    override fun onDisplayChanged(displayId: Int) = refresh()

    private fun refresh() {
        val display = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        val current = presentation
        if (current != null && current.isShowing && current.display.displayId == display?.displayId) return

        current?.dismiss()
        presentation = null
        if (display == null) {
            _state.value = TvState()
            return
        }
        val next = GamePresentation(activity, display, engine)
        try {
            next.show()
            presentation = next
            notices.clear(KEY)
            val mode = display.mode
            _state.value = TvState(true, display.name, "${mode.physicalWidth}×${mode.physicalHeight}")
        } catch (e: WindowManager.InvalidDisplayException) {
            _state.value = TvState()
            notices.error(KEY, "A TV was detected but the game couldn't be shown on it. Unplug and replug the HDMI adapter.", "display", "Presentation.show failed", e, mapOf("display" to display.name))
        }
    }

    private companion object {
        const val KEY = "tv-display"
    }
}
