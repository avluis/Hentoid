package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.annimon.stream.Stream;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.Theme;
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
            sharedPreferences.edit()
                    .clear()
                    .apply();
        }
    }

    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    public static void performHousekeeping() {
        // Fling factor -> Swipe to fling (v1.9.0)
        if (sharedPreferences.contains(Key.VIEWER_FLING_FACTOR)) {
            int flingFactor = Integer.parseInt(sharedPreferences.getString(Key.VIEWER_FLING_FACTOR, "0") + "");
            sharedPreferences.edit().putBoolean(Key.VIEWER_SWIPE_TO_FLING, flingFactor > 0).apply();
            sharedPreferences.edit().remove(Key.VIEWER_FLING_FACTOR).apply();
        }

        if (sharedPreferences.contains(Key.ANALYTICS_TRACKING)) {
            boolean analyticsTracking = sharedPreferences.getBoolean(Key.ANALYTICS_TRACKING, false);
            sharedPreferences.edit().putBoolean(Key.ANALYTICS_PREFERENCE, !analyticsTracking).apply();
            sharedPreferences.edit().remove(Key.ANALYTICS_TRACKING).apply();
        }

        if (sharedPreferences.contains(Key.HIDE_RECENT)) {
            boolean hideRecent = sharedPreferences.getBoolean(Key.HIDE_RECENT, !BuildConfig.DEBUG);
            sharedPreferences.edit().putBoolean(Key.APP_PREVIEW, !hideRecent).apply();
            sharedPreferences.edit().remove(Key.HIDE_RECENT).apply();
        }

        if (sharedPreferences.contains(Key.CHECK_UPDATES_LISTS)) {
            boolean checkUpdates = sharedPreferences.getBoolean(Key.CHECK_UPDATES_LISTS, Default.CHECK_UPDATES);
            sharedPreferences.edit().putBoolean(Key.CHECK_UPDATES, checkUpdates).apply();
            sharedPreferences.edit().remove(Key.CHECK_UPDATES_LISTS).apply();
        }

        if (sharedPreferences.contains(Key.DARK_MODE)) {
            int darkMode = Integer.parseInt(sharedPreferences.getString(Key.DARK_MODE, "0") + "");
            int colorTheme = (0 == darkMode) ? Constant.COLOR_THEME_LIGHT : Constant.COLOR_THEME_DARK;
            sharedPreferences.edit().putString(Key.COLOR_THEME, colorTheme + "").apply();
            sharedPreferences.edit().remove(Key.DARK_MODE).apply();
        }

        if (sharedPreferences.contains(Key.ORDER_CONTENT_LISTS)) {
            int field;
            boolean isDesc = false;

            switch (sharedPreferences.getInt(Key.ORDER_CONTENT_LISTS, Constant.ORDER_CONTENT_TITLE_ALPHA)) {
                case (Constant.ORDER_CONTENT_TITLE_ALPHA):
                    field = Constant.ORDER_FIELD_TITLE;
                    break;
                case (Constant.ORDER_CONTENT_TITLE_ALPHA_INVERTED):
                    field = Constant.ORDER_FIELD_TITLE;
                    isDesc = true;
                    break;
                case (Constant.ORDER_CONTENT_LAST_DL_DATE_FIRST):
                    field = Constant.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE;
                    isDesc = true;
                    break;
                case (Constant.ORDER_CONTENT_LAST_DL_DATE_LAST):
                    field = Constant.ORDER_FIELD_DOWNLOAD_PROCESSING_DATE;
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
                    field = 0;
            }
            sharedPreferences.edit().putInt(Key.ORDER_CONTENT_FIELD, field).apply();
            sharedPreferences.edit().putBoolean(Key.ORDER_CONTENT_DESC, isDesc).apply();
            sharedPreferences.edit().remove(Key.ORDER_CONTENT_LISTS).apply();
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
        result.remove(Key.FIRST_RUN);
        result.remove(Key.WELCOME_DONE);
        result.remove(Key.SD_STORAGE_URI);
        result.remove(Key.EXTERNAL_LIBRARY_URI);
        result.remove(Key.LAST_KNOWN_APP_VERSION_CODE);

        return result;
    }

    public static void importInformation(Map<String, ?> settings) {
        for (Map.Entry<String, ?> entry : settings.entrySet()) {
            if (entry.getValue() instanceof Integer) {
                sharedPreferences.edit()
                        .putInt(entry.getKey(), (Integer) entry.getValue())
                        .apply();
            } else if (entry.getValue() instanceof String) {
                sharedPreferences.edit()
                        .putString(entry.getKey(), (String) entry.getValue())
                        .apply();
            } else if (entry.getValue() instanceof Boolean) {
                sharedPreferences.edit()
                        .putBoolean(entry.getKey(), (Boolean) entry.getValue())
                        .apply();
            } else if (entry.getValue() instanceof Float) {
                sharedPreferences.edit()
                        .putFloat(entry.getKey(), (Float) entry.getValue())
                        .apply();
            } else if (entry.getValue() instanceof Long) {
                sharedPreferences.edit()
                        .putLong(entry.getKey(), (Long) entry.getValue())
                        .apply();
            }
        }
    }

    private static int getIntPref(@NonNull String key, int defaultValue) {
        if (null == sharedPreferences) return defaultValue;
        else
            return Integer.parseInt(sharedPreferences.getString(key, Integer.toString(defaultValue)) + "");
    }

    private static boolean getBoolPref(@NonNull String key, boolean defaultValue) {
        if (null == sharedPreferences) return defaultValue;
        else
            return sharedPreferences.getBoolean(key, defaultValue);
    }


    // ======= PROPERTIES GETTERS / SETTERS

    public static boolean isFirstRunProcessComplete() {
        return sharedPreferences.getBoolean(Key.WELCOME_DONE, false);
    }

    public static void setIsFirstRunProcessComplete(boolean isFirstRunProcessComplete) {
        sharedPreferences.edit()
                .putBoolean(Key.WELCOME_DONE, isFirstRunProcessComplete)
                .apply();
    }

    public static boolean isAnalyticsEnabled() {
        return getBoolPref(Key.ANALYTICS_PREFERENCE, true);
    }

    public static boolean isAutomaticUpdateEnabled() {
        return getBoolPref(Key.CHECK_UPDATES, Default.CHECK_UPDATES);
    }

    public static boolean isFirstRun() {
        return getBoolPref(Key.FIRST_RUN, Default.FIRST_RUN);
    }

    public static void setIsFirstRun(boolean isFirstRun) {
        sharedPreferences.edit()
                .putBoolean(Key.FIRST_RUN, isFirstRun)
                .apply();
    }

    public static boolean isImportQueueEmptyBooks() {
        return getBoolPref(Key.IMPORT_QUEUE_EMPTY, Default.IMPORT_QUEUE_EMPTY);
    }

    @Deprecated
    public static String getSettingsFolder() {
        return sharedPreferences.getString(Key.SETTINGS_FOLDER, "");
    }

    public static int getLibraryDisplay() {
        return getIntPref(Key.LIBRARY_DISPLAY, Default.LIBRARY_DISPLAY);
    }

    public static void setLibraryDisplay(int displayMode) {
        sharedPreferences.edit()
                .putString(Key.LIBRARY_DISPLAY, Integer.toString(displayMode))
                .apply();
    }

    public static boolean isForceEnglishLocale() {
        return getBoolPref(Key.FORCE_ENGLISH, Default.FORCE_ENGLISH);
    }

    public static int getContentSortField() {
        return sharedPreferences.getInt(Key.ORDER_CONTENT_FIELD, Default.ORDER_CONTENT_FIELD);
    }

    public static void setContentSortField(int sortField) {
        sharedPreferences.edit()
                .putInt(Key.ORDER_CONTENT_FIELD, sortField)
                .apply();
    }

    public static boolean isContentSortDesc() {
        return sharedPreferences.getBoolean(Key.ORDER_CONTENT_DESC, Default.ORDER_CONTENT_DESC);
    }

    public static void setContentSortDesc(boolean isDesc) {
        sharedPreferences.edit()
                .putBoolean(Key.ORDER_CONTENT_DESC, isDesc)
                .apply();
    }

    public static int getGroupSortField() {
        return sharedPreferences.getInt(Key.ORDER_GROUP_FIELD, Default.ORDER_GROUP_FIELD);
    }

    public static void setGroupSortField(int sortField) {
        sharedPreferences.edit()
                .putInt(Key.ORDER_GROUP_FIELD, sortField)
                .apply();
    }

    public static boolean isGroupSortDesc() {
        return sharedPreferences.getBoolean(Key.ORDER_GROUP_DESC, Default.ORDER_GROUP_DESC);
    }

    public static void setGroupSortDesc(boolean isDesc) {
        sharedPreferences.edit()
                .putBoolean(Key.ORDER_GROUP_DESC, isDesc)
                .apply();
    }

    public static int getSearchAttributesSortOrder() {
        return Integer.parseInt(sharedPreferences.getString(Key.SEARCH_ORDER_ATTRIBUTE_LISTS, Default.SEARCH_ORDER_ATTRIBUTES + "") + "");
    }

    public static boolean getSearchAttributesCount() {
        return sharedPreferences.getBoolean(Key.SEARCH_COUNT_ATTRIBUTE_RESULTS, Default.SEARCH_COUNT_ATTRIBUTE_RESULTS);
    }

    public static int getContentPageQuantity() {
        return Integer.parseInt(sharedPreferences.getString(Key.QUANTITY_PER_PAGE_LISTS,
                Default.QUANTITY_PER_PAGE + "") + "");
    }

    public static String getAppLockPin() {
        return sharedPreferences.getString(Key.APP_LOCK, "");
    }

    public static void setAppLockPin(String pin) {
        sharedPreferences.edit()
                .putString(Key.APP_LOCK, pin)
                .apply();
    }

    public static boolean getEndlessScroll() {
        return sharedPreferences.getBoolean(Key.ENDLESS_SCROLL, Default.ENDLESS_SCROLL);
    }

    public static boolean isTopFabEnabled() {
        return sharedPreferences.getBoolean(Key.TOP_FAB, Default.TOP_FAB);
    }

    public static void setTopFabEnabled(boolean value) {
        sharedPreferences.edit()
                .putBoolean(Key.TOP_FAB, value)
                .apply();
    }

    public static boolean getRecentVisibility() {
        return sharedPreferences.getBoolean(Key.APP_PREVIEW, BuildConfig.DEBUG);
    }

    public static String getStorageUri() {
        return sharedPreferences.getString(Key.SD_STORAGE_URI, "");
    }

    public static void setStorageUri(String uri) {
        sharedPreferences.edit()
                .putString(Key.SD_STORAGE_URI, uri)
                .apply();
    }

    public static int getMemoryAlertThreshold() {
        return Integer.parseInt(sharedPreferences.getString(Key.MEMORY_ALERT, Integer.toString(Default.MEMORY_ALERT)) + "");
    }

    public static String getExternalLibraryUri() {
        return sharedPreferences.getString(Key.EXTERNAL_LIBRARY_URI, "");
    }

    public static void setExternalLibraryUri(String uri) {
        sharedPreferences.edit()
                .putString(Key.EXTERNAL_LIBRARY_URI, uri)
                .apply();
    }

    public static boolean isDeleteExternalLibrary() {
        return sharedPreferences.getBoolean(Key.EXTERNAL_LIBRARY_DELETE, Default.EXTERNAL_LIBRARY_DELETE);
    }

    static int getFolderNameFormat() {
        return Integer.parseInt(
                sharedPreferences.getString(Key.FOLDER_NAMING_CONTENT_LISTS,
                        Default.FOLDER_NAMING_CONTENT + "") + "");
    }

    public static int getWebViewInitialZoom() {
        return Integer.parseInt(
                sharedPreferences.getString(
                        Key.WEBVIEW_INITIAL_ZOOM_LISTS,
                        Default.WEBVIEW_INITIAL_ZOOM + "") + "");
    }

    public static boolean getWebViewOverview() {
        return sharedPreferences.getBoolean(
                Key.WEBVIEW_OVERRIDE_OVERVIEW_LISTS,
                Default.WEBVIEW_OVERRIDE_OVERVIEW);
    }

    public static boolean isBrowserResumeLast() {
        return sharedPreferences.getBoolean(Key.BROWSER_RESUME_LAST, Default.BROWSER_RESUME_LAST);
    }

    public static boolean isBrowserAugmented() {
        return sharedPreferences.getBoolean(Key.BROWSER_AUGMENTED, Default.BROWSER_AUGMENTED);
    }

    public static boolean isBrowserMarkDownloaded() {
        return sharedPreferences.getBoolean(Key.BROWSER_MARK_DOWNLOADED, Default.BROWSER_MARK_DOWNLOADED);
    }

    public static int getBrowserDlAction() {
        return Integer.parseInt(
                sharedPreferences.getString(
                        Key.BROWSER_DL_ACTION,
                        Default.BROWSER_DL_ACTION + "") + "");
    }

    public static boolean isBrowserQuickDl() {
        return sharedPreferences.getBoolean(Key.BROWSER_QUICK_DL, Default.BROWSER_QUICK_DL);
    }

    public static int getBrowserQuickDlThreshold() {
        return Integer.parseInt(sharedPreferences.getString(Key.BROWSER_QUICK_DL_THRESHOLD, Integer.toString(Default.BROWSER_QUICK_DL_THRESHOLD)) + "");
    }

    public static int getDnsOverHttps() {
        return Integer.parseInt(sharedPreferences.getString(Key.BROWSER_DNS_OVER_HTTPS, Integer.toString(Default.BROWSER_DNS_OVER_HTTPS)) + "");
    }

    public static boolean isBrowserNhentaiInvisibleBlacklist() {
        return sharedPreferences.getBoolean(Key.BROWSER_NHENTAI_INVISIBLE_BLACKLIST, Default.BROWSER_NHENTAI_INVISIBLE_BLACKLIST);
    }

    public static int getDownloadThreadCount() {
        return Integer.parseInt(sharedPreferences.getString(Key.DL_THREADS_QUANTITY_LISTS,
                Default.DL_THREADS_QUANTITY + "") + "");
    }

    static int getFolderTruncationNbChars() {
        return Integer.parseInt(sharedPreferences.getString(Key.FOLDER_TRUNCATION_LISTS,
                Default.FOLDER_TRUNCATION + "") + "");
    }

    public static boolean isViewerResumeLastLeft() {
        return sharedPreferences.getBoolean(Key.VIEWER_RESUME_LAST_LEFT, Default.VIEWER_RESUME_LAST_LEFT);
    }

    public static boolean isViewerKeepScreenOn() {
        return sharedPreferences.getBoolean(Key.VIEWER_KEEP_SCREEN_ON, Default.VIEWER_KEEP_SCREEN_ON);
    }

    public static boolean isViewerDisplayAroundNotch() {
        return sharedPreferences.getBoolean(Key.VIEWER_DISPLAY_AROUND_NOTCH, Default.VIEWER_DISPLAY_AROUND_NOTCH);
    }

    public static int getContentDisplayMode(final Map<String, String> bookPrefs) {
        if (Constant.VIEWER_ORIENTATION_HORIZONTAL == getContentOrientation(bookPrefs))
            return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_IMAGE_DISPLAY, Integer.toString(Default.VIEWER_IMAGE_DISPLAY)) + "");
        else
            return Constant.VIEWER_DISPLAY_FIT; // The only relevant mode for vertical (aka. webtoon) display
    }

    public static int getContentBrowseMode(final Map<String, String> bookPrefs) {
        if (bookPrefs != null && bookPrefs.containsKey(Key.VIEWER_BROWSE_MODE)) {
            String value = bookPrefs.get(Key.VIEWER_BROWSE_MODE);
            if (value != null) return Integer.parseInt(value);
        }
        return getViewerBrowseMode();
    }

    public static int getContentDirection(final Map<String, String> bookPrefs) {
        return (getContentBrowseMode(bookPrefs) == Constant.VIEWER_BROWSE_RTL) ? Constant.VIEWER_DIRECTION_RTL : Constant.VIEWER_DIRECTION_LTR;
    }

    public static int getContentOrientation(final Map<String, String> bookPrefs) {
        return (getContentBrowseMode(bookPrefs) == Constant.VIEWER_BROWSE_TTB) ? Constant.VIEWER_ORIENTATION_VERTICAL : Constant.VIEWER_ORIENTATION_HORIZONTAL;
    }

    public static int getViewerBrowseMode() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_BROWSE_MODE, Integer.toString(Default.VIEWER_BROWSE_MODE)) + "");
    }

    public static void setViewerBrowseMode(int browseMode) {
        sharedPreferences.edit()
                .putString(Key.VIEWER_BROWSE_MODE, Integer.toString(browseMode))
                .apply();
    }

    public static boolean isContentSmoothRendering(final Map<String, String> bookPrefs) {
        if (bookPrefs != null && bookPrefs.containsKey(Key.VIEWER_RENDERING)) {
            String value = bookPrefs.get(Key.VIEWER_RENDERING);
            if (value != null) return isSmoothRendering(Integer.parseInt(value));
        }
        return isViewerSmoothRendering();
    }

    public static boolean isViewerSmoothRendering() {
        return isSmoothRendering(getViewerRenderingMode());
    }

    private static boolean isSmoothRendering(int mode) {
        return (mode == Constant.VIEWER_RENDERING_SMOOTH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
    }

    private static int getViewerRenderingMode() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_RENDERING, Integer.toString(Default.VIEWER_RENDERING)) + "");
    }

    public static boolean isViewerDisplayPageNum() {
        return sharedPreferences.getBoolean(Key.VIEWER_DISPLAY_PAGENUM, Default.VIEWER_DISPLAY_PAGENUM);
    }

    public static boolean isViewerTapTransitions() {
        return sharedPreferences.getBoolean(Key.VIEWER_TAP_TRANSITIONS, Default.VIEWER_TAP_TRANSITIONS);
    }

    public static boolean isViewerZoomTransitions() {
        return sharedPreferences.getBoolean(Key.VIEWER_ZOOM_TRANSITIONS, Default.VIEWER_ZOOM_TRANSITIONS);
    }

    public static boolean isViewerSwipeToFling() {
        return sharedPreferences.getBoolean(Key.VIEWER_SWIPE_TO_FLING, Default.VIEWER_SWIPE_TO_FLING);
    }

    public static boolean isViewerInvertVolumeRocker() {
        return sharedPreferences.getBoolean(Key.VIEWER_INVERT_VOLUME_ROCKER, Default.VIEWER_INVERT_VOLUME_ROCKER);
    }

    public static boolean isViewerTapToTurn() {
        return sharedPreferences.getBoolean(Key.VIEWER_PAGE_TURN_TAP, Default.VIEWER_PAGE_TURN_TAP);
    }

    public static boolean isViewerTapToTurn2x() {
        return sharedPreferences.getBoolean(Key.VIEWER_PAGE_TURN_TAP_2X, Default.VIEWER_PAGE_TURN_TAP_2X);
    }

    public static boolean isViewerSwipeToTurn() {
        return sharedPreferences.getBoolean(Key.VIEWER_PAGE_TURN_SWIPE, Default.VIEWER_PAGE_TURN_SWIPE);
    }

    public static boolean isViewerVolumeToTurn() {
        return sharedPreferences.getBoolean(Key.VIEWER_PAGE_TURN_VOLUME, Default.VIEWER_PAGE_TURN_VOLUME);
    }

    public static boolean isViewerKeyboardToTurn() {
        return sharedPreferences.getBoolean(Key.VIEWER_PAGE_TURN_KEYBOARD, Default.VIEWER_PAGE_TURN_KEYBOARD);
    }

    public static boolean isViewerVolumeToSwitchBooks() {
        return sharedPreferences.getBoolean(Key.VIEWER_BOOK_SWITCH_VOLUME, Default.VIEWER_BOOK_SWITCH_VOLUME);
    }

    public static boolean isViewerOpenBookInGalleryMode() {
        return sharedPreferences.getBoolean(Key.VIEWER_OPEN_GALLERY, Default.VIEWER_OPEN_GALLERY);
    }

    public static boolean isViewerContinuous() {
        return sharedPreferences.getBoolean(Key.VIEWER_CONTINUOUS, Default.VIEWER_CONTINUOUS);
    }

    public static int getViewerReadThreshold() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_READ_THRESHOLD, Integer.toString(Default.VIEWER_READ_THRESHOLD)) + "");
    }

    public static int getViewerSlideshowDelay() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_SLIDESHOW_DELAY, Integer.toString(Default.VIEWER_SLIDESHOW_DELAY)) + "");
    }

    public static void setViewerSlideshowDelay(int value) {
        sharedPreferences.edit()
                .putString(Key.VIEWER_SLIDESHOW_DELAY, Integer.toString(value))
                .apply();
    }

    public static int getViewerSlideshowDelayVertical() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_SLIDESHOW_DELAY_VERTICAL, Integer.toString(Default.VIEWER_SLIDESHOW_DELAY_VERTICAL)) + "");
    }

    public static void setViewerSlideshowDelayVertical(int value) {
        sharedPreferences.edit()
                .putString(Key.VIEWER_SLIDESHOW_DELAY_VERTICAL, Integer.toString(value))
                .apply();
    }

    public static int getViewerSeparatingBars() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_SEPARATING_BARS, Integer.toString(Default.VIEWER_SEPARATING_BARS)) + "");
    }

    public static boolean isViewerHoldToZoom() {
        return sharedPreferences.getBoolean(Key.VIEWER_HOLD_TO_ZOOM, Default.VIEWER_HOLD_TO_ZOOM);
    }

    public static int getViewerCapTapZoom() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_CAP_TAP_ZOOM, Integer.toString(Default.VIEWER_CAP_TAP_ZOOM)) + "");
    }

    public static boolean isViewerMaintainHorizontalZoom() {
        return sharedPreferences.getBoolean(Key.VIEWER_MAINTAIN_HORIZONTAL_ZOOM, Default.VIEWER_MAINTAIN_HORIZONTAL_ZOOM);
    }

    public static boolean isViewerAutoRotate() {
        return sharedPreferences.getBoolean(Key.VIEWER_AUTO_ROTATE, Default.VIEWER_AUTO_ROTATE);
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
        return sharedPreferences.getBoolean(Key.QUEUE_AUTOSTART, Default.QUEUE_AUTOSTART);
    }

    public static int getQueueNewDownloadPosition() {
        return Integer.parseInt(sharedPreferences.getString(Key.QUEUE_NEW_DOWNLOADS_POSITION, Integer.toString(Default.QUEUE_NEW_DOWNLOADS_POSITION)) + "");
    }

    public static boolean isQueueWifiOnly() {
        return sharedPreferences.getBoolean(Key.QUEUE_WIFI_ONLY, Default.QUEUE_WIFI_ONLY);
    }

    public static boolean isDownloadLargeOnlyWifi() {
        return sharedPreferences.getBoolean(Key.DL_SIZE_WIFI, Default.DL_SIZE_WIFI);
    }

    public static int getDownloadLargeOnlyWifiThresholdMB() {
        return Integer.parseInt(sharedPreferences.getString(Key.DL_SIZE_WIFI_THRESHOLD, Integer.toString(Default.DL_SIZE_WIFI_THRESHOLD)) + "");
    }

    public static int getDownloadLargeOnlyWifiThresholdPages() {
        return Integer.parseInt(sharedPreferences.getString(Key.DL_PAGES_WIFI_THRESHOLD, Integer.toString(Default.DL_PAGES_WIFI_THRESHOLD)) + "");
    }

    public static boolean isDlRetriesActive() {
        return sharedPreferences.getBoolean(Key.DL_RETRIES_ACTIVE, Default.DL_RETRIES_ACTIVE);
    }

    public static int getDlRetriesNumber() {
        return getIntPref(Key.DL_RETRIES_NUMBER, Default.DL_RETRIES_NUMBER);
    }

    public static int getDlRetriesMemLimit() {
        return getIntPref(Key.DL_RETRIES_MEM_LIMIT, Default.DL_RETRIES_MEM_LIMIT);
    }

    public static List<String> getBlockedTags() {
        return Stream.of(sharedPreferences.getString(Key.DL_BLOCKED_TAGS, "").split(",")).map(String::trim).filterNot(String::isEmpty).toList();
    }

    public static int getTagBlockingBehaviour() {
        return Integer.parseInt(sharedPreferences.getString(Key.DL_BLOCKED_TAG_BEHAVIOUR, Integer.toString(Default.DL_BLOCKED_TAGS_BEHAVIOUR)) + "");
    }

    public static List<Site> getActiveSites() {
        String siteCodesStr = sharedPreferences.getString(Key.ACTIVE_SITES, Default.ACTIVE_SITES) + "";
        if (siteCodesStr.isEmpty()) return Collections.emptyList();

        return Stream.of(siteCodesStr.split(",")).distinct().map(s -> Site.searchByCode(Long.parseLong(s))).toList();
    }

    public static void setActiveSites(List<Site> activeSites) {
        List<Integer> siteCodes = Stream.of(activeSites).map(Site::getCode).distinct().toList();
        sharedPreferences.edit()
                .putString(Key.ACTIVE_SITES, android.text.TextUtils.join(",", siteCodes))
                .apply();
    }

    public static int getColorTheme() {
        return getIntPref(Key.COLOR_THEME, Default.COLOR_THEME);
    }

    public static void setColorTheme(int colorTheme) {
        sharedPreferences.edit()
                .putString(Key.COLOR_THEME, Integer.toString(colorTheme))
                .apply();
    }

    public static boolean isLockOnAppRestore() {
        return sharedPreferences.getBoolean(Key.LOCK_ON_APP_RESTORE, Default.LOCK_ON_APP_RESTORE);
    }

    public static void setLockOnAppRestore(boolean lockOnAppRestore) {
        sharedPreferences.edit()
                .putBoolean(Key.LOCK_ON_APP_RESTORE, lockOnAppRestore)
                .apply();
    }

    public static int getLockTimer() {
        return Integer.parseInt(sharedPreferences.getString(Key.LOCK_TIMER, Integer.toString(Default.LOCK_TIMER)) + "");
    }

    public static void setLockTimer(int lockTimer) {
        sharedPreferences.edit()
                .putString(Key.LOCK_TIMER, Integer.toString(lockTimer))
                .apply();
    }

    public static long getMaxDbSizeKb() {
        return Long.parseLong(sharedPreferences.getString(Key.DB_MAX_SIZE, Long.toString(Default.DB_MAX_SIZE_KB)) + "");
    }

    public static Grouping getGroupingDisplay() {
        return Grouping.searchById(Integer.parseInt(sharedPreferences.getString(Key.GROUPING_DISPLAY, Integer.toString(Default.GROUPING_DISPLAY)) + ""));
    }

    public static void setGroupingDisplay(int groupingDisplay) {
        sharedPreferences.edit()
                .putString(Key.GROUPING_DISPLAY, Integer.toString(groupingDisplay))
                .apply();
    }

    public static int getArtistGroupVisibility() {
        return Integer.parseInt(sharedPreferences.getString(Key.ARTIST_GROUP_VISIBILITY, Integer.toString(Default.ARTIST_GROUP_VISIBILITY)) + "");
    }

    public static void setArtistGroupVisibility(int artistGroupVisibility) {
        sharedPreferences.edit()
                .putString(Key.ARTIST_GROUP_VISIBILITY, Integer.toString(artistGroupVisibility))
                .apply();
    }

    public static int getViewerDeleteAskMode() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_DELETE_ASK_MODE, Integer.toString(Default.VIEWER_DELETE_ASK_MODE)) + "");
    }

    public static void setViewerDeleteAskMode(int viewerDeleteAskMode) {
        sharedPreferences.edit()
                .putString(Key.VIEWER_DELETE_ASK_MODE, Integer.toString(viewerDeleteAskMode))
                .apply();
    }

    public static int getViewerDeleteTarget() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_DELETE_TARGET, Integer.toString(Default.VIEWER_DELETE_TARGET)) + "");
    }

    public static void setViewerDeleteTarget(int viewerDeleteTarget) {
        sharedPreferences.edit()
                .putString(Key.VIEWER_DELETE_TARGET, Integer.toString(viewerDeleteTarget))
                .apply();
    }

    public static int getDuplicateSensitivity() {
        return Integer.parseInt(sharedPreferences.getString(Key.DUPLICATE_SENSITIVITY, Integer.toString(Default.DUPLICATE_SENSITIVITY)) + "");
    }

    public static void setDuplicateSensitivity(int duplicateSensitivity) {
        sharedPreferences.edit()
                .putString(Key.DUPLICATE_SENSITIVITY, Integer.toString(duplicateSensitivity))
                .apply();
    }

    public static boolean isDuplicateUseTitle() {
        return sharedPreferences.getBoolean(Key.DUPLICATE_USE_TITLE, Default.DUPLICATE_USE_TITLE);
    }

    public static void setDuplicateUseTitle(boolean useTitle) {
        sharedPreferences.edit()
                .putBoolean(Key.DUPLICATE_USE_TITLE, useTitle)
                .apply();
    }

    public static boolean isDuplicateUseCover() {
        return sharedPreferences.getBoolean(Key.DUPLICATE_USE_COVER, Default.DUPLICATE_USE_COVER);
    }

    public static void setDuplicateUseCover(boolean useCover) {
        sharedPreferences.edit()
                .putBoolean(Key.DUPLICATE_USE_COVER, useCover)
                .apply();
    }

    public static boolean isDuplicateUseArtist() {
        return sharedPreferences.getBoolean(Key.DUPLICATE_USE_ARTIST, Default.DUPLICATE_USE_ARTIST);
    }

    public static void setDuplicateUseArtist(boolean useArtist) {
        sharedPreferences.edit()
                .putBoolean(Key.DUPLICATE_USE_ARTIST, useArtist)
                .apply();
    }

    public static boolean isDuplicateUseSameLanguage() {
        return sharedPreferences.getBoolean(Key.DUPLICATE_USE_SAME_LANGUAGE, Default.DUPLICATE_USE_SAME_LANGUAGE);
    }

    public static void setDuplicateUseSameLanguage(boolean useSameLanguage) {
        sharedPreferences.edit()
                .putBoolean(Key.DUPLICATE_USE_SAME_LANGUAGE, useSameLanguage)
                .apply();
    }

    public static boolean isDuplicateIgnoreChapters() {
        return sharedPreferences.getBoolean(Key.DUPLICATE_IGNORE_CHAPTERS, Default.DUPLICATE_IGNORE_CHAPTERS);
    }

    public static void setDuplicateIgnoreChapters(boolean value) {
        sharedPreferences.edit()
                .putBoolean(Key.DUPLICATE_IGNORE_CHAPTERS, value)
                .apply();
    }

    public static int getDuplicateLastIndex() {
        return Integer.parseInt(sharedPreferences.getString(Key.DUPLICATE_LAST_INDEX, "-1") + "");
    }

    public static void setDuplicateLastIndex(int lastIndex) {
        sharedPreferences.edit()
                .putString(Key.DUPLICATE_LAST_INDEX, Integer.toString(lastIndex))
                .apply();
    }

    public static boolean isDownloadDuplicateAsk() {
        return sharedPreferences.getBoolean(Key.DOWNLOAD_DUPLICATE_ASK, Default.DOWNLOAD_DUPLICATE_ASK);
    }

    public static void setDownloadDuplicateAsk(boolean value) {
        sharedPreferences.edit()
                .putBoolean(Key.DOWNLOAD_DUPLICATE_ASK, value)
                .apply();
    }

    public static boolean isDownloadPlusDuplicateTry() {
        return sharedPreferences.getBoolean(Key.DOWNLOAD_PLUS_DUPLICATE_TRY, Default.DOWNLOAD_PLUS_DUPLICATE_TRY);
    }

    public static void setDownloadDuplicateTry(boolean value) {
        sharedPreferences.edit()
                .putBoolean(Key.DOWNLOAD_PLUS_DUPLICATE_TRY, value)
                .apply();
    }

    public static long getViewerCurrentContent() {
        return Long.parseLong(sharedPreferences.getString(Key.VIEWER_CURRENT_CONTENT, "-1") + "");
    }

    public static void setViewerCurrentContent(long value) {
        sharedPreferences.edit()
                .putString(Key.VIEWER_CURRENT_CONTENT, Long.toString(value))
                .apply();
    }

    public static int getViewerCurrentPageNum() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_CURRENT_PAGENUM, "-1") + "");
    }

    public static void setViewerCurrentPageNum(int value) {
        sharedPreferences.edit()
                .putString(Key.VIEWER_CURRENT_PAGENUM, Integer.toString(value))
                .apply();
    }

    public static int getViewerGalleryColumns() {
        return Integer.parseInt(sharedPreferences.getString(Key.VIEWER_GALLERY_COLUMNS, Integer.toString(Default.VIEWER_GALLERY_COLUMNS)) + "");
    }

    public static final class Key {

        private Key() {
            throw new IllegalStateException("Utility class");
        }

        public static final String ANALYTICS_PREFERENCE = "pref_analytics_preference";
        public static final String APP_LOCK = "pref_app_lock";
        public static final String APP_PREVIEW = "pref_app_preview";
        static final String CHECK_UPDATES = "pref_check_updates";
        public static final String CHECK_UPDATE_MANUAL = "pref_check_updates_manual";
        public static final String REFRESH_LIBRARY = "pref_refresh_bookshelf";
        public static final String DELETE_ALL_EXCEPT_FAVS = "pref_delete_all_except_favs";
        static final String WELCOME_DONE = "pref_welcome_done";
        static final String VERSION_KEY = "prefs_version";
        public static final String FORCE_ENGLISH = "force_english";
        public static final String LIBRARY_DISPLAY = "pref_library_display";
        public static final String IMPORT_QUEUE_EMPTY = "pref_import_queue_empty";
        static final String QUANTITY_PER_PAGE_LISTS = "pref_quantity_per_page_lists";
        static final String ORDER_CONTENT_FIELD = "pref_order_content_field";
        static final String ORDER_CONTENT_DESC = "pref_order_content_desc";
        static final String ORDER_GROUP_FIELD = "pref_order_group_field";
        static final String ORDER_GROUP_DESC = "pref_order_group_desc";
        static final String SEARCH_ORDER_ATTRIBUTE_LISTS = "pref_order_attribute_lists";
        static final String SEARCH_COUNT_ATTRIBUTE_RESULTS = "pref_order_attribute_count";
        static final String FIRST_RUN = "pref_first_run";
        public static final String DRAWER_SOURCES = "pref_drawer_sources";
        public static final String ENDLESS_SCROLL = "pref_endless_scroll";
        public static final String TOP_FAB = "pref_top_fab";
        public static final String SD_STORAGE_URI = "pref_sd_storage_uri";
        public static final String EXTERNAL_LIBRARY = "pref_external_library";
        public static final String EXTERNAL_LIBRARY_URI = "pref_external_library_uri";
        public static final String EXTERNAL_LIBRARY_DELETE = "pref_external_library_delete";
        public static final String EXTERNAL_LIBRARY_DETACH = "pref_detach_external_library";
        static final String FOLDER_NAMING_CONTENT_LISTS = "pref_folder_naming_content_lists";
        public static final String SETTINGS_FOLDER = "folder";
        public static final String MEMORY_USAGE = "pref_memory_usage";
        public static final String MEMORY_ALERT = "pref_memory_alert";
        static final String WEBVIEW_OVERRIDE_OVERVIEW_LISTS = "pref_webview_override_overview_lists";
        static final String WEBVIEW_INITIAL_ZOOM_LISTS = "pref_webview_initial_zoom_lists";
        static final String BROWSER_RESUME_LAST = "pref_browser_resume_last";
        static final String BROWSER_AUGMENTED = "pref_browser_augmented";
        public static final String BROWSER_MARK_DOWNLOADED = "browser_mark_downloaded";
        public static final String BROWSER_DL_ACTION = "pref_browser_dl_action";
        static final String BROWSER_QUICK_DL = "pref_browser_quick_dl";
        static final String BROWSER_QUICK_DL_THRESHOLD = "pref_browser_quick_dl_threshold";
        public static final String BROWSER_DNS_OVER_HTTPS = "pref_browser_dns_over_https";
        public static final String BROWSER_CLEAR_COOKIES = "pref_browser_clear_cookies";
        public static final String BROWSER_NHENTAI_INVISIBLE_BLACKLIST = "pref_nhentai_invisible_blacklist";
        static final String FOLDER_TRUNCATION_LISTS = "pref_folder_trunc_lists";
        static final String VIEWER_RESUME_LAST_LEFT = "pref_viewer_resume_last_left";
        public static final String VIEWER_KEEP_SCREEN_ON = "pref_viewer_keep_screen_on";
        public static final String VIEWER_DISPLAY_AROUND_NOTCH = "pref_viewer_display_notch";
        public static final String VIEWER_IMAGE_DISPLAY = "pref_viewer_image_display";
        public static final String VIEWER_RENDERING = "pref_viewer_rendering";
        public static final String VIEWER_BROWSE_MODE = "pref_viewer_browse_mode";
        public static final String VIEWER_DISPLAY_PAGENUM = "pref_viewer_display_pagenum";
        public static final String VIEWER_SWIPE_TO_FLING = "pref_viewer_swipe_to_fling";
        static final String VIEWER_TAP_TRANSITIONS = "pref_viewer_tap_transitions";
        public static final String VIEWER_ZOOM_TRANSITIONS = "pref_viewer_zoom_transitions";
        static final String VIEWER_OPEN_GALLERY = "pref_viewer_open_gallery";
        public static final String VIEWER_CONTINUOUS = "pref_viewer_continuous";
        static final String VIEWER_INVERT_VOLUME_ROCKER = "pref_viewer_invert_volume_rocker";
        static final String VIEWER_PAGE_TURN_SWIPE = "pref_viewer_page_turn_swipe";
        static final String VIEWER_PAGE_TURN_TAP = "pref_viewer_page_turn_tap";
        static final String VIEWER_PAGE_TURN_TAP_2X = "pref_viewer_page_turn_tap_2x";
        static final String VIEWER_PAGE_TURN_VOLUME = "pref_viewer_page_turn_volume";
        static final String VIEWER_PAGE_TURN_KEYBOARD = "pref_viewer_page_turn_keyboard";
        static final String VIEWER_BOOK_SWITCH_VOLUME = "pref_viewer_book_switch_volume";
        public static final String VIEWER_SEPARATING_BARS = "pref_viewer_separating_bars";
        static final String VIEWER_READ_THRESHOLD = "pref_viewer_read_threshold";
        static final String VIEWER_SLIDESHOW_DELAY = "pref_viewer_slideshow_delay";
        static final String VIEWER_SLIDESHOW_DELAY_VERTICAL = "pref_viewer_slideshow_delay_vertical";
        public static final String VIEWER_HOLD_TO_ZOOM = "pref_viewer_zoom_holding";
        public static final String VIEWER_CAP_TAP_ZOOM = "pref_viewer_cap_tap_zoom";
        public static final String VIEWER_MAINTAIN_HORIZONTAL_ZOOM = "pref_viewer_maintain_horizontal_zoom";
        public static final String VIEWER_AUTO_ROTATE = "pref_viewer_auto_rotate";
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
        static final String DL_BLOCKED_TAGS = "pref_dl_blocked_tags";
        static final String DL_BLOCKED_TAG_BEHAVIOUR = "pref_dl_blocked_tags_behaviour";
        public static final String DL_THREADS_QUANTITY_LISTS = "pref_dl_threads_quantity_lists";
        public static final String ACTIVE_SITES = "active_sites";
        static final String LOCK_ON_APP_RESTORE = "pref_lock_on_app_restore";
        static final String LOCK_TIMER = "pref_lock_timer";
        static final String DB_MAX_SIZE = "db_max_size";
        public static final String GROUPING_DISPLAY = "grouping_display";
        public static final String ARTIST_GROUP_VISIBILITY = "artist_group_visibility";
        public static final String VIEWER_DELETE_ASK_MODE = "viewer_delete_ask";
        public static final String VIEWER_DELETE_TARGET = "viewer_delete_target";
        public static final String VIEWER_CURRENT_CONTENT = "viewer_current_content";
        public static final String VIEWER_CURRENT_PAGENUM = "viewer_current_pagenum";
        public static final String VIEWER_GALLERY_COLUMNS = "viewer_gallery_columns";
        public static final String DUPLICATE_SENSITIVITY = "duplicate_sensitivity";
        public static final String DUPLICATE_USE_TITLE = "duplicate_use_title";
        public static final String DUPLICATE_USE_COVER = "duplicate_use_cover";
        public static final String DUPLICATE_USE_ARTIST = "duplicate_use_artist";
        public static final String DUPLICATE_USE_SAME_LANGUAGE = "duplicate_use_same_language";
        public static final String DUPLICATE_IGNORE_CHAPTERS = "duplicate_ignore_chapters";
        public static final String DUPLICATE_LAST_INDEX = "last_index";
        public static final String DOWNLOAD_DUPLICATE_ASK = "download_duplicate_ask";
        public static final String DOWNLOAD_PLUS_DUPLICATE_TRY = "download_plus_duplicate_try";

        // Deprecated values kept for housekeeping/migration
        static final String ANALYTICS_TRACKING = "pref_analytics_tracking";
        static final String HIDE_RECENT = "pref_hide_recent";
        static final String VIEWER_FLING_FACTOR = "pref_viewer_fling_factor";
        static final String CHECK_UPDATES_LISTS = "pref_check_updates_lists";
        static final String DARK_MODE = "pref_dark_mode";
        static final String ORDER_CONTENT_LISTS = "pref_order_content_lists";
    }

    // IMPORTANT : Any default value change must be mirrored in res/values/strings_settings.xml
    public static final class Default {

        private Default() {
            throw new IllegalStateException("Utility class");
        }

        public static final int LIBRARY_DISPLAY = Constant.LIBRARY_DISPLAY_LIST;
        static final boolean FORCE_ENGLISH = false;
        static final int QUANTITY_PER_PAGE = 20;
        public static final int ORDER_CONTENT_FIELD = Constant.ORDER_FIELD_TITLE;
        public static final int ORDER_GROUP_FIELD = Constant.ORDER_FIELD_TITLE;
        static final boolean ORDER_CONTENT_DESC = false;
        static final boolean ORDER_GROUP_DESC = false;
        static final int SEARCH_ORDER_ATTRIBUTES = Constant.SEARCH_ORDER_ATTRIBUTES_COUNT;
        static final boolean SEARCH_COUNT_ATTRIBUTE_RESULTS = true;
        static final boolean FIRST_RUN = true;
        static final boolean ENDLESS_SCROLL = true;
        static final boolean TOP_FAB = true;
        static final int MEMORY_ALERT = 110;
        static final boolean IMPORT_QUEUE_EMPTY = false;
        static final boolean EXTERNAL_LIBRARY_DELETE = false;
        static final int FOLDER_NAMING_CONTENT = Constant.FOLDER_NAMING_CONTENT_AUTH_TITLE_ID;
        static final boolean WEBVIEW_OVERRIDE_OVERVIEW = false;
        public static final int WEBVIEW_INITIAL_ZOOM = 20;
        static final boolean BROWSER_RESUME_LAST = false;
        static final boolean BROWSER_AUGMENTED = true;
        static final boolean BROWSER_MARK_DOWNLOADED = false;
        static final int BROWSER_DL_ACTION = Constant.DL_ACTION_DL_PAGES;
        static final boolean BROWSER_QUICK_DL = true;
        static final int BROWSER_QUICK_DL_THRESHOLD = 500; // 500ms
        static final int BROWSER_DNS_OVER_HTTPS = -1; // No DNS
        static final boolean BROWSER_NHENTAI_INVISIBLE_BLACKLIST = false;
        static final int DL_THREADS_QUANTITY = Constant.DOWNLOAD_THREAD_COUNT_AUTO;
        static final int FOLDER_TRUNCATION = Constant.TRUNCATE_FOLDER_100;
        static final boolean VIEWER_RESUME_LAST_LEFT = true;
        static final boolean VIEWER_KEEP_SCREEN_ON = true;
        static final boolean VIEWER_DISPLAY_AROUND_NOTCH = true;
        static final int VIEWER_IMAGE_DISPLAY = Constant.VIEWER_DISPLAY_FIT;
        static final int VIEWER_RENDERING = Constant.VIEWER_RENDERING_SHARP;
        static final int VIEWER_BROWSE_MODE = Constant.VIEWER_BROWSE_NONE;
        static final boolean VIEWER_DISPLAY_PAGENUM = false;
        static final boolean VIEWER_TAP_TRANSITIONS = true;
        static final boolean VIEWER_ZOOM_TRANSITIONS = true;
        static final boolean VIEWER_OPEN_GALLERY = false;
        static final boolean VIEWER_CONTINUOUS = false;
        static final boolean VIEWER_PAGE_TURN_SWIPE = true;
        static final boolean VIEWER_PAGE_TURN_TAP = true;
        static final boolean VIEWER_PAGE_TURN_TAP_2X = false;
        static final boolean VIEWER_PAGE_TURN_VOLUME = true;
        static final boolean VIEWER_PAGE_TURN_KEYBOARD = true;
        static final boolean VIEWER_BOOK_SWITCH_VOLUME = false;
        static final boolean VIEWER_SWIPE_TO_FLING = false;
        static final boolean VIEWER_INVERT_VOLUME_ROCKER = false;
        static final int VIEWER_SEPARATING_BARS = Constant.VIEWER_SEPARATING_BARS_OFF;
        static final int VIEWER_READ_THRESHOLD = Constant.VIEWER_READ_THRESHOLD_1;
        public static final int VIEWER_SLIDESHOW_DELAY = Constant.VIEWER_SLIDESHOW_DELAY_2;
        public static final int VIEWER_SLIDESHOW_DELAY_VERTICAL = Constant.VIEWER_SLIDESHOW_DELAY_2;
        static final boolean VIEWER_HOLD_TO_ZOOM = false;
        static final int VIEWER_CAP_TAP_ZOOM = Constant.VIEWER_CAP_TAP_ZOOM_NONE;
        static final boolean VIEWER_MAINTAIN_HORIZONTAL_ZOOM = false;
        static final boolean VIEWER_AUTO_ROTATE = false;
        public static final int COLOR_THEME = Constant.COLOR_THEME_LIGHT;
        static final boolean QUEUE_AUTOSTART = true;
        static final int QUEUE_NEW_DOWNLOADS_POSITION = Constant.QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM;
        static final boolean QUEUE_WIFI_ONLY = false;
        static final boolean DL_SIZE_WIFI = false;
        static final int DL_SIZE_WIFI_THRESHOLD = 40;
        static final int DL_PAGES_WIFI_THRESHOLD = 999999;
        static final boolean DL_RETRIES_ACTIVE = false;
        static final int DL_RETRIES_NUMBER = 3;
        static final int DL_RETRIES_MEM_LIMIT = 100;
        static final int DL_BLOCKED_TAGS_BEHAVIOUR = Constant.DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE;
        static final boolean CHECK_UPDATES = true;
        // Default menu in v1.9.x
        static final Site[] DEFAULT_SITES = new Site[]{Site.NHENTAI, Site.HITOMI, Site.ASMHENTAI, Site.TSUMINO, Site.PURURIN, Site.EHENTAI, Site.FAKKU2, Site.NEXUS, Site.MUSES, Site.DOUJINS};
        static final String ACTIVE_SITES = TextUtils.join(",", Stream.of(DEFAULT_SITES).map(Site::getCode).toList());
        static final boolean LOCK_ON_APP_RESTORE = false;
        static final int LOCK_TIMER = Constant.LOCK_TIMER_30S;
        static final long DB_MAX_SIZE_KB = 2L * 1024 * 1024; // 2GB
        static final int GROUPING_DISPLAY = Grouping.FLAT.getId();
        static final int ARTIST_GROUP_VISIBILITY = Constant.ARTIST_GROUP_VISIBILITY_ARTISTS_GROUPS;
        static final int VIEWER_DELETE_ASK_MODE = Constant.VIEWER_DELETE_ASK_AGAIN;
        static final int VIEWER_DELETE_TARGET = Constant.VIEWER_DELETE_TARGET_PAGE;
        static final int VIEWER_GALLERY_COLUMNS = 4;
        static final int DUPLICATE_SENSITIVITY = 1;
        static final boolean DUPLICATE_USE_TITLE = true;
        static final boolean DUPLICATE_USE_COVER = false;
        static final boolean DUPLICATE_USE_ARTIST = true;
        static final boolean DUPLICATE_USE_SAME_LANGUAGE = false;
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

        // Sorting field codes for content and group
        public static final int ORDER_FIELD_NONE = -1;
        public static final int ORDER_FIELD_TITLE = 0;
        public static final int ORDER_FIELD_ARTIST = 1;
        public static final int ORDER_FIELD_NB_PAGES = 2;
        public static final int ORDER_FIELD_DOWNLOAD_PROCESSING_DATE = 3;
        public static final int ORDER_FIELD_UPLOAD_DATE = 4;
        public static final int ORDER_FIELD_READ_DATE = 5;
        public static final int ORDER_FIELD_READS = 6;
        public static final int ORDER_FIELD_SIZE = 7;
        public static final int ORDER_FIELD_CHILDREN = 8; // Groups only
        public static final int ORDER_FIELD_READ_PROGRESS = 9;
        public static final int ORDER_FIELD_DOWNLOAD_COMPLETION_DATE = 10;
        public static final int ORDER_FIELD_CUSTOM = 98;
        public static final int ORDER_FIELD_RANDOM = 99;

        public static final int SEARCH_ORDER_ATTRIBUTES_ALPHABETIC = 0;
        static final int SEARCH_ORDER_ATTRIBUTES_COUNT = 1;

        public static final int LIBRARY_DISPLAY_LIST = 0;
        public static final int LIBRARY_DISPLAY_GRID = 1;

        static final int FOLDER_NAMING_CONTENT_ID = 0;
        static final int FOLDER_NAMING_CONTENT_TITLE_ID = 1;
        static final int FOLDER_NAMING_CONTENT_AUTH_TITLE_ID = 2;
        static final int FOLDER_NAMING_CONTENT_TITLE_AUTH_ID = 3;

        public static final int QUEUE_NEW_DOWNLOADS_POSITION_TOP = 0;
        public static final int QUEUE_NEW_DOWNLOADS_POSITION_BOTTOM = 1;
        public static final int QUEUE_NEW_DOWNLOADS_POSITION_ASK = 2;

        public static final int DL_TAG_BLOCKING_BEHAVIOUR_DONT_QUEUE = 0;
        public static final int DL_TAG_BLOCKING_BEHAVIOUR_QUEUE_ERROR = 1;

        public static final int DL_ACTION_DL_PAGES = 0;
        public static final int DL_ACTION_STREAM = 1;

        static final int TRUNCATE_FOLDER_100 = 100;

        public static final int VIEWER_DISPLAY_FIT = 0;
        public static final int VIEWER_DISPLAY_FILL = 1;
        public static final int VIEWER_DISPLAY_STRETCH = 2;

        public static final int VIEWER_RENDERING_SHARP = 0;
        public static final int VIEWER_RENDERING_SMOOTH = 1;

        public static final int VIEWER_BROWSE_NONE = -1;
        public static final int VIEWER_BROWSE_LTR = 0;
        public static final int VIEWER_BROWSE_RTL = 1;
        public static final int VIEWER_BROWSE_TTB = 2;

        public static final int VIEWER_DIRECTION_LTR = 0;
        public static final int VIEWER_DIRECTION_RTL = 1;

        public static final int VIEWER_ORIENTATION_HORIZONTAL = 0;
        public static final int VIEWER_ORIENTATION_VERTICAL = 1;

        public static final int VIEWER_SEPARATING_BARS_OFF = 0;
        public static final int VIEWER_SEPARATING_BARS_SMALL = 1;
        public static final int VIEWER_SEPARATING_BARS_MEDIUM = 2;
        public static final int VIEWER_SEPARATING_BARS_LARGE = 3;

        public static final int VIEWER_READ_THRESHOLD_1 = 0;
        public static final int VIEWER_READ_THRESHOLD_2 = 1;
        public static final int VIEWER_READ_THRESHOLD_5 = 2;
        public static final int VIEWER_READ_THRESHOLD_ALL = 3;

        public static final int VIEWER_SLIDESHOW_DELAY_2 = 0;
        public static final int VIEWER_SLIDESHOW_DELAY_4 = 1;
        public static final int VIEWER_SLIDESHOW_DELAY_8 = 2;
        public static final int VIEWER_SLIDESHOW_DELAY_16 = 3;
        public static final int VIEWER_SLIDESHOW_DELAY_1 = 4;
        public static final int VIEWER_SLIDESHOW_DELAY_05 = 5;

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

        public static final int VIEWER_DELETE_ASK_AGAIN = 0;
        public static final int VIEWER_DELETE_ASK_BOOK = 1;
        public static final int VIEWER_DELETE_ASK_SESSION = 2;

        public static final int VIEWER_DELETE_TARGET_BOOK = 0;
        public static final int VIEWER_DELETE_TARGET_PAGE = 1;

        public static final int VIEWER_CAP_TAP_ZOOM_NONE = 0;
        public static final int VIEWER_CAP_TAP_ZOOM_2X = 2;
        public static final int VIEWER_CAP_TAP_ZOOM_4X = 4;
        public static final int VIEWER_CAP_TAP_ZOOM_6X = 6;

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
