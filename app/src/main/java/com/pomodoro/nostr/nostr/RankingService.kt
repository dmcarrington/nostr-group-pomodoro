package com.pomodoro.nostr.nostr

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
import javax.inject.Inject
import javax.inject.Singleton

data class RankingEntry(
    val pubkeyHex: String,
    val sessionCount: Int,
    val level: PomodoroLevel? = null
)

data class Rankings(
    val daily: List<RankingEntry>,
    val weekly: List<RankingEntry>,
    val monthly: List<RankingEntry>
)

@Singleton
class RankingService @Inject constructor() {

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

    suspend fun fetchRankings(pubkeys: Set<String>): Rankings = withContext(Dispatchers.IO) {
        if (pubkeys.isEmpty()) return@withContext Rankings(emptyList(), emptyList(), emptyList())

        val now = System.currentTimeMillis() / 1000
        val oneDayAgo = now - 86400
        val oneWeekAgo = now - 604800
        val oneMonthAgo = now - 2592000

        val events = fetchSessionEvents(pubkeys.toList(), oneMonthAgo)

        val dailyCounts = mutableMapOf<String, Int>()
        val weeklyCounts = mutableMapOf<String, Int>()
        val monthlyCounts = mutableMapOf<String, Int>()
        val latestLevel = mutableMapOf<String, Pair<Long, PomodoroLevel>>()

        pubkeys.forEach { pk ->
            dailyCounts[pk] = 0
            weeklyCounts[pk] = 0
            monthlyCounts[pk] = 0
        }

        events.forEach { (pubkey, timestamp, levelTag) ->
            monthlyCounts[pubkey] = (monthlyCounts[pubkey] ?: 0) + 1
            if (timestamp >= oneWeekAgo) {
                weeklyCounts[pubkey] = (weeklyCounts[pubkey] ?: 0) + 1
            }
            if (timestamp >= oneDayAgo) {
                dailyCounts[pubkey] = (dailyCounts[pubkey] ?: 0) + 1
            }
            if (levelTag != null) {
                val current = latestLevel[pubkey]
                if (current == null || timestamp > current.first) {
                    latestLevel[pubkey] = timestamp to PomodoroLevel.fromTag(levelTag)
                }
            }
        }

        Rankings(
            daily = dailyCounts.map { RankingEntry(it.key, it.value, latestLevel[it.key]?.second) }
                .sortedByDescending { it.sessionCount },
            weekly = weeklyCounts.map { RankingEntry(it.key, it.value, latestLevel[it.key]?.second) }
                .sortedByDescending { it.sessionCount },
            monthly = monthlyCounts.map { RankingEntry(it.key, it.value, latestLevel[it.key]?.second) }
                .sortedByDescending { it.sessionCount }
        )
    }

    private suspend fun fetchSessionEvents(
        pubkeys: List<String>,
        sinceTimestamp: Long
    ): List<Triple<String, Long, String?>> {
        val allResults = mutableListOf<Triple<String, Long, String?>>()

        for (relayUrl in relays) {
            try {
                val relayResults = fetchFromRelay(relayUrl, pubkeys, sinceTimestamp)
                synchronized(allResults) {
                    allResults.addAll(relayResults)
                }
                if (allResults.isNotEmpty()) break
            } catch (_: Exception) {
                continue
            }
        }

        return allResults
    }

    private suspend fun fetchFromRelay(
        relayUrl: String,
        pubkeys: List<String>,
        sinceTimestamp: Long
    ): List<Triple<String, Long, String?>> {
        val results = mutableListOf<Triple<String, Long, String?>>()
        val completed = CompletableDeferred<Unit>()
        val subscriptionId = "ranking_${System.currentTimeMillis()}"

        val filterJson = JSONObject().apply {
            put("kinds", JSONArray().put(SessionEvents.KIND_POMODORO_SESSION.toInt()))
            put("authors", JSONArray().apply { pubkeys.forEach { put(it) } })
            put("since", sinceTimestamp)
            put("limit", 500)
        }

        val reqMessage = JSONArray().apply {
            put("REQ")
            put(subscriptionId)
            put(filterJson)
        }.toString()

        val request = Request.Builder().url(relayUrl).build()

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
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
                            val pubkey = event.author().toHex()
                            val timestamp = event.createdAt().asSecs().toLong()

                            // Parse level tag from event
                            val levelTag = try {
                                val tags = eventJsonObj.getJSONArray("tags")
                                var found: String? = null
                                for (i in 0 until tags.length()) {
                                    val tag = tags.getJSONArray(i)
                                    if (tag.length() >= 2 && tag.getString(0) == "level") {
                                        found = tag.getString(1)
                                    }
                                }
                                found
                            } catch (_: Exception) { null }

                            synchronized(results) {
                                results.add(Triple(pubkey, timestamp, levelTag))
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

        val webSocket = client.newWebSocket(request, listener)

        withTimeoutOrNull(8000) { completed.await() }

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
