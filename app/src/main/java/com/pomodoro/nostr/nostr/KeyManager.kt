package com.pomodoro.nostr.nostr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rust.nostr.protocol.Keys
import rust.nostr.protocol.PublicKey
import rust.nostr.protocol.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "nostr_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var cachedKeys: Keys? = null

    private val _authState = MutableStateFlow(AuthState.UNKNOWN)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _pendingAmberCallback = MutableStateFlow<AmberCallbackResult?>(null)
    val pendingAmberCallback: StateFlow<AmberCallbackResult?> = _pendingAmberCallback.asStateFlow()

    fun setPendingAmberCallback(result: AmberCallbackResult) {
        _pendingAmberCallback.value = result
    }

    fun clearPendingAmberCallback() {
        _pendingAmberCallback.value = null
    }

    init {
        _authState.value = when {
            isAmberConnected() -> AuthState.AUTHENTICATED_AMBER
            hasLocalKeys() -> AuthState.AUTHENTICATED_LOCAL
            else -> AuthState.NOT_AUTHENTICATED
        }
    }

    fun isAuthenticated(): Boolean = hasLocalKeys() || isAmberConnected()

    fun hasLocalKeys(): Boolean = securePrefs.contains("nsec")

    fun isAmberConnected(): Boolean = securePrefs.getBoolean("amber_connected", false)

    fun getPublicKey(): PublicKey? {
        val npub = securePrefs.getString("npub", null) ?: return null
        return try {
            PublicKey.fromBech32(npub)
        } catch (e: Exception) {
            null
        }
    }

    fun getPublicKeyHex(): String? {
        return getPublicKey()?.toHex()
    }

    fun getNpub(): String? {
        return securePrefs.getString("npub", null)
    }

    fun getKeys(): Keys? {
        if (isAmberConnected()) return null

        if (cachedKeys != null) return cachedKeys

        val nsec = securePrefs.getString("nsec", null) ?: return null
        return try {
            val secretKey = SecretKey.fromBech32(nsec)
            Keys(secretKey).also { cachedKeys = it }
        } catch (e: Exception) {
            null
        }
    }

    fun importNsec(nsec: String): Result<Keys> {
        return try {
            val secretKey = SecretKey.fromBech32(nsec.trim())
            val keys = Keys(secretKey)

            securePrefs.edit()
                .putString("nsec", nsec.trim())
                .putString("npub", keys.publicKey().toBech32())
                .putBoolean("amber_connected", false)
                .apply()

            cachedKeys = keys
            _authState.value = AuthState.AUTHENTICATED_LOCAL
            Result.success(keys)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateNewKeys(): Keys {
        val keys = Keys.generate()

        securePrefs.edit()
            .putString("nsec", keys.secretKey().toBech32())
            .putString("npub", keys.publicKey().toBech32())
            .putBoolean("amber_connected", false)
            .apply()

        cachedKeys = keys
        _authState.value = AuthState.AUTHENTICATED_LOCAL
        return keys
    }

    fun clearKeys() {
        securePrefs.edit().clear().apply()
        cachedKeys = null
        _authState.value = AuthState.NOT_AUTHENTICATED
    }

    // ==================== Amber Integration (NIP-55) ====================

    fun isAmberInstalled(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
        val activities = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return activities.isNotEmpty()
    }

    fun createAmberGetPublicKeyIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
            putExtra("type", "get_public_key")
        }
    }

    fun createAmberSignEventIntent(eventJson: String, eventId: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$eventJson")).apply {
            putExtra("type", "sign_event")
            putExtra("id", eventId)
        }
    }

    fun saveAmberPublicKey(npub: String): Result<Unit> {
        return try {
            PublicKey.fromBech32(npub.trim())

            securePrefs.edit()
                .putString("npub", npub.trim())
                .putBoolean("amber_connected", true)
                .remove("nsec")
                .apply()

            cachedKeys = null
            _authState.value = AuthState.AUTHENTICATED_AMBER
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveAmberPublicKeyAny(pubkeyString: String): Result<Unit> {
        return try {
            val trimmed = pubkeyString.trim()

            val pubkey = when {
                trimmed.startsWith("npub1") -> PublicKey.fromBech32(trimmed)
                trimmed.length == 64 && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } ->
                    PublicKey.fromHex(trimmed)
                else -> throw IllegalArgumentException(
                    "Unrecognized format (len=${trimmed.length}, first20='${trimmed.take(20)}')"
                )
            }
            val npub = pubkey.toBech32()

            securePrefs.edit()
                .putString("npub", npub)
                .putBoolean("amber_connected", true)
                .remove("nsec")
                .apply()

            cachedKeys = null
            _authState.value = AuthState.AUTHENTICATED_AMBER
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Received '${pubkeyString.take(30)}...': ${e.message}"))
        }
    }

    fun parseAmberCallback(uri: Uri): AmberCallbackResult {
        return when (uri.host) {
            "callback" -> {
                val signature = uri.getQueryParameter("signature")
                val event = uri.getQueryParameter("event")
                val npub = uri.getQueryParameter("npub")
                val error = uri.getQueryParameter("error")
                val result = uri.getQueryParameter("result")
                val type = uri.getQueryParameter("type")

                when {
                    error != null -> AmberCallbackResult.Error(error)
                    npub != null -> AmberCallbackResult.PublicKey(npub)
                    type == "nip04_encrypt" && result != null -> AmberCallbackResult.EncryptedContent(result)
                    type == "nip04_decrypt" && result != null -> AmberCallbackResult.DecryptedContent(result)
                    signature != null -> AmberCallbackResult.Signature(signature, event)
                    event != null -> AmberCallbackResult.SignedEvent(event)
                    else -> AmberCallbackResult.Error("Unknown callback format")
                }
            }
            else -> AmberCallbackResult.Error("Unknown callback host: ${uri.host}")
        }
    }

    enum class AuthState {
        UNKNOWN,
        NOT_AUTHENTICATED,
        AUTHENTICATED_LOCAL,
        AUTHENTICATED_AMBER
    }
}

sealed class AmberCallbackResult {
    data class PublicKey(val npub: String) : AmberCallbackResult()
    data class Signature(val signature: String, val event: String?) : AmberCallbackResult()
    data class SignedEvent(val eventJson: String) : AmberCallbackResult()
    data class EncryptedContent(val ciphertext: String) : AmberCallbackResult()
    data class DecryptedContent(val plaintext: String) : AmberCallbackResult()
    data class Error(val message: String) : AmberCallbackResult()
}
