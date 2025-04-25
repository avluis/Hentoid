package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import timber.log.Timber;

/**
 * Decorator class that wraps a SharedPreference to implement properties
 * Some properties do not have a setter because it is changed by PreferenceActivity
 * Some properties are parsed as ints because of limitations with the Preference subclass used
 */

public final class Preferences {

    private Preferences() {
        throw new IllegalStateException("Utility class");
    }

    private static final int VERSION = 4;

    private static SharedPreferences sharedPreferences;

    public static void init(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        int savedVersion = sharedPreferences.getInt(Key.VERSION_KEY, VERSION);
        if (savedVersion != VERSION) {
            Timber.d("Shared Prefs Key Mismatch! Clearing Prefs!");
            sharedPreferences.edit().clear().apply();
        }
    }

    public static final class Key {

        private Key() {
            throw new IllegalStateException("Utility class");
        }

        static final String VERSION_KEY = "prefs_version";
        public static final String CHECK_UPDATE_MANUAL = "pref_check_updates_manual";
        public static final String DRAWER_SOURCES = "pref_drawer_sources";
        public static final String SOURCE_SPECIFICS = "pref_source_specifics";
        public static final String EXTERNAL_LIBRARY = "pref_external_library";
        public static final String EXTERNAL_LIBRARY_DETACH = "pref_detach_external_library";
        public static final String STORAGE_MANAGEMENT = "storage_mgt";
    }
}
