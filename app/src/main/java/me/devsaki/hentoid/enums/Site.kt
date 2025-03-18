package me.devsaki.hentoid.enums

import io.objectbox.converter.PropertyConverter
import me.devsaki.hentoid.R
import me.devsaki.hentoid.json.core.JsonSiteSettings.JsonSite
import me.devsaki.hentoid.util.network.getDesktopUserAgent
import me.devsaki.hentoid.util.network.getDomainFromUri
import me.devsaki.hentoid.util.network.getMobileUserAgent
import timber.log.Timber


// Safe-for-work/wife/gf option; not used anymore and kept here for retrocompatibility
private val INVISIBLE_SITES = setOf(
    Site.EDOUJIN,  // Dead
    Site.NEXUS,  // Dead
    Site.HBROWSE,  // Dead
    Site.HENTAICAFE,  // Dead
    Site.KSK,  // Dead
    Site.ANCHIRA,  // Dead
    Site.FAKKU,  // Old Fakku; kept for retrocompatibility
    Site.FAKKU2,  // Dropped after Fakku decided to flag downloading accounts and IPs
    Site.ASMHENTAI_COMICS,  // Does not work directly
    Site.PANDA,  // Dropped; kept for retrocompatibility
    Site.NONE // Technical fallback
)

enum class Site(val code: Int, val description: String, val url: String, val ico: Int) {
    // NOTE : to maintain compatiblity with saved JSON files and prefs, do _not_ edit either existing names or codes
    FAKKU(
        0,
        "Fakku",
        "https://www.fakku.net",
        R.drawable.ic_site_fakku
    ), // Legacy support for old fakku archives
    PURURIN(1, "Pururin", "https://pururin.me", R.drawable.ic_site_pururin),
    HITOMI(2, "hitomi", "https://hitomi.la", R.drawable.ic_site_hitomi),
    NHENTAI(3, "nhentai", "https://nhentai.net", R.drawable.ic_site_nhentai),
    TSUMINO(4, "tsumino", "https://www.tsumino.com", R.drawable.ic_site_tsumino),
    HENTAICAFE(5, "hentaicafe", "https://hentai.cafe", R.drawable.ic_site_hentaicafe),
    ASMHENTAI(6, "asmhentai", "https://asmhentai.com", R.drawable.ic_site_asmhentai),
    ASMHENTAI_COMICS(
        7,
        "asmhentai comics",
        "https://comics.asmhentai.com",
        R.drawable.ic_site_asmcomics
    ),
    EHENTAI(8, "e-hentai", "https://e-hentai.org", R.drawable.ic_site_ehentai),
    FAKKU2(9, "Fakku", "https://www.fakku.net", R.drawable.ic_site_fakku),
    NEXUS(10, "Hentai Nexus", "https://hentainexus.com", R.drawable.ic_site_nexus),
    MUSES(11, "8Muses", "https://www.8muses.com", R.drawable.ic_site_8muses),
    DOUJINS(12, "doujins.com", "https://doujins.com/", R.drawable.ic_site_doujins),
    LUSCIOUS(
        13,
        "luscious.net",
        "https://members.luscious.net/manga/",
        R.drawable.ic_site_luscious
    ),
    EXHENTAI(14, "exhentai", "https://exhentai.org", R.drawable.ic_site_exhentai),
    PORNCOMIX(15, "porncomixonline", "https://porncomix.online/", R.drawable.ic_site_porncomix),
    HBROWSE(16, "Hbrowse", "https://www.hbrowse.com/", R.drawable.ic_site_hbrowse),
    HENTAI2READ(17, "Hentai2Read", "https://hentai2read.com/", R.drawable.ic_site_hentai2read),
    HENTAIFOX(18, "Hentaifox", "https://hentaifox.com", R.drawable.ic_site_hentaifox),
    MRM(19, "MyReadingManga", "https://myreadingmanga.info/", R.drawable.ic_site_mrm),
    MANHWA(20, "ManwhaHentai", "https://manhwahentai.me/", R.drawable.ic_site_manhwa),
    IMHENTAI(21, "Imhentai", "https://imhentai.xxx", R.drawable.ic_site_imhentai),
    TOONILY(22, "Toonily", "https://toonily.com/", R.drawable.ic_site_toonily),
    ALLPORNCOMIC(23, "Allporncomic", "https://allporncomic.com/", R.drawable.ic_site_allporncomic),
    PIXIV(24, "Pixiv", "https://www.pixiv.net/", R.drawable.ic_site_pixiv),
    MANHWA18(25, "Manhwa18", "https://manhwa18.net/", R.drawable.ic_site_manhwa18),
    MULTPORN(26, "Multporn", "https://multporn.net/", R.drawable.ic_site_multporn),
    SIMPLY(27, "Simply Hentai", "https://www.simply-hentai.com/", R.drawable.ic_site_simply),
    HDPORNCOMICS(
        28,
        "HD Porn Comics",
        "https://hdporncomics.com/",
        R.drawable.ic_site_hdporncomics
    ),
    EDOUJIN(29, "Edoujin", "https://ehentaimanga.com/", R.drawable.ic_site_edoujin),
    KSK(30, "Koushoku", "https://ksk.moe", R.drawable.ic_site_ksk),
    ANCHIRA(31, "Anchira", "https://anchira.to", R.drawable.ic_site_anchira),
    DEVIANTART(32, "DeviantArt", "https://www.deviantart.com/", R.drawable.ic_site_deviantart),
    MANGAGO(33, "Mangago", "https://www.mangago.me/", R.drawable.ic_site_mangago),
    HIPERDEX(34, "Hiperdex", "https://hiperdex.com/", R.drawable.ic_site_hiperdex),
    NOVELCROW(35, "Novelcrow", "https://novelcrow.com/", R.drawable.ic_site_novelcrow),
    TMO(36, "TMOHentai", "https://tmohentai.com/", R.drawable.ic_site_tmo),
    NONE(98, "none", "", R.drawable.ic_attribute_source), // External library; fallback site
    PANDA(
        99,
        "panda",
        "https://www.mangapanda.com",
        R.drawable.ic_site_panda
    ); // Safe-for-work/wife/gf option; not used anymore and kept here for retrocompatibility

    // Default values overridden in sites.json
    var useMobileAgent = true
        private set
    var useHentoidAgent = false
        private set
    var useWebviewAgent = true
        private set

    // Download behaviour control
    var hasBackupURLs = false
        private set
    var hasCoverBasedPageUpdates = false
        private set
    var useCloudflare = false
        private set
    var hasUniqueBookId = false
        private set
    var requestsCapPerSecond = -1
        private set
    var parallelDownloadCap = 0
        private set

    // Controls for "Mark downloaded/merged" in browser
    var bookCardDepth = 2
        private set
    var bookCardExcludedParentClasses: Set<String> = HashSet()
        private set

    // Controls for "Mark books with blocked tags" in browser
    var galleryHeight = -1
        private set

    // Determine which Jsoup output to use when rewriting the HTML
    // 0 : html; 1 : xml
    var jsoupOutputSyntax = 0
        private set


    val isVisible: Boolean
        get() {
            return !INVISIBLE_SITES.contains(this)
        }

    val folder: String
        get() {
            return if (this == FAKKU) "Downloads" else description
        }

    val userAgent: String
        get() {
            return if (useMobileAgent) getMobileUserAgent(useHentoidAgent, useWebviewAgent)
            else getDesktopUserAgent(useHentoidAgent, useWebviewAgent)
        }


    fun updateFrom(jsonSite: JsonSite) {
        if (jsonSite.useMobileAgent != null) useMobileAgent = jsonSite.useMobileAgent
        if (jsonSite.useHentoidAgent != null) useHentoidAgent = jsonSite.useHentoidAgent
        if (jsonSite.useWebviewAgent != null) useWebviewAgent = jsonSite.useWebviewAgent
        if (jsonSite.hasBackupURLs != null) hasBackupURLs = jsonSite.hasBackupURLs
        if (jsonSite.hasCoverBasedPageUpdates != null)
            hasCoverBasedPageUpdates = jsonSite.hasCoverBasedPageUpdates
        if (jsonSite.useCloudflare != null) useCloudflare = jsonSite.useCloudflare
        if (jsonSite.hasUniqueBookId != null) hasUniqueBookId = jsonSite.hasUniqueBookId
        if (jsonSite.parallelDownloadCap != null) parallelDownloadCap = jsonSite.parallelDownloadCap
        if (jsonSite.requestsCapPerSecond != null)
            requestsCapPerSecond = jsonSite.requestsCapPerSecond
        if (jsonSite.bookCardDepth != null) bookCardDepth = jsonSite.bookCardDepth
        if (jsonSite.bookCardExcludedParentClasses != null)
            bookCardExcludedParentClasses =
                java.util.HashSet(jsonSite.bookCardExcludedParentClasses)
        if (jsonSite.galleryHeight != null) galleryHeight = jsonSite.galleryHeight
        if (jsonSite.jsoupOutputSyntax != null) jsoupOutputSyntax = jsonSite.jsoupOutputSyntax
    }

    class SiteConverter : PropertyConverter<Site, Long?> {
        override fun convertToEntityProperty(databaseValue: Long?): Site {
            if (databaseValue == null) return NONE
            for (site in Site.entries) if (site.code.toLong() == databaseValue) return site
            return NONE
        }

        override fun convertToDatabaseValue(entityProperty: Site): Long {
            return entityProperty.code.toLong()
        }
    }

    companion object {
        fun searchByCode(code: Long): Site {
            for (s in Site.entries) if (s.code.toLong() == code) return s
            return NONE
        }

        // Same as ValueOf with a fallback to NONE
        // (vital for forward compatibility)
        fun searchByName(name: String?): Site {
            for (s in Site.entries) if (s.name.equals(name, ignoreCase = true)) return s
            return NONE
        }

        fun searchByUrl(url: String?): Site? {
            if (url.isNullOrEmpty()) {
                Timber.w("Invalid url")
                return null
            }

            for (s in Site.entries) if (s.code > 0 && getDomainFromUri(url).equals(
                    getDomainFromUri(s.url), ignoreCase = true
                )
            ) return s

            return NONE
        }
    }
}