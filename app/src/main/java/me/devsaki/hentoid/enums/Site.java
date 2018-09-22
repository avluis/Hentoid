package me.devsaki.hentoid.enums;

import javax.annotation.Nullable;

import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Created by neko on 20/06/2015.
 * Site enumerator
 */
public enum Site {

    // TODO : https://hentai2read.com/
    FAKKU(0, "Fakku", "https://www.fakku.net", "fakku", R.drawable.ic_menu_fakku, false),
    PURURIN(1, "Pururin", "https://pururin.io", "pururin", R.drawable.ic_menu_pururin, false),
    HITOMI(2, "hitomi", "https://hitomi.la", "hitomi", R.drawable.ic_menu_hitomi, false),
    NHENTAI(3, "nhentai", "https://nhentai.net", "nhentai", R.drawable.ic_menu_nhentai, false),
    TSUMINO(4, "tsumino", "http://www.tsumino.com", "tsumino", R.drawable.ic_menu_tsumino, false),
    HENTAICAFE(5, "hentaicafe", "https://hentai.cafe", "hentai.cafe", R.drawable.ic_menu_hentaicafe, false),
    ASMHENTAI(6, "asmhentai", "http://asmhentai.com", "/asmhentai", R.drawable.ic_menu_asmhentai, false),
    ASMHENTAI_COMICS(7, "asmhentai", "http://comics.asmhentai.com", "comics.asmhentai", R.drawable.ic_menu_asmcomics, false),
    EHENTAI(8, "e-hentai", "https://e-hentai.org", "e-hentai", R.drawable.ic_menu_ehentai, false),
    PANDA(99, "panda", "https://www.mangapanda.com", "mangapanda", R.drawable.ic_menu_panda, false); // Safe-for-work/wife/gf option


    private final int code;
    private final String description;
    private final String uniqueKeyword;
    private final String url;
    private final int ico;
    private final boolean noParallelDownloads;

    Site(int code, String description, String url, String uniqueKeyword, int ico, boolean noParallelDownloads) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.uniqueKeyword = uniqueKeyword;
        this.ico = ico;
        this.noParallelDownloads = noParallelDownloads;
    }

    @Nullable
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

    @Nullable
    public static Site searchByUrl(String url) {
        if (null == url || 0 == url.length()) {
            Timber.w("Invalid url");
            return null;
        }
        for (Site s : Site.values()) {
            if (url.contains(s.getUniqueKeyword()))
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

    public String getUniqueKeyword() {
        return uniqueKeyword;
    }

    public String getUrl() {
        return url;
    }

    public int getIco() {
        return ico;
    }

    public boolean isNoParallelDownloads() {
        return noParallelDownloads;
    }

    public String getFolder() {
        if (this == FAKKU) {
            return "/Downloads/";
        } else {
            return '/' + description + '/';
        }
    }
}
