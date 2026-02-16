package com.pomodoro.nostr.nostr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import rust.nostr.protocol.Event
import rust.nostr.protocol.Filter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NostrClient @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val relayConnections = ConcurrentHashMap<String, RelayConnection>()
    private val activeSubscriptions = ConcurrentHashMap<String, List<Filter>>()

    private val _events = MutableSharedFlow<NostrEvent>(replay = 0, extraBufferCapacity = 1000)
    val events: SharedFlow<NostrEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _relayStatus = MutableStateFlow<Map<String, RelayStatus>>(emptyMap())
    val relayStatus: StateFlow<Map<String, RelayStatus>> = _relayStatus.asStateFlow()

    val defaultRelays = listOf(
        "wss://relay.damus.io",
        "wss://relay.primal.net",
        "wss://nos.lol",
        "wss://relay.nostr.band"
    )

    suspend fun connect(relayUrls: List<String> = defaultRelays) {
        _connectionState.value = ConnectionState.Connecting

        relayUrls.forEach { url ->
            if (!relayConnections.containsKey(url)) {
                connectToRelay(url)
            }
        }

        updateConnectionState()
    }

    private fun connectToRelay(url: String) {
        val normalizedUrl = normalizeRelayUrl(url)

        val request = Request.Builder()
            .url(normalizedUrl)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                updateRelayStatus(normalizedUrl, RelayStatus.Connected)
                updateConnectionState()

                activeSubscriptions.forEach { (subId, filters) ->
                    sendSubscription(webSocket, subId, filters)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(normalizedUrl, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                relayConnections.remove(normalizedUrl)
                updateRelayStatus(normalizedUrl, RelayStatus.Disconnected)
                updateConnectionState()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                relayConnections.remove(normalizedUrl)
                updateRelayStatus(normalizedUrl, RelayStatus.Error(t.message ?: "Unknown error"))
                updateConnectionState()
            }
        }

        val webSocket = httpClient.newWebSocket(request, listener)
        relayConnections[normalizedUrl] = RelayConnection(normalizedUrl, webSocket)
        updateRelayStatus(normalizedUrl, RelayStatus.Connecting)
    }

    suspend fun reconnect(relayUrls: List<String> = defaultRelays) {
        val currentStatus = _relayStatus.value
        val needsReconnect = relayUrls.filter { url ->
            val normalizedUrl = normalizeRelayUrl(url)
            val status = currentStatus[normalizedUrl]
            status != RelayStatus.Connected && status != RelayStatus.Connecting
        }

        if (needsReconnect.isNotEmpty()) {
            connect(needsReconnect)
        }
    }

    fun disconnect() {
        relayConnections.values.forEach { connection ->
            connection.webSocket.close(1000, "Client disconnect")
        }
        relayConnections.clear()
        _connectionState.value = ConnectionState.Disconnected
        _relayStatus.value = emptyMap()
    }

    fun disconnectRelay(url: String) {
        val normalizedUrl = normalizeRelayUrl(url)
        relayConnections.remove(normalizedUrl)?.webSocket?.close(1000, "Client disconnect")
        _relayStatus.update { it - normalizedUrl }
        updateConnectionState()
    }

    fun connectSingleRelay(url: String) {
        val normalizedUrl = normalizeRelayUrl(url)
        if (!relayConnections.containsKey(normalizedUrl)) {
            connectToRelay(normalizedUrl)
        }
    }

    fun subscribe(subscriptionId: String, filters: List<Filter>) {
        activeSubscriptions[subscriptionId] = filters

        relayConnections.values.forEach { connection ->
            sendSubscription(connection.webSocket, subscriptionId, filters)
        }
    }

    fun unsubscribe(subscriptionId: String) {
        activeSubscriptions.remove(subscriptionId)

        val closeMessage = JSONArray().apply {
            put("CLOSE")
            put(subscriptionId)
        }.toString()

        relayConnections.values.forEach { connection ->
            connection.webSocket.send(closeMessage)
        }
    }

    fun publish(event: Event): Boolean {
        val eventJson = event.asJson()
        val message = JSONArray().apply {
            put("EVENT")
            put(JSONObject(eventJson))
        }.toString()

        var sentToAny = false
        relayConnections.values.forEach { connection ->
            if (_relayStatus.value[connection.url] == RelayStatus.Connected) {
                if (connection.webSocket.send(message)) {
                    sentToAny = true
                }
            }
        }

        return sentToAny
    }

    fun getConnectedRelays(): List<String> {
        return _relayStatus.value
            .filter { it.value == RelayStatus.Connected }
            .keys
            .toList()
    }

    // ==================== Private Helpers ====================

    private fun sendSubscription(webSocket: WebSocket, subscriptionId: String, filters: List<Filter>) {
        val message = JSONArray().apply {
            put("REQ")
            put(subscriptionId)
            filters.forEach { filter ->
                put(JSONObject(filter.asJson()))
            }
        }.toString()

        webSocket.send(message)
    }

    private fun handleMessage(relayUrl: String, message: String) {
        scope.launch {
            try {
                val json = JSONArray(message)
                when (json.getString(0)) {
                    "EVENT" -> {
                        val subscriptionId = json.getString(1)
                        val eventJson = json.getJSONObject(2).toString()
                        val event = Event.fromJson(eventJson)
                        _events.emit(NostrEvent(event, relayUrl, subscriptionId))
                    }
                    "EOSE" -> { /* End of stored events */ }
                    "OK" -> { /* Event publish confirmation */ }
                    "NOTICE" -> { /* Relay notice */ }
                    "CLOSED" -> { /* Subscription closed by relay */ }
                }
            } catch (_: Exception) {
                // Parsing error
            }
        }
    }

    private fun updateRelayStatus(url: String, status: RelayStatus) {
        _relayStatus.update { it + (url to status) }
    }

    private fun updateConnectionState() {
        val statuses = _relayStatus.value.values
        _connectionState.value = when {
            statuses.any { it == RelayStatus.Connected } -> ConnectionState.Connected
            statuses.any { it == RelayStatus.Connecting } -> ConnectionState.Connecting
            statuses.all { it is RelayStatus.Error } -> ConnectionState.Error
            else -> ConnectionState.Disconnected
        }
    }

    private fun normalizeRelayUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("wss://") && !normalized.startsWith("ws://")) {
            normalized = "wss://$normalized"
        }
        return normalized.trimEnd('/')
    }

    private data class RelayConnection(
        val url: String,
        val webSocket: WebSocket
    )

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Error : ConnectionState()
    }

    sealed class RelayStatus {
        object Connecting : RelayStatus()
        object Connected : RelayStatus()
        object Disconnected : RelayStatus()
        data class Error(val message: String) : RelayStatus()
    }
}

data class NostrEvent(
    val event: Event,
    val relayUrl: String,
    val subscriptionId: String
)
