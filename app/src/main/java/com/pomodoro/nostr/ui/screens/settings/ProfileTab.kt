package com.pomodoro.nostr.ui.screens.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pomodoro.nostr.ui.components.QrCodeDialog
import com.pomodoro.nostr.ui.components.UserAvatar
import com.pomodoro.nostr.ui.theme.NeonCyan
import com.pomodoro.nostr.ui.theme.NeonMagenta
import com.pomodoro.nostr.viewmodel.ProfileViewModel
import com.pomodoro.nostr.viewmodel.RelayInfo

@Composable
fun ProfileTab(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }

    // Amber signing launcher
    val amberLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val signedEvent = result.data?.getStringExtra("event")
                ?: result.data?.getStringExtra("signature")
                ?: result.data?.getStringExtra("result")
            if (signedEvent != null) {
                viewModel.handleAmberSignedEvent(signedEvent)
            } else {
                viewModel.clearAmberSigningRequest()
                Toast.makeText(context, "Signing cancelled", Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModel.clearAmberSigningRequest()
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.uploadProfilePicture(context, it) }
    }

    // Blossom Amber signing launcher
    val blossomAmberLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val signedEvent = result.data?.getStringExtra("event")
                ?: result.data?.getStringExtra("signature")
                ?: result.data?.getStringExtra("result")
            if (signedEvent != null) {
                viewModel.handleBlossomAmberSignedEvent(signedEvent)
            } else {
                viewModel.clearBlossomAmberIntent()
                Toast.makeText(context, "Upload signing cancelled", Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModel.clearBlossomAmberIntent()
        }
    }

    // Launch Amber for Blossom upload signing
    LaunchedEffect(uiState.pendingBlossomAmberIntent) {
        uiState.pendingBlossomAmberIntent?.let { intent ->
            try {
                blossomAmberLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to launch Amber: ${e.message}", Toast.LENGTH_LONG).show()
                viewModel.clearBlossomAmberIntent()
            }
        }
    }

    // Launch Amber when needed
    LaunchedEffect(uiState.needsAmberSigning) {
        uiState.needsAmberSigning?.let { intent ->
            try {
                amberLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to launch Amber: ${e.message}", Toast.LENGTH_LONG).show()
                viewModel.clearAmberSigningRequest()
            }
        }
    }

    // Handle save success
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            Toast.makeText(context, "Profile saved!", Toast.LENGTH_SHORT).show()
            viewModel.clearSaveSuccess()
        }
    }

    // Handle errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = NeonCyan)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Loading profile...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Profile picture at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    imageUrl = uiState.picture.takeIf { it.isNotBlank() },
                    size = 100.dp,
                    modifier = Modifier.clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Identity section
            SectionHeader("YOUR IDENTITY")

            uiState.npub?.let { npub ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = npub.take(16) + "..." + npub.takeLast(8),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("npub", npub))
                        Toast.makeText(context, "npub copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = NeonCyan)
                    }
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Default.QrCode, "Show QR", tint = NeonCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Profile fields
            SectionHeader("PROFILE")

            ProfileTextField(
                value = uiState.displayName,
                onValueChange = { viewModel.updateDisplayName(it) },
                label = "Display Name",
                placeholder = "Your display name",
                leadingIcon = Icons.Default.Person
            )
            ProfileTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = "Username",
                placeholder = "username (lowercase, no spaces)",
                leadingIcon = Icons.Default.AccountCircle
            )
            ProfileTextField(
                value = uiState.about,
                onValueChange = { viewModel.updateAbout(it) },
                label = "About",
                placeholder = "Tell people about yourself",
                leadingIcon = Icons.Default.Info,
                singleLine = false,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("IMAGES")

            // Picture URL field with upload button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileTextField(
                    value = uiState.picture,
                    onValueChange = { viewModel.updatePicture(it) },
                    label = "Profile Picture URL",
                    placeholder = "https://example.com/avatar.jpg",
                    leadingIcon = Icons.Default.AccountCircle,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !uiState.isUploadingPicture
                ) {
                    if (uiState.isUploadingPicture) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = NeonCyan
                        )
                    } else {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = "Upload picture",
                            tint = NeonCyan
                        )
                    }
                }
            }
            ProfileTextField(
                value = uiState.banner,
                onValueChange = { viewModel.updateBanner(it) },
                label = "Banner Image URL",
                placeholder = "https://example.com/banner.jpg",
                leadingIcon = Icons.Default.Image
            )

            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("VERIFICATION & PAYMENTS")

            ProfileTextField(
                value = uiState.nip05,
                onValueChange = { viewModel.updateNip05(it) },
                label = "NIP-05 Identifier",
                placeholder = "you@yourdomain.com",
                leadingIcon = Icons.Default.Verified,
                supportingText = "Verifies your identity on Nostr"
            )
            ProfileTextField(
                value = uiState.lud16,
                onValueChange = { viewModel.updateLud16(it) },
                label = "Lightning Address",
                placeholder = "you@getalby.com",
                leadingIcon = Icons.Default.Bolt,
                iconTint = Color(0xFFFFD700),
                supportingText = "Required to receive zaps"
            )

            Spacer(modifier = Modifier.height(20.dp))

            SectionHeader("LINKS")

            ProfileTextField(
                value = uiState.website,
                onValueChange = { viewModel.updateWebsite(it) },
                label = "Website",
                placeholder = "https://yourwebsite.com",
                leadingIcon = Icons.Default.Link
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = { viewModel.saveProfile() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.hasChanges && !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color.Black,
                    disabledContainerColor = NeonCyan.copy(alpha = 0.3f),
                    disabledContentColor = Color.Black.copy(alpha = 0.5f)
                )
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isSaving) "Saving..." else "Save Profile")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Changes will be published to all connected relays.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Relays section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("RELAYS (${uiState.relays.size})")
                IconButton(onClick = { viewModel.showAddRelayDialog() }) {
                    Icon(Icons.Default.Add, "Add Relay", tint = NeonCyan)
                }
            }

            uiState.relays.forEach { relay ->
                RelayItem(
                    relay = relay,
                    onReconnect = { viewModel.reconnectRelay(relay.url) },
                    onRemove = { viewModel.removeRelay(relay.url) }
                )
            }

            if (uiState.relays.isEmpty()) {
                Text(
                    text = "No relays connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // QR Code Dialog
    if (showQrDialog && uiState.npub != null) {
        QrCodeDialog(
            npub = uiState.npub!!,
            onDismiss = { showQrDialog = false }
        )
    }

    // Add Relay Dialog
    if (uiState.showAddRelayDialog) {
        AddRelayDialog(
            onDismiss = { viewModel.hideAddRelayDialog() },
            onAdd = { viewModel.addRelay(it) }
        )
    }
}

@Composable
private fun RelayItem(
    relay: RelayInfo,
    onReconnect: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onReconnect() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (relay.isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = relay.status,
                tint = if (relay.isConnected) NeonCyan else NeonMagenta.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relay.url.removePrefix("wss://").removePrefix("ws://"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = relay.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (relay.isConnected) NeonCyan.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = NeonMagenta.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AddRelayDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var relayUrl by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "ADD RELAY",
                fontFamily = FontFamily.Monospace,
                color = NeonCyan
            )
        },
        text = {
            OutlinedTextField(
                value = relayUrl,
                onValueChange = { relayUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("wss://relay.example.com") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(relayUrl) },
                enabled = relayUrl.isNotBlank()
            ) {
                Text("Add", color = NeonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    supportingText: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconTint
            )
        },
        supportingText = supportingText?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
