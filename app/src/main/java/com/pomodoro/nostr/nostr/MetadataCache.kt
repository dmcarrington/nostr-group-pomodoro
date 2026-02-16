package com.pomodoro.nostr.nostr

import com.pomodoro.nostr.nostr.models.UserMetadata
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataCache @Inject constructor() {

    private val cache = ConcurrentHashMap<String, UserMetadata>()

    fun put(metadata: UserMetadata) {
        val existing = cache[metadata.pubkey]
        if (existing == null || metadata.createdAt > existing.createdAt) {
            cache[metadata.pubkey] = metadata
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
