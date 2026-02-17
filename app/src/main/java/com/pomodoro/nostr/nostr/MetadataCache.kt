package com.pomodoro.nostr.nostr

import com.pomodoro.nostr.nostr.models.UserMetadata
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataCache @Inject constructor() {

    private val cache = ConcurrentHashMap<String, UserMetadata>()

    private val _updates = MutableSharedFlow<UserMetadata>(extraBufferCapacity = 16)
    val updates: SharedFlow<UserMetadata> = _updates.asSharedFlow()

    fun put(metadata: UserMetadata) {
        val existing = cache[metadata.pubkey]
        if (existing == null || metadata.createdAt > existing.createdAt) {
            cache[metadata.pubkey] = metadata
            _updates.tryEmit(metadata)
        }
    }

    fun get(pubkey: String): UserMetadata? {
        return cache[pubkey]
    }

    fun contains(pubkey: String): Boolean {
        return cache.containsKey(pubkey)
    }

    fun clear() {
        cache.clear()
    }
}
