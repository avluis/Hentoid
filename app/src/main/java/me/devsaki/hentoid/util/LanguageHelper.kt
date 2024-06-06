package me.devsaki.hentoid.util

import android.content.Context
import androidx.annotation.DrawableRes
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.json.core.JsonLangSettings
import me.devsaki.hentoid.util.file.readStreamAsString
import org.apache.commons.collections4.map.HashedMap
import timber.log.Timber
import java.io.IOException
import java.util.Locale

object LanguageHelper {
    // label -> (language code, flag country code)
    private var languageCodes: MutableMap<String, Pair<String, String>> = HashedMap()

    init {
        val context: Context = getInstance()
        try {
            context.resources.openRawResource(R.raw.languages).use { `is` ->
                val siteSettingsStr = readStreamAsString(`is`)
                val langSettings = JsonHelper.jsonToObject(
                    siteSettingsStr,
                    JsonLangSettings::class.java
                )
                for (entry in langSettings.languages) {
                    val properties =
                        Pair(
                            entry.langCode,
                            entry.flagCountryCode
                        )
                    // Create one entry for the local name
                    languageCodes[entry.localName] = properties
                    // Create one entry for the english name (as most sites refer to the language in english)
                    languageCodes[entry.englishName] = properties
                    // Create one entry for the translated name in the current locale
                    val stringId = context.resources
                        .getIdentifier("lang_" + entry.langCode, "string", context.packageName)
                    languageCodes[context.getString(stringId)] = properties
                }
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    /**
     * Returns the localized name of the given language
     *
     * @param language Language name
     * @return Language name localized in the device's language, if supported by Hentoid
     */
    fun getLocalNameFromLanguage(context: Context, language: String): String {
        if (language.isEmpty()) return ""
        val languageClean = language.lowercase(Locale.getDefault())
            .split("/")[0]
            .split("(")[0]
            .trim()
        val languageProps = languageCodes[languageClean]
        return if (null == languageProps) language else context.getString(
            context.resources.getIdentifier(
                "lang_" + languageProps.first,
                "string",
                context.packageName
            )
        )
    }

    /**
     * Returns the country code of the given language
     *
     * @param language Language name, either in english or in its native spelling
     * @return Country code of the given language, or empty string if not found
     */
    fun getCountryCodeFromLanguage(language: String): String {
        if (language.isEmpty()) return ""
        val languageClean = language.lowercase(Locale.getDefault())
            .split("/")[0]
            .split("(")[0]
            .trim()
        val languageProps = languageCodes[languageClean]
        return languageProps?.second ?: ""
    }

    /**
     * Returns the resource ID of the image of the flag representing the given language
     *
     * @param context  Context to be used
     * @param language Language name, either in english or in its native spelling
     * @return Resource ID of the image of the flag representing the given language; 0 if the given language is not supported
     */
    @DrawableRes
    fun getFlagFromLanguage(context: Context, language: String): Int {
        val countryCode = getCountryCodeFromLanguage(language)
        return if (countryCode.isEmpty()) 0 else getFlagId(context, countryCode)
    }

    /**
     * Returns the resource ID of the image of the flag representing the given country code
     *
     * @param context     Context to be used
     * @param countryCode Country code (2-letter ISO-3166)
     * @return Resource ID of the image of the flag representing the given country code
     */
    @DrawableRes
    private fun getFlagId(context: Context, countryCode: String): Int {
        return context.resources.getIdentifier("flag_$countryCode", "drawable", context.packageName)
    }
}