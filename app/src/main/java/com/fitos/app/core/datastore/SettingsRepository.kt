package com.fitos.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitos.app.domain.model.ReminderKind
import com.fitos.app.domain.model.ReminderSetting
import com.fitos.app.domain.model.ThemeMode
import com.fitos.app.domain.repository.AppSettings
import com.fitos.app.domain.repository.ReminderSettings
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) : AppSettings, ReminderSettings {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val glassMl = intPreferencesKey("default_glass_ml")
    }

    override val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK
    }

    override val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.dynamicColor] ?: false }

    override val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboardingDone] ?: false }

    override val defaultGlassMl: Flow<Int> = context.dataStore.data.map { it[Keys.glassMl] ?: 250 }

    // ---- reminders (kept in the same DataStore, exposed through the separate ReminderSettings interface)
    private fun enabledKey(k: ReminderKind) = booleanPreferencesKey("reminder_${k.name}_enabled")
    private fun minutesKey(k: ReminderKind) = intPreferencesKey("reminder_${k.name}_minutes")
    private fun androidx.datastore.preferences.core.Preferences.reminder(k: ReminderKind) =
        ReminderSetting(k, this[enabledKey(k)] ?: false, (this[minutesKey(k)] ?: k.defaultMinutes).coerceIn(0, 1439))

    override fun observe(): Flow<List<ReminderSetting>> = context.dataStore.data.map { p -> ReminderKind.entries.map { p.reminder(it) } }
    override suspend fun get(kind: ReminderKind): ReminderSetting = context.dataStore.data.first().reminder(kind)
    override suspend fun set(setting: ReminderSetting) {
        context.dataStore.edit {
            it[enabledKey(setting.kind)] = setting.enabled
            it[minutesKey(setting.kind)] = setting.minutes.coerceIn(0, 1439)
        }
    }

    override suspend fun setDefaultGlassMl(ml: Int) { context.dataStore.edit { it[Keys.glassMl] = ml.coerceIn(50, 2000) } }
    override suspend fun setThemeMode(mode: ThemeMode) { context.dataStore.edit { it[Keys.theme] = mode.name } }
    override suspend fun setDynamicColor(enabled: Boolean) { context.dataStore.edit { it[Keys.dynamicColor] = enabled } }
    override suspend fun setOnboardingDone(done: Boolean) { context.dataStore.edit { it[Keys.onboardingDone] = done } }
}
