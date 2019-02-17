package me.devsaki.hentoid.enums;

import javax.annotation.Nullable;

import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Created by neko on 20/06/2015.
 * Site enumerator
 * TODO: deprecate {@link #allowParallelDownloads} on 1/10/2020 if not needed by that time
 */
public enum Site {

    // TODO : https://hentai2read.com/,
    FAKKU(0, "Fakku", "https://www.fakku.net", "fakku", R.drawable.ic_menu_fakku, true, true), // Legacy support for old fakku archives
    PURURIN(1, "Pururin", "https://pururin.io", "pururin", R.drawable.ic_menu_pururin, true, true),
    HITOMI(2, "hitomi", "https://hitomi.la", "hitomi", R.drawable.ic_menu_hitomi, true, true),
    NHENTAI(3, "nhentai", "https://nhentai.net", "nhentai", R.drawable.ic_menu_nhentai, true, true),
    TSUMINO(4, "tsumino", "https://www.tsumino.com", "tsumino", R.drawable.ic_menu_tsumino, true, true),
    HENTAICAFE(5, "hentaicafe", "https://hentai.cafe", "hentai.cafe", R.drawable.ic_menu_hentaicafe, true, true),
    ASMHENTAI(6, "asmhentai", "https://asmhentai.com", "/asmhentai", R.drawable.ic_menu_asmhentai, true, true),
    ASMHENTAI_COMICS(7, "asmhentai comics", "https://comics.asmhentai.com", "comics.asmhentai", R.drawable.ic_menu_asmcomics, true, true),
    EHENTAI(8, "e-hentai", "https://e-hentai.org", "e-hentai", R.drawable.ic_menu_ehentai, true, true),
    FAKKU2(9, "Fakku", "https://www.fakku.net", "fakku2", R.drawable.ic_menu_fakku, true, false),
    NONE(98, "none", "", "none", R.drawable.ic_menu_about, true, true),
    PANDA(99, "panda", "https://www.mangapanda.com", "mangapanda", R.drawable.ic_menu_panda, true, true); // Safe-for-work/wife/gf option


    private final int code;
    private final String description;
    private final String uniqueKeyword;
    private final String url;
    private final int ico;
    private final boolean allowParallelDownloads;
    private final boolean canKnowHentoidAgent;

    Site(int code, String description, String url, String uniqueKeyword, int ico, boolean allowParallelDownloads, boolean canKnowHentoidAgent) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.uniqueKeyword = uniqueKeyword;
        this.ico = ico;
        this.allowParallelDownloads = allowParallelDownloads;
        this.canKnowHentoidAgent = canKnowHentoidAgent;
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

    public boolean isAllowParallelDownloads() {
        return allowParallelDownloads;
    }

    public boolean canKnowHentoidAgent() {
        return canKnowHentoidAgent;
    }

    public String getFolder() {
        if (this == FAKKU) {
            return "/Downloads/";
        } else {
            return '/' + description + '/';
        }
    }
}
