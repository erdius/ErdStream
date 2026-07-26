package com.erdman.erdstream.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
)

/**
 * Stores the Navidrome/Subsonic server URL and login credentials in
 * encrypted SharedPreferences (the password is needed in plaintext on-device
 * to compute the Subsonic token-auth hash per request; it is never sent
 * as plaintext over the network).
 */
class CredentialsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _credentials = MutableStateFlow(readCredentials())
    val credentials: StateFlow<ServerCredentials?> = _credentials.asStateFlow()

    private fun readCredentials(): ServerCredentials? {
        val url = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return ServerCredentials(url, username, password)
    }

    fun save(serverUrl: String, username: String, password: String) {
        val normalizedUrl = serverUrl.trim().trimEnd('/')
        prefs.edit()
            .putString(KEY_SERVER_URL, normalizedUrl)
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
        _credentials.value = ServerCredentials(normalizedUrl, username.trim(), password)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _credentials.value = null
    }

    companion object {
        private const val PREFS_NAME = "erdstream_credentials"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
