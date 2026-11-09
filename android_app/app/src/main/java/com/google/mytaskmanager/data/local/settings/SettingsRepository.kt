package com.google.mytaskmanager.data.local.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.mytaskmanager.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "settings_prefs")

/** User preferences that survive logout: theme, diagnostics opt-in and the "onboarding seen" flag. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics_enabled")
        val ONBOARDING = booleanPreferencesKey("onboarding_seen")
    }

    private val data: Flow<Preferences> = context.settingsStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    /** Selected theme; defaults to [ThemeMode.AUTOMATIC]. */
    val themeMode: Flow<ThemeMode> = data.map { p ->
        ThemeMode.entries.firstOrNull { it.name == p[Keys.THEME] } ?: ThemeMode.AUTOMATIC
    }.distinctUntilChanged()

    /** Whether crash/error reports may be sent to the developers (default: on). */
    val diagnosticsEnabled: Flow<Boolean> = data.map { it[Keys.DIAGNOSTICS] ?: true }.distinctUntilChanged()

    /** Whether the intro slides were already shown. */
    val onboardingSeen: Flow<Boolean> = data.map { it[Keys.ONBOARDING] ?: false }.distinctUntilChanged()

    /** Persists the theme choice; the UI reacts immediately because [themeMode] is a Flow. */
    suspend fun setThemeMode(mode: ThemeMode) { context.settingsStore.edit { it[Keys.THEME] = mode.name } }

    /** Persists the diagnostics opt-in. */
    suspend fun setDiagnosticsEnabled(enabled: Boolean) { context.settingsStore.edit { it[Keys.DIAGNOSTICS] = enabled } }

    /** Remembers that onboarding was shown. */
    suspend fun setOnboardingSeen() { context.settingsStore.edit { it[Keys.ONBOARDING] = true } }
}
