package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auraglass_prefs")

data class LocalAppPreferences(
    val languageCode: String = "tr",
    val darkMode: String = "system",
    val offlineModeManual: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val notifyDigest: Boolean = true,
    val notifySync: Boolean = true,
    val notifyAi: Boolean = true,
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 7
)

class UserPreferencesStore(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language_code")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val OFFLINE_MANUAL = booleanPreferencesKey("offline_manual")
        val NOTIFY_ENABLED = booleanPreferencesKey("notify_enabled")
        val NOTIFY_DIGEST = booleanPreferencesKey("notify_digest")
        val NOTIFY_SYNC = booleanPreferencesKey("notify_sync")
        val NOTIFY_AI = booleanPreferencesKey("notify_ai")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
    }

    val preferences: Flow<LocalAppPreferences> = context.applicationContext.dataStore.data.map { prefs ->
        LocalAppPreferences(
            languageCode = prefs[Keys.LANGUAGE] ?: "tr",
            darkMode = prefs[Keys.DARK_MODE] ?: "system",
            offlineModeManual = prefs[Keys.OFFLINE_MANUAL] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFY_ENABLED] ?: true,
            notifyDigest = prefs[Keys.NOTIFY_DIGEST] ?: true,
            notifySync = prefs[Keys.NOTIFY_SYNC] ?: true,
            notifyAi = prefs[Keys.NOTIFY_AI] ?: true,
            quietStartHour = prefs[Keys.QUIET_START] ?: 23,
            quietEndHour = prefs[Keys.QUIET_END] ?: 7
        )
    }

    suspend fun setLanguage(code: String) {
        context.applicationContext.dataStore.edit { it[Keys.LANGUAGE] = code }
    }

    suspend fun setDarkMode(mode: String) {
        context.applicationContext.dataStore.edit { it[Keys.DARK_MODE] = mode }
    }

    suspend fun setOfflineManual(enabled: Boolean) {
        context.applicationContext.dataStore.edit { it[Keys.OFFLINE_MANUAL] = enabled }
    }

    suspend fun updateNotificationPrefs(
        master: Boolean,
        digest: Boolean,
        sync: Boolean,
        ai: Boolean,
        quietStart: Int,
        quietEnd: Int
    ) {
        context.applicationContext.dataStore.edit {
            it[Keys.NOTIFY_ENABLED] = master
            it[Keys.NOTIFY_DIGEST] = digest
            it[Keys.NOTIFY_SYNC] = sync
            it[Keys.NOTIFY_AI] = ai
            it[Keys.QUIET_START] = quietStart
            it[Keys.QUIET_END] = quietEnd
        }
    }
}
