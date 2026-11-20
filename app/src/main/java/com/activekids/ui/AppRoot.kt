package com.activekids.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.activekids.ActiveKidsApp
import com.activekids.core.Notice
import com.activekids.core.NoticeLevel
import com.activekids.display.TvDisplayController.TvState
import com.activekids.game.EngineStatus
import com.activekids.game.GameKind
import com.activekids.game.GameView
import com.activekids.pose.CameraController
import com.activekids.pose.PoseTracker
import kotlinx.coroutines.flow.StateFlow

private enum class Screen { HOME, SETTINGS }

@Composable
fun AppRoot(
    app: ActiveKidsApp,
    permission: StateFlow<CameraPermission>,
    cameraStatus: StateFlow<CameraController.Status>,
    trackerStatus: StateFlow<PoseTracker.Status>,
    tvState: StateFlow<TvState>,
    onRequestPermission: () -> Unit,
) {
    val perm by permission.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            perm == CameraPermission.DENIED -> PermissionScreen(onRequestPermission)
            screen == Screen.SETTINGS -> {
                BackHandler { screen = Screen.HOME }
                SettingsScreen(app.settings, onBack = { screen = Screen.HOME })
            }
            else -> HomeScreen(app, cameraStatus, trackerStatus, tvState, onOpenSettings = { screen = Screen.SETTINGS })
        }
    }
}

@Composable
private fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Active Kids needs the camera", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "The camera sees players' movements. Video is processed on this phone only and is never saved or uploaded.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission) { Text("Allow camera") }
    }
}

@Composable
private fun HomeScreen(
    app: ActiveKidsApp,
    cameraStatus: StateFlow<CameraController.Status>,
    trackerStatus: StateFlow<PoseTracker.Status>,
    tvState: StateFlow<TvState>,
    onOpenSettings: () -> Unit,
) {
    val tv by tvState.collectAsStateWithLifecycle()
    val cam by cameraStatus.collectAsStateWithLifecycle()
    val trk by trackerStatus.collectAsStateWithLifecycle()
    val engine by app.engine.status.collectAsStateWithLifecycle()
    val notices by app.notices.items.collectAsStateWithLifecycle()
    var panelHidden by remember { mutableStateOf(false) }
    var selectedGame by rememberSaveable { mutableStateOf(GameKind.BUBBLE_POP) }

    Row(Modifier.fillMaxSize()) {
        if (!panelHidden) {
            Column(
                Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Active Kids", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                GameCard(
                    engine, selectedGame, onSelect = { selectedGame = it },
                    onStart = app.engine::startGame, onStop = app.engine::stopGame,
                )
                NoticeList(notices, onDismiss = app.notices::clear)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
                    if (!tv.connected) OutlinedButton(onClick = { panelHidden = true }) { Text("Full screen") }
                }
                StatusCard(tv, cam, trk, engine)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            // When a TV is connected this is a live preview of what the TV shows.
            AndroidView(factory = { GameView(it, app.engine) }, modifier = Modifier.fillMaxSize())
            if (panelHidden) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(12.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .clickable { panelHidden = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("Show controls") }
            }
        }
    }
}

@Composable
private fun StatusCard(tv: TvState, cam: CameraController.Status, trk: PoseTracker.Status, engine: EngineStatus) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusLine(
                "TV",
                if (tv.connected) "${tv.name} (${tv.size})" else "Not connected. Plug in a USB-C → HDMI adapter. The game shows on this phone for now.",
                ok = tv.connected,
            )
            val zoom = cam.zoomRatio?.let { " · %.2f×".format(it) } ?: ""
            StatusLine("Camera", cam.label + zoom + (cam.resolution?.let { " · $it" } ?: ""), ok = cam.active)
            val tracking = when {
                trk.failed -> "Failed to start"
                !trk.ready -> "Starting…"
                else -> "${trk.model?.name?.lowercase()} on ${trk.processorInUse?.label} · %.0f fps · %d ms".format(engine.trackingFps, engine.latencyMs)
            }
            StatusLine("Tracking", tracking, ok = trk.ready && !trk.failed)
            StatusLine("Players", engine.playersSeen.toString(), ok = engine.playersSeen > 0)
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row {
        Text(if (ok) "● " else "○ ", color = if (ok) Color(0xFF4ADE80) else WarningColor)
        Text("$label: ", fontWeight = FontWeight.SemiBold)
        Text(value)
    }
}

@Composable
private fun GameCard(
    engine: EngineStatus,
    selected: GameKind,
    onSelect: (GameKind) -> Unit,
    onStart: (GameKind) -> Unit,
    onStop: () -> Unit,
) {
    val running = engine.game
    if (running != null) {
        val summary = engine.summary
        Card {
            Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(running.title, style = MaterialTheme.typography.titleLarge)
                if (summary != null) {
                    Text(if (summary.secondsLeft > 0) "${summary.phase} · ${summary.secondsLeft}s left" else summary.phase)
                    Text(summary.scores.mapIndexed { i, s -> "P${i + 1}: $s" }.joinToString("   "))
                    summary.goal?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onStart(running) }) { Text("Play again") }
                    OutlinedButton(onClick = onStop) { Text("Games") }
                }
            }
        }
        return
    }
    var choosing by remember { mutableStateOf(false) }
    Card {
        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(selected.title, style = MaterialTheme.typography.titleLarge)
            Text(selected.description, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStart(selected) }) { Text("Start") }
                Box {
                    OutlinedButton(onClick = { choosing = true }) { Text("Choose game") }
                    DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                        GameKind.entries.forEach { kind ->
                            DropdownMenuItem(text = { Text(kind.title) }, onClick = {
                                onSelect(kind)
                                choosing = false
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeList(notices: List<Notice>, onDismiss: (String) -> Unit) {
    notices.forEach { n ->
        val color = when (n.level) {
            NoticeLevel.ERROR -> MaterialTheme.colorScheme.error
            NoticeLevel.WARNING -> WarningColor
            NoticeLevel.INFO -> MaterialTheme.colorScheme.primary
        }
        Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.16f))) {
            Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (n.fallback) "Fallback: " else "") + n.message,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onDismiss(n.key) }) { Text("Dismiss") }
            }
        }
    }
}
