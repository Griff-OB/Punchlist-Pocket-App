package com.punchlist.pocket.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single [DataStore] instance for app preferences. Declared as a
 * top-level extension so the Application context owns it (one per process).
 */
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "punchlist_settings"
)

/**
 * Persists user-controlled settings via DataStore Preferences. Exposes the
 * values as cold flows; callers convert to state at the Compose boundary.
 */
class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val onboardingKey = booleanPreferencesKey("onboarding_completed")

    /** The chosen appearance mode, defaulting to "follow the system". */
    val themeMode: Flow<ThemeMode> = context.appDataStore.data.map { prefs ->
        prefs[themeKey]?.let { name ->
            runCatching { ThemeMode.valueOf(name) }.getOrNull()
        } ?: ThemeMode.SYSTEM
    }

    /** Stores the user's appearance choice. */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.appDataStore.edit { it[themeKey] = mode.name }
    }

    /**
     * Whether the first-launch tutorial has been completed. False until the
     * user finishes (or skips) the onboarding flow, so the overlay shows once
     * per device install.
     */
    val onboardingCompleted: Flow<Boolean> = context.appDataStore.data.map { prefs ->
        prefs[onboardingKey] ?: false
    }

    /** Marks the tutorial as completed so it won't show again on next launch. */
    suspend fun setOnboardingCompleted(done: Boolean) {
        context.appDataStore.edit { it[onboardingKey] = done }
    }
}

