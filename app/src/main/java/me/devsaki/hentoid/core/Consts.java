package me.devsaki.hentoid.core;

/**
 * Common app constants.
 */
public abstract class Consts {

    private Consts() {
        throw new IllegalStateException("Utility class");
    }

    public static final String DEFAULT_PRIMARY_FOLDER_OLD = "Hentoid";
    public static final String DEFAULT_PRIMARY_FOLDER = ".Hentoid";

    public static final String JSON_FILE_NAME_OLD = "data.json";
    public static final String JSON_FILE_NAME = "content.json";
    public static final String JSON_FILE_NAME_V2 = "contentV2.json";

    public static final String QUEUE_JSON_FILE_NAME = "queue.json";
    public static final String BOOKMARKS_JSON_FILE_NAME = "bookmarks.json";
    public static final String GROUPS_JSON_FILE_NAME = "groups.json";

    public static final String THUMB_FILE_NAME = "thumb";
    public static final String EXT_THUMB_FILE_PREFIX = "ext-thumb-";
    public static final String PICTURE_CACHE_FOLDER = "pictures";
    public static final String UGOIRA_CACHE_FOLDER = "ugoira";

    public static final String SEED_CONTENT = "content";
    public static final String SEED_PAGES = "pages";

    public static final String WORK_CLOSEABLE = "closeable";

    public static final String CLOUDFLARE_COOKIE = "cf_clearance";


    public static final String URL_GITHUB = "https://github.com/AVnetWS/Hentoid";
    public static final String URL_GITHUB_WIKI = "https://github.com/AVnetWS/Hentoid/wiki";
    public static final String URL_DISCORD = "https://discord.gg/QEZ3qk9";
    public static final String URL_REDDIT = "https://www.reddit.com/r/Hentoid/";
}
