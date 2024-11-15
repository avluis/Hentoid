package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Map;

import me.devsaki.hentoid.database.domains.DownloadMode;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Theme;
import me.devsaki.hentoid.util.network.Source;
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

    public static void performHousekeeping() {
        // Fling factor -> Swipe to fling (v1.9.0)
        if (sharedPreferences.contains(Key.VIEWER_FLING_FACTOR)) {
            int flingFactor = getIntPref(Key.VIEWER_FLING_FACTOR, 0);
            sharedPreferences.edit().putBoolean(Settings.Key.VIEWER_SWIPE_TO_FLING, flingFactor > 0).apply();
            sharedPreferences.edit().remove(Key.VIEWER_FLING_FACTOR).apply();
        }
        // PIN activation -> Lock type (v1.18.4)
        if (sharedPreferences.contains(Settings.Key.APP_LOCK)) {
            if (!Settings.INSTANCE.getAppLockPin().isEmpty()) Settings.INSTANCE.setLockType(1);
        }
    }

    public static void registerPrefsChangedListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public static void unregisterPrefsChangedListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static Map<String, Object> extractPortableInformation() {
        Map<String, Object> result = new HashMap<>(sharedPreferences.getAll());

        // Remove non-exportable settings that make no sense on another instance
        result.remove(Settings.Key.FIRST_RUN);
        result.remove(Settings.Key.WELCOME_DONE);
        result.remove(Settings.Key.PRIMARY_STORAGE_URI);
        result.remove(Settings.Key.EXTERNAL_LIBRARY_URI);
        result.remove(Key.LAST_KNOWN_APP_VERSION_CODE);
        result.remove(Settings.Key.REFRESH_JSON_1_DONE);
        result.remove(Settings.Key.LOCK_TYPE);
        result.remove(Settings.Key.ACHIEVEMENTS);
        result.remove(Settings.Key.ACHIEVEMENTS_NB_AI_RESCALE);

        return result;
    }

    public static void importInformation(Map<String, ?> settings) {
        for (Map.Entry<String, ?> entry : settings.entrySet()) {
            if (entry.getValue() instanceof Integer) {
                sharedPreferences.edit().putInt(entry.getKey(), (Integer) entry.getValue()).apply();
            } else if (entry.getValue() instanceof String) {
                sharedPreferences.edit().putString(entry.getKey(), (String) entry.getValue()).apply();
            } else if (entry.getValue() instanceof Boolean) {
                sharedPreferences.edit().putBoolean(entry.getKey(), (Boolean) entry.getValue()).apply();
            } else if (entry.getValue() instanceof Float) {
                sharedPreferences.edit().putFloat(entry.getKey(), (Float) entry.getValue()).apply();
            } else if (entry.getValue() instanceof Long) {
                sharedPreferences.edit().putLong(entry.getKey(), (Long) entry.getValue()).apply();
            }
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
    public static int getWebViewInitialZoom() {
        return getIntPref(Key.WEBVIEW_INITIAL_ZOOM_LISTS, Default.WEBVIEW_INITIAL_ZOOM);
    }

    public static boolean getWebViewOverview() {
        return getBoolPref(Key.WEBVIEW_OVERRIDE_OVERVIEW_LISTS, Default.WEBVIEW_OVERRIDE_OVERVIEW);
    }

    public static boolean isBrowserResumeLast() {
        return getBoolPref(Key.BROWSER_RESUME_LAST, Default.BROWSER_RESUME_LAST);
    }

    public static boolean isBrowserMarkDownloaded() {
        return getBoolPref(Key.BROWSER_MARK_DOWNLOADED, Default.BROWSER_MARK_DOWNLOADED);
    }

    public static boolean isBrowserMarkMerged() {
        return getBoolPref(Key.BROWSER_MARK_MERGED, Default.BROWSER_MARK_MERGED);
    }

    public static boolean isBrowserMarkQueued() {
        return getBoolPref(Key.BROWSER_MARK_QUEUED, Default.BROWSER_MARK_QUEUED);
    }

    public static boolean isBrowserMarkBlockedTags() {
        return getBoolPref(Key.BROWSER_MARK_BLOCKED, Default.BROWSER_MARK_BLOCKED);
    }

    public static DownloadMode getBrowserDlAction() {
        return DownloadMode.Companion.fromValue(getIntPref(Key.BROWSER_DL_ACTION, Default.BROWSER_DL_ACTION));
    }

    public static boolean isBrowserQuickDl() {
        return getBoolPref(Key.BROWSER_QUICK_DL, Default.BROWSER_QUICK_DL);
    }

    public static int getBrowserQuickDlThreshold() {
        return getIntPref(Key.BROWSER_QUICK_DL_THRESHOLD, Default.BROWSER_QUICK_DL_THRESHOLD);
    }

    public static int getDnsOverHttps() {
        return getIntPref(Key.BROWSER_DNS_OVER_HTTPS, Default.BROWSER_DNS_OVER_HTTPS);
    }

    public static boolean isBrowserNhentaiInvisibleBlacklist() {
        return getBoolPref(Key.BROWSER_NHENTAI_INVISIBLE_BLACKLIST, Default.BROWSER_NHENTAI_INVISIBLE_BLACKLIST);
    }

    public static int getDownloadThreadCount() {
        return getIntPref(Key.DL_THREADS_QUANTITY_LISTS, Default.DL_THREADS_QUANTITY);
    }

    public static int getHttp429DefaultDelaySecs() {
        return getIntPref(Key.DL_HTTP_429_DEFAULT_DELAY, Default.DL_HTTP_429_DEFAULT_DELAY);
    }


    public static int getLastKnownAppVersionCode() {
        return getIntPref(Key.LAST_KNOWN_APP_VERSION_CODE, 0);
    }

    public static void setLastKnownAppVersionCode(int versionCode) {
        sharedPreferences.edit().putString(Key.LAST_KNOWN_APP_VERSION_CODE, Integer.toString(versionCode)).apply();
    }

    public static boolean isQueueAutostart() {
        return getBoolPref(Key.QUEUE_AUTOSTART, Default.QUEUE_AUTOSTART);
    }

    public static int getQueueNewDownloadPosition() {
        return getIntPref(Key.QUEUE_NEW_DOWNLOADS_POSITION, Default.QUEUE_NEW_DOWNLOADS_POSITION);
    }

    public static boolean isQueueWifiOnly() {
        return getBoolPref(Key.QUEUE_WIFI_ONLY, Default.QUEUE_WIFI_ONLY);
    }

    public static boolean isDownloadLargeOnlyWifi() {
        return getBoolPref(Key.DL_SIZE_WIFI, Default.DL_SIZE_WIFI);
    }

    public static int getDownloadLargeOnlyWifiThresholdMB() {
        return getIntPref(Key.DL_SIZE_WIFI_THRESHOLD, Default.DL_SIZE_WIFI_THRESHOLD);
    }

    public static int getDownloadLargeOnlyWifiThresholdPages() {
        return getIntPref(Key.DL_PAGES_WIFI_THRESHOLD, Default.DL_PAGES_WIFI_THRESHOLD);
    }

    public static boolean isDlRetriesActive() {
        return getBoolPref(Key.DL_RETRIES_ACTIVE, Default.DL_RETRIES_ACTIVE);
    }

    public static int getDlRetriesNumber() {
        return getIntPref(Key.DL_RETRIES_NUMBER, Default.DL_RETRIES_NUMBER);
    }

    public static int getDlRetriesMemLimit() {
        return getIntPref(Key.DL_RETRIES_MEM_LIMIT, Default.DL_RETRIES_MEM_LIMIT);
    }

    public static int getDlSpeedCap() {
        return getIntPref(Key.DL_SPEED_CAP, Default.DL_SPEED_CAP);
    }

    public static int getTagBlockingBehaviour() {
        return getIntPref(Key.DL_BLOCKED_TAG_BEHAVIOUR, Default.DL_BLOCKED_TAGS_BEHAVIOUR);
    }

    public static int getColorTheme() {
        return getIntPref(Key.COLOR_THEME, Default.COLOR_THEME);
    }

    public static void setColorTheme(int colorTheme) {
        sharedPreferences.edit().putString(Key.COLOR_THEME, Integer.toString(colorTheme)).apply();
    }

    public static boolean isLockOnAppRestore() {
        return getBoolPref(Key.LOCK_ON_APP_RESTORE, Default.LOCK_ON_APP_RESTORE);
    }

    public static void setLockOnAppRestore(boolean lockOnAppRestore) {
        sharedPreferences.edit().putBoolean(Key.LOCK_ON_APP_RESTORE, lockOnAppRestore).apply();
    }

    public static int getLockTimer() {
        return getIntPref(Key.LOCK_TIMER, Default.LOCK_TIMER);
    }

    public static void setLockTimer(int lockTimer) {
        sharedPreferences.edit().putString(Key.LOCK_TIMER, Integer.toString(lockTimer)).apply();
    }

    public static Grouping getGroupingDisplay() {
        return Grouping.Companion.searchById(getIntPref(Key.GROUPING_DISPLAY, Default.GROUPING_DISPLAY));
    }

    public static void setGroupingDisplay(int groupingDisplay) {
        sharedPreferences.edit().putString(Key.GROUPING_DISPLAY, Integer.toString(groupingDisplay)).apply();
    }

    public static int getArtistGroupVisibility() {
        return getIntPref(Key.ARTIST_GROUP_VISIBILITY, Default.ARTIST_GROUP_VISIBILITY);
    }

    public static void setArtistGroupVisibility(int artistGroupVisibility) {
        sharedPreferences.edit().putString(Key.ARTIST_GROUP_VISIBILITY, Integer.toString(artistGroupVisibility)).apply();
    }

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

    public static boolean isDownloadDuplicateAsk() {
        return getBoolPref(Key.DOWNLOAD_DUPLICATE_ASK, Default.DOWNLOAD_DUPLICATE_ASK);
    }

    public static void setDownloadDuplicateAsk(boolean value) {
        sharedPreferences.edit().putBoolean(Key.DOWNLOAD_DUPLICATE_ASK, value).apply();
    }

    public static boolean isDownloadPlusDuplicateTry() {
        return getBoolPref(Key.DOWNLOAD_PLUS_DUPLICATE_TRY, Default.DOWNLOAD_PLUS_DUPLICATE_TRY);
    }

    public static void setDownloadDuplicateTry(boolean value) {
        sharedPreferences.edit().putBoolean(Key.DOWNLOAD_PLUS_DUPLICATE_TRY, value).apply();
    }

    public static boolean isDownloadEhHires() {
        return getBoolPref(Key.DL_EH_HIRES, Default.DL_EH_HIRES);
    }


    public static final class Key {

        private Key() {
            throw new IllegalStateException("Utility class");
        }

        public static final String CHECK_UPDATE_MANUAL = "pref_check_updates_manual";
        static final String VERSION_KEY = "prefs_version";
        public static final String DRAWER_SOURCES = "pref_drawer_sources";
        public static final String EXTERNAL_LIBRARY = "pref_external_library";
        public static final String EXTERNAL_LIBRARY_DETACH = "pref_detach_external_library";
        public static final String STORAGE_MANAGEMENT = "storage_mgt";
        static final String WEBVIEW_OVERRIDE_OVERVIEW_LISTS = "pref_webview_override_overview_lists";
        static final String WEBVIEW_INITIAL_ZOOM_LISTS = "pref_webview_initial_zoom_lists";
        static final String BROWSER_RESUME_LAST = "pref_browser_resume_last";
        public static final String BROWSER_MARK_DOWNLOADED = "browser_mark_downloaded";
        public static final String BROWSER_MARK_MERGED = "browser_mark_merged";
        public static final String BROWSER_MARK_QUEUED = "browser_mark_queued";
        public static final String BROWSER_MARK_BLOCKED = "browser_mark_blocked";
        public static final String BROWSER_DL_ACTION = "pref_browser_dl_action";
        public static final String BROWSER_QUICK_DL = "pref_browser_quick_dl";
        public static final String BROWSER_QUICK_DL_THRESHOLD = "pref_browser_quick_dl_threshold";
        public static final String BROWSER_DNS_OVER_HTTPS = "pref_browser_dns_over_https";
        public static final String BROWSER_CLEAR_COOKIES = "pref_browser_clear_cookies";
        public static final String BROWSER_NHENTAI_INVISIBLE_BLACKLIST = "pref_nhentai_invisible_blacklist";
        static final String LAST_KNOWN_APP_VERSION_CODE = "last_known_app_version_code";
        public static final String COLOR_THEME = "pref_color_theme";
        static final String QUEUE_AUTOSTART = "pref_queue_autostart";
        static final String QUEUE_NEW_DOWNLOADS_POSITION = "pref_queue_new_position";
        static final String QUEUE_WIFI_ONLY = "pref_queue_wifi_only";
        static final String DL_SIZE_WIFI = "pref_dl_size_wifi";
        static final String DL_SIZE_WIFI_THRESHOLD = "pref_dl_size_wifi_threshold";
        static final String DL_PAGES_WIFI_THRESHOLD = "pref_dl_pages_wifi_threshold";
        static final String DL_RETRIES_ACTIVE = "pref_dl_retries_active";
        static final String DL_RETRIES_NUMBER = "pref_dl_retries_number";
        static final String DL_RETRIES_MEM_LIMIT = "pref_dl_retries_mem_limit";
        public static final String DL_SPEED_CAP = "dl_speed_cap";
        static final String DL_BLOCKED_TAG_BEHAVIOUR = "pref_dl_blocked_tags_behaviour";
        static final String DL_EH_HIRES = "pref_dl_eh_hires";
        public static final String DL_THREADS_QUANTITY_LISTS = "pref_dl_threads_quantity_lists";
        public static final String DL_HTTP_429_DEFAULT_DELAY = "pref_dl_http_429_default_delay";
        public static final String ACTIVE_SITES = "active_sites";
        static final String LOCK_ON_APP_RESTORE = "pref_lock_on_app_restore";
        static final String LOCK_TIMER = "pref_lock_timer";
        public static final String GROUPING_DISPLAY = "grouping_display";
        public static final String ARTIST_GROUP_VISIBILITY = "artist_group_visibility";
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
        public static final String DOWNLOAD_DUPLICATE_ASK = "download_duplicate_ask";
        public static final String DOWNLOAD_PLUS_DUPLICATE_TRY = "download_plus_duplicate_try";

        // Deprecated values kept for housekeeping/migration
        static final String VIEWER_FLING_FACTOR = "pref_viewer_fling_factor";
    }

    // IMPORTANT : Any default value change must be mirrored in res/values/strings_settings.xml
    public static final class Default {

        private Default() {
            throw new IllegalStateException("Utility class");
        }

        static final boolean WEBVIEW_OVERRIDE_OVERVIEW = false;
        public static final int WEBVIEW_INITIAL_ZOOM = 20;
        static final boolean BROWSER_RESUME_LAST = false;
        static final boolean BROWSER_MARK_DOWNLOADED = false;
        static final boolean BROWSER_MARK_MERGED = false;
        static final boolean BROWSER_MARK_QUEUED = false;
        static final boolean BROWSER_MARK_BLOCKED = false;
        static final int BROWSER_DL_ACTION = Constant.DL_ACTION_DL_PAGES;
        static final boolean BROWSER_QUICK_DL = true;
        static final int BROWSER_QUICK_DL_THRESHOLD = 1500; // 1.5s
        static final int BROWSER_DNS_OVER_HTTPS = Source.NONE.getValue(); // No DNS
        static final boolean BROWSER_NHENTAI_INVISIBLE_BLACKLIST = false;
        static final int DL_THREADS_QUANTITY = Constant.DOWNLOAD_THREAD_COUNT_AUTO;
        static final int DL_HTTP_429_DEFAULT_DELAY = 120;
        public static final int COLOR_THEME = Constant.COLOR_THEME_LIGHT;
        static final boolean QUEUE_AUTOSTART = true;
        public static final int QUEUE_NEW_DOWNLOADS_POSITION = Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM;
        static final boolean QUEUE_WIFI_ONLY = false;
        static final boolean DL_SIZE_WIFI = false;
        static final int DL_SIZE_WIFI_THRESHOLD = 40;
        static final int DL_PAGES_WIFI_THRESHOLD = 999999;
        static final boolean DL_RETRIES_ACTIVE = false;
        static final int DL_RETRIES_NUMBER = 5;
        static final int DL_RETRIES_MEM_LIMIT = 100;
        static final boolean DL_EH_HIRES = false;
        static final int DL_SPEED_CAP = Constant.DL_SPEED_CAP_NONE;
        static final int DL_BLOCKED_TAGS_BEHAVIOUR = Constant.DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE;
        // Default menu in v1.9.x
        static final boolean LOCK_ON_APP_RESTORE = false;
        static final int LOCK_TIMER = Constant.LOCK_TIMER_30S;
        static final int GROUPING_DISPLAY = Grouping.FLAT.getId();
        static final int ARTIST_GROUP_VISIBILITY = Constant.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS;
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
        static final boolean DOWNLOAD_DUPLICATE_ASK = true;
        static final boolean DOWNLOAD_PLUS_DUPLICATE_TRY = true;
    }

    // IMPORTANT : Any value change must be mirrored in res/values/array_preferences.xml
    @SuppressWarnings("unused")
    public static final class Constant {

        private Constant() {
            throw new IllegalStateException("Utility class");
        }

        public static final int DOWNLOAD_THREAD_COUNT_AUTO = 0;

        public static final int ORDER_CONTENT_FAVOURITE = -2; // Artificial order created for clarity purposes

        public static final int QUEUE_NEW_DOWNLOADS_POSITION_TOP = 0;
        public static final int QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM = 1;
        public static final int QUEUE_NEW_DOWNLOADS_POSITION_ASK = 2;

        public static final int DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE = 0;
        public static final int DL_TAG_BLOCKING_BEHAVIOUR_QUEUE_ERROR = 1;

        public static final int DL_ACTION_DL_PAGES = 0;
        public static final int DL_ACTION_STREAM = 1;
        public static final int DL_ACTION_ASK = 2;

        public static final int DL_SPEED_CAP_NONE = -1;
        public static final int DL_SPEED_CAP_100 = 0;
        public static final int DL_SPEED_CAP_200 = 1;
        public static final int DL_SPEED_CAP_400 = 2;
        public static final int DL_SPEED_CAP_800 = 3;

        public static final int COLOR_THEME_LIGHT = Theme.LIGHT.getId();
        public static final int COLOR_THEME_DARK = Theme.DARK.getId();
        public static final int COLOR_THEME_BLACK = Theme.BLACK.getId();

        public static final int LOCK_TIMER_OFF = 0;
        public static final int LOCK_TIMER_10S = 1;
        public static final int LOCK_TIMER_30S = 2;
        public static final int LOCK_TIMER_1M = 3;
        public static final int LOCK_TIMER_2M = 4;

        public static final int ARTIST_GROUP_VISIBILITY_ARTISTS = 0;
        public static final int ARTIST_GROUP_VISIBILITY_GROUPS = 1;
        public static final int ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS = 2;
    }
}
