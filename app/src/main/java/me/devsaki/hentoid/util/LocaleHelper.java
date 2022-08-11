package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public class LocaleHelper {
    public static void convertLocaleToEnglish(Context context) {
        if (Preferences.isForceEnglishLocale()) {
            Configuration config = context.getResources().getConfiguration();
            if (!config.locale.equals(Locale.ENGLISH)) {
                Locale englishLocale = new Locale("en");
                Locale.setDefault(englishLocale);
                config.setLocale(englishLocale);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    context.createConfigurationContext(config);
                context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
            }
        }
    }
}