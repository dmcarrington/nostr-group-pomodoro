package com.pomodoro.nostr.ui.screens.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pomodoro.nostr.ui.theme.NeonCyan
import com.pomodoro.nostr.ui.theme.NeonMagenta
import com.pomodoro.nostr.viewmodel.SettingsViewModel

@Composable
fun NotificationsTab(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val hasPermission = notificationManager.isNotificationPolicyAccessGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader("FOCUS MODE")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitch(
                    label = "Focus Mode (DND)",
                    description = "Enable Do Not Disturb while a session is running",
                    checked = uiState.dndEnabled,
                    onCheckedChange = { viewModel.setDndEnabled(it) }
                )

                if (uiState.dndEnabled && !hasPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "DND permission required",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonMagenta,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text(
                            "Grant DND Access",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.background
                        )
                    }
                }
            }
        }

        if (uiState.dndEnabled) {
            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("ALLOWED DURING FOCUS")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitch(
                        label = "Calls",
                        description = "Allow incoming phone calls",
                        checked = uiState.allowCalls,
                        onCheckedChange = { viewModel.setAllowCalls(it) }
                    )
                    SettingSwitch(
                        label = "Messages",
                        description = "Allow text messages",
                        checked = uiState.allowMessages,
                        onCheckedChange = { viewModel.setAllowMessages(it) }
                    )
                    SettingSwitch(
                        label = "Alarms",
                        description = "Allow alarms to sound",
                        checked = uiState.allowAlarms,
                        onCheckedChange = { viewModel.setAllowAlarms(it) }
                    )
                    SettingSwitch(
                        label = "Reminders",
                        description = "Allow reminder notifications",
                        checked = uiState.allowReminders,
                        onCheckedChange = { viewModel.setAllowReminders(it) }
                    )
                    SettingSwitch(
                        label = "Events",
                        description = "Allow calendar event notifications",
                        checked = uiState.allowEvents,
                        onCheckedChange = { viewModel.setAllowEvents(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}
