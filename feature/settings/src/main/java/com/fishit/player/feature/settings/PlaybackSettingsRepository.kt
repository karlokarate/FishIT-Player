package com.fishit.player.feature.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fishit.player.core.model.VariantPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for playback variant preferences.
 *
 * Persists user preferences for:
 * - Preferred language (ISO-639-1 code)
 * - OmU (Original with subtitles) preference
 * - Source priority (Xtream vs Telegram)
 *
 * These preferences are used by [VariantSelector] to choose the best playback variant when multiple
 * sources are available.
 */
@Singleton
class PlaybackSettingsRepository
@Inject
constructor(
        @ApplicationContext private val context: Context,
) {
    companion object {
        private val KEY_PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        private val KEY_PREFER_OMU = booleanPreferencesKey("prefer_omu")
        private val KEY_PREFER_XTREAM = booleanPreferencesKey("prefer_xtream")
    }

    private val Context.dataStore by preferencesDataStore(name = "playback_settings")

    /** Flow of current variant preferences. */
    val variantPreferences: Flow<VariantPreferences> =
            context.dataStore.data.map { prefs ->
                VariantPreferences(
                        preferredLanguage = prefs[KEY_PREFERRED_LANGUAGE]
                                        ?: Locale.getDefault().language,
                        preferOmu = prefs[KEY_PREFER_OMU] ?: false,
                        preferXtream = prefs[KEY_PREFER_XTREAM] ?: true,
                )
            }

    /**
     * Update preferred language.
     *
     * @param languageCode ISO-639-1 language code (e.g., "de", "en")
     */
    suspend fun setPreferredLanguage(languageCode: String) {
        context.dataStore.edit { prefs -> prefs[KEY_PREFERRED_LANGUAGE] = languageCode }
    }

    /**
     * Update OmU preference.
     *
     * @param preferOmu True to prefer Original with subtitles
     */
    suspend fun setPreferOmu(preferOmu: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_PREFER_OMU] = preferOmu }
    }

    /**
     * Update source priority preference.
     *
     * @param preferXtream True to prefer Xtream sources over Telegram
     */
    suspend fun setPreferXtream(preferXtream: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_PREFER_XTREAM] = preferXtream }
    }

    /** Update all preferences at once. */
    suspend fun updatePreferences(preferences: VariantPreferences) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREFERRED_LANGUAGE] = preferences.preferredLanguage
            prefs[KEY_PREFER_OMU] = preferences.preferOmu
            prefs[KEY_PREFER_XTREAM] = preferences.preferXtream
        }
    }
}

/** Supported languages for variant selection UI. */
object SupportedLanguages {
    val languages =
            listOf(
                    LanguageOption("de", "Deutsch", "🇩🇪"),
                    LanguageOption("en", "English", "🇬🇧"),
                    LanguageOption("fr", "Français", "🇫🇷"),
                    LanguageOption("es", "Español", "🇪🇸"),
                    LanguageOption("it", "Italiano", "🇮🇹"),
                    LanguageOption("tr", "Türkçe", "🇹🇷"),
                    LanguageOption("ru", "Русский", "🇷🇺"),
                    LanguageOption("pl", "Polski", "🇵🇱"),
                    LanguageOption("nl", "Nederlands", "🇳🇱"),
                    LanguageOption("pt", "Português", "🇵🇹"),
            )

    fun findByCode(code: String): LanguageOption? = languages.find { it.code == code }
}

/** Language option for settings UI. */
data class LanguageOption(
        val code: String,
        val displayName: String,
        val flag: String,
)
