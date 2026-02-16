package com.pomodoro.nostr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pomodoro.nostr.nostr.ContactsManager
import com.pomodoro.nostr.nostr.KeyManager
import com.pomodoro.nostr.nostr.MetadataCache
import com.pomodoro.nostr.nostr.RankingService
import com.pomodoro.nostr.nostr.Rankings
import com.pomodoro.nostr.nostr.SearchService
import com.pomodoro.nostr.nostr.models.UserMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rust.nostr.protocol.PublicKey
import javax.inject.Inject

data class ContactWithMetadata(
    val pubkeyHex: String,
    val metadata: UserMetadata?
)

data class ContactsUiState(
    val contacts: List<ContactWithMetadata> = emptyList(),
    val rankings: Rankings? = null,
    val isLoadingRankings: Boolean = false,
    val isLoadingContacts: Boolean = false,
    val myPubkeyHex: String? = null,
    val myRankDaily: Int? = null,
    val myRankWeekly: Int? = null,
    val myRankMonthly: Int? = null,
    val myCountDaily: Int = 0,
    val myCountWeekly: Int = 0,
    val myCountMonthly: Int = 0,
    val totalParticipants: Int = 0,
    // Search / add friend
    val showAddDialog: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<UserMetadata> = emptyList(),
    val isSearching: Boolean = false,
    val showQrScanner: Boolean = false,
    val npubInput: String = "",
    val addError: String? = null
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactsManager: ContactsManager,
    private val searchService: SearchService,
    private val rankingService: RankingService,
    private val metadataCache: MetadataCache,
    private val keyManager: KeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        _uiState.update { it.copy(myPubkeyHex = keyManager.getPublicKeyHex()) }

        // Observe contacts changes
        viewModelScope.launch {
            contactsManager.contacts.collect { pubkeys ->
                loadContactsMetadata(pubkeys)
                refreshRankings(pubkeys)
            }
        }
    }

    private fun loadContactsMetadata(pubkeys: Set<String>) {
        if (pubkeys.isEmpty()) {
            _uiState.update { it.copy(contacts = emptyList(), isLoadingContacts = false) }
            return
        }

        // Show immediately from cache
        val cachedContacts = pubkeys.map { pk ->
            ContactWithMetadata(pk, metadataCache.get(pk))
        }
        _uiState.update { it.copy(contacts = cachedContacts, isLoadingContacts = true) }

        // Fetch fresh metadata
        viewModelScope.launch {
            try {
                val uncached = pubkeys.filter { !metadataCache.contains(it) }
                if (uncached.isNotEmpty()) {
                    searchService.fetchMetadataForPubkeys(uncached)
                }
                // Rebuild from cache
                val updatedContacts = pubkeys.map { pk ->
                    ContactWithMetadata(pk, metadataCache.get(pk))
                }
                _uiState.update { it.copy(contacts = updatedContacts, isLoadingContacts = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingContacts = false) }
            }
        }
    }

    private fun refreshRankings(contactPubkeys: Set<String>) {
        val myPubkey = keyManager.getPublicKeyHex() ?: return
        val allPubkeys = contactPubkeys + myPubkey

        _uiState.update { it.copy(isLoadingRankings = true) }

        viewModelScope.launch {
            try {
                val rankings = rankingService.fetchRankings(allPubkeys)
                val myDailyRank = rankings.daily.indexOfFirst { it.pubkeyHex == myPubkey } + 1
                val myWeeklyRank = rankings.weekly.indexOfFirst { it.pubkeyHex == myPubkey } + 1
                val myMonthlyRank = rankings.monthly.indexOfFirst { it.pubkeyHex == myPubkey } + 1
                val myDailyCount = rankings.daily.find { it.pubkeyHex == myPubkey }?.sessionCount ?: 0
                val myWeeklyCount = rankings.weekly.find { it.pubkeyHex == myPubkey }?.sessionCount ?: 0
                val myMonthlyCount = rankings.monthly.find { it.pubkeyHex == myPubkey }?.sessionCount ?: 0

                _uiState.update {
                    it.copy(
                        rankings = rankings,
                        isLoadingRankings = false,
                        myRankDaily = myDailyRank,
                        myRankWeekly = myWeeklyRank,
                        myRankMonthly = myMonthlyRank,
                        myCountDaily = myDailyCount,
                        myCountWeekly = myWeeklyCount,
                        myCountMonthly = myMonthlyCount,
                        totalParticipants = allPubkeys.size
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingRankings = false) }
            }
        }
    }

    fun refreshRankings() {
        refreshRankings(contactsManager.contacts.value)
    }

    // Add friend dialog
    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showAddDialog = true,
                searchQuery = "",
                searchResults = emptyList(),
                npubInput = "",
                addError = null
            )
        }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        searchJob?.cancel()
        if (query.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // debounce
            _uiState.update { it.copy(isSearching = true) }
            try {
                val results = searchService.searchUsers(query)
                // Filter out self and existing contacts
                val myPubkey = keyManager.getPublicKeyHex()
                val contacts = contactsManager.contacts.value
                val filtered = results.filter { it.pubkey != myPubkey && it.pubkey !in contacts }
                _uiState.update { it.copy(searchResults = filtered, isSearching = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun updateNpubInput(value: String) {
        _uiState.update { it.copy(npubInput = value, addError = null) }
    }

    fun addContactByNpub() {
        val input = _uiState.value.npubInput.trim()
        addContactFromString(input)
    }

    fun addContactFromSearch(pubkeyHex: String) {
        contactsManager.addContact(pubkeyHex)
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun addContactFromQr(scannedValue: String) {
        _uiState.update { it.copy(showQrScanner = false) }
        addContactFromString(scannedValue.trim())
    }

    private fun addContactFromString(input: String) {
        try {
            val pubkeyHex = when {
                input.startsWith("npub1") -> {
                    PublicKey.fromBech32(input).toHex()
                }
                input.length == 64 && input.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> {
                    PublicKey.fromHex(input).toHex()
                }
                else -> {
                    _uiState.update { it.copy(addError = "Invalid npub or hex key") }
                    return
                }
            }

            val myPubkey = keyManager.getPublicKeyHex()
            if (pubkeyHex == myPubkey) {
                _uiState.update { it.copy(addError = "You can't add yourself") }
                return
            }

            if (contactsManager.isContact(pubkeyHex)) {
                _uiState.update { it.copy(addError = "Already in your contacts") }
                return
            }

            contactsManager.addContact(pubkeyHex)
            _uiState.update { it.copy(showAddDialog = false, npubInput = "", addError = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(addError = "Invalid key: ${e.message}") }
        }
    }

    fun removeContact(pubkeyHex: String) {
        contactsManager.removeContact(pubkeyHex)
    }

    fun showQrScanner() {
        _uiState.update { it.copy(showQrScanner = true) }
    }

    fun hideQrScanner() {
        _uiState.update { it.copy(showQrScanner = false) }
    }
}
