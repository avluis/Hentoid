package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.annimon.stream.Stream;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.Theme;
import timber.log.Timber;

/**
 * Created by Shiro on 2/21/2018.
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

        int savedVersion = sharedPreferences.getInt(Key.PREFS_VERSION_KEY, VERSION);
        if (savedVersion != VERSION) {
            Timber.d("Shared Prefs Key Mismatch! Clearing Prefs!");
            sharedPreferences.edit()
                    .clear()
                    .apply();
        }
    }

    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    public static void performHousekeeping() {
        // Fling factor -> Swipe to fling (v1.9.0)
        if (sharedPreferences.contains(Key.PREF_VIEWER_FLING_FACTOR)) {
            int flingFactor = Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_FLING_FACTOR, "0") + "");
            sharedPreferences.edit().putBoolean(Key.PREF_VIEWER_SWIPE_TO_FLING, flingFactor > 0).apply();
            sharedPreferences.edit().remove(Key.PREF_VIEWER_FLING_FACTOR).apply();
        }

        if (sharedPreferences.contains(Key.PREF_ANALYTICS_TRACKING)) {
            boolean analyticsTracking = sharedPreferences.getBoolean(Key.PREF_ANALYTICS_TRACKING, false);
            sharedPreferences.edit().putBoolean(Key.PREF_ANALYTICS_PREFERENCE, !analyticsTracking).apply();
            sharedPreferences.edit().remove(Key.PREF_ANALYTICS_TRACKING).apply();
        }

        if (sharedPreferences.contains(Key.PREF_HIDE_RECENT)) {
            boolean hideRecent = sharedPreferences.getBoolean(Key.PREF_HIDE_RECENT, !BuildConfig.DEBUG);
            sharedPreferences.edit().putBoolean(Key.PREF_APP_PREVIEW, !hideRecent).apply();
            sharedPreferences.edit().remove(Key.PREF_HIDE_RECENT).apply();
        }

        if (sharedPreferences.contains(Key.PREF_CHECK_UPDATES_LISTS)) {
            boolean checkUpdates = sharedPreferences.getBoolean(Key.PREF_CHECK_UPDATES_LISTS, Default.PREF_CHECK_UPDATES);
            sharedPreferences.edit().putBoolean(Key.PREF_CHECK_UPDATES, checkUpdates).apply();
            sharedPreferences.edit().remove(Key.PREF_CHECK_UPDATES_LISTS).apply();
        }

        if (sharedPreferences.contains(Key.DARK_MODE)) {
            int darkMode = Integer.parseInt(sharedPreferences.getString(Key.DARK_MODE, "0") + "");
            int colorTheme = (0 == darkMode) ? Constant.COLOR_THEME_LIGHT : Constant.COLOR_THEME_DARK;
            sharedPreferences.edit().putString(Key.PREF_COLOR_THEME, colorTheme + "").apply();
            sharedPreferences.edit().remove(Key.DARK_MODE).apply();
        }

        if (sharedPreferences.contains(Key.PREF_ORDER_CONTENT_LISTS)) {
            int field = 0;
            boolean isDesc = false;

            switch (sharedPreferences.getInt(Key.PREF_ORDER_CONTENT_LISTS, Constant.ORDER_CONTENT_TITLE_ALPHA)) {
                case (Constant.ORDER_CONTENT_TITLE_ALPHA):
                    field = Constant.ORDER_FIELD_TITLE;
                    break;
                case (Constant.ORDER_CONTENT_TITLE_ALPHA_INVERTED):
                    field = Constant.ORDER_FIELD_TITLE;
                    isDesc = true;
                    break;
                case (Constant.ORDER_CONTENT_LAST_DL_DATE_FIRST):
                    field = Constant.ORDER_FIELD_DOWNLOAD_DATE;
                    isDesc = true;
                    break;
                case (Constant.ORDER_CONTENT_LAST_DL_DATE_LAST):
                    field = Constant.ORDER_FIELD_DOWNLOAD_DATE;
                    break;
                case (Constant.ORDER_CONTENT_RANDOM):
                    field = Constant.ORDER_FIELD_RANDOM;
                    break;
                case (Constant.ORDER_CONTENT_LAST_UL_DATE_FIRST):
                    field = Constant.ORDER_FIELD_UPLOAD_DATE;
                    isDesc = true;
                    break;
                case (Constant.ORDER_CONTENT_LEAST_READ):
                    field = Constant.ORDER_FIELD_READS;
                    break;
                case (Constant.ORDER_CONTENT_MOST_READ):
                    field = Constant.ORDER_FIELD_READS;
                    isDesc = true;
                    break;
                case (Constant.ORDER_CONTENT_LAST_READ):
                    field = Constant.ORDER_FIELD_READ_DATE;
                    isDesc = true;
                    break;
                case (Constant.ORDER_CONTENT_PAGES_DESC):
                    field = Constant.ORDER_FIELD_NB_PAGES;
                    isDesc = true;
                    break;
                case (Constant.ORDER_CONTENT_PAGES_ASC):
                    field = Constant.ORDER_FIELD_NB_PAGES;
                    break;
                default:
                    // Nothing there
            }
            sharedPreferences.edit().putInt(Key.PREF_ORDER_CONTENT_FIELD, field).apply();
            sharedPreferences.edit().putBoolean(Key.PREF_ORDER_CONTENT_DESC, isDesc).apply();
            sharedPreferences.edit().remove(Key.PREF_ORDER_CONTENT_LISTS).apply();
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

    public static boolean isAnalyticsEnabled() {
        return sharedPreferences.getBoolean(Key.PREF_ANALYTICS_PREFERENCE, true);
    }

    public static boolean isAutomaticUpdateEnabled() {
        return sharedPreferences.getBoolean(Key.PREF_CHECK_UPDATES, Default.PREF_CHECK_UPDATES);
    }

    public static boolean isFirstRun() {
        return sharedPreferences.getBoolean(Key.PREF_FIRST_RUN, Default.PREF_FIRST_RUN_DEFAULT);
    }

    public static void setIsFirstRun(boolean isFirstRun) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_FIRST_RUN, isFirstRun)
                .apply();
    }

    public static String getSettingsFolder() {
        return sharedPreferences.getString(Key.PREF_SETTINGS_FOLDER, "");
    }

    public static int getContentSortField() {
        return sharedPreferences.getInt(Key.PREF_ORDER_CONTENT_FIELD, Default.PREF_ORDER_CONTENT_FIELD);
    }

    public static void setContentSortField(int sortField) {
        sharedPreferences.edit()
                .putInt(Key.PREF_ORDER_CONTENT_FIELD, sortField)
                .apply();
    }

    public static boolean isContentSortDesc() {
        return sharedPreferences.getBoolean(Key.PREF_ORDER_CONTENT_DESC, Default.PREF_ORDER_CONTENT_DESC);
    }

    public static void setContentSortDesc(boolean isDesc) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_ORDER_CONTENT_DESC, isDesc)
                .apply();
    }

    public static int getAttributesSortOrder() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_ORDER_ATTRIBUTE_LISTS, Default.PREF_ORDER_ATTRIBUTES_DEFAULT + "") + "");
    }

    public static int getContentPageQuantity() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_QUANTITY_PER_PAGE_LISTS,
                Default.PREF_QUANTITY_PER_PAGE_DEFAULT + "") + "");
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
        return sharedPreferences.getBoolean(Key.PREF_APP_PREVIEW, BuildConfig.DEBUG);
    }

    public static String getStorageUri() {
        return sharedPreferences.getString(Key.PREF_SD_STORAGE_URI, "");
    }

    public static void setStorageUri(String uri) {
        sharedPreferences.edit()
                .putString(Key.PREF_SD_STORAGE_URI, uri)
                .apply();
    }

    static int getFolderNameFormat() {
        return Integer.parseInt(
                sharedPreferences.getString(Key.PREF_FOLDER_NAMING_CONTENT_LISTS,
                        Default.PREF_FOLDER_NAMING_CONTENT_DEFAULT + "") + "");
    }

    public static int getWebViewInitialZoom() {
        return Integer.parseInt(
                sharedPreferences.getString(
                        Key.PREF_WEBVIEW_INITIAL_ZOOM_LISTS,
                        Default.PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT + "") + "");
    }

    public static boolean getWebViewOverview() {
        return sharedPreferences.getBoolean(
                Key.PREF_WEBVIEW_OVERRIDE_OVERVIEW_LISTS,
                Default.PREF_WEBVIEW_OVERRIDE_OVERVIEW_DEFAULT);
    }

    public static boolean isBrowserResumeLast() {
        return sharedPreferences.getBoolean(Key.PREF_BROWSER_RESUME_LAST, Default.PREF_BROWSER_RESUME_LAST_DEFAULT);
    }

    public static boolean isBrowserAugmented() {
        return sharedPreferences.getBoolean(Key.PREF_BROWSER_AUGMENTED, Default.PREF_BROWSER_AUGMENTED_DEFAULT);
    }

    public static boolean isBrowserQuickDl() {
        return sharedPreferences.getBoolean(Key.PREF_BROWSER_QUICK_DL, Default.PREF_BROWSER_QUICK_DL);
    }

    public static int getDownloadThreadCount() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_DL_THREADS_QUANTITY_LISTS,
                Default.PREF_DL_THREADS_QUANTITY_DEFAULT + "") + "");
    }

    static int getFolderTruncationNbChars() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_FOLDER_TRUNCATION_LISTS,
                Default.PREF_FOLDER_TRUNCATION_DEFAULT + "") + "");
    }

    public static boolean isViewerResumeLastLeft() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_RESUME_LAST_LEFT, Default.PREF_VIEWER_RESUME_LAST_LEFT);
    }

    public static boolean isViewerKeepScreenOn() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_KEEP_SCREEN_ON, Default.PREF_VIEWER_KEEP_SCREEN_ON);
    }

    public static int getViewerDisplayMode() {
        if (Constant.PREF_VIEWER_ORIENTATION_HORIZONTAL == getViewerOrientation())
            return Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_IMAGE_DISPLAY, Integer.toString(Default.PREF_VIEWER_IMAGE_DISPLAY)) + "");
        else
            return Constant.PREF_VIEWER_DISPLAY_FIT; // The only relevant mode for vertical (aka. webtoon) display
    }

    public static int getViewerBrowseMode() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_BROWSE_MODE, Integer.toString(Default.PREF_VIEWER_BROWSE_MODE)) + "");
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

    public static boolean isContentSmoothRendering(Map<String, String> bookPrefs) {
        if (bookPrefs.containsKey(Key.PREF_VIEWER_RENDERING)) {
            String value = bookPrefs.get(Key.PREF_VIEWER_RENDERING);
            if (value != null) return isSmoothRendering(Integer.parseInt(value));
        }
        return isViewerSmoothRendering();
    }

    public static boolean isViewerSmoothRendering() {
        return isSmoothRendering(getViewerRenderingMode());
    }

    public static boolean isSmoothRendering(int mode) {
        return (mode == Constant.PREF_VIEWER_RENDERING_SMOOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
    }

    public static int getViewerRenderingMode() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_RENDERING, Integer.toString(Default.PREF_VIEWER_RENDERING)) + "");
    }

    public static boolean isViewerDisplayPageNum() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_DISPLAY_PAGENUM, Default.PREF_VIEWER_DISPLAY_PAGENUM);
    }

    public static boolean isViewerTapTransitions() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_TAP_TRANSITIONS, Default.PREF_VIEWER_TAP_TRANSITIONS);
    }

    public static boolean isViewerZoomTransitions() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_ZOOM_TRANSITIONS, Default.PREF_VIEWER_ZOOM_TRANSITIONS);
    }

    public static boolean isViewerSwipeToFling() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_SWIPE_TO_FLING, Default.PREF_VIEWER_SWIPE_TO_FLING);
    }

    public static boolean isViewerInvertVolumeRocker() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_INVERT_VOLUME_ROCKER, Default.PREF_VIEWER_INVERT_VOLUME_ROCKER);
    }

    public static boolean isViewerTapToTurn() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_PAGE_TURN_TAP, Default.PREF_VIEWER_PAGE_TURN_TAP);
    }

    public static boolean isViewerSwipeToTurn() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_PAGE_TURN_SWIPE, Default.PREF_VIEWER_PAGE_TURN_SWIPE);
    }

    public static boolean isViewerVolumeToTurn() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_PAGE_TURN_VOLUME, Default.PREF_VIEWER_PAGE_TURN_VOLUME);
    }

    public static boolean isViewerOpenBookInGalleryMode() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_OPEN_GALLERY, Default.PREF_VIEWER_OPEN_GALLERY);
    }

    public static boolean isViewerContinuous() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_CONTINUOUS, Default.PREF_VIEWER_CONTINUOUS);
    }

    public static int getViewerReadThreshold() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_READ_THRESHOLD, Integer.toString(Default.PREF_VIEWER_READ_THRESHOLD)) + "");
    }

    public static int getViewerSlideshowDelay() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_SLIDESHOW_DELAY, Integer.toString(Default.PREF_VIEWER_SLIDESHOW_DELAY)) + "");
    }

    public static int getViewerSeparatingBars() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_VIEWER_SEPARATING_BARS, Integer.toString(Default.PREF_VIEWER_SEPARATING_BARS)) + "");
    }

    public static boolean isViewerHoldToZoom() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_HOLD_TO_ZOOM, Default.PREF_VIEWER_HOLD_TO_ZOOM);
    }

    public static boolean isViewerAutoRotate() {
        return sharedPreferences.getBoolean(Key.PREF_VIEWER_AUTO_ROTATE, Default.PREF_VIEWER_AUTO_ROTATE);
    }

    public static int getLastKnownAppVersionCode() {
        return Integer.parseInt(sharedPreferences.getString(Key.LAST_KNOWN_APP_VERSION_CODE, "0") + "");
    }

    public static void setLastKnownAppVersionCode(int versionCode) {
        sharedPreferences.edit()
                .putString(Key.LAST_KNOWN_APP_VERSION_CODE, Integer.toString(versionCode))
                .apply();
    }

    public static boolean isQueueAutostart() {
        return sharedPreferences.getBoolean(Key.PREF_QUEUE_AUTOSTART, Default.PREF_QUEUE_AUTOSTART);
    }

    public static boolean isQueueWifiOnly() {
        return sharedPreferences.getBoolean(Key.PREF_QUEUE_WIFI_ONLY, Default.PREF_QUEUE_WIFI_ONLY);
    }

    public static boolean isDownloadLargeOnlyWifi() {
        return sharedPreferences.getBoolean(Key.PREF_DL_SIZE_WIFI, Default.PREF_DL_SIZE_WIFI);
    }

    public static int getDownloadLargeOnlyWifiThreshold() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_DL_SIZE_WIFI_THRESHOLD, Integer.toString(Default.PREF_DL_SIZE_WIFI_THRESHOLD)) + "");
    }

    public static boolean isDlRetriesActive() {
        return sharedPreferences.getBoolean(Key.PREF_DL_RETRIES_ACTIVE, Default.PREF_DL_RETRIES_ACTIVE);
    }

    public static int getDlRetriesNumber() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_DL_RETRIES_NUMBER, Integer.toString(Default.PREF_DL_RETRIES_NUMBER)) + "");
    }

    public static int getDlRetriesMemLimit() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_DL_RETRIES_MEM_LIMIT, Integer.toString(Default.PREF_DL_RETRIES_MEM_LIMIT)) + "");
    }

    public static boolean isDlHitomiWebp() {
        return sharedPreferences.getBoolean(Key.PREF_DL_HITOMI_WEBP, Default.PREF_DL_HITOMI_WEBP);
    }

    public static List<Site> getActiveSites() {
        String siteCodesStr = sharedPreferences.getString(Key.ACTIVE_SITES, Default.ACTIVE_SITES) + "";
        if (siteCodesStr.isEmpty()) return Collections.emptyList();

        List<String> siteCodes = Arrays.asList(siteCodesStr.split(","));
        return Stream.of(siteCodes).map(s -> Site.searchByCode(Long.valueOf(s))).toList();
    }

    public static void setActiveSites(List<Site> activeSites) {
        List<Integer> siteCodes = Stream.of(activeSites).map(Site::getCode).toList();
        sharedPreferences.edit()
                .putString(Key.ACTIVE_SITES, android.text.TextUtils.join(",", siteCodes))
                .apply();
    }

    public static int getColorTheme() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_COLOR_THEME, Integer.toString(Default.PREF_COLOR_THEME)) + "");
    }

    public static void setColorTheme(int colorTheme) {
        sharedPreferences.edit()
                .putString(Key.PREF_COLOR_THEME, Integer.toString(colorTheme))
                .apply();
    }

    public static boolean isLockOnAppRestore() {
        return sharedPreferences.getBoolean(Key.PREF_LOCK_ON_APP_RESTORE, Default.PREF_LOCK_ON_APP_RESTORE);
    }

    public static void setLockOnAppRestore(boolean lockOnAppRestore) {
        sharedPreferences.edit()
                .putBoolean(Key.PREF_LOCK_ON_APP_RESTORE, lockOnAppRestore)
                .apply();
    }

    public static int getLockTimer() {
        return Integer.parseInt(sharedPreferences.getString(Key.PREF_LOCK_TIMER, Integer.toString(Default.PREF_LOCK_TIMER)) + "");
    }

    public static void setLockTimer(int lockTimer) {
        sharedPreferences.edit()
                .putString(Key.PREF_LOCK_TIMER, Integer.toString(lockTimer))
                .apply();
    }

    public static final class Key {

        private Key() {
            throw new IllegalStateException("Utility class");
        }

        public static final String PREF_ANALYTICS_PREFERENCE = "pref_analytics_preference";
        public static final String PREF_APP_LOCK = "pref_app_lock";
        public static final String PREF_APP_PREVIEW = "pref_app_preview";
        public static final String PREF_ADD_NO_MEDIA_FILE = "pref_add_no_media_file";
        static final String PREF_CHECK_UPDATES = "pref_check_updates";
        public static final String PREF_CHECK_UPDATE_MANUAL = "pref_check_updates_manual";
        public static final String PREF_REFRESH_LIBRARY = "pref_refresh_bookshelf";
        public static final String DELETE_ALL_EXCEPT_FAVS = "pref_delete_all_except_favs";
        public static final String EXPORT_LIBRARY = "pref_export_library";
        public static final String IMPORT_LIBRARY = "pref_import_library";
        static final String PREF_WELCOME_DONE = "pref_welcome_done";
        static final String PREFS_VERSION_KEY = "prefs_version";
        static final String PREF_QUANTITY_PER_PAGE_LISTS = "pref_quantity_per_page_lists";
        static final String PREF_ORDER_CONTENT_FIELD = "pref_order_content_field";
        static final String PREF_ORDER_CONTENT_DESC = "pref_order_content_desc";
        static final String PREF_ORDER_ATTRIBUTE_LISTS = "pref_order_attribute_lists";
        static final String PREF_FIRST_RUN = "pref_first_run";
        public static final String PREF_ENDLESS_SCROLL = "pref_endless_scroll";
        public static final String PREF_SD_STORAGE_URI = "pref_sd_storage_uri";
        static final String PREF_FOLDER_NAMING_CONTENT_LISTS = "pref_folder_naming_content_lists";
        public static final String PREF_SETTINGS_FOLDER = "folder";
        static final String PREF_WEBVIEW_OVERRIDE_OVERVIEW_LISTS = "pref_webview_override_overview_lists";
        static final String PREF_WEBVIEW_INITIAL_ZOOM_LISTS = "pref_webview_initial_zoom_lists";
        static final String PREF_BROWSER_RESUME_LAST = "pref_browser_resume_last";
        static final String PREF_BROWSER_AUGMENTED = "pref_browser_augmented";
        static final String PREF_BROWSER_QUICK_DL = "pref_browser_quick_dl";
        static final String PREF_FOLDER_TRUNCATION_LISTS = "pref_folder_trunc_lists";
        static final String PREF_VIEWER_RESUME_LAST_LEFT = "pref_viewer_resume_last_left";
        public static final String PREF_VIEWER_KEEP_SCREEN_ON = "pref_viewer_keep_screen_on";
        public static final String PREF_VIEWER_IMAGE_DISPLAY = "pref_viewer_image_display";
        public static final String PREF_VIEWER_RENDERING = "pref_viewer_rendering";
        public static final String PREF_VIEWER_BROWSE_MODE = "pref_viewer_browse_mode";
        public static final String PREF_VIEWER_DISPLAY_PAGENUM = "pref_viewer_display_pagenum";
        public static final String PREF_VIEWER_SWIPE_TO_FLING = "pref_viewer_swipe_to_fling";
        static final String PREF_VIEWER_TAP_TRANSITIONS = "pref_viewer_tap_transitions";
        public static final String PREF_VIEWER_ZOOM_TRANSITIONS = "pref_viewer_zoom_transitions";
        static final String PREF_VIEWER_OPEN_GALLERY = "pref_viewer_open_gallery";
        public static final String PREF_VIEWER_CONTINUOUS = "pref_viewer_continuous";
        static final String PREF_VIEWER_INVERT_VOLUME_ROCKER = "pref_viewer_invert_volume_rocker";
        static final String PREF_VIEWER_PAGE_TURN_SWIPE = "pref_viewer_page_turn_swipe";
        static final String PREF_VIEWER_PAGE_TURN_TAP = "pref_viewer_page_turn_tap";
        static final String PREF_VIEWER_PAGE_TURN_VOLUME = "pref_viewer_page_turn_volume";
        public static final String PREF_VIEWER_SEPARATING_BARS = "pref_viewer_separating_bars";
        static final String PREF_VIEWER_READ_THRESHOLD = "pref_viewer_read_threshold";
        static final String PREF_VIEWER_SLIDESHOW_DELAY = "pref_viewer_slideshow_delay";
        public static final String PREF_VIEWER_HOLD_TO_ZOOM = "pref_viewer_zoom_holding";
        public static final String PREF_VIEWER_AUTO_ROTATE = "pref_viewer_auto_rotate";
        static final String LAST_KNOWN_APP_VERSION_CODE = "last_known_app_version_code";
        public static final String PREF_COLOR_THEME = "pref_color_theme";
        static final String PREF_QUEUE_AUTOSTART = "pref_queue_autostart";
        static final String PREF_QUEUE_WIFI_ONLY = "pref_queue_wifi_only";
        static final String PREF_DL_SIZE_WIFI = "pref_dl_size_wifi";
        static final String PREF_DL_SIZE_WIFI_THRESHOLD = "pref_dl_size_wifi_threshold";
        static final String PREF_DL_RETRIES_ACTIVE = "pref_dl_retries_active";
        static final String PREF_DL_RETRIES_NUMBER = "pref_dl_retries_number";
        static final String PREF_DL_RETRIES_MEM_LIMIT = "pref_dl_retries_mem_limit";
        static final String PREF_DL_HITOMI_WEBP = "pref_dl_hitomi_webp";
        public static final String PREF_DL_THREADS_QUANTITY_LISTS = "pref_dl_threads_quantity_lists";
        public static final String ACTIVE_SITES = "active_sites";
        static final String PREF_LOCK_ON_APP_RESTORE = "pref_lock_on_app_restore";
        static final String PREF_LOCK_TIMER = "pref_lock_timer";

        // Deprecated values kept for housekeeping/migration
        static final String PREF_ANALYTICS_TRACKING = "pref_analytics_tracking";
        static final String PREF_HIDE_RECENT = "pref_hide_recent";
        static final String PREF_VIEWER_FLING_FACTOR = "pref_viewer_fling_factor";
        static final String PREF_CHECK_UPDATES_LISTS = "pref_check_updates_lists";
        static final String DARK_MODE = "pref_dark_mode";
        static final String PREF_ORDER_CONTENT_LISTS = "pref_order_content_lists";
    }

    // IMPORTANT : Any default value change must be mirrored in res/values/strings_settings.xml
    public static final class Default {

        private Default() {
            throw new IllegalStateException("Utility class");
        }

        static final int PREF_QUANTITY_PER_PAGE_DEFAULT = 20;
        static final int PREF_ORDER_CONTENT_FIELD = Constant.ORDER_FIELD_TITLE;
        static final boolean PREF_ORDER_CONTENT_DESC = false;
        static final int PREF_ORDER_ATTRIBUTES_DEFAULT = Constant.ORDER_ATTRIBUTES_COUNT;
        static final boolean PREF_FIRST_RUN_DEFAULT = true;
        static final boolean PREF_ENDLESS_SCROLL_DEFAULT = true;
        static final int PREF_FOLDER_NAMING_CONTENT_DEFAULT = Constant.PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID;
        static final boolean PREF_WEBVIEW_OVERRIDE_OVERVIEW_DEFAULT = false;
        public static final int PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT = 20;
        static final boolean PREF_BROWSER_RESUME_LAST_DEFAULT = false;
        static final boolean PREF_BROWSER_AUGMENTED_DEFAULT = true;
        static final boolean PREF_BROWSER_QUICK_DL = true;
        static final int PREF_DL_THREADS_QUANTITY_DEFAULT = Constant.DOWNLOAD_THREAD_COUNT_AUTO;
        static final int PREF_FOLDER_TRUNCATION_DEFAULT = Constant.TRUNCATE_FOLDER_NONE;
        static final boolean PREF_VIEWER_RESUME_LAST_LEFT = true;
        static final boolean PREF_VIEWER_KEEP_SCREEN_ON = true;
        static final int PREF_VIEWER_IMAGE_DISPLAY = Constant.PREF_VIEWER_DISPLAY_FIT;
        static final int PREF_VIEWER_RENDERING = Constant.PREF_VIEWER_RENDERING_SHARP;
        static final int PREF_VIEWER_BROWSE_MODE = Constant.PREF_VIEWER_BROWSE_NONE;
        static final boolean PREF_VIEWER_DISPLAY_PAGENUM = false;
        static final boolean PREF_VIEWER_TAP_TRANSITIONS = true;
        static final boolean PREF_VIEWER_ZOOM_TRANSITIONS = true;
        static final boolean PREF_VIEWER_OPEN_GALLERY = false;
        static final boolean PREF_VIEWER_CONTINUOUS = false;
        static final boolean PREF_VIEWER_PAGE_TURN_SWIPE = true;
        static final boolean PREF_VIEWER_PAGE_TURN_TAP = true;
        static final boolean PREF_VIEWER_PAGE_TURN_VOLUME = true;
        static final boolean PREF_VIEWER_SWIPE_TO_FLING = false;
        static final boolean PREF_VIEWER_INVERT_VOLUME_ROCKER = false;
        static final int PREF_VIEWER_SEPARATING_BARS = Constant.PREF_VIEWER_SEPARATING_BARS_OFF;
        static final int PREF_VIEWER_READ_THRESHOLD = Constant.PREF_VIEWER_READ_THRESHOLD_1;
        static final int PREF_VIEWER_SLIDESHOW_DELAY = Constant.PREF_VIEWER_SLIDESHOW_DELAY_2;
        static final boolean PREF_VIEWER_HOLD_TO_ZOOM = false;
        static final boolean PREF_VIEWER_AUTO_ROTATE = false;
        static final int PREF_COLOR_THEME = Constant.COLOR_THEME_LIGHT;
        static final boolean PREF_QUEUE_AUTOSTART = true;
        static final boolean PREF_QUEUE_WIFI_ONLY = false;
        static final boolean PREF_DL_SIZE_WIFI = false;
        static final int PREF_DL_SIZE_WIFI_THRESHOLD = 40;
        static final boolean PREF_DL_RETRIES_ACTIVE = false;
        static final int PREF_DL_RETRIES_NUMBER = 3;
        static final int PREF_DL_RETRIES_MEM_LIMIT = 100;
        static final boolean PREF_DL_HITOMI_WEBP = true;
        static final boolean PREF_CHECK_UPDATES = true;
        // Default menu in v1.9.x
        static final Site[] DEFAULT_SITES = new Site[]{Site.NHENTAI, Site.HENTAICAFE, Site.HITOMI, Site.ASMHENTAI, Site.TSUMINO, Site.PURURIN, Site.EHENTAI, Site.FAKKU2, Site.NEXUS, Site.MUSES, Site.DOUJINS};
        static final String ACTIVE_SITES = TextUtils.join(",", Stream.of(DEFAULT_SITES).map(Site::getCode).toList());
        static final boolean PREF_LOCK_ON_APP_RESTORE = false;
        static final int PREF_LOCK_TIMER = Constant.PREF_LOCK_TIMER_30S;
    }

    // IMPORTANT : Any value change must be mirrored in res/values/array_preferences.xml
    public static final class Constant {

        private Constant() {
            throw new IllegalStateException("Utility class");
        }

        public static final int DOWNLOAD_THREAD_COUNT_AUTO = 0;

        public static final int ORDER_CONTENT_FAVOURITE = -2; // Artificial order created for clarity purposes

        public static final int ORDER_FIELD_NONE = -1;
        public static final int ORDER_FIELD_TITLE = 0;
        public static final int ORDER_FIELD_ARTIST = 1; // Not implemented yet
        public static final int ORDER_FIELD_NB_PAGES = 2;
        public static final int ORDER_FIELD_DOWNLOAD_DATE = 3;
        public static final int ORDER_FIELD_UPLOAD_DATE = 4;
        public static final int ORDER_FIELD_READ_DATE = 5;
        public static final int ORDER_FIELD_READS = 6;
        public static final int ORDER_FIELD_SIZE = 7; // Not implemented yet
        public static final int ORDER_FIELD_RANDOM = 99;

        public static final int ORDER_ATTRIBUTES_ALPHABETIC = 0;
        static final int ORDER_ATTRIBUTES_COUNT = 1;
        static final int PREF_FOLDER_NAMING_CONTENT_ID = 0;
        static final int PREF_FOLDER_NAMING_CONTENT_TITLE_ID = 1;
        static final int PREF_FOLDER_NAMING_CONTENT_AUTH_TITLE_ID = 2;
        static final int PREF_FOLDER_NAMING_CONTENT_TITLE_AUTH_ID = 3;
        static final int TRUNCATE_FOLDER_NONE = 0;
        public static final int PREF_VIEWER_DISPLAY_FIT = 0;
        public static final int PREF_VIEWER_DISPLAY_FILL = 1;
        public static final int PREF_VIEWER_DISPLAY_STRETCH = 2;
        public static final int PREF_VIEWER_RENDERING_SHARP = 0;
        public static final int PREF_VIEWER_RENDERING_SMOOTH = 1;
        public static final int PREF_VIEWER_BROWSE_NONE = -1;
        public static final int PREF_VIEWER_BROWSE_LTR = 0;
        public static final int PREF_VIEWER_BROWSE_RTL = 1;
        public static final int PREF_VIEWER_BROWSE_TTB = 2;
        public static final int PREF_VIEWER_DIRECTION_LTR = 0;
        public static final int PREF_VIEWER_DIRECTION_RTL = 1;
        public static final int PREF_VIEWER_ORIENTATION_HORIZONTAL = 0;
        public static final int PREF_VIEWER_ORIENTATION_VERTICAL = 1;
        public static final int PREF_VIEWER_SEPARATING_BARS_OFF = 0;
        public static final int PREF_VIEWER_SEPARATING_BARS_SMALL = 1;
        public static final int PREF_VIEWER_SEPARATING_BARS_MEDIUM = 2;
        public static final int PREF_VIEWER_SEPARATING_BARS_LARGE = 3;
        public static final int PREF_VIEWER_READ_THRESHOLD_1 = 0;
        public static final int PREF_VIEWER_READ_THRESHOLD_2 = 1;
        public static final int PREF_VIEWER_READ_THRESHOLD_5 = 2;
        public static final int PREF_VIEWER_READ_THRESHOLD_ALL = 3;
        public static final int PREF_VIEWER_SLIDESHOW_DELAY_2 = 0;
        public static final int PREF_VIEWER_SLIDESHOW_DELAY_4 = 1;
        public static final int PREF_VIEWER_SLIDESHOW_DELAY_8 = 2;
        public static final int PREF_VIEWER_SLIDESHOW_DELAY_16 = 3;
        public static final int COLOR_THEME_LIGHT = Theme.LIGHT.getId();
        public static final int COLOR_THEME_DARK = Theme.DARK.getId();
        public static final int COLOR_THEME_BLACK = Theme.BLACK.getId();
        public static final int PREF_LOCK_TIMER_OFF = 0;
        public static final int PREF_LOCK_TIMER_10S = 1;
        public static final int PREF_LOCK_TIMER_30S = 2;
        public static final int PREF_LOCK_TIMER_1M = 3;
        public static final int PREF_LOCK_TIMER_2M = 4;

        // Deprecated values kept for housekeeping/migration
        @Deprecated
        public static final int ORDER_CONTENT_TITLE_ALPHA = 0;
        @Deprecated
        public static final int ORDER_CONTENT_LAST_DL_DATE_FIRST = 1;
        @Deprecated
        public static final int ORDER_CONTENT_TITLE_ALPHA_INVERTED = 2;
        @Deprecated
        public static final int ORDER_CONTENT_LAST_DL_DATE_LAST = 3;
        @Deprecated
        public static final int ORDER_CONTENT_RANDOM = 4;
        @Deprecated
        public static final int ORDER_CONTENT_LAST_UL_DATE_FIRST = 5;
        @Deprecated
        public static final int ORDER_CONTENT_LEAST_READ = 6;
        @Deprecated
        public static final int ORDER_CONTENT_MOST_READ = 7;
        @Deprecated
        public static final int ORDER_CONTENT_LAST_READ = 8;
        @Deprecated
        public static final int ORDER_CONTENT_PAGES_DESC = 9;
        @Deprecated
        public static final int ORDER_CONTENT_PAGES_ASC = 10;
    }
}
