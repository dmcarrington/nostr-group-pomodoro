package com.pomodoro.nostr.nostr

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import rust.nostr.protocol.Event
import rust.nostr.protocol.EventBuilder
import rust.nostr.protocol.Kind
import rust.nostr.protocol.Tag
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendSignalService @Inject constructor(
    private val keyManager: KeyManager,
    private val nostrClient: NostrClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val relays = listOf(
        "wss://relay.damus.io",
        "wss://relay.primal.net",
        "wss://nos.lol"
    )

    companion object {
        private const val TAG = "FriendSignalService"
        const val KIND_FRIEND_SIGNAL: UShort = 8809u
    }

    /**
     * Publish a friend-add signal event tagging the given pubkey.
     * Returns unsigned event JSON if Amber signing is needed, null if published locally.
     */
    fun publishFriendAdd(targetPubkeyHex: String): String? {
        Log.d(TAG, "publishFriendAdd called for target: ${targetPubkeyHex.take(12)}...")
        if (keyManager.isAmberConnected()) {
            return createUnsignedFriendEvent(targetPubkeyHex)
        } else {
            val keys = keyManager.getKeys() ?: run {
                Log.e(TAG, "No keys available for signing")
                return null
            }
            val tags = listOf(
                Tag.parse(listOf("p", targetPubkeyHex)),
                Tag.parse(listOf("t", "pomodoro-friend"))
            )
            val event = EventBuilder(
                Kind(KIND_FRIEND_SIGNAL),
                "",
                tags
            ).toEvent(keys)

            val eventJson = event.asJson()
            Log.d(TAG, "Created signed event: kind=${KIND_FRIEND_SIGNAL}, id=${event.id().toHex().take(12)}...")
            scope.launch {
                publishToRelays(eventJson)
            }
            return null
        }
    }

    fun publishSignedEvent(signedEventJson: String) {
        scope.launch {
            publishToRelays(signedEventJson)
        }
    }

    /**
     * Fetch inbound friend-add signals where my pubkey is tagged.
     * Returns set of pubkey hex strings of users who have added me.
     */
    suspend fun fetchInboundFriendSignals(): Set<String> = withContext(Dispatchers.IO) {
        val myPubkey = keyManager.getPublicKeyHex() ?: return@withContext emptySet()
        Log.d(TAG, "Fetching inbound friend signals for: ${myPubkey.take(12)}...")
        val results = mutableSetOf<String>()

        // Try all relays, don't break early
        for (relayUrl in relays) {
            try {
                val relayResults = fetchFromRelay(relayUrl, myPubkey)
                Log.d(TAG, "Relay $relayUrl returned ${relayResults.size} friend signals")
                results.addAll(relayResults)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch from $relayUrl: ${e.message}")
                continue
            }
        }

        Log.d(TAG, "Total inbound friend signals: ${results.size}")
        results
    }

    /**
     * Publish event JSON directly to relays via dedicated WebSocket connections.
     */
    private suspend fun publishToRelays(eventJson: String) = withContext(Dispatchers.IO) {
        val eventMessage = JSONArray().apply {
            put("EVENT")
            put(JSONObject(eventJson))
        }.toString()

        Log.d(TAG, "Publishing friend signal to ${relays.size} relays")
        for (relayUrl in relays) {
            try {
                publishToRelay(relayUrl, eventMessage)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to publish to $relayUrl: ${e.message}")
            }
        }
    }

    private suspend fun publishToRelay(relayUrl: String, eventMessage: String) {
        val completed = CompletableDeferred<Unit>()

        val request = Request.Builder().url(relayUrl).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Connected to $relayUrl, sending event...")
                ws.send(eventMessage)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONArray(text)
                    when (json.getString(0)) {
                        "OK" -> {
                            val accepted = json.getBoolean(2)
                            if (accepted) {
                                Log.d(TAG, "Friend signal ACCEPTED by $relayUrl")
                            } else {
                                val reason = if (json.length() > 3) json.getString(3) else "unknown"
                                Log.w(TAG, "Friend signal REJECTED by $relayUrl: $reason")
                            }
                            completed.complete(Unit)
                        }
                        "NOTICE" -> {
                            Log.w(TAG, "Relay notice from $relayUrl: ${json.getString(1)}")
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure for $relayUrl: ${t.message}")
                completed.complete(Unit)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                completed.complete(Unit)
            }
        }

        val webSocket = client.newWebSocket(request, listener)

        val result = withTimeoutOrNull(5000) { completed.await() }
        if (result == null) {
            Log.w(TAG, "Publish to $relayUrl timed out after 5s")
        }

        try {
            webSocket.close(1000, "Done")
        } catch (_: Exception) {}
    }

    private fun createUnsignedFriendEvent(targetPubkeyHex: String): String {
        val pubkey = keyManager.getPublicKeyHex() ?: ""
        val createdAt = System.currentTimeMillis() / 1000
        val tags = JSONArray().apply {
            put(JSONArray().apply { put("p"); put(targetPubkeyHex) })
            put(JSONArray().apply { put("t"); put("pomodoro-friend") })
        }
        return JSONObject().apply {
            put("kind", KIND_FRIEND_SIGNAL.toInt())
            put("pubkey", pubkey)
            put("created_at", createdAt)
            put("tags", tags)
            put("content", "")
        }.toString()
    }

    private suspend fun fetchFromRelay(
        relayUrl: String,
        myPubkeyHex: String
    ): Set<String> {
        val results = mutableSetOf<String>()
        val completed = CompletableDeferred<Unit>()
        val subscriptionId = "friend_${System.currentTimeMillis()}"

        val filterJson = JSONObject().apply {
            put("kinds", JSONArray().put(KIND_FRIEND_SIGNAL.toInt()))
            put("#p", JSONArray().put(myPubkeyHex))
            put("limit", 200)
        }

        val reqMessage = JSONArray().apply {
            put("REQ")
            put(subscriptionId)
            put(filterJson)
        }.toString()

        Log.d(TAG, "Fetching from $relayUrl with filter: $filterJson")

        val request = Request.Builder().url(relayUrl).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Fetch connected to $relayUrl, sending REQ...")
                ws.send(reqMessage)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONArray(text)
                    when (json.getString(0)) {
                        "EVENT" -> {
                            val eventJsonObj = json.getJSONObject(2)
                            val eventJson = eventJsonObj.toString()
                            val event = Event.fromJson(eventJson)
                            val authorPubkey = event.author().toHex()
                            Log.d(TAG, "Received friend signal from: ${authorPubkey.take(12)}...")
                            if (authorPubkey != myPubkeyHex) {
                                synchronized(results) {
                                    results.add(authorPubkey)
                                }
                            }
                        }
                        "EOSE" -> {
                            Log.d(TAG, "EOSE from $relayUrl, found ${results.size} signals")
                            completed.complete(Unit)
                        }
                        "CLOSED" -> {
                            val reason = if (json.length() > 2) json.getString(2) else "unknown"
                            Log.w(TAG, "Subscription CLOSED by $relayUrl: $reason")
                            completed.complete(Unit)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing message from $relayUrl: ${e.message}")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Fetch WebSocket failure for $relayUrl: ${t.message}")
                completed.complete(Unit)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                completed.complete(Unit)
            }
        }

        val webSocket = client.newWebSocket(request, listener)

        val result = withTimeoutOrNull(8000) { completed.await() }
        if (result == null) {
            Log.w(TAG, "Fetch from $relayUrl timed out after 8s")
        }

        try {
            webSocket.send(JSONArray().apply {
                put("CLOSE")
                put(subscriptionId)
            }.toString())
            webSocket.close(1000, "Done")
        } catch (_: Exception) {}

        return results
    }
}
