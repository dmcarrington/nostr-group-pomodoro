package com.pomodoro.nostr.ui.screens.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.pomodoro.nostr.ui.components.ContactListItem
import com.pomodoro.nostr.ui.components.QrScanner
import com.pomodoro.nostr.ui.theme.NeonCyan
import com.pomodoro.nostr.ui.theme.NeonPurple
import com.pomodoro.nostr.viewmodel.ContactsViewModel

@Composable
fun ContactsTab(
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Ranking header
            SectionHeader("RANKING")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                if (uiState.isLoadingRankings) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp))
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RankingColumn(
                            label = "24h",
                            rank = uiState.myRankDaily,
                            total = uiState.totalParticipants,
                            count = uiState.myCountDaily
                        )
                        RankingColumn(
                            label = "Week",
                            rank = uiState.myRankWeekly,
                            total = uiState.totalParticipants,
                            count = uiState.myCountWeekly
                        )
                        RankingColumn(
                            label = "Month",
                            rank = uiState.myRankMonthly,
                            total = uiState.totalParticipants,
                            count = uiState.myCountMonthly
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Friends header with add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("FRIENDS (${uiState.contacts.size})")
                IconButton(onClick = { viewModel.showAddDialog() }) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = "Add Friend",
                        tint = NeonCyan
                    )
                }
            }
        }

        if (uiState.contacts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No friends yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Add friends to compare sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Sort contacts by monthly ranking
            val rankings = uiState.rankings
            val sortedContacts = if (rankings != null) {
                uiState.contacts.sortedBy { contact ->
                    rankings.monthly.indexOfFirst { it.pubkeyHex == contact.pubkeyHex }
                        .let { if (it == -1) Int.MAX_VALUE else it }
                }
            } else {
                uiState.contacts
            }

            items(
                items = sortedContacts,
                key = { it.pubkeyHex }
            ) { contact ->
                val monthlyRank = rankings?.monthly
                    ?.indexOfFirst { it.pubkeyHex == contact.pubkeyHex }
                    ?.let { if (it >= 0) it + 1 else null }
                val monthlyCount = rankings?.monthly
                    ?.find { it.pubkeyHex == contact.pubkeyHex }
                    ?.sessionCount

                ContactListItem(
                    metadata = contact.metadata,
                    pubkeyHex = contact.pubkeyHex,
                    rank = monthlyRank,
                    sessionCount = monthlyCount,
                    onRemove = { viewModel.removeContact(contact.pubkeyHex) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Add friend dialog
    if (uiState.showAddDialog) {
        AddFriendDialog(
            onDismiss = { viewModel.hideAddDialog() },
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
            searchResults = uiState.searchResults,
            isSearching = uiState.isSearching,
            onAddFromSearch = { viewModel.addContactFromSearch(it) },
            npubInput = uiState.npubInput,
            onNpubInputChange = { viewModel.updateNpubInput(it) },
            onAddByNpub = { viewModel.addContactByNpub() },
            onOpenQrScanner = {
                viewModel.hideAddDialog()
                viewModel.showQrScanner()
            },
            addError = uiState.addError
        )
    }

    // QR Scanner
    if (uiState.showQrScanner) {
        Dialog(
            onDismissRequest = { viewModel.hideQrScanner() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                QrScanner(
                    onQrCodeScanned = { scannedValue ->
                        viewModel.addContactFromQr(scannedValue)
                    },
                    onDismiss = { viewModel.hideQrScanner() }
                )
            }
        }
    }
}

@Composable
private fun RankingColumn(
    label: String,
    rank: Int?,
    total: Int,
    count: Int
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (rank != null && total > 0) "#$rank" else "-",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = NeonCyan
        )
        Text(
            text = if (total > 0) "of $total" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "$count sessions",
            style = MaterialTheme.typography.bodySmall,
            color = NeonPurple,
            fontFamily = FontFamily.Monospace
        )
    }
}
