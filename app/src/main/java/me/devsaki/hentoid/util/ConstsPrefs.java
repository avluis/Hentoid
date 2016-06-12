package me.devsaki.hentoid.util;

/**
 * Created by DevSaki on 19/05/2015.
 * Common app preference constants.
 */
public abstract class ConstsPrefs {

    public static final String PREFS_VERSION_KEY = "prefs_version";
    public static final int PREFS_VERSION = 2;

    public static final String PREF_QUANTITY_PER_PAGE_LISTS = "pref_quantity_per_page_lists";
    public static final int PREF_QUANTITY_PER_PAGE_DEFAULT = 20;
    public static final String PREF_READ_CONTENT_LISTS = "pref_read_content_lists";
    public static final int PREF_READ_CONTENT_DEFAULT = 0;
    public static final int PREF_READ_CONTENT_ASK = 0;
    public static final int PREF_READ_CONTENT_PERFECT_VIEWER = 1;
    public static final String PREF_ORDER_CONTENT_LISTS = "pref_order_content_lists";
    public static final int PREF_ORDER_CONTENT_ALPHABETIC = 0;
    public static final int PREF_ORDER_CONTENT_BY_DATE = 1;
    public static final boolean PREF_WEBVIEW_OVERRIDE_OVERVIEW_DEFAULT = false;
    public static final String PREF_WEBVIEW_OVERRIDE_OVERVIEW_LISTS = "prefs_webview_override_overview_lists";
    public static final int PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT = 20;
    public static final String PREF_WEBVIEW_INITIAL_ZOOM_LISTS = "pref_webview_initial_zoom_lists";
    public static final boolean PREF_CHECK_UPDATES_DEFAULT = true;
    public static final String PREF_CHECK_UPDATE_MANUAL = "pref_check_updates_manual";
    public static final String PREF_CHECK_UPDATES_LISTS = "pref_check_updates_lists";
    public static final String PREF_APP_LOCK = "pref_app_lock";
    public static final String PREF_ADD_NO_MEDIA_FILE = "pref_add_no_media_file";
    public static final String PREF_FIRST_RUN = "pref_first_run";
    public static final boolean PREF_FIRST_RUN_DEFAULT = true;
    public static final String PREF_WELCOME_DONE = "pref_welcome_done";
    public static final boolean PREF_ENDLESS_SCROLL_DEFAULT = true;
    public static final String PREF_ENDLESS_SCROLL = "pref_endless_scroll";
}
