package com.pomodoro.nostr.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pomodoro.nostr.nostr.KeyManager
import com.pomodoro.nostr.nostr.MetadataCache
import com.pomodoro.nostr.nostr.NostrClient
import com.pomodoro.nostr.nostr.NostrEvent
import com.pomodoro.nostr.nostr.models.UserMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import rust.nostr.protocol.Event
import rust.nostr.protocol.EventBuilder
import rust.nostr.protocol.Filter
import rust.nostr.protocol.Kind
import rust.nostr.protocol.PublicKey
import javax.inject.Inject

data class RelayInfo(
    val url: String,
    val isConnected: Boolean,
    val status: String
)

data class ProfileUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val hasChanges: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val displayName: String = "",
    val name: String = "",
    val about: String = "",
    val picture: String = "",
    val banner: String = "",
    val nip05: String = "",
    val lud16: String = "",
    val website: String = "",
    val needsAmberSigning: Intent? = null,
    val npub: String? = null,
    val relays: List<RelayInfo> = emptyList(),
    val showAddRelayDialog: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val keyManager: KeyManager,
    private val nostrClient: NostrClient,
    private val metadataCache: MetadataCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // Store original values for change detection
    private var originalMetadata: UserMetadata? = null

    init {
        _uiState.update { it.copy(npub = keyManager.getNpub()) }
        loadCurrentProfile()
        observeRelayStatus()
    }

    private fun loadCurrentProfile() {
        val pubkeyHex = keyManager.getPublicKeyHex() ?: return

        _uiState.update { it.copy(isLoading = true) }

        // Show cached metadata immediately if available
        val cached = metadataCache.get(pubkeyHex)
        if (cached != null) {
            applyMetadata(cached)
        }

        // Fetch fresh metadata from relays
        val subscriptionId = "profile_edit_${System.currentTimeMillis()}"

        viewModelScope.launch {
            try {
                // Wait for at least one relay to be connected before subscribing
                withTimeoutOrNull(8000L) {
                    nostrClient.connectionState.first { it == NostrClient.ConnectionState.Connected }
                } ?: run {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val filter = Filter()
                    .kind(Kind(0u))
                    .author(PublicKey.fromHex(pubkeyHex))
                    .limit(1u)

                val result = withTimeoutOrNull(5000L) {
                    val deferred = async {
                        nostrClient.events.first { nostrEvent: NostrEvent ->
                            nostrEvent.subscriptionId == subscriptionId &&
                                nostrEvent.event.kind().asU16().toInt() == 0
                        }
                    }
                    yield()
                    nostrClient.subscribe(subscriptionId, listOf(filter))
                    deferred.await()
                }

                nostrClient.unsubscribe(subscriptionId)

                if (result != null) {
                    val metadata = UserMetadata.fromJson(
                        pubkey = result.event.author().toHex(),
                        json = result.event.content(),
                        createdAt = result.event.createdAt().asSecs().toLong()
                    )
                    metadataCache.put(metadata)
                    applyMetadata(metadata)
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun applyMetadata(metadata: UserMetadata) {
        originalMetadata = metadata
        _uiState.update {
            it.copy(
                isLoading = false,
                displayName = metadata.displayName ?: "",
                name = metadata.name ?: "",
                about = metadata.about ?: "",
                picture = metadata.picture ?: "",
                banner = metadata.banner ?: "",
                nip05 = metadata.nip05 ?: "",
                lud16 = metadata.lud16 ?: "",
                website = metadata.website ?: "",
                hasChanges = false
            )
        }
    }

    private fun checkForChanges() {
        val state = _uiState.value
        val orig = originalMetadata
        val changed = if (orig == null) {
            state.displayName.isNotBlank() || state.name.isNotBlank() ||
                state.about.isNotBlank() || state.picture.isNotBlank()
        } else {
            state.displayName != (orig.displayName ?: "") ||
                state.name != (orig.name ?: "") ||
                state.about != (orig.about ?: "") ||
                state.picture != (orig.picture ?: "") ||
                state.banner != (orig.banner ?: "") ||
                state.nip05 != (orig.nip05 ?: "") ||
                state.lud16 != (orig.lud16 ?: "") ||
                state.website != (orig.website ?: "")
        }
        _uiState.update { it.copy(hasChanges = changed) }
    }

    fun updateDisplayName(value: String) {
        _uiState.update { it.copy(displayName = value) }
        checkForChanges()
    }

    fun updateName(value: String) {
        _uiState.update { it.copy(name = value) }
        checkForChanges()
    }

    fun updateAbout(value: String) {
        _uiState.update { it.copy(about = value) }
        checkForChanges()
    }

    fun updatePicture(value: String) {
        _uiState.update { it.copy(picture = value) }
        checkForChanges()
    }

    fun updateBanner(value: String) {
        _uiState.update { it.copy(banner = value) }
        checkForChanges()
    }

    fun updateNip05(value: String) {
        _uiState.update { it.copy(nip05 = value) }
        checkForChanges()
    }

    fun updateLud16(value: String) {
        _uiState.update { it.copy(lud16 = value) }
        checkForChanges()
    }

    fun updateWebsite(value: String) {
        _uiState.update { it.copy(website = value) }
        checkForChanges()
    }

    fun saveProfile() {
        val state = _uiState.value
        val pubkeyHex = keyManager.getPublicKeyHex() ?: return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val metadata = UserMetadata(
                    pubkey = pubkeyHex,
                    name = state.name.takeIf { it.isNotBlank() },
                    displayName = state.displayName.takeIf { it.isNotBlank() },
                    about = state.about.takeIf { it.isNotBlank() },
                    picture = state.picture.takeIf { it.isNotBlank() },
                    banner = state.banner.takeIf { it.isNotBlank() },
                    nip05 = state.nip05.takeIf { it.isNotBlank() },
                    lud16 = state.lud16.takeIf { it.isNotBlank() },
                    website = state.website.takeIf { it.isNotBlank() }
                )

                val metadataJson = metadata.toJson()

                if (keyManager.isAmberConnected()) {
                    val unsignedEvent = createUnsignedMetadataEvent(metadataJson)
                    val intent = keyManager.createAmberSignEventIntent(
                        eventJson = unsignedEvent,
                        eventId = "profile_update"
                    )
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            needsAmberSigning = intent
                        )
                    }
                } else {
                    val keys = keyManager.getKeys() ?: return@launch
                    val event = EventBuilder(Kind(0u), metadataJson, emptyList())
                        .toEvent(keys)

                    val success = nostrClient.publish(event)
                    if (success) {
                        metadataCache.put(metadata.copy(
                            createdAt = System.currentTimeMillis() / 1000
                        ))
                        originalMetadata = metadata
                    }
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = success,
                            hasChanges = !success,
                            error = if (!success) "Failed to publish to relays" else null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message)
                }
            }
        }
    }

    fun handleAmberSignedEvent(signedEventJson: String) {
        viewModelScope.launch {
            try {
                val event = Event.fromJson(signedEventJson)
                val success = nostrClient.publish(event)
                if (success) {
                    val state = _uiState.value
                    val pubkeyHex = keyManager.getPublicKeyHex() ?: ""
                    val metadata = UserMetadata(
                        pubkey = pubkeyHex,
                        name = state.name.takeIf { it.isNotBlank() },
                        displayName = state.displayName.takeIf { it.isNotBlank() },
                        about = state.about.takeIf { it.isNotBlank() },
                        picture = state.picture.takeIf { it.isNotBlank() },
                        banner = state.banner.takeIf { it.isNotBlank() },
                        nip05 = state.nip05.takeIf { it.isNotBlank() },
                        lud16 = state.lud16.takeIf { it.isNotBlank() },
                        website = state.website.takeIf { it.isNotBlank() },
                        createdAt = System.currentTimeMillis() / 1000
                    )
                    metadataCache.put(metadata)
                    originalMetadata = metadata
                }
                _uiState.update {
                    it.copy(
                        needsAmberSigning = null,
                        saveSuccess = success,
                        hasChanges = !success,
                        error = if (!success) "Failed to publish to relays" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        needsAmberSigning = null,
                        error = e.message
                    )
                }
            }
        }
    }

    fun clearAmberSigningRequest() {
        _uiState.update { it.copy(needsAmberSigning = null) }
    }

    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ==================== Relay Management ====================

    private fun observeRelayStatus() {
        viewModelScope.launch {
            nostrClient.relayStatus.collect { statusMap ->
                val relays = statusMap.map { (url, status) ->
                    RelayInfo(
                        url = url,
                        isConnected = status == NostrClient.RelayStatus.Connected,
                        status = when (status) {
                            is NostrClient.RelayStatus.Connected -> "Connected"
                            is NostrClient.RelayStatus.Connecting -> "Connecting..."
                            is NostrClient.RelayStatus.Disconnected -> "Disconnected"
                            is NostrClient.RelayStatus.Error -> status.message
                        }
                    )
                }.sortedBy { it.url }

                _uiState.update { it.copy(relays = relays) }
            }
        }
    }

    fun showAddRelayDialog() {
        _uiState.update { it.copy(showAddRelayDialog = true) }
    }

    fun hideAddRelayDialog() {
        _uiState.update { it.copy(showAddRelayDialog = false) }
    }

    fun addRelay(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return

        nostrClient.connectSingleRelay(trimmed)
        _uiState.update { it.copy(showAddRelayDialog = false) }
    }

    fun removeRelay(url: String) {
        nostrClient.disconnectRelay(url)
    }

    fun reconnectRelay(url: String) {
        nostrClient.disconnectRelay(url)
        nostrClient.connectSingleRelay(url)
    }

    private fun createUnsignedMetadataEvent(content: String): String {
        val pubkey = keyManager.getPublicKeyHex() ?: ""
        val createdAt = System.currentTimeMillis() / 1000
        return JSONObject().apply {
            put("kind", 0)
            put("pubkey", pubkey)
            put("created_at", createdAt)
            put("tags", JSONArray())
            put("content", content)
        }.toString()
    }
}
