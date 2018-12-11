package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import me.devsaki.hentoid.BuildConfig;
import timber.log.Timber;

/**
 * Created by Shiro on 2/21/2018.
 * Decorator class that wraps a SharedPreference to implement properties
 * Some properties do not have a setter because it is changed by PreferenceActivity
 * Some properties are parsed as ints because of limitations with the Preference subclass used
 */

public final class Preferences {

    private static final int VERSION = 4;

    private static SharedPreferences sharedPreferences;

    public static void init(Context context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        int savedVersion = sharedPreferences.getInt(Key.PREFS_VERSION_KEY, VERSION);
        if (savedVersion != VERSION) {
            Timber.d("Shared Prefs Key Mismatch! Clearing Prefs!");
            sharedPreferences.edit()
                    .clear()
                    .apply();
        }
    }

    public static boolean isFirstRunProcessComplete() {
        return sharedPreferences.getBoolean(Key.PREF_WELCOME_DONE, false);
    }

    public static void setIsFirstRunProcessComplete(boolean isFirstRunProcessComplete) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_WELCOME_DONE, isFirstRunProcessComplete)
                .apply();
    }

    public static boolean isAnalyticsDisabled() {
        return sharedPreferences.getBoolean(Key.PREF_ANALYTICS_TRACKING, false);
    }

    public static boolean isFirstRun() {
        return sharedPreferences.getBoolean(Key.PREF_FIRST_RUN, Default.PREF_FIRST_RUN_DEFAULT);
    }

    public static void setIsFirstRun(boolean isFirstRun) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_FIRST_RUN, isFirstRun)
                .apply();
    }

    public static int getContentSortOrder() {
        return sharedPreferences.getInt(Key.PREF_ORDER_CONTENT_LISTS, Default.PREF_ORDER_CONTENT_DEFAULT);
    }

    public static void setContentSortOrder(int sortOrder) {
        sharedPreferences.edit()
                .putInt(Key.PREF_ORDER_CONTENT_LISTS, sortOrder)
                .apply();
    }

    public static int getAttributesSortOrder() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_ORDER_ATTRIBUTE_LISTS, Default.PREF_ORDER_ATTRIBUTES_DEFAULT + ""));
    }

    public static void setAttributesSortOrder(int sortOrder) {
        sharedPreferences.edit()
                .putString(Key.PREF_ORDER_ATTRIBUTE_LISTS, sortOrder + "")
                .apply();
    }

    public static int getContentPageQuantity() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_QUANTITY_PER_PAGE_LISTS,
                Default.PREF_QUANTITY_PER_PAGE_DEFAULT + ""));
    }

    public static String getAppLockPin() {
        return sharedPreferences.getString(Key.PREF_APP_LOCK, "");
    }

    public static void setAppLockPin(String pin) {
        sharedPreferences.edit()
                .putString(Key.PREF_APP_LOCK, pin)
                .apply();
    }

    public static boolean getAppLockVibrate() {
        return sharedPreferences.getBoolean(Key.PREF_APP_LOCK_VIBRATE, Default.PREF_APP_LOCK_VIBRATE_DEFAULT);
    }

    public static boolean getEndlessScroll() {
        return sharedPreferences.getBoolean(Key.PREF_ENDLESS_SCROLL, Default.PREF_ENDLESS_SCROLL_DEFAULT);
    }

    public static boolean getRecentVisibility() {
        return sharedPreferences.getBoolean(Key.PREF_HIDE_RECENT, Default.PREF_HIDE_RECENT_DEFAULT);
    }

    public static String getSdStorageUri() {
        return sharedPreferences.getString(Key.PREF_SD_STORAGE_URI, null);
    }

    public static void setSdStorageUri(String uri) {
        sharedPreferences.edit()
                .putString(Key.PREF_SD_STORAGE_URI, uri)
                .apply();
    }

    public static int getFolderNameFormat() {
        return Integer.parseInt(
                sharedPreferences.getString(Key.PREF_FOLDER_NAMING_CONTENT_LISTS,
                        Default.PREF_FOLDER_NAMING_CONTENT_DEFAULT + ""));
    }

    public static String getRootFolderName() {
        return sharedPreferences.getString(Key.PREF_SETTINGS_FOLDER, "");
    }

    public static boolean setRootFolderName(String rootFolderName) {
        return sharedPreferences.edit()
                .putString(Key.PREF_SETTINGS_FOLDER, rootFolderName)
                .commit();
    }

    public static int getContentReadAction() {
        return Integer.parseInt(
                sharedPreferences.getString(Key.PREF_READ_CONTENT_LISTS,
                        Default.PREF_READ_CONTENT_ACTION + ""));
    }

    public static boolean getMobileUpdate() {
        return sharedPreferences.getBoolean(Key.PREF_CHECK_UPDATES_LISTS, Default.PREF_CHECK_UPDATES_DEFAULT);
    }

    public static int getWebViewInitialZoom() {
        return Integer.parseInt(
                sharedPreferences.getString(
                        Key.PREF_WEBVIEW_INITIAL_ZOOM_LISTS,
                        Default.PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT + ""));
    }

    public static boolean getWebViewOverview() {
        return sharedPreferences.getBoolean(
                Key.PREF_WEBVIEW_OVERRIDE_OVERVIEW_LISTS,
                Default.PREF_WEBVIEW_OVERRIDE_OVERVIEW_DEFAULT);
    }

    public static boolean isUseSfw() {
        return sharedPreferences.getBoolean(Key.PREF_USE_SFW, Default.PREF_USE_SFW_DEFAULT);
    }

    public static void setIsUseSfw(boolean useSfw) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_USE_SFW, useSfw)
                .apply();
    }

    public static int getDownloadThreadCount() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_DL_THREADS_QUANTITY_LISTS,
                Default.PREF_DL_THREADS_QUANTITY_DEFAULT + ""));
    }

    public static int getFolderTruncationNbChars() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_FOLDER_TRUNCATION_LISTS,
                Default.PREF_FOLDER_TRUNCATION_DEFAULT + ""));
    }

    public static final class Key {
        public static final String PREF_APP_LOCK = "pref_app_lock";
        public static final String PREF_HIDE_RECENT = "pref_hide_recent";
        public static final String PREF_ADD_NO_MEDIA_FILE = "pref_add_no_media_file";
        public static final String PREF_CHECK_UPDATE_MANUAL = "pref_check_updates_manual";
        public static final String PREF_REFRESH_LIBRARY = "pref_refresh_bookshelf";
        public static final String PREF_ANALYTICS_TRACKING = "pref_analytics_tracking";
        static final String PREF_WELCOME_DONE = "pref_welcome_done";
        static final String PREFS_VERSION_KEY = "prefs_version";
        static final String PREF_QUANTITY_PER_PAGE_LISTS = "pref_quantity_per_page_lists";
        static final String PREF_ORDER_CONTENT_LISTS = "pref_order_content_lists";
        static final String PREF_ORDER_ATTRIBUTE_LISTS = "pref_order_attribute_lists";
        static final String PREF_FIRST_RUN = "pref_first_run";
        static final String PREF_APP_LOCK_VIBRATE = "pref_app_lock_vibrate";
        static final String PREF_ENDLESS_SCROLL = "pref_endless_scroll";
        static final String PREF_SD_STORAGE_URI = "pref_sd_storage_uri";
        static final String PREF_FOLDER_NAMING_CONTENT_LISTS = "pref_folder_naming_content_lists";
        static final String PREF_SETTINGS_FOLDER = "folder";
        static final String PREF_READ_CONTENT_LISTS = "pref_read_content_lists";
        static final String PREF_CHECK_UPDATES_LISTS = "pref_check_updates_lists";
        static final String PREF_WEBVIEW_OVERRIDE_OVERVIEW_LISTS = "pref_webview_override_overview_lists";
        static final String PREF_WEBVIEW_INITIAL_ZOOM_LISTS = "pref_webview_initial_zoom_lists";
        public static final String PREF_USE_SFW = "pref_use_sfw";
        public static final String PREF_DL_THREADS_QUANTITY_LISTS = "pref_dl_threads_quantity_lists";
        static final String PREF_FOLDER_TRUNCATION_LISTS = "pref_folder_trunc_lists";
    }

    // IMPORTANT : Any default value change must be mirrored in res/values/strings_settings.xml
    public static final class Default {
        public static final int PREF_QUANTITY_PER_PAGE_DEFAULT = 20;
        public static final int PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT = 20;
        static final int PREF_ORDER_CONTENT_DEFAULT = Constant.PREF_ORDER_CONTENT_ALPHABETIC;
        static final int PREF_ORDER_ATTRIBUTES_DEFAULT = Constant.PREF_ORDER_ATTRIBUTES_COUNT;
        static final boolean PREF_FIRST_RUN_DEFAULT = true;
        static final boolean PREF_APP_LOCK_VIBRATE_DEFAULT = true;
        static final boolean PREF_ENDLESS_SCROLL_DEFAULT = true;
        static final boolean PREF_HIDE_RECENT_DEFAULT = (!BuildConfig.DEBUG); // Debug apps always visible to facilitate video capture
        static final int PREF_FOLDER_NAMING_CONTENT_DEFAULT = Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID;
        static final int PREF_READ_CONTENT_ACTION = Constant.PREF_READ_CONTENT_DEFAULT;
        static final boolean PREF_CHECK_UPDATES_DEFAULT = true;
        static final boolean PREF_WEBVIEW_OVERRIDE_OVERVIEW_DEFAULT = false;
        static final boolean PREF_USE_SFW_DEFAULT = false;
        static final int PREF_DL_THREADS_QUANTITY_DEFAULT = Constant.DOWNLOAD_THREAD_COUNT_AUTO;
        static final int PREF_FOLDER_TRUNCATION_DEFAULT = Constant.TRUNCATE_FOLDER_NONE;
    }

    // IMPORTANT : Any value change must be mirrored in res/values/array_preferences.xml
    public static final class Constant {
        public static final int DOWNLOAD_THREAD_COUNT_AUTO = 0;
        public static final int PREF_ORDER_CONTENT_ALPHABETIC = 0;
        public static final int PREF_ORDER_CONTENT_LAST_DL_DATE_FIRST = 1;
        public static final int PREF_ORDER_CONTENT_ALPHABETIC_INVERTED = 2;
        public static final int PREF_ORDER_CONTENT_LAST_DL_DATE_LAST = 3;
        public static final int PREF_ORDER_CONTENT_RANDOM = 4;
        public static final int PREF_ORDER_CONTENT_LAST_UL_DATE_FIRST = 5;
        public static final int PREF_ORDER_CONTENT_LEAST_READ = 6;
        public static final int PREF_ORDER_CONTENT_MOST_READ = 7;
        public static final int PREF_ORDER_CONTENT_LAST_READ = 8;
        public static final int PREF_ORDER_ATTRIBUTES_ALPHABETIC = 0;
        public static final int PREF_ORDER_ATTRIBUTES_COUNT = 1;
        static final int PREF_FOLDER_NAMING_CONTENT_ID = 0;
        static final int PREF_FOLDER_NAMING_CONTENT_TITLE_ID = 1;
        static final int PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID = 2;
        static final int PREF_READ_CONTENT_DEFAULT = 0;
        static final int PREF_READ_CONTENT_PERFECT_VIEWER = 1;
        static final int TRUNCATE_FOLDER_NONE = 0;
    }
}
