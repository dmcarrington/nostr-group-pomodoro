package com.pomodoro.nostr.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import rust.nostr.protocol.Event
import rust.nostr.protocol.EventBuilder
import rust.nostr.protocol.Kind
import rust.nostr.protocol.Tag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPublisher @Inject constructor(
    private val keyManager: KeyManager,
    private val nostrClient: NostrClient,
    private val levelCalculator: LevelCalculator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Publish a session completion event.
     * Returns unsigned event JSON if Amber signing is needed, null if published locally.
     */
    fun publishSession(durationMinutes: Int): String? {
        val level = levelCalculator.calculateCurrentLevel().tag
        if (keyManager.isAmberConnected()) {
            return createUnsignedSessionEvent(durationMinutes, level)
        } else {
            val keys = keyManager.getKeys() ?: return null
            val tags = SessionEvents.createSessionTags(durationMinutes, level).map { tagParts ->
                Tag.parse(tagParts)
            }
            val event = EventBuilder(
                Kind(SessionEvents.KIND_POMODORO_SESSION),
                "",
                tags
            ).toEvent(keys)

            scope.launch { nostrClient.publish(event) }
            return null
        }
    }

    fun createAmberSignIntent(unsignedEventJson: String): android.content.Intent {
        return keyManager.createAmberSignEventIntent(
            eventJson = unsignedEventJson,
            eventId = "pomodoro_session"
        )
    }

    fun publishSignedEvent(signedEventJson: String) {
        scope.launch {
            try {
                val event = Event.fromJson(signedEventJson)
                nostrClient.publish(event)
            } catch (_: Exception) {}
        }
    }

    private fun createUnsignedSessionEvent(durationMinutes: Int, level: String): String {
        val pubkey = keyManager.getPublicKeyHex() ?: ""
        val createdAt = System.currentTimeMillis() / 1000
        val tags = JSONArray().apply {
            put(JSONArray().apply { put("t"); put("pomodoro") })
            put(JSONArray().apply { put("duration"); put(durationMinutes.toString()) })
            put(JSONArray().apply { put("level"); put(level) })
        }
        return JSONObject().apply {
            put("kind", SessionEvents.KIND_POMODORO_SESSION.toInt())
            put("pubkey", pubkey)
            put("created_at", createdAt)
            put("tags", tags)
            put("content", "")
        }.toString()
    }
}
