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

    private static int getIntPref(@NonNull String key, int defaultValue) {
        if (null == sharedPreferences) return defaultValue;
        return Integer.parseInt(sharedPreferences.getString(key, Integer.toString(defaultValue)));
    }

    private static boolean getBoolPref(@NonNull String key, boolean defaultValue) {
        if (null == sharedPreferences) return defaultValue;
        return sharedPreferences.getBoolean(key, defaultValue);
    }


    // ======= PROPERTIES GETTERS / SETTERS
    public static int getDuplicateSensitivity() {
        return getIntPref(Key.DUPLICATE_SENSITIVITY, Default.DUPLICATE_SENSITIVITY);
    }

    public static void setDuplicateSensitivity(int duplicateSensitivity) {
        sharedPreferences.edit().putString(Key.DUPLICATE_SENSITIVITY, Integer.toString(duplicateSensitivity)).apply();
    }

    public static boolean isDuplicateUseTitle() {
        return getBoolPref(Key.DUPLICATE_USE_TITLE, Default.DUPLICATE_USE_TITLE);
    }

    public static void setDuplicateUseTitle(boolean useTitle) {
        sharedPreferences.edit().putBoolean(Key.DUPLICATE_USE_TITLE, useTitle).apply();
    }

    public static boolean isDuplicateUseCover() {
        return getBoolPref(Key.DUPLICATE_USE_COVER, Default.DUPLICATE_USE_COVER);
    }

    public static void setDuplicateUseCover(boolean useCover) {
        sharedPreferences.edit().putBoolean(Key.DUPLICATE_USE_COVER, useCover).apply();
    }

    public static boolean isDuplicateUseArtist() {
        return getBoolPref(Key.DUPLICATE_USE_ARTIST, Default.DUPLICATE_USE_ARTIST);
    }

    public static void setDuplicateUseArtist(boolean useArtist) {
        sharedPreferences.edit().putBoolean(Key.DUPLICATE_USE_ARTIST, useArtist).apply();
    }

    public static boolean isDuplicateUseSameLanguage() {
        return getBoolPref(Key.DUPLICATE_USE_SAME_LANGUAGE, Default.DUPLICATE_USE_SAME_LANGUAGE);
    }

    public static void setDuplicateUseSameLanguage(boolean useSameLanguage) {
        sharedPreferences.edit().putBoolean(Key.DUPLICATE_USE_SAME_LANGUAGE, useSameLanguage).apply();
    }

    public static boolean isDuplicateBrowserUseTitle() {
        return getBoolPref(Key.DUPLICATE_BROWSER_USE_TITLE, Default.DUPLICATE_BROWSER_USE_TITLE);
    }

    public static boolean isDuplicateBrowserUseCover() {
        return getBoolPref(Key.DUPLICATE_BROWSER_USE_COVER, Default.DUPLICATE_BROWSER_USE_COVER);
    }

    public static boolean isDuplicateBrowserUseArtist() {
        return getBoolPref(Key.DUPLICATE_BROWSER_USE_ARTIST, Default.DUPLICATE_BROWSER_USE_ARTIST);
    }

    public static boolean isDuplicateBrowserUseSameLanguage() {
        return getBoolPref(Key.DUPLICATE_BROWSER_USE_SAME_LANGUAGE, Default.DUPLICATE_BROWSER_USE_SAME_LANGUAGE);
    }

    public static int getDuplicateBrowserSensitivity() {
        return getIntPref(Key.DUPLICATE_BROWSER_SENSITIVITY, Default.DUPLICATE_BROWSER_SENSITIVITY);
    }

    public static boolean isDuplicateIgnoreChapters() {
        return getBoolPref(Key.DUPLICATE_IGNORE_CHAPTERS, Default.DUPLICATE_IGNORE_CHAPTERS);
    }

    public static void setDuplicateIgnoreChapters(boolean value) {
        sharedPreferences.edit().putBoolean(Key.DUPLICATE_IGNORE_CHAPTERS, value).apply();
    }

    public static int getDuplicateLastIndex() {
        return getIntPref(Key.DUPLICATE_LAST_INDEX, -1);
    }

    public static void setDuplicateLastIndex(int lastIndex) {
        sharedPreferences.edit().putString(Key.DUPLICATE_LAST_INDEX, Integer.toString(lastIndex)).apply();
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
        public static final String DUPLICATE_SENSITIVITY = "duplicate_sensitivity";
        public static final String DUPLICATE_BROWSER_SENSITIVITY = "duplicate_browser_sensitivity";
        public static final String DUPLICATE_USE_TITLE = "duplicate_use_title";
        public static final String DUPLICATE_USE_COVER = "duplicate_use_cover";
        public static final String DUPLICATE_USE_ARTIST = "duplicate_use_artist";
        public static final String DUPLICATE_USE_SAME_LANGUAGE = "duplicate_use_same_language";
        public static final String DUPLICATE_BROWSER_USE_TITLE = "duplicate_browser_use_title";
        public static final String DUPLICATE_BROWSER_USE_COVER = "duplicate_browser_use_cover";
        public static final String DUPLICATE_BROWSER_USE_ARTIST = "duplicate_browser_use_artist";
        public static final String DUPLICATE_BROWSER_USE_SAME_LANGUAGE = "duplicate_browser_use_same_language";
        public static final String DUPLICATE_IGNORE_CHAPTERS = "duplicate_ignore_chapters";
        public static final String DUPLICATE_LAST_INDEX = "last_index";
    }

    // IMPORTANT : Any default value change must be mirrored in res/values/strings_settings.xml
    public static final class Default {

        private Default() {
            throw new IllegalStateException("Utility class");
        }

        static final int DUPLICATE_SENSITIVITY = 1;
        static final int DUPLICATE_BROWSER_SENSITIVITY = 2;
        static final boolean DUPLICATE_USE_TITLE = true;
        static final boolean DUPLICATE_USE_COVER = false;
        static final boolean DUPLICATE_USE_ARTIST = true;
        static final boolean DUPLICATE_USE_SAME_LANGUAGE = false;
        static final boolean DUPLICATE_BROWSER_USE_TITLE = true;
        static final boolean DUPLICATE_BROWSER_USE_COVER = true;
        static final boolean DUPLICATE_BROWSER_USE_ARTIST = true;
        static final boolean DUPLICATE_BROWSER_USE_SAME_LANGUAGE = false;
        static final boolean DUPLICATE_IGNORE_CHAPTERS = true;
    }
}
