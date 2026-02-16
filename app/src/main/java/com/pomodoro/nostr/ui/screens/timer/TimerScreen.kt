package com.pomodoro.nostr.ui.screens.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pomodoro.nostr.timer.TimerPhase
import com.pomodoro.nostr.timer.formatTime
import com.pomodoro.nostr.ui.theme.NeonCyan
import com.pomodoro.nostr.ui.theme.NeonGreen
import com.pomodoro.nostr.ui.theme.NeonMagenta
import com.pomodoro.nostr.ui.theme.NeonPurple
import com.pomodoro.nostr.viewmodel.TimerViewModel

@Composable
fun TimerScreen(
    onSettingsClick: () -> Unit,
    viewModel: TimerViewModel = hiltViewModel()
) {
    val state by viewModel.timerState.collectAsState()
    val context = LocalContext.current

    // Request notification permission on Android 13+
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val phaseColor by animateColorAsState(
        targetValue = when (state.phase) {
            TimerPhase.WORK -> NeonCyan
            TimerPhase.SHORT_BREAK -> NeonGreen
            TimerPhase.LONG_BREAK -> NeonPurple
            TimerPhase.IDLE -> NeonCyan
        },
        animationSpec = tween(500),
        label = "phaseColor"
    )

    val progress by animateFloatAsState(
        targetValue = if (state.totalMillis > 0) {
            state.remainingMillis.toFloat() / state.totalMillis.toFloat()
        } else 1f,
        animationSpec = tween(150),
        label = "progress"
    )

    var selectedPresetIndex by remember { mutableIntStateOf(viewModel.getSelectedPresetIndex()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar with settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onSettingsClick,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // Preset chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            viewModel.presets.forEachIndexed { index, preset ->
                FilterChip(
                    selected = selectedPresetIndex == index,
                    onClick = {
                        selectedPresetIndex = index
                        viewModel.selectPreset(index)
                    },
                    label = {
                        Text(
                            preset.name,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = phaseColor.copy(alpha = 0.2f),
                        selectedLabelColor = phaseColor
                    ),
                    enabled = state.phase == TimerPhase.IDLE
                )
                if (index < viewModel.presets.lastIndex) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Timer circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
            // Background arc
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 12.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Background track
                drawArc(
                    color = phaseColor.copy(alpha = 0.1f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc with glow
                val sweepAngle = progress * 360f
                if (sweepAngle > 0f) {
                    // Glow layer
                    drawArc(
                        color = phaseColor.copy(alpha = 0.3f),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth + 8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Main arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(phaseColor, phaseColor.copy(alpha = 0.6f), phaseColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Timer text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(state.remainingMillis),
                    fontSize = 56.sp,
                    fontFamily = FontFamily.Monospace,
                    color = phaseColor,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = when (state.phase) {
                        TimerPhase.WORK -> "FOCUS"
                        TimerPhase.SHORT_BREAK -> "BREAK"
                        TimerPhase.LONG_BREAK -> "LONG BREAK"
                        TimerPhase.IDLE -> "READY"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = phaseColor.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Session dots
        SessionIndicator(
            completedSessions = state.completedSessions,
            totalSessions = state.currentPreset.sessionsBeforeLongBreak,
            activeColor = phaseColor,
            isInWorkPhase = state.phase == TimerPhase.WORK
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reset
            IconButton(
                onClick = { viewModel.reset() },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(28.dp))
            }

            // Play/Pause (larger)
            IconButton(
                onClick = {
                    if (state.isRunning) viewModel.pause() else viewModel.start()
                },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = phaseColor
                )
            ) {
                Icon(
                    imageVector = if (state.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isRunning) "Pause" else "Start",
                    modifier = Modifier.size(48.dp)
                )
            }

            // Skip
            IconButton(
                onClick = { viewModel.skip() },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                enabled = state.phase != TimerPhase.IDLE
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Skip", modifier = Modifier.size(28.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SessionIndicator(
    completedSessions: Int,
    totalSessions: Int,
    activeColor: Color,
    isInWorkPhase: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSessions) { index ->
            val isFilled = index < completedSessions
            val isCurrent = index == completedSessions && isInWorkPhase

            Canvas(modifier = Modifier.size(12.dp)) {
                if (isFilled) {
                    drawCircle(color = activeColor)
                } else if (isCurrent) {
                    drawCircle(color = activeColor.copy(alpha = 0.5f))
                    drawCircle(
                        color = activeColor,
                        style = Stroke(width = 2.dp.toPx())
                    )
                } else {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.2f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}
