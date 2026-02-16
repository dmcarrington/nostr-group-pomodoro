package com.pomodoro.nostr.nostr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("pomodoro_contacts", Context.MODE_PRIVATE)

    private val _contacts = MutableStateFlow<Set<String>>(emptySet())
    val contacts: StateFlow<Set<String>> = _contacts.asStateFlow()

    init {
        _contacts.value = prefs.getStringSet("friends", emptySet()) ?: emptySet()
    }

    fun addContact(pubkeyHex: String) {
        val updated = _contacts.value + pubkeyHex
        prefs.edit().putStringSet("friends", updated).apply()
        _contacts.value = updated
    }

    fun removeContact(pubkeyHex: String) {
        val updated = _contacts.value - pubkeyHex
        prefs.edit().putStringSet("friends", updated).apply()
        _contacts.value = updated
    }

    fun isContact(pubkeyHex: String): Boolean = pubkeyHex in _contacts.value
}
