package com.pomodoro.nostr.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pomodoro.nostr.nostr.KeyManager
import com.pomodoro.nostr.nostr.MetadataCache
import com.pomodoro.nostr.nostr.NostrClient
import com.pomodoro.nostr.nostr.SessionPublisher
import com.pomodoro.nostr.nostr.models.UserMetadata
import com.pomodoro.nostr.timer.DEFAULT_PRESETS
import com.pomodoro.nostr.timer.PomodoroPreset
import com.pomodoro.nostr.timer.TimerPhase
import com.pomodoro.nostr.timer.TimerPreferences
import com.pomodoro.nostr.timer.TimerService
import com.pomodoro.nostr.timer.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import rust.nostr.protocol.Filter
import rust.nostr.protocol.Kind
import rust.nostr.protocol.PublicKey
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timerPreferences: TimerPreferences,
    private val sessionPublisher: SessionPublisher,
    private val keyManager: KeyManager,
    private val metadataCache: MetadataCache,
    private val nostrClient: NostrClient
) : ViewModel() {

    val timerState: StateFlow<TimerState> = TimerService.timerState

    val presets: List<PomodoroPreset> = DEFAULT_PRESETS

    private val _pendingAmberSessionSign = MutableStateFlow<Intent?>(null)
    val pendingAmberSessionSign: StateFlow<Intent?> = _pendingAmberSessionSign.asStateFlow()

    private val _profilePictureUrl = MutableStateFlow<String?>(null)
    val profilePictureUrl: StateFlow<String?> = _profilePictureUrl.asStateFlow()

    init {
        refreshProfilePicture()
        observeMetadataUpdates()
        fetchMyMetadata()
        val preset = timerPreferences.getActivePreset()
        TimerService.initPreset(context, preset)

        // Observe session completions and publish to Nostr
        viewModelScope.launch {
            TimerService.sessionCompleted.collect { durationMinutes ->
                if (durationMinutes != null) {
                    TimerService.clearSessionCompleted()
                    val unsignedEvent = sessionPublisher.publishSession(durationMinutes)
                    if (unsignedEvent != null) {
                        // Amber user — need to sign via intent
                        // Store for UI to launch Amber
                        _pendingAmberSessionSign.value =
                            sessionPublisher.createAmberSignIntent(unsignedEvent)
                    }
                }
            }
        }
    }

    fun refreshProfilePicture() {
        val pubkey = keyManager.getPublicKeyHex() ?: return
        val metadata = metadataCache.get(pubkey)
        _profilePictureUrl.value = metadata?.picture
    }

    private fun observeMetadataUpdates() {
        val pubkey = keyManager.getPublicKeyHex() ?: return
        viewModelScope.launch {
            metadataCache.updates.collect { metadata ->
                if (metadata.pubkey == pubkey) {
                    _profilePictureUrl.value = metadata.picture
                }
            }
        }
    }

    private fun fetchMyMetadata() {
        val pubkeyHex = keyManager.getPublicKeyHex() ?: return

        viewModelScope.launch {
            try {
                // Wait for relay connection
                withTimeoutOrNull(8000L) {
                    nostrClient.connectionState.first { it == NostrClient.ConnectionState.Connected }
                } ?: return@launch

                val filter = Filter()
                    .kind(Kind(0u))
                    .author(PublicKey.fromHex(pubkeyHex))
                    .limit(1u)

                val subscriptionId = "my_metadata"
                nostrClient.subscribe(subscriptionId, listOf(filter))

                // Wait for the event to arrive, then cache it
                val result = withTimeoutOrNull(5000L) {
                    nostrClient.events.first { nostrEvent ->
                        nostrEvent.subscriptionId == subscriptionId &&
                            nostrEvent.event.kind().asU16().toInt() == 0
                    }
                }

                nostrClient.unsubscribe(subscriptionId)

                if (result != null) {
                    val metadata = UserMetadata.fromJson(
                        pubkey = result.event.author().toHex(),
                        json = result.event.content(),
                        createdAt = result.event.createdAt().asSecs().toLong()
                    )
                    metadataCache.put(metadata)
                }
            } catch (_: Exception) {
                // Non-critical — profile picture just won't show until settings visited
            }
        }
    }

    fun handleAmberSessionSigned(signedEventJson: String) {
        _pendingAmberSessionSign.value = null
        sessionPublisher.publishSignedEvent(signedEventJson)
    }

    fun clearPendingAmberSessionSign() {
        _pendingAmberSessionSign.value = null
    }

    fun start() {
        TimerService.sendAction(context, TimerService.ACTION_START)
    }

    fun pause() {
        TimerService.sendAction(context, TimerService.ACTION_PAUSE)
    }

    fun reset() {
        TimerService.sendAction(context, TimerService.ACTION_RESET)
    }

    fun skip() {
        TimerService.sendAction(context, TimerService.ACTION_SKIP)
    }

    fun selectPreset(index: Int) {
        val state = timerState.value
        if (state.phase != TimerPhase.IDLE && state.isRunning) return

        timerPreferences.selectedPresetIndex = index
        timerPreferences.isCustom = false
        val preset = DEFAULT_PRESETS[index]
        TimerService.initPreset(context, preset)
    }

    fun getSelectedPresetIndex(): Int {
        return if (timerPreferences.isCustom) -1 else timerPreferences.selectedPresetIndex
    }
}
