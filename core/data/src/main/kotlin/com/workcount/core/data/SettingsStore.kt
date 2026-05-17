package com.workcount.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.workcount.core.domain.PaySettings
import com.workcount.core.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.paySettingsStore by preferencesDataStore(name = "pay_settings")

class PreferencesSettingsRepository(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) : SettingsRepository {
    private val settingsKey = stringPreferencesKey("settings_json")

    override fun observeSettings(): Flow<PaySettings> {
        return context.paySettingsStore.data.map { preferences ->
            preferences[settingsKey]
                ?.let { stored -> runCatching { json.decodeFromString<PaySettings>(stored) }.getOrNull() }
                ?: PaySettings()
        }
    }

    override suspend fun setSettings(settings: PaySettings) {
        context.paySettingsStore.edit { preferences ->
            preferences[settingsKey] = json.encodeToString(settings)
        }
    }
}
