package me.devsaki.hentoid.database.enums;

import me.devsaki.hentoid.R;

/**
 * Created by neko on 20/06/2015.
 */
public enum Site {

    FAKKU(0, "Fakku", "https://www.fakku.net", R.drawable.ic_fakku, "/Downloads"),
    PURURIN(1, "Pururin", "http://pururin.com", R.drawable.ic_pururin, "/Pururin"),
    HITOMI(2, "Hitomi", "http://hitomi.la", R.drawable.ic_hitomi, "/Hitomi");

    private int code;
    private String description;
    private String url;
    private String folder;
    private int ico;

    Site(int code, String description, String url, int ico, String folder) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.ico = ico;
        this.folder = folder;
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

    public static Site searchByCode(int code) {

        for (Site s : Site.values()) {
            if (s.getCode() == code)
                return s;
        }

        return null;
    }
}
