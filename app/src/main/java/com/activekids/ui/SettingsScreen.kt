package com.activekids.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.activekids.settings.AnalysisResolution
import com.activekids.settings.AppSettings
import com.activekids.settings.CameraChoice
import com.activekids.settings.NumberSpec
import com.activekids.settings.PoseModel
import com.activekids.settings.Processor
import com.activekids.settings.SettingSpecs
import com.activekids.settings.SettingsRepository

private val SettingsBackground = Color(0xFF0D182A)
private val SettingsCard = Color(0xFF20324D)
private val SettingsText = Color(0xFFF7FAFF)
private val SettingsHelp = Color(0xFFDAE6F8)
private val SettingsAccent = Color(0xFF9ED4FF)
private val SettingsControl = Color(0xFF405A7E)

@Composable
fun SettingsScreen(repo: SettingsRepository, onBack: () -> Unit) {
    val s by repo.settings.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val update: ((AppSettings) -> AppSettings) -> Unit = { error = repo.update(it) }

    LazyColumn(
        Modifier.fillMaxSize().background(SettingsBackground),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("← Back") }
                Spacer(Modifier.width(16.dp))
                Text("Settings", color = SettingsText, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { error = repo.resetToDefaults() }) { Text("Reset to defaults") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        item { Section("Camera") }
        item { ChoiceRow("Camera", CameraChoice.entries, s.camera, { it.label }) { v -> update { it.copy(camera = v) } } }
        item { ChoiceRow("Resolution", AnalysisResolution.entries, s.resolution, { it.label }) { v -> update { it.copy(resolution = v) } } }

        item { Section("Body tracking") }
        item { ChoiceRow("Model", PoseModel.entries, s.model, { it.label }) { v -> update { it.copy(model = v) } } }
        item { ChoiceRow("Run on", Processor.entries, s.processor, { it.label }) { v -> update { it.copy(processor = v) } } }
        SettingSpecs.tracking.forEach { spec -> item(spec.key) { NumberRow(spec, s, update) } }

        for ((title, specs) in listOf(
            "All games" to SettingSpecs.round,
            "Round goals" to SettingSpecs.goals,
            "Bubble Pop" to SettingSpecs.bubblePop,
            "Star Reach" to SettingSpecs.starReach,
            "Pose Match" to SettingSpecs.poseMatch,
            "Trail Dash" to SettingSpecs.trailDash,
        ) + SettingSpecs.partyGames) {
            item { Section(title) }
            specs.forEach { spec -> item(spec.key) { NumberRow(spec, s, update) } }
        }

        item { Section("Display & sound") }
        SettingSpecs.display.forEach { spec -> item(spec.key) { NumberRow(spec, s, update) } }
        item { SwitchRow("Show skeletons", s.showSkeleton) { v -> update { it.copy(showSkeleton = v) } } }
        item { SwitchRow("Mirror picture (like a mirror)", s.mirrorView) { v -> update { it.copy(mirrorView = v) } } }
        item { SwitchRow("Sound effects", s.soundOn) { v -> update { it.copy(soundOn = v) } } }
        item { Spacer(Modifier.padding(16.dp)) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = SettingsAccent, style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = SettingsCard, contentColor = SettingsText,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    SettingCard {
        Text(label, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(name(option)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SettingsControl, labelColor = SettingsText,
                        selectedContainerColor = SettingsAccent, selectedLabelColor = SettingsBackground,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NumberRow(spec: NumberSpec, s: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val stored = spec.read(s)
    // Local value while dragging; saved when the finger lifts.
    var dragging by remember(stored) { mutableStateOf(stored) }
    SettingCard {
        Row {
            Text(spec.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(spec.format(dragging) + spec.unit, color = SettingsAccent, fontWeight = FontWeight.SemiBold)
        }
        Text(spec.help, color = SettingsHelp, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = dragging,
            onValueChange = { dragging = spec.snap(it) },
            onValueChangeFinished = { update { cur -> spec.write(cur, dragging) } },
            valueRange = spec.min..spec.max,
            steps = spec.sliderSteps.coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = SettingsAccent, activeTrackColor = SettingsAccent, inactiveTrackColor = SettingsControl,
                activeTickColor = SettingsBackground, inactiveTickColor = SettingsHelp,
            ),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(
                checked = checked, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = SettingsAccent, checkedThumbColor = SettingsBackground,
                    uncheckedTrackColor = SettingsControl, uncheckedThumbColor = SettingsHelp, uncheckedBorderColor = SettingsHelp,
                ),
            )
        }
    }
}
