package me.devsaki.hentoid.enums;

import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Created by neko on 20/06/2015.
 * Site enumerator
 */
public enum Site {

    FAKKU(0, "Fakku", "https://www.fakku.net", R.drawable.ic_favicon_fakku),
    PURURIN(1, "Pururin", "http://pururin.io", R.drawable.ic_menu_pururin),
    HITOMI(2, "hitomi", "https://hitomi.la", R.drawable.ic_menu_hitomi),
    NHENTAI(3, "nhentai", "https://nhentai.net", R.drawable.ic_menu_nhentai),
    TSUMINO(4, "tsumino", "http://www.tsumino.com", R.drawable.ic_menu_tsumino),
    HENTAICAFE(5, "hentaicafe", "https://hentai.cafe", R.drawable.ic_menu_hentaicafe),
    ASMHENTAI(6, "asmhentai", "http://asmhentai.com", R.drawable.ic_menu_asmhentai);


    private final int code;
    private final String description;
    private final String url;
    private final int ico;

    Site(int code, String description, String url, int ico) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.ico = ico;
    }

    public static Site searchByCode(int code) {
        if (code == -1) {
            Timber.w("Invalid site code!");
        }
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
        if (this == FAKKU) {
            return "/Downloads/";
        } else {
            return '/' + description + '/';
        }
    }
}
