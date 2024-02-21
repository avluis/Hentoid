package me.devsaki.hentoid.json.core

data class JsonSiteSettings(
    val sites: Map<String, JsonSite>
) {
    data class JsonSite(
        val useMobileAgent: Boolean?,
        val useHentoidAgent: Boolean?,
        val useWebviewAgent: Boolean?,
        val hasBackupURLs: Boolean?,
        val hasCoverBasedPageUpdates: Boolean?,
        val useCloudflare: Boolean?,
        val hasUniqueBookId: Boolean?,
        val parallelDownloadCap: Int?,
        val requestsCapPerSecond: Int?,
        val bookCardDepth: Int?,
        val bookCardExcludedParentClasses: List<String>?,
        val galleryHeight: Int?
    )
}
