package me.devsaki.hentoid.util;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.R;

public class LanguageHelper {

    private LanguageHelper() {
        throw new IllegalStateException("Utility class");
    }

    static Map<String, String> languageCodes = new HashMap<>();

    static {
        String[] languages = HentoidApp.getInstance().getResources().getStringArray(R.array.languages);
        for (String line : languages) {
            String[] parts1 = line.split("-");
            String code = parts1[1];
            String[] labels = parts1[0].split(",");
            for (String label : labels) languageCodes.put(label, code);
        }
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
        if (language.isEmpty()) return 0;

        String languageClean = language.toLowerCase().split("/")[0].split("\\(")[0].trim();
        String countryCode = languageCodes.get(languageClean);
        if (countryCode != null)
            return getFlagId(context, countryCode);
        else
            return 0;
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
