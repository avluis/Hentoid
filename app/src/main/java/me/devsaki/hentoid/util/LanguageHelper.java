package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.json.core.JsonLangSettings;
import me.devsaki.hentoid.util.file.FileHelper;
import timber.log.Timber;

public class LanguageHelper {

    private LanguageHelper() {
        throw new IllegalStateException("Utility class");
    }

    // label -> (language code, flag country code)
    static Map<String, Pair<String, String>> languageCodes = new HashMap<>();

    static {
        Context context = HentoidApp.getInstance();
        try (InputStream is = context.getResources().openRawResource(R.raw.languages)) {
            String siteSettingsStr = FileHelper.readStreamAsString(is);
            JsonLangSettings langSettings = JsonHelper.jsonToObject(siteSettingsStr, JsonLangSettings.class);
            for (JsonLangSettings.JsonLanguage entry : langSettings.languages) {
                Pair<String, String> properties = new Pair<>(entry.lang_code, entry.flag_country_code);
                // Create one entry for the local name
                languageCodes.put(entry.local_name, properties);
                // Create one entry for the english name (as most sites refer to the language in english)
                languageCodes.put(entry.english_name, properties);
                // Create one entry for the translated name in the current locale
                int stringId = context.getResources().getIdentifier("lang_" + entry.lang_code, "string", context.getPackageName());
                languageCodes.put(context.getString(stringId), properties);
            }
        } catch (IOException e) {
            Timber.e(e);
        }
    }

    /**
     * Returns the localized name of the given language
     *
     * @param language Language name
     * @return Language name localized in the device's language, if supported by Hentoid
     */
    public static String getLocalNameFromLanguage(@NonNull final Context context, @NonNull final String language) {
        if (language.isEmpty()) return "";
        String languageClean = language.toLowerCase().split("/")[0].split("\\(")[0].trim();
        Pair<String, String> languageProps = languageCodes.get(languageClean);
        if (null == languageProps) return language;
        else
            return context.getString(
                    context.getResources().getIdentifier("lang_" + languageProps.first, "string", context.getPackageName())
            );
    }

    /**
     * Returns the country code of the given language
     *
     * @param language Language name, either in english or in its native spelling
     * @return Country code of the given language, or empty string if not found
     */
    public static String getCountryCodeFromLanguage(@NonNull final String language) {
        if (language.isEmpty()) return "";
        String languageClean = language.toLowerCase().split("/")[0].split("\\(")[0].trim();
        Pair<String, String> languageProps = languageCodes.get(languageClean);
        if (null == languageProps) return "";
        else return languageProps.second;
    }

    /**
     * Returns the resource ID of the image of the flag representing the given language
     *
     * @param context  Context to be used
     * @param language Language name, either in english or in its native spelling
     * @return Resource ID of the image of the flag representing the given language; 0 if the given language is not supported
     */
    public static @DrawableRes
    int getFlagFromLanguage(@NonNull Context context, @NonNull final String language) {
        String countryCode = getCountryCodeFromLanguage(language);
        if (countryCode.isEmpty()) return 0;
        else return getFlagId(context, countryCode);
    }

    /**
     * Returns the resource ID of the image of the flag representing the given country code
     *
     * @param context     Context to be used
     * @param countryCode Country code (2-letter ISO-3166)
     * @return Resource ID of the image of the flag representing the given country code
     */
    private static @DrawableRes
    int getFlagId(@NonNull Context context, @NonNull String countryCode) {
        return context.getResources().getIdentifier("flag_" + countryCode, "drawable", context.getPackageName());
    }
}
