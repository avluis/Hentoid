package me.devsaki.hentoid.util

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.text.TextUtils
import androidx.preference.PreferenceManager
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.util.Settings.Value.SEARCH_ORDER_ATTRIBUTES_COUNT
import kotlin.reflect.KProperty

object Settings {
    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * FIELDS
     */
    // IMPORT
    val isImportQueueEmptyBooks: Boolean by BoolSetting(Key.IMPORT_QUEUE_EMPTY, false)

    // LIBRARY
    var libraryDisplay: Int by IntSettingStr(Key.LIBRARY_DISPLAY, Value.LIBRARY_DISPLAY_DEFAULT)
    var libraryDisplayGridFav: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_FAV, true)
    var libraryDisplayGridRating: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_RATING, true)
    var libraryDisplayGridSource: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_SOURCE, true)
    var libraryDisplayGridStorageInfo: Boolean by BoolSetting(
        Key.LIBRARY_DISPLAY_GRID_STORAGE,
        true
    )
    var libraryDisplayGridTitle: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_TITLE, true)
    var libraryDisplayGridLanguage: Boolean by BoolSetting(Key.LIBRARY_DISPLAY_GRID_LANG, true)
    var libraryGridCardWidthDP: Int by IntSettingStr(Key.LIBRARY_GRID_CARD_WIDTH, 150)
    var activeSites: List<Site> by ListSiteSetting("active_sites", Value.ACTIVE_SITES)
    var contentSortField: Int by IntSetting(
        "pref_order_content_field",
        Default.ORDER_CONTENT_FIELD
    )
    var isContentSortDesc: Boolean by BoolSetting("pref_order_content_desc", false)
    var groupSortField: Int by IntSetting("pref_order_group_field", Default.ORDER_GROUP_FIELD)
    var isGroupSortDesc: Boolean by BoolSetting("pref_order_group_desc", false)
    var contentPageQuantity: Int by IntSettingStr("pref_quantity_per_page_lists", 20)
    var appLockPin: String by StringSetting(Key.APP_LOCK, "")
    val endlessScroll: Boolean by BoolSetting(Key.ENDLESS_SCROLL, true)
    var topFabEnabled: Boolean by BoolSetting(Key.TOP_FAB, true)

    // ADV SEARCH
    val searchAttributesSortOrder: Int by IntSettingStr(
        "pref_order_attribute_lists",
        SEARCH_ORDER_ATTRIBUTES_COUNT
    )
    val searchAttributesCount: Boolean by BoolSetting("pref_order_attribute_count", true)

    // DOWNLOADER

    // LOCK
    var lockType: Int by IntSettingStr(Key.LOCK_TYPE, 0)

    // MASS OPERATIONS
    var massOperation: Int by IntSettingStr("MASS_OPERATION", 0)
    var massOperationScope: Int by IntSettingStr("MASS_SCOPE", 0)

    // TRANSFORM
    var isResizeEnabled: Boolean by BoolSetting("TRANSFORM_RESIZE_ENABLED", false)
    var resizeMethod: Int by IntSettingStr("TRANSFORM_RESIZE_METHOD", 0)
    var resizeMethod1Ratio: Int by IntSettingStr("TRANSFORM_RESIZE_1_RATIO", 120)
    var resizeMethod2Height: Int by IntSettingStr("TRANSFORM_RESIZE_2_HEIGHT", 0)
    var resizeMethod2Width: Int by IntSettingStr("TRANSFORM_RESIZE_2_WIDTH", 0)
    var resizeMethod3Ratio: Int by IntSettingStr("TRANSFORM_RESIZE_3_RATIO", 80)
    var transcodeMethod: Int by IntSettingStr("TRANSFORM_TRANSCODE_METHOD", 0)
    var transcodeEncoderAll: Int by IntSettingStr(
        "TRANSFORM_TRANSCODE_ENC_ALL",
        PictureEncoder.PNG.value
    )
    var transcodeEncoderLossless: Int by IntSettingStr(
        "TRANSFORM_TRANSCODE_ENC_LOSSLESS",
        PictureEncoder.PNG.value
    )
    var transcodeEncoderLossy: Int by IntSettingStr(
        "TRANSFORM_TRANSCODE_ENC_LOSSY",
        PictureEncoder.JPEG.value
    )
    var transcodeQuality: Int by IntSettingStr("TRANSFORM_TRANSCODE_QUALITY", 90)

    // ARCHIVES
    var archiveTargetFolder: String by StringSetting(
        "ARCHIVE_TARGET_FOLDER",
        Value.TARGET_FOLDER_DOWNLOADS
    )
    var latestArchiveTargetFolderUri: String by StringSetting("ARCHIVE_TARGET_FOLDER_LATEST", "")
    var archiveTargetFormat: Int by IntSettingStr("ARCHIVE_TARGET_FORMAT", 0)
    var pdfBackgroundColor: Int by IntSettingStr("ARCHIVE_PDF_BGCOLOR", 0)
    var isArchiveOverwrite: Boolean by BoolSetting("ARCHIVE_OVERWRITE", true)
    var isArchiveDeleteOnSuccess: Boolean by BoolSetting("ARCHIVE_DELETE_ON_SUCCESS", false)

    // BROWSER
    var isBrowserAugmented: Boolean by BoolSetting(Key.WEB_AUGMENTED_BROWSER, true)
    var isAdBlockerOn: Boolean by BoolSetting(Key.WEB_ADBLOCKER, true)
    var isBrowserForceLightMode: Boolean by BoolSetting(Key.WEB_FORCE_LIGHTMODE, false)
    var isBrowserLanguageFilter: Boolean by BoolSetting("pref_browser_language_filter", false)
    var browserLanguageFilterValue: String by StringSetting("pref_language_filter_value", "english")
    var blockedTags: List<String> by ListStringSetting(Key.DL_BLOCKED_TAGS)

    // READER
    var isReaderResumeLastLeft: Boolean by BoolSetting("pref_viewer_resume_last_left", true)
    var isReaderKeepScreenOn: Boolean by BoolSetting(Key.VIEWER_KEEP_SCREEN_ON, true)
    var isReaderDisplayAroundNotch: Boolean by BoolSetting(Key.VIEWER_DISPLAY_AROUND_NOTCH, true)

    var readerColorDepth: Int by IntSettingStr(Key.READER_COLOR_DEPTH, 0)
    private var reader2PagesMode: Boolean by BoolSetting(Key.READER_TWOPAGES, false)
    fun getContent2PagesMode(bookPrefs: Map<String, String>): Boolean {
        return bookPrefs.getOrDefault(Key.READER_TWOPAGES, reader2PagesMode.toString()).toBoolean()
    }

    fun getContentDisplayMode(bookPrefs: Map<String, String>): Int {
        if (Value.VIEWER_ORIENTATION_HORIZONTAL == getContentOrientation(bookPrefs)) {
            if (bookPrefs.containsKey(Key.VIEWER_IMAGE_DISPLAY)) {
                val value = bookPrefs[Key.VIEWER_IMAGE_DISPLAY]
                if (value != null) return value.toInt()
            }
            return readerDisplayMode
        } else return Value.VIEWER_DISPLAY_FIT // The only relevant mode for vertical (aka. webtoon) display
    }

    val readerDisplayMode: Int by IntSettingStr(Key.VIEWER_IMAGE_DISPLAY, Value.VIEWER_DISPLAY_FIT)

    fun getContentBrowseMode(bookPrefs: Map<String, String>?): Int {
        bookPrefs?.let {
            if (it.containsKey(Key.VIEWER_BROWSE_MODE)) {
                val value = it[Key.VIEWER_BROWSE_MODE]
                if (value != null) return value.toInt()
            }
        }
        return readerBrowseMode
    }

    var readerBrowseMode: Int by IntSettingStr(Key.VIEWER_BROWSE_MODE, Value.VIEWER_BROWSE_NONE)

    fun getContentDirection(bookPrefs: Map<String, String>?): Int {
        return if ((getContentBrowseMode(bookPrefs) == Value.VIEWER_BROWSE_RTL)) Value.VIEWER_DIRECTION_RTL else Value.VIEWER_DIRECTION_LTR
    }

    private fun getContentOrientation(bookPrefs: Map<String, String>): Int {
        return if ((getContentBrowseMode(bookPrefs) == Value.VIEWER_BROWSE_TTB)) Value.VIEWER_ORIENTATION_VERTICAL else Value.VIEWER_ORIENTATION_HORIZONTAL
    }

    fun isContentSmoothRendering(bookPrefs: Map<String, String>): Boolean {
        if (bookPrefs.containsKey(Key.VIEWER_RENDERING)) {
            val value = bookPrefs[Key.VIEWER_RENDERING]
            if (value != null) return isSmoothRendering(value.toInt())
        }
        return isReaderSmoothRendering()
    }

    fun isReaderSmoothRendering(): Boolean {
        return isSmoothRendering(readerRenderingMode)
    }

    private fun isSmoothRendering(mode: Int): Boolean {
        return (mode == Value.VIEWER_RENDERING_SMOOTH)
    }

    private val readerRenderingMode: Int by IntSettingStr(
        Key.VIEWER_RENDERING,
        Value.VIEWER_RENDERING_SHARP
    )
    val isReaderDisplayPageNum: Boolean by BoolSetting(Key.VIEWER_DISPLAY_PAGENUM, false)
    val isReaderTapTransitions: Boolean by BoolSetting("pref_viewer_tap_transitions", true)
    val isReaderZoomTransitions: Boolean by BoolSetting(Key.VIEWER_ZOOM_TRANSITIONS, true)
    val isReaderSwipeToFling: Boolean by BoolSetting(Key.VIEWER_SWIPE_TO_FLING, false)
    val isReaderInvertVolumeRocker: Boolean by BoolSetting(
        "pref_viewer_invert_volume_rocker",
        false
    )
    val isReaderTapToTurn: Boolean by BoolSetting("pref_viewer_page_turn_tap", true)
    val isReaderTapToTurn2x: Boolean by BoolSetting("pref_viewer_page_turn_tap_2x", false)
    val isReaderVolumeToTurn: Boolean by BoolSetting("pref_viewer_page_turn_volume", true)
    val isReaderSwipeToTurn: Boolean by BoolSetting(Key.VIEWER_PAGE_TURN_SWIPE, true)
    val isReaderKeyboardToTurn: Boolean by BoolSetting("pref_viewer_page_turn_keyboard", true)
    val isReaderVolumeToSwitchBooks: Boolean by BoolSetting("pref_viewer_book_switch_volume", false)
    val isReaderOpenBookInGalleryMode: Boolean by BoolSetting("pref_viewer_open_gallery", false)
    val isReaderChapteredNavigation: Boolean by BoolSetting("viewer_chaptered_navigation", false)
    val isReaderContinuous: Boolean by BoolSetting(Key.VIEWER_CONTINUOUS, false)
    val readerPageReadThreshold: Int by IntSettingStr(
        "pref_viewer_read_threshold",
        Value.VIEWER_READ_THRESHOLD_1
    )
    val readerRatioCompletedThreshold: Int by IntSettingStr(
        "pref_viewer_ratio_completed_threshold",
        Value.VIEWER_COMPLETED_RATIO_THRESHOLD_NONE
    )
    var readerSlideshowDelay: Int by IntSettingStr(
        "pref_viewer_slideshow_delay",
        Value.VIEWER_SLIDESHOW_DELAY_2
    )
    var readerSlideshowDelayVertical: Int by IntSettingStr(
        "pref_viewer_slideshow_delay_vertical",
        Value.VIEWER_SLIDESHOW_DELAY_2
    )
    val readerSeparatingBars: Int by IntSettingStr(
        Key.VIEWER_SEPARATING_BARS,
        Value.VIEWER_SEPARATING_BARS_OFF
    )
    val isReaderHoldToZoom: Boolean by BoolSetting(Key.VIEWER_HOLD_TO_ZOOM, false)
    val readerCapTapZoom: Int by IntSettingStr(
        "pref_viewer_cap_tap_zoom",
        Value.VIEWER_CAP_TAP_ZOOM_NONE
    )
    val isReaderMaintainHorizontalZoom: Boolean by BoolSetting(
        "pref_viewer_maintain_horizontal_zoom",
        false
    )
    val isReaderAutoRotate: Boolean by BoolSetting(Key.VIEWER_AUTO_ROTATE, false)
    var readerCurrentContent: Long by LongSetting("viewer_current_content", -1)
    var readerCurrentPageNum: Int by IntSettingStr("viewer_current_pagenum", -1)
    var readerGalleryColumns: Int by IntSettingStr("viewer_gallery_columns", 4)
    var readerDeleteAskMode: Int by IntSettingStr(
        Key.VIEWER_DELETE_ASK_MODE,
        Value.VIEWER_DELETE_ASK_AGAIN
    )
    var readerDeleteTarget: Int by IntSettingStr(
        Key.VIEWER_DELETE_TARGET,
        Value.VIEWER_DELETE_TARGET_PAGE
    )
    var readerSlideshowLoop: Int by IntSettingStr(
        "viewer_slideshow_loop",
        Value.VIEWER_SLIDESHOW_LOOP_NONE
    )
    var readerTargetFolder: String by StringSetting(
        "READER_TARGET_FOLDER",
        Value.TARGET_FOLDER_DOWNLOADS
    )
    var latestReaderTargetFolderUri: String by StringSetting("READER_TARGET_FOLDER_LATEST", "")

    // METADATA & RULES EDITOR
    var ruleSortField: Int by IntSetting("pref_order_rule_field", Value.ORDER_FIELD_SOURCE_NAME)
    var isRuleSortDesc: Boolean by BoolSetting("pref_order_rule_desc", false)

    // ACHIEVEMENTS
    var achievements: ULong by ULongSetting(Key.ACHIEVEMENTS, 0UL)
    var nbAIRescale: Int by IntSettingStr(Key.ACHIEVEMENTS_NB_AI_RESCALE, 0)

    // STORAGE
    private var storageUri: String by StringSetting(Key.PRIMARY_STORAGE_URI, "")
    private var storageUri2: String by StringSetting(Key.PRIMARY_STORAGE_URI_2, "")
    var externalLibraryUri: String by StringSetting(Key.EXTERNAL_LIBRARY_URI, "")
    fun getStorageUri(location: StorageLocation): String {
        return when (location) {
            StorageLocation.PRIMARY_1 -> storageUri
            StorageLocation.PRIMARY_2 -> storageUri2
            StorageLocation.EXTERNAL -> externalLibraryUri
            else -> ""
        }
    }

    fun setStorageUri(location: StorageLocation, uri: String) {
        when (location) {
            StorageLocation.PRIMARY_1 -> storageUri = uri
            StorageLocation.PRIMARY_2 -> storageUri2 = uri
            StorageLocation.EXTERNAL -> externalLibraryUri = uri
            else -> {}
        }
    }

    val folderNameFormat: Int by IntSettingStr(
        "pref_folder_naming_content_lists",
        Value.FOLDER_NAMING_CONTENT_AUTH_TITLE_ID
    )
    var storageDownloadStrategy: Int by IntSettingStr(
        Key.PRIMARY_STORAGE_FILL_METHOD,
        Value.STORAGE_FILL_BALANCE_FREE
    )
    var storageSwitchThresholdPc: Int by IntSettingStr(Key.PRIMARY_STORAGE_SWITCH_THRESHOLD_PC, 90)
    var memoryAlertThreshold: Int by IntSettingStr("pref_memory_alert", 110)
    val isDeleteExternalLibrary: Boolean by BoolSetting(Key.EXTERNAL_LIBRARY_DELETE, false)
    val folderTruncationNbChars: Int by IntSettingStr("pref_folder_trunc_lists", 100)

    // APP-WIDE
    var isFirstRun: Boolean by BoolSetting(Key.FIRST_RUN, true)
    var isFirstRunProcessComplete: Boolean by BoolSetting(Key.WELCOME_DONE, false)
    var isRefreshJson1Complete: Boolean by BoolSetting(Key.REFRESH_JSON_1_DONE, false)
    val isAnalyticsEnabled: Boolean by BoolSetting(Key.ANALYTICS_PREFERENCE, true)
    val isAutomaticUpdateEnabled: Boolean by BoolSetting("pref_check_updates", true)
    var isBrowserMode: Boolean by BoolSetting(Key.BROWSER_MODE, false)
    val isForceEnglishLocale: Boolean by BoolSetting(Key.FORCE_ENGLISH, false)
    var isTextMenuOn: Boolean by BoolSetting(Key.TEXT_SELECT_MENU, false)
    val recentVisibility: Boolean by BoolSetting(Key.APP_PREVIEW, BuildConfig.DEBUG)
    val maxDbSizeKb: Long by LongSetting("db_max_size", 2L * 1024 * 1024) // 2GB


    // Public Helpers

    fun registerPrefsChangedListener(listener: OnSharedPreferenceChangeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPrefsChangedListener(listener: OnSharedPreferenceChangeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }


    // Delegates

    private class ULongSetting(val key: String, val default: ULong) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): ULong {
            return (sharedPreferences.getString(key, default.toString()) + "").toULong()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: ULong) {
            sharedPreferences.edit().putString(key, value.toString()).apply()
        }
    }

    private class LongSetting(val key: String, val default: Long) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Long {
            return (sharedPreferences.getString(key, default.toString()) + "").toLong()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            sharedPreferences.edit().putString(key, value.toString()).apply()
        }
    }

    private class IntSettingStr(val key: String, val default: Int) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            return (sharedPreferences.getString(key, default.toString()) + "").toInt()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            sharedPreferences.edit().putString(key, value.toString()).apply()
        }
    }

    private class IntSetting(val key: String, val default: Int) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            return (sharedPreferences.getInt(key, default))
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            sharedPreferences.edit().putInt(key, value).apply()
        }
    }

    private class BoolSetting(val key: String, val default: Boolean) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
            return sharedPreferences.getBoolean(key, default)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            sharedPreferences.edit().putBoolean(key, value).apply()
        }
    }

    private class StringSetting(val key: String, val default: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
            return sharedPreferences.getString(key, default) ?: ""
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            sharedPreferences.edit().putString(key, value).apply()
        }
    }

    private class ListStringSetting(val key: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): List<String> {
            return sharedPreferences.getString(key, "")
                ?.split(",")
                ?.map { it.trim() }
                ?.filterNot { it.isEmpty() }
                ?: emptyList()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: List<String>) {
            sharedPreferences.edit().putString(key, TextUtils.join(",", value)).apply()
        }
    }

    private class ListSiteSetting(val key: String, val default: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): List<Site> {
            return sharedPreferences.getString(key, default)
                ?.split(",")
                ?.distinct()
                ?.map { it.trim() }
                ?.filterNot { it.isEmpty() }
                ?.map { Site.searchByCode(it.toLong()) }
                ?: emptyList()
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: List<Site>) {
            val codes = value.map { it.code }.distinct()
            sharedPreferences.edit().putString(key, TextUtils.join(",", codes)).apply()
        }
    }


    // Consts
    object Key {
        const val FIRST_RUN = "pref_first_run"
        const val WELCOME_DONE = "pref_welcome_done"
        const val REFRESH_JSON_1_DONE = "refresh_json_1_done"
        const val ANALYTICS_PREFERENCE = "pref_analytics_preference"
        const val BROWSER_MODE = "browser_mode"
        const val FORCE_ENGLISH = "force_english"

        const val IMPORT_QUEUE_EMPTY = "pref_import_queue_empty"

        const val LIBRARY_DISPLAY = "pref_library_display"
        const val LOCK_TYPE = "LOCK_TYPE"
        const val LIBRARY_DISPLAY_GRID_FAV = "LIBRARY_DISPLAY_GRID_FAV"
        const val LIBRARY_DISPLAY_GRID_RATING = "LIBRARY_DISPLAY_GRID_RATING"
        const val LIBRARY_DISPLAY_GRID_SOURCE = "LIBRARY_DISPLAY_GRID_SOURCE"
        const val LIBRARY_DISPLAY_GRID_STORAGE = "LIBRARY_DISPLAY_GRID_STORAGE"
        const val LIBRARY_DISPLAY_GRID_TITLE = "LIBRARY_DISPLAY_GRID_TITLE"
        const val LIBRARY_DISPLAY_GRID_LANG = "LIBRARY_DISPLAY_GRID_LANG"
        const val LIBRARY_GRID_CARD_WIDTH = "grid_card_width"
        const val ACHIEVEMENTS = "achievements"
        const val ACHIEVEMENTS_NB_AI_RESCALE = "ach_nb_ai_rescale"
        const val WEB_AUGMENTED_BROWSER = "pref_browser_augmented"
        const val WEB_ADBLOCKER = "WEB_ADBLOCKER"
        const val WEB_FORCE_LIGHTMODE = "WEB_FORCE_LIGHTMODE"
        const val DL_BLOCKED_TAGS = "pref_dl_blocked_tags"
        const val TEXT_SELECT_MENU = "TEXT_SELECT_MENU"
        const val APP_LOCK = "pref_app_lock"
        const val ENDLESS_SCROLL = "pref_endless_scroll"
        const val TOP_FAB = "pref_top_fab"
        const val APP_PREVIEW = "pref_app_preview"
        const val PRIMARY_STORAGE_URI = "pref_sd_storage_uri"
        const val PRIMARY_STORAGE_URI_2 = "pref_sd_storage_uri_2"
        const val EXTERNAL_LIBRARY_URI = "pref_external_library_uri"
        const val PRIMARY_STORAGE_FILL_METHOD = "pref_storage_fill_method"
        const val PRIMARY_STORAGE_SWITCH_THRESHOLD_PC = "pref_storage_switch_threshold_pc"
        const val EXTERNAL_LIBRARY_DELETE = "pref_external_library_delete"

        const val READER_COLOR_DEPTH = "viewer_color_depth"
        const val READER_TWOPAGES = "reader_two_pages"
        const val VIEWER_KEEP_SCREEN_ON = "pref_viewer_keep_screen_on"
        const val VIEWER_DISPLAY_AROUND_NOTCH = "pref_viewer_display_notch"
        const val VIEWER_IMAGE_DISPLAY = "pref_viewer_image_display"
        const val VIEWER_BROWSE_MODE = "pref_viewer_browse_mode"
        const val VIEWER_RENDERING = "pref_viewer_rendering"
        const val VIEWER_DISPLAY_PAGENUM = "pref_viewer_display_pagenum"
        const val VIEWER_ZOOM_TRANSITIONS = "pref_viewer_zoom_transitions"
        const val VIEWER_SWIPE_TO_FLING = "pref_viewer_swipe_to_fling"
        const val VIEWER_PAGE_TURN_SWIPE = "pref_viewer_page_turn_swipe"
        const val VIEWER_CONTINUOUS = "pref_viewer_continuous"
        const val VIEWER_SEPARATING_BARS = "pref_viewer_separating_bars"
        const val VIEWER_HOLD_TO_ZOOM = "pref_viewer_zoom_holding"
        const val VIEWER_AUTO_ROTATE = "pref_viewer_auto_rotate"
        const val VIEWER_DELETE_ASK_MODE = "viewer_delete_ask"
        const val VIEWER_DELETE_TARGET = "viewer_delete_target"
    }

    object Default {
        const val ORDER_CONTENT_FIELD = Value.ORDER_FIELD_TITLE
        const val ORDER_GROUP_FIELD = Value.ORDER_FIELD_TITLE
    }

    object Value {
        private val DEFAULT_SITES = arrayOf(
            Site.NHENTAI,
            Site.HITOMI,
            Site.ASMHENTAI,
            Site.TSUMINO,
            Site.PURURIN,
            Site.EHENTAI,
            Site.FAKKU2,
            Site.NEXUS,
            Site.MUSES,
            Site.DOUJINS
        )
        val ACTIVE_SITES: String = TextUtils.join(",", DEFAULT_SITES.map { it.code })

        const val TARGET_FOLDER_DOWNLOADS = "downloads"

        const val LIBRARY_DISPLAY_LIST = 0
        const val LIBRARY_DISPLAY_GRID = 1
        const val LIBRARY_DISPLAY_DEFAULT = LIBRARY_DISPLAY_LIST

        const val SEARCH_ORDER_ATTRIBUTES_ALPHABETIC = 0
        const val SEARCH_ORDER_ATTRIBUTES_COUNT = 1

        // Sorting field codes for content and group
        const val ORDER_FIELD_NONE = -1
        const val ORDER_FIELD_TITLE = 0
        const val ORDER_FIELD_ARTIST = 1
        const val ORDER_FIELD_NB_PAGES = 2
        const val ORDER_FIELD_DOWNLOAD_PROCESSING_DATE = 3
        const val ORDER_FIELD_UPLOAD_DATE = 4
        const val ORDER_FIELD_READ_DATE = 5
        const val ORDER_FIELD_READS = 6
        const val ORDER_FIELD_SIZE = 7
        const val ORDER_FIELD_CHILDREN = 8 // Groups only
        const val ORDER_FIELD_READ_PROGRESS = 9
        const val ORDER_FIELD_DOWNLOAD_COMPLETION_DATE = 10
        const val ORDER_FIELD_SOURCE_NAME = 11 // Rules only
        const val ORDER_FIELD_TARGET_NAME = 12 // Rules only
        const val ORDER_FIELD_CUSTOM = 98
        const val ORDER_FIELD_RANDOM = 99

        const val STORAGE_FILL_BALANCE_FREE = 0
        const val STORAGE_FILL_FALLOVER = 1

        const val FOLDER_NAMING_CONTENT_ID = 0
        const val FOLDER_NAMING_CONTENT_TITLE_ID = 1
        const val FOLDER_NAMING_CONTENT_AUTH_TITLE_ID = 2
        const val FOLDER_NAMING_CONTENT_TITLE_AUTH_ID = 3

        const val VIEWER_DISPLAY_FIT = 0
        const val VIEWER_DISPLAY_FILL = 1
        const val VIEWER_DISPLAY_STRETCH = 2

        const val VIEWER_ORIENTATION_HORIZONTAL = 0
        const val VIEWER_ORIENTATION_VERTICAL = 1

        const val VIEWER_DIRECTION_LTR = 0
        const val VIEWER_DIRECTION_RTL = 1

        const val VIEWER_BROWSE_NONE = -1
        const val VIEWER_BROWSE_LTR = 0
        const val VIEWER_BROWSE_RTL = 1
        const val VIEWER_BROWSE_TTB = 2

        const val VIEWER_RENDERING_SHARP = 0
        const val VIEWER_RENDERING_SMOOTH = 1

        const val VIEWER_READ_THRESHOLD_NONE = -1
        const val VIEWER_READ_THRESHOLD_1 = 0
        const val VIEWER_READ_THRESHOLD_2 = 1
        const val VIEWER_READ_THRESHOLD_5 = 2
        const val VIEWER_READ_THRESHOLD_ALL = 3

        const val VIEWER_COMPLETED_RATIO_THRESHOLD_NONE = -1
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_10 = 0
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_25 = 1
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_33 = 2
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_50 = 3
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_75 = 4
        const val VIEWER_COMPLETED_RATIO_THRESHOLD_ALL = 99

        const val VIEWER_SLIDESHOW_DELAY_2 = 0
        const val VIEWER_SLIDESHOW_DELAY_4 = 1
        const val VIEWER_SLIDESHOW_DELAY_8 = 2
        const val VIEWER_SLIDESHOW_DELAY_16 = 3
        const val VIEWER_SLIDESHOW_DELAY_1 = 4
        const val VIEWER_SLIDESHOW_DELAY_05 = 5

        const val VIEWER_SLIDESHOW_LOOP_NONE = 0
        const val VIEWER_SLIDESHOW_LOOP_CHAPTER = 1
        const val VIEWER_SLIDESHOW_LOOP_BOOK = 2
        const val VIEWER_SLIDESHOW_FOLLOW_CONTINUOUS = 3

        const val VIEWER_SEPARATING_BARS_OFF = 0
        const val VIEWER_SEPARATING_BARS_SMALL = 1
        const val VIEWER_SEPARATING_BARS_MEDIUM = 2
        const val VIEWER_SEPARATING_BARS_LARGE = 3

        const val VIEWER_DELETE_ASK_AGAIN = 0
        const val VIEWER_DELETE_ASK_BOOK = 1
        const val VIEWER_DELETE_ASK_SESSION = 2

        const val VIEWER_DELETE_TARGET_BOOK = 0
        const val VIEWER_DELETE_TARGET_PAGE = 1

        const val VIEWER_CAP_TAP_ZOOM_NONE = 0
        const val VIEWER_CAP_TAP_ZOOM_2X = 2
        const val VIEWER_CAP_TAP_ZOOM_4X = 4
        const val VIEWER_CAP_TAP_ZOOM_6X = 6
    }
}