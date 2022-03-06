package me.devsaki.hentoid.enums;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.objectbox.converter.PropertyConverter;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.json.core.JsonSiteSettings;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Site enumerator
 */
public enum Site {

    // NOTE : to maintain compatiblity with saved JSON files and prefs, do _not_ edit either existing names or codes
    //FAKKU(0, "Fakku", "https://www.fakku.net", R.drawable.ic_menu_fakku), // Legacy support for old fakku archives
    PURURIN(1, "Pururin", "https://pururin.to", R.drawable.ic_site_pururin),
    HITOMI(2, "hitomi", "https://hitomi.la", R.drawable.ic_site_hitomi),
    NHENTAI(3, "nhentai", "https://nhentai.net", R.drawable.ic_site_nhentai),
    TSUMINO(4, "tsumino", "https://www.tsumino.com", R.drawable.ic_site_tsumino),
    HENTAICAFE(5, "hentaicafe", "https://hentai.cafe", R.drawable.ic_site_hentaicafe),
    ASMHENTAI(6, "asmhentai", "https://asmhentai.com", R.drawable.ic_site_asmhentai),
    ASMHENTAI_COMICS(7, "asmhentai comics", "https://comics.asmhentai.com", R.drawable.ic_site_asmcomics),
    EHENTAI(8, "e-hentai", "https://e-hentai.org", R.drawable.ic_site_ehentai),
    //FAKKU2(9, "Fakku", "https://www.fakku.net", R.drawable.ic_menu_fakku),
    //NEXUS(10, "Hentai Nexus", "https://hentainexus.com", R.drawable.ic_menu_nexus),
    MUSES(11, "8Muses", "https://www.8muses.com", R.drawable.ic_site_8muses),
    DOUJINS(12, "doujins.com", "https://doujins.com/", R.drawable.ic_site_doujins),
    LUSCIOUS(13, "luscious.net", "https://members.luscious.net/manga/", R.drawable.ic_site_luscious),
    EXHENTAI(14, "exhentai", "https://exhentai.org", R.drawable.ic_site_exhentai),
    PORNCOMIX(15, "porncomixonline", "https://www.porncomixonline.net/", R.drawable.ic_site_porncomix),
    HBROWSE(16, "Hbrowse", "https://www.hbrowse.com/", R.drawable.ic_site_hbrowse),
    HENTAI2READ(17, "Hentai2Read", "https://hentai2read.com/", R.drawable.ic_site_hentai2read),
    HENTAIFOX(18, "Hentaifox", "https://hentaifox.com", R.drawable.ic_site_hentaifox),
    MRM(19, "MyReadingManga", "https://myreadingmanga.info/", R.drawable.ic_site_mrm),
    MANHWA(20, "ManwhaHentai", "https://manhwahentai.me/", R.drawable.ic_site_manhwa),
    IMHENTAI(21, "Imhentai", "https://imhentai.xxx", R.drawable.ic_site_imhentai),
    TOONILY(22, "Toonily", "https://toonily.com/", R.drawable.ic_site_toonily),
    ALLPORNCOMIC(23, "Allporncomic", "https://allporncomic.com/", R.drawable.ic_site_allporncomic),
    PIXIV(24, "Pixiv", "https://www.pixiv.net/", R.drawable.ic_site_pixiv),
    MANHWA18(25, "Manhwa18", "https://manhwa18.com/", R.drawable.ic_site_manhwa18),
    NONE(98, "none", "", R.drawable.ic_external_library), // External library; fallback site
    PANDA(99, "panda", "https://www.mangapanda.com", R.drawable.ic_site_panda); // Safe-for-work/wife/gf option; not used anymore and kept here for retrocompatibility


    private static final Site[] INVISIBLE_SITES = {
            //NEXUS, // Dead
            //HENTAICAFE, // Removed as per Fakku request
            //FAKKU, // Old Fakku; kept for retrocompatibility
            //FAKKU2, // Dropped after Fakku decided to flag downloading accounts and IPs
            ASMHENTAI_COMICS, // Does not work directly
            PANDA, // Dropped; kept for retrocompatibility
            NONE // Technical fallback
    };


    private final int code;
    private final String description;
    private final String url;
    private final int ico;
    // Default values overridden in sites.json
    private boolean useMobileAgent = true;
    private boolean useHentoidAgent = false;
    private boolean useWebviewAgent = true;
    private boolean hasImageProcessing = false;
    private boolean hasBackupURLs = false;
    private boolean hasCoverBasedPageUpdates = false;
    private boolean useCloudflare = false;
    private int requestsCapPerSecond = -1;
    private int parallelDownloadCap = 0;

    Site(int code,
         String description,
         String url,
         int ico) {
        this.code = code;
        this.description = description;
        this.url = url;
        this.ico = ico;
    }

    public static Site searchByCode(long code) {
        for (Site s : values())
            if (s.getCode() == code) return s;

        return NONE;
    }

    // Same as ValueOf with a fallback to NONE
    // (vital for forward compatibility)
    public static Site searchByName(String name) {
        for (Site s : values())
            if (s.name().equalsIgnoreCase(name)) return s;

        return NONE;
    }

    @Nullable
    public static Site searchByUrl(String url) {
        if (null == url || url.isEmpty()) {
            Timber.w("Invalid url");
            return null;
        }

        for (Site s : Site.values())
            if (s.code > 0 && HttpHelper.getDomainFromUri(url).equalsIgnoreCase(HttpHelper.getDomainFromUri(s.url)))
                return s;

        return Site.NONE;
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

    public boolean useMobileAgent() {
        return useMobileAgent;
    }

    public boolean useHentoidAgent() {
        return useHentoidAgent;
    }

    public boolean useWebviewAgent() {
        return useWebviewAgent;
    }

    public boolean hasImageProcessing() {
        return hasImageProcessing;
    }

    public boolean hasBackupURLs() {
        return hasBackupURLs;
    }

    public boolean hasCoverBasedPageUpdates() {
        return hasCoverBasedPageUpdates;
    }

    public boolean isUseCloudflare() {
        return useCloudflare;
    }

    public int getRequestsCapPerSecond() {
        return requestsCapPerSecond;
    }

    public int getParallelDownloadCap() {
        return parallelDownloadCap;
    }

    public boolean isVisible() {
        for (Site s : INVISIBLE_SITES) if (s.equals(this)) return false;
        return true;
    }

    public String getFolder() {
            return description;
    }

    public String getUserAgent() {
        if (useMobileAgent())
            return HttpHelper.getMobileUserAgent(useHentoidAgent(), useWebviewAgent());
        else
            return HttpHelper.getDesktopUserAgent(useHentoidAgent(), useWebviewAgent());
    }

    public void updateFrom(@NonNull final JsonSiteSettings.JsonSite jsonSite) {
        if (jsonSite.useMobileAgent != null) useMobileAgent = jsonSite.useMobileAgent;
        if (jsonSite.useHentoidAgent != null) useHentoidAgent = jsonSite.useHentoidAgent;
        if (jsonSite.useWebviewAgent != null) useWebviewAgent = jsonSite.useWebviewAgent;
        if (jsonSite.hasImageProcessing != null) hasImageProcessing = jsonSite.hasImageProcessing;
        if (jsonSite.hasBackupURLs != null) hasBackupURLs = jsonSite.hasBackupURLs;
        if (jsonSite.hasCoverBasedPageUpdates != null)
            hasCoverBasedPageUpdates = jsonSite.hasCoverBasedPageUpdates;
        if (jsonSite.useCloudflare != null)
            useCloudflare = jsonSite.useCloudflare;
        if (jsonSite.parallelDownloadCap != null)
            parallelDownloadCap = jsonSite.parallelDownloadCap;
        if (jsonSite.requestsCapPerSecond != null)
            requestsCapPerSecond = jsonSite.requestsCapPerSecond;
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
