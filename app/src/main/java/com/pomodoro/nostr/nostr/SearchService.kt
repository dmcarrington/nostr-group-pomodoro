package com.pomodoro.nostr.nostr

import com.pomodoro.nostr.nostr.models.UserMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchService @Inject constructor(
    private val metadataCache: MetadataCache
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val searchRelays = listOf(
        "wss://relay.nostr.band",
        "wss://search.nos.today"
    )

    suspend fun searchUsers(query: String): List<UserMetadata> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, UserMetadata>()

        for (relayUrl in searchRelays) {
            try {
                val relayResults = searchViaRelay(relayUrl, query)
                relayResults.forEach { metadata ->
                    if (!results.containsKey(metadata.pubkey)) {
                        results[metadata.pubkey] = metadata
                    }
                }
                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                continue
            }
        }

        results.values.toList().sortedBy { it.bestName.lowercase() }
    }

    private suspend fun searchViaRelay(relayUrl: String, query: String): List<UserMetadata> {
        val results = mutableListOf<UserMetadata>()
        val completed = CompletableDeferred<Unit>()
        val isConnected = AtomicBoolean(false)

        val subscriptionId = "search_${System.currentTimeMillis()}"

        val filterJson = JSONObject().apply {
            put("kinds", JSONArray().put(0))
            put("search", query)
            put("limit", 30)
        }

        val reqMessage = JSONArray().apply {
            put("REQ")
            put(subscriptionId)
            put(filterJson)
        }.toString()

        val request = Request.Builder()
            .url(relayUrl)
            .build()

        var webSocket: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected.set(true)
                ws.send(reqMessage)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONArray(text)
                    when (json.getString(0)) {
                        "EVENT" -> {
                            val eventJson = json.getJSONObject(2).toString()
                            val event = Event.fromJson(eventJson)
                            val pubkey = event.author().toHex()
                            val metadata = UserMetadata.fromJson(
                                pubkey = pubkey,
                                json = event.content(),
                                createdAt = event.createdAt().asSecs().toLong()
                            )
                            metadataCache.put(metadata)
                            synchronized(results) {
                                results.add(metadata)
                            }
                        }
                        "EOSE" -> completed.complete(Unit)
                        "CLOSED" -> completed.complete(Unit)
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                completed.complete(Unit)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                completed.complete(Unit)
            }
        }

        webSocket = client.newWebSocket(request, listener)

        withTimeoutOrNull(5000) {
            completed.await()
        }

        try {
            val closeMessage = JSONArray().apply {
                put("CLOSE")
                put(subscriptionId)
            }.toString()
            webSocket.send(closeMessage)
            webSocket.close(1000, "Search complete")
        } catch (_: Exception) {}

        return results
    }

    suspend fun fetchMetadataForPubkeys(pubkeys: List<String>): List<UserMetadata> = withContext(Dispatchers.IO) {
        if (pubkeys.isEmpty()) return@withContext emptyList()

        val results = mutableMapOf<String, UserMetadata>()

        val relays = listOf(
            "wss://relay.nostr.band",
            "wss://relay.damus.io",
            "wss://relay.primal.net"
        )

        for (relayUrl in relays) {
            try {
                val relayResults = fetchMetadataFromRelay(relayUrl, pubkeys)
                relayResults.forEach { metadata ->
                    if (!results.containsKey(metadata.pubkey)) {
                        results[metadata.pubkey] = metadata
                        metadataCache.put(metadata)
                    }
                }
                if (results.size >= pubkeys.size) break
            } catch (_: Exception) {
                continue
            }
        }

        results.values.toList()
    }

    private suspend fun fetchMetadataFromRelay(relayUrl: String, pubkeys: List<String>): List<UserMetadata> {
        val results = mutableListOf<UserMetadata>()
        val completed = CompletableDeferred<Unit>()

        val subscriptionId = "metadata_batch_${System.currentTimeMillis()}"

        val filterJson = JSONObject().apply {
            put("kinds", JSONArray().put(0))
            put("authors", JSONArray().apply {
                pubkeys.forEach { put(it) }
            })
        }

        val reqMessage = JSONArray().apply {
            put("REQ")
            put(subscriptionId)
            put(filterJson)
        }.toString()

        val request = Request.Builder()
            .url(relayUrl)
            .build()

        var webSocket: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send(reqMessage)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONArray(text)
                    when (json.getString(0)) {
                        "EVENT" -> {
                            val eventJson = json.getJSONObject(2).toString()
                            val event = Event.fromJson(eventJson)
                            val pubkey = event.author().toHex()
                            val metadata = UserMetadata.fromJson(
                                pubkey = pubkey,
                                json = event.content(),
                                createdAt = event.createdAt().asSecs().toLong()
                            )
                            synchronized(results) {
                                results.add(metadata)
                            }
                        }
                        "EOSE" -> completed.complete(Unit)
                        "CLOSED" -> completed.complete(Unit)
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                completed.complete(Unit)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                completed.complete(Unit)
            }
        }

        webSocket = client.newWebSocket(request, listener)

        withTimeoutOrNull(5000) {
            completed.await()
        }

        try {
            val closeMessage = JSONArray().apply {
                put("CLOSE")
                put(subscriptionId)
            }.toString()
            webSocket.send(closeMessage)
            webSocket.close(1000, "Fetch complete")
        } catch (_: Exception) {}

        return results
    }
}
