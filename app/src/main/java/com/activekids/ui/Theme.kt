package com.activekids.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1220),
    secondary = Color(0xFFFACC15),
    background = Color(0xFF0B1220),
    surface = Color(0xFF111A2E),
    surfaceVariant = Color(0xFF1B2640),
    error = Color(0xFFF87171),
)

val WarningColor = Color(0xFFFDE047)

@Composable
fun ActiveKidsTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, content = content)
