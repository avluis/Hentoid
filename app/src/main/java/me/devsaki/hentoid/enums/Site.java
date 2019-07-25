package me.devsaki.hentoid.enums;

import java.io.File;

import io.objectbox.converter.PropertyConverter;
import me.devsaki.hentoid.R;
import timber.log.Timber;

/**
 * Created by neko on 20/06/2015.
 * Site enumerator
 * TODO: deprecate {@link #allowParallelDownloads} on 1/10/2020 if not needed by that time
 */
public enum Site {

    // TODO : https://hentai2read.com/,
    FAKKU(0, "Fakku", "https://www.fakku.net", "fakku", R.drawable.ic_menu_fakku, true, true, false), // Legacy support for old fakku archives
    PURURIN(1, "Pururin", "https://pururin.io", "pururin", R.drawable.ic_menu_pururin, true, true, false),
    HITOMI(2, "hitomi", "https://hitomi.la", "hitomi", R.drawable.ic_menu_hitomi, true, false, false),
    NHENTAI(3, "nhentai", "https://nhentai.net", "nhentai", R.drawable.ic_menu_nhentai, true, true, false),
    TSUMINO(4, "tsumino", "https://www.tsumino.com", "tsumino", R.drawable.ic_menu_tsumino, true, true, false),
    HENTAICAFE(5, "hentaicafe", "https://hentai.cafe", "hentai.cafe", R.drawable.ic_menu_hentaicafe, true, true, false),
    ASMHENTAI(6, "asmhentai", "https://asmhentai.com", "/asmhentai", R.drawable.ic_menu_asmhentai, true, true, false),
    ASMHENTAI_COMICS(7, "asmhentai comics", "https://comics.asmhentai.com", "comics.asmhentai", R.drawable.ic_menu_asmcomics, true, true, false),
    EHENTAI(8, "e-hentai", "https://e-hentai.org", "e-hentai", R.drawable.ic_menu_ehentai, true, true, false),
    FAKKU2(9, "Fakku", "https://www.fakku.net", "fakku2", R.drawable.ic_menu_fakku, true, false, true),
    NEXUS(10, "Hentai Nexus", "https://hentainexus.com", "nexus", R.drawable.ic_menu_nexus, true, false, false),
    MUSES(11, "8Muses", "https://www.8muses.com", "8muses", R.drawable.ic_menu_8muses, true, false, false),
    NONE(98, "none", "", "none", R.drawable.ic_menu_about, true, true, false), // Fallback site
    PANDA(99, "panda", "https://www.mangapanda.com", "mangapanda", R.drawable.ic_menu_panda, true, true, false); // Safe-for-work/wife/gf option


    private final int code;
    private final String description;
    private final String uniqueKeyword;
    private final String url;
    private final int ico;
    private final boolean allowParallelDownloads;
    private final boolean canKnowHentoidAgent;
    private final boolean hasImageProcessing;

    Site(int code,
         String description,
         String url,
         String uniqueKeyword,
         int ico,
         boolean allowParallelDownloads,
         boolean canKnowHentoidAgent,
         boolean hasImageProcessing) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.uniqueKeyword = uniqueKeyword;
        this.ico = ico;
        this.allowParallelDownloads = allowParallelDownloads;
        this.canKnowHentoidAgent = canKnowHentoidAgent;
        this.hasImageProcessing = hasImageProcessing;
    }

    public static Site searchByCode(long code) {
        if (code == -1) {
            Timber.w("Invalid site code!");
        }
        for (Site s : Site.values()) {
            if (s.getCode() == code)
                return s;
        }
        return Site.NONE;
    }

    public static Site searchByUrl(String url) {
        if (null == url || url.isEmpty()) {
            Timber.w("Invalid url");
            return null;
        }
        for (Site s : Site.values()) {
            if (url.contains(s.getUniqueKeyword()))
                return s;
        }
        return Site.NONE;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    private String getUniqueKeyword() {
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

    public boolean hasImageProcessing() {
        return hasImageProcessing;
    }

    public String getFolder() {
        if (this == FAKKU) {
            return File.separator + "Downloads" + File.separator;
        } else {
            return File.separator + description + File.separator;
        }
    }

    public static class SiteConverter implements PropertyConverter<Site, Long> {
        @Override
        public Site convertToEntityProperty(Long databaseValue) {
            if (databaseValue == null) {
                return Site.NONE;
            }
            for (Site site : Site.values()) {
                if (site.getCode() == databaseValue) {
                    return site;
                }
            }
            return Site.NONE;
        }

        @Override
        public Long convertToDatabaseValue(Site entityProperty) {
            return entityProperty == null ? null : (long) entityProperty.getCode();
        }
    }
}
