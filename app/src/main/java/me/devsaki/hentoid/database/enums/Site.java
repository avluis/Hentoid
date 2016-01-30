package me.devsaki.hentoid.database.enums;

import android.util.Log;

import me.devsaki.hentoid.R;

/**
 * Created by neko on 20/06/2015.
 */
public enum Site {

    FAKKU(0, "Fakku", "https://www.fakku.net", R.drawable.ic_favicon_fakku, "/Downloads"),
    PURURIN(1, "Pururin", "http://pururin.com", R.drawable.ic_favicon_pururin, "/Pururin"),
    HITOMI(2, "Hitomi", "https://hitomi.la", R.drawable.ic_favicon_hitomi, "/Hitomi"),
    NHENTAI(3, "nhentai", "http://nhentai.net", R.drawable.ic_favicon_nhentai, "/nhentai"),
    TSUMINO(4, "Tsumino", "http://www.tsumino.com", R.drawable.ic_favicon_tsumino, "/Tsumino");

    private static final String TAG = Site.class.getName();
    private final int code;
    private final String description;
    private final String url;
    private final String folder;
    private final int ico;

    Site(int code, String description, String url, int ico, String folder) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.ico = ico;
        this.folder = folder;
    }

    public static Site searchByCode(int code) {

        if (code == -1)
            Log.e(TAG, "Invalid site code");

        for (Site s : Site.values()) {
            if (s.getCode() == code)
                return s;
        }

        return null;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public int getIco() {
        return ico;
    }

    public String getFolder() {
        return folder;
    }
}