package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import me.devsaki.hentoid.BuildConfig;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.P;

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

    public static void registerPrefsChangedListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public static void unregisterPrefsChangedListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
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

    public static boolean getEndlessScroll() {
        return sharedPreferences.getBoolean(Key.PREF_ENDLESS_SCROLL, Default.PREF_ENDLESS_SCROLL_DEFAULT);
    }

    public static boolean getRecentVisibility() {
        return sharedPreferences.getBoolean(Key.PREF_HIDE_RECENT, Default.PREF_HIDE_RECENT_DEFAULT);
    }

    static String getSdStorageUri() {
        return sharedPreferences.getString(Key.PREF_SD_STORAGE_URI, "");
    }

    static void setSdStorageUri(String uri) {
        sharedPreferences.edit()
                .putString(Key.PREF_SD_STORAGE_URI, uri)
                .apply();
    }

    static int getFolderNameFormat() {
        return Integer.parseInt(
                sharedPreferences.getString(Key.PREF_FOLDER_NAMING_CONTENT_LISTS,
                        Default.PREF_FOLDER_NAMING_CONTENT_DEFAULT + ""));
    }

    public static String getRootFolderName() {
        return sharedPreferences.getString(Key.PREF_SETTINGS_FOLDER, "");
    }

    static boolean setRootFolderName(String rootFolderName) {
        return sharedPreferences.edit()
                .putString(Key.PREF_SETTINGS_FOLDER, rootFolderName)
                .commit();
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

    public static int getDownloadThreadCount() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_DL_THREADS_QUANTITY_LISTS,
                Default.PREF_DL_THREADS_QUANTITY_DEFAULT + ""));
    }

    static int getFolderTruncationNbChars() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_FOLDER_TRUNCATION_LISTS,
                Default.PREF_FOLDER_TRUNCATION_DEFAULT + ""));
    }

    public static boolean isViewerResumeLastLeft() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_RESUME_LAST_LEFT, Default.PREF_VIEWER_RESUME_LAST_LEFT);
    }

    public static void setViewerResumeLastLeft(boolean resumeLastLeft) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_VIEWER_RESUME_LAST_LEFT, resumeLastLeft)
                .apply();
    }

    public static boolean isViewerKeepScreenOn() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_KEEP_SCREEN_ON, Default.PREF_VIEWER_KEEP_SCREEN_ON);
    }

    public static void setViewerKeepScreenOn(boolean keepScreenOn) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_VIEWER_KEEP_SCREEN_ON, keepScreenOn)
                .apply();
    }

    public static int getViewerResizeMode() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_IMAGE_DISPLAY, Integer.toString(Default.PREF_VIEWER_IMAGE_DISPLAY)));
    }

    public static void setViewerResizeMode(int resizeMode) {
        sharedPreferences.edit()
                .putString(Key.PREF_VIEWER_IMAGE_DISPLAY, Integer.toString(resizeMode))
                .apply();
    }

    public static int getViewerBrowseMode() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_BROWSE_MODE, Integer.toString(Default.PREF_VIEWER_BROWSE_MODE)));
    }

    public static int getViewerDirection() {
        return (getViewerBrowseMode() == Constant.PREF_VIEWER_BROWSE_RTL) ? Constant.PREF_VIEWER_DIRECTION_RTL : Constant.PREF_VIEWER_DIRECTION_LTR;
    }

    public static int getViewerOrientation() {
        return (getViewerBrowseMode() == Constant.PREF_VIEWER_BROWSE_TTB) ? Constant.PREF_VIEWER_ORIENTATION_VERTICAL : Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL;
    }

    public static void setViewerBrowseMode(int browseMode) {
        sharedPreferences.edit()
                .putString(Key.PREF_VIEWER_BROWSE_MODE, Integer.toString(browseMode))
                .apply();
    }

    public static int getViewerFlingFactor() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_FLING_FACTOR, Integer.toString(Default.PREF_VIEWER_FLING_FACTOR)));
    }

    public static void setViewerFlingFactor(int flingFactor) {
        sharedPreferences.edit()
                .putString(Key.PREF_VIEWER_FLING_FACTOR, Integer.toString(flingFactor))
                .apply();
    }

    public static boolean isViewerDisplayPageNum() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_DISPLAY_PAGENUM, Default.PREF_VIEWER_DISPLAY_PAGENUM);
    }

    public static void setViewerDisplayPageNum(boolean displayPageNum) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_VIEWER_DISPLAY_PAGENUM, displayPageNum)
                .apply();
    }

    public static boolean isViewerTapTransitions() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_TAP_TRANSITIONS, Default.PREF_VIEWER_TAP_TRANSITIONS);
    }

    public static void setViewerTapTransitions(boolean tapTransitions) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_VIEWER_TAP_TRANSITIONS, tapTransitions)
                .apply();
    }

    public static boolean isOpenBookInGalleryMode() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_OPEN_GALLERY, Default.PREF_VIEWER_OPEN_GALLERY);
    }

    public static void setOpenBookInGalleryMode(boolean openBookInGalleryMode) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_VIEWER_OPEN_GALLERY, openBookInGalleryMode)
                .apply();
    }

    public static int getLastKnownAppVersionCode() {
        return Integer.parseInt(sharedPreferences.getString(Key.LAST_KNOWN_APP_VERSION_CODE, "0"));
    }

    public static void setLastKnownAppVersionCode(int versionCode) {
        sharedPreferences.edit()
                .putString(Key.LAST_KNOWN_APP_VERSION_CODE, Integer.toString(versionCode))
                .apply();
    }

    public static int getDarkMode() {
        return Integer.parseInt(sharedPreferences.getString(Key.DARK_MODE, Integer.toString(Default.PREF_VIEWER_DARK_MODE)));
    }

    public static void setDarkMode(int darkMode) {
        sharedPreferences.edit()
                .putString(Key.DARK_MODE, Integer.toString(darkMode))
                .apply();
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
        static final String PREF_ENDLESS_SCROLL = "pref_endless_scroll";
        static final String PREF_SD_STORAGE_URI = "pref_sd_storage_uri";
        static final String PREF_FOLDER_NAMING_CONTENT_LISTS = "pref_folder_naming_content_lists";
        static final String PREF_SETTINGS_FOLDER = "folder";
        static final String PREF_WEBVIEW_OVERRIDE_OVERVIEW_LISTS = "pref_webview_override_overview_lists";
        static final String PREF_WEBVIEW_INITIAL_ZOOM_LISTS = "pref_webview_initial_zoom_lists";
        public static final String PREF_DL_THREADS_QUANTITY_LISTS = "pref_dl_threads_quantity_lists";
        static final String PREF_FOLDER_TRUNCATION_LISTS = "pref_folder_trunc_lists";
        static final String PREF_VIEWER_RESUME_LAST_LEFT = "pref_viewer_resume_last_left";
        public static final String PREF_VIEWER_KEEP_SCREEN_ON = "pref_viewer_keep_screen_on";
        public static final String PREF_VIEWER_IMAGE_DISPLAY = "pref_viewer_image_display";
        public static final String PREF_VIEWER_BROWSE_MODE = "pref_viewer_browse_mode";
        public static final String PREF_VIEWER_FLING_FACTOR = "pref_viewer_fling_factor";
        public static final String PREF_VIEWER_DISPLAY_PAGENUM = "pref_viewer_display_pagenum";
        static final String PREF_VIEWER_TAP_TRANSITIONS = "pref_viewer_tap_transitions";
        static final String PREF_VIEWER_OPEN_GALLERY = "pref_viewer_open_gallery";
        static final String LAST_KNOWN_APP_VERSION_CODE = "last_known_app_version_code";
        public static final String DARK_MODE = "pref_dark_mode";
    }

    // IMPORTANT : Any default value change must be mirrored in res/values/strings_settings.xml
    public static final class Default {
        public static final int PREF_QUANTITY_PER_PAGE_DEFAULT = 20;
        public static final int PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT = 20;
        static final int PREF_ORDER_CONTENT_DEFAULT = Constant.ORDER_CONTENT_TITLE_ALPHA;
        static final int PREF_ORDER_ATTRIBUTES_DEFAULT = Constant.ORDER_ATTRIBUTES_COUNT;
        static final boolean PREF_FIRST_RUN_DEFAULT = true;
        static final boolean PREF_ENDLESS_SCROLL_DEFAULT = true;
        static final boolean PREF_HIDE_RECENT_DEFAULT = (!BuildConfig.DEBUG); // Debug apps always visible to facilitate video capture
        static final int PREF_FOLDER_NAMING_CONTENT_DEFAULT = Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID;
        static final boolean PREF_WEBVIEW_OVERRIDE_OVERVIEW_DEFAULT = false;
        static final int PREF_DL_THREADS_QUANTITY_DEFAULT = Constant.DOWNLOAD_THREAD_COUNT_AUTO;
        static final int PREF_FOLDER_TRUNCATION_DEFAULT = Constant.TRUNCATE_FOLDER_NONE;
        static final boolean PREF_VIEWER_RESUME_LAST_LEFT = true;
        static final boolean PREF_VIEWER_KEEP_SCREEN_ON = true;
        static final int PREF_VIEWER_IMAGE_DISPLAY = Constant.PREF_VIEWER_DISPLAY_FIT;
        static final int PREF_VIEWER_BROWSE_MODE = Constant.PREF_VIEWER_BROWSE_NONE;
        static final boolean PREF_VIEWER_DISPLAY_PAGENUM = false;
        static final boolean PREF_VIEWER_TAP_TRANSITIONS = true;
        static final boolean PREF_VIEWER_OPEN_GALLERY = false;
        static final int PREF_VIEWER_FLING_FACTOR = 0;
        static final int PREF_VIEWER_DARK_MODE = (Build.VERSION.SDK_INT > P) ? Constant.DARK_MODE_DEVICE : Constant.DARK_MODE_OFF;
    }

    // IMPORTANT : Any value change must be mirrored in res/values/array_preferences.xml
    public static final class Constant {
        public static final int DOWNLOAD_THREAD_COUNT_AUTO = 0;
        public static final int ORDER_CONTENT_NONE = -1;
        public static final int ORDER_CONTENT_TITLE_ALPHA = 0;
        public static final int ORDER_CONTENT_LAST_DL_DATE_FIRST = 1;
        public static final int ORDER_CONTENT_TITLE_ALPHA_INVERTED = 2;
        public static final int ORDER_CONTENT_LAST_DL_DATE_LAST = 3;
        public static final int ORDER_CONTENT_RANDOM = 4;
        public static final int ORDER_CONTENT_LAST_UL_DATE_FIRST = 5;
        public static final int ORDER_CONTENT_LEAST_READ = 6;
        public static final int ORDER_CONTENT_MOST_READ = 7;
        public static final int ORDER_CONTENT_LAST_READ = 8;
        public static final int ORDER_ATTRIBUTES_ALPHABETIC = 0;
        static final int ORDER_ATTRIBUTES_COUNT = 1;
        static final int PREF_FOLDER_NAMING_CONTENT_ID = 0;
        static final int PREF_FOLDER_NAMING_CONTENT_TITLE_ID = 1;
        static final int PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID = 2;
        static final int TRUNCATE_FOLDER_NONE = 0;
        public static final int PREF_VIEWER_DISPLAY_FIT = 0;
        public static final int PREF_VIEWER_DISPLAY_FILL = 1;
        public static final int PREF_VIEWER_BROWSE_NONE = -1;
        public static final int PREF_VIEWER_BROWSE_LTR = 0;
        public static final int PREF_VIEWER_BROWSE_RTL = 1;
        public static final int PREF_VIEWER_BROWSE_TTB = 2;
        public static final int PREF_VIEWER_DIRECTION_LTR = 0;
        public static final int PREF_VIEWER_DIRECTION_RTL = 1;
        public static final int PREF_VIEWER_ORIENTATION_HORIZONTAL = 0;
        public static final int PREF_VIEWER_ORIENTATION_VERTICAL = 1;
        public static final int DARK_MODE_OFF = 0;
        public static final int DARK_MODE_ON = 1;
        public static final int DARK_MODE_BATTERY = 2;
        public static final int DARK_MODE_DEVICE = 3;
    }
}
