package com.adamway.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PreferredNavApp { GOOGLE_MAPS, WAZE }

/**
 * Everything here stays on-device: an Android Keystore-backed encrypted file,
 * never synced or transmitted anywhere. The What3Words key is the one piece
 * of user-entered secret data in the app, hence the encryption.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "adam_way_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _what3wordsApiKey = MutableStateFlow(prefs.getString(KEY_W3W_API_KEY, "") ?: "")
    val what3wordsApiKey: StateFlow<String> = _what3wordsApiKey.asStateFlow()

    private val _preferredNavApp = MutableStateFlow(
        PreferredNavApp.valueOf(prefs.getString(KEY_PREFERRED_NAV_APP, PreferredNavApp.GOOGLE_MAPS.name)!!)
    )
    val preferredNavApp: StateFlow<PreferredNavApp> = _preferredNavApp.asStateFlow()

    fun setWhat3wordsApiKey(key: String) {
        prefs.edit().putString(KEY_W3W_API_KEY, key.trim()).apply()
        _what3wordsApiKey.value = key.trim()
    }

    fun setPreferredNavApp(app: PreferredNavApp) {
        prefs.edit().putString(KEY_PREFERRED_NAV_APP, app.name).apply()
        _preferredNavApp.value = app
    }

    companion object {
        private const val KEY_W3W_API_KEY = "what3words_api_key"
        private const val KEY_PREFERRED_NAV_APP = "preferred_nav_app"
    }
}
