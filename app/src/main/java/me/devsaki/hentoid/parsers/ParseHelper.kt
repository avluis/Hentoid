package me.devsaki.hentoid.parsers

import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Chapter
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.events.DownloadPreparationEvent
import me.devsaki.hentoid.parsers.content.BaseContentParser
import me.devsaki.hentoid.util.MAP_STRINGS
import me.devsaki.hentoid.util.isNumeric
import me.devsaki.hentoid.util.network.HEADER_COOKIE_KEY
import me.devsaki.hentoid.util.network.HEADER_REFERER_KEY
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.getUserAgent
import me.devsaki.hentoid.util.parseDateToEpoch
import me.devsaki.hentoid.util.parseDownloadParams
import me.devsaki.hentoid.util.removeNonPrintableChars
import me.devsaki.hentoid.util.serializeToJson
import org.apache.commons.text.StringEscapeUtils
import org.greenrobot.eventbus.EventBus
import org.jsoup.nodes.Element
import java.util.regex.Pattern
import kotlin.math.floor
import kotlin.math.log10

private val SQUARE_BRACKETS = Pattern.compile("\\[[^]]*\\]")

/**
 * Remove counters from given string (e.g. "Futanari (2660)" => "Futanari")
 *
 * @param s String to clean up
 * @return String with removed brackets
 */
fun removeBrackets(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    var bracketPos = s.lastIndexOf('(')
    if (bracketPos > 1 && ' ' == s[bracketPos - 1]) bracketPos--
    return if (bracketPos > -1) s.substring(0, bracketPos)
    else s
}

/**
 * Remove all terms between square brackets that are used
 * to "tag" book titles
 * (e.g. "[Author] Title [English] [Digital]" => "Title")
 *
 * @param s String to clean up
 * @return String with removed terms
 */
fun removeTextualTags(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    val m = SQUARE_BRACKETS.matcher(s)
    return m.replaceAll("").replace("  ", " ").trim()
}

/**
 * Remove trailing numbers from given string (e.g. "Futanari 2660" => "Futanari")
 * Only works when the numbers come after a space, so that tags ending with numbers
 * are not altered (e.g. "circle64")
 *
 * @param s String to clean up
 * @return String with removed trailing numbers
 */
private fun removeTrailingNumbers(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    val parts = s.split(" ")
    if (parts.size > 1 && isNumeric(parts[parts.size - 1])) {
        val sb = StringBuilder()
        for (i in 0 until parts.size - 1) sb.append(parts[i]).append(" ")
        return sb.toString().trim()
    }
    return s
}

/**
 * See definition of the main method below
 */
fun parseAttributes(
    map: AttributeMap,
    type: AttributeType,
    elements: List<Element>?,
    removeTrailingNumbers: Boolean,
    site: Site
) {
    if (elements != null)
        for (a in elements) parseAttribute(map, type, a, removeTrailingNumbers, site)
}

/**
 * See definition of the main method below
 */
fun parseAttributes(
    map: AttributeMap,
    type: AttributeType,
    elements: List<Element>?,
    removeTrailingNumbers: Boolean,
    childElementClass: String,
    site: Site
) {
    if (elements != null)
        for (a in elements)
            parseAttribute(map, type, a, removeTrailingNumbers, childElementClass, site)
}

/**
 * See definition of the main method below
 */
fun parseAttribute(
    map: AttributeMap,
    type: AttributeType,
    element: Element,
    removeTrailingNumbers: Boolean,
    site: Site
) {
    parseAttribute(element, map, type, site, "", removeTrailingNumbers, null)
}

/**
 * See definition of the main method below
 */
private fun parseAttribute(
    map: AttributeMap,
    type: AttributeType,
    element: Element,
    removeTrailingNumbers: Boolean,
    childElementClass: String,
    site: Site
) {
    parseAttribute(element, map, type, site, "", removeTrailingNumbers, childElementClass)
}

/**
 * Extract Attributes from the given Element and put them into the given AttributeMap,
 * using the given properties
 *
 * @param element               Element to parse Attributes from
 * @param map                   Output map where the detected attributes will be put
 * @param type                  AttributeType to give to the detected Attributes
 * @param site                  Site to give to the detected Attributes
 * @param prefix                If set, detected attributes will have this prefix added to their name
 * @param removeTrailingNumbers If true trailing numbers will be removed from the attribute name
 * @param childElementClass     If set, the parser will look for sub-elements of the given class
 */
private fun parseAttribute(
    element: Element,
    map: AttributeMap,
    type: AttributeType,
    site: Site,
    prefix: String,
    removeTrailingNumbers: Boolean,
    childElementClass: String?
) {
    var name: String
    name = if (null == childElementClass) {
        element.ownText()
    } else {
        val e = element.selectFirst(".$childElementClass")
        if (e != null) e.ownText() else ""
    }
    name = cleanup(name)
    name = removeBrackets(name)
    if (removeTrailingNumbers) name = removeTrailingNumbers(name)
    if (name.isEmpty() || name == "-" || name == "/") return
    if (prefix.isNotEmpty()) name = "$prefix:$name"
    val attribute = Attribute(type, name, element.attr("href"), site)
    map.add(attribute)
}

/**
 * See definition of the main method below
 */
fun urlsToImageFiles(
    imgUrls: List<String>,
    coverUrl: String,
    status: StatusContent
): List<ImageFile> {
    return urlsToImageFiles(imgUrls, status, coverUrl, null)
}

/**
 * See definition of the main method below
 */
fun urlsToImageFiles(
    imgUrls: List<String>,
    status: StatusContent,
    coverUrl: String?,
    chapter: Chapter?
): List<ImageFile> {
    val result: MutableList<ImageFile> = ArrayList()
    if (!coverUrl.isNullOrEmpty()) result.add(
        ImageFile.newCover(coverUrl, status)
    )
    result.addAll(urlsToImageFiles(imgUrls, 1, status, imgUrls.size, chapter))
    return result
}

/**
 * Build a list of ImageFiles using the given properties
 *
 * @param imgUrls        URLs of the images
 * @param initialOrder   Order of the 1st image to be generated
 * @param status         Status of the resulting ImageFiles
 * @param totalBookPages Total number of pages of the corresponding book
 * @param chapter        Chapter to link to the resulting ImageFiles (optional)
 * @return List of ImageFiles built using all given arguments
 */
fun urlsToImageFiles(
    imgUrls: List<String>,
    initialOrder: Int,
    status: StatusContent,
    totalBookPages: Int,
    chapter: Chapter?
): List<ImageFile> {
    val result: MutableList<ImageFile> = ArrayList()
    var order = initialOrder
    // Remove duplicates and MACOSX indexes (yes, it does happen!) before creating the ImageFiles
    val imgUrlsUnique = imgUrls.distinct().filterNot { it.contains("__MACOSX") }
    for (s in imgUrlsUnique) result.add(
        urlToImageFile(
            s.trim(),
            order++,
            totalBookPages,
            status,
            chapter
        )
    )
    return result
}

/**
 * Build an ImageFile using the given given properties
 *
 * @param imgUrl         URL of the image
 * @param order          Order of the image
 * @param totalBookPages Total number of pages of the corresponding book
 * @param status         Status of the resulting ImageFile
 * @param chapter        Chapter to link to the resulting ImageFile (optional)
 * @return ImageFile built using all given arguments
 */
fun urlToImageFile(
    imgUrl: String,
    order: Int,
    totalBookPages: Int,
    status: StatusContent,
    chapter: Chapter? = null
): ImageFile {
    val result = ImageFile(dbOrder = order, dbUrl = imgUrl, status = status)
    val nbMaxDigits = (floor(log10(totalBookPages.toDouble())) + 1).toInt()
    result.computeName(nbMaxDigits)
    if (chapter != null) result.setChapter(chapter)
    return result
}

/**
 * Signal download preparation event for the given processed elements
 *
 * @param contentId Online content ID being processed
 * @param storedId  Stored content ID being processed
 * @param progress  Progress (0.0 -> 1.0)
 */
fun signalProgress(contentId: Long, storedId: Long, progress: Float) {
    EventBus.getDefault()
        .post(DownloadPreparationEvent(contentId, storedId, progress))
}

/**
 * Extract the cookie string, if it exists, from the given download parameters
 *
 * @param downloadParams Download parameters to extract the cookie string from
 * @return Cookie string, if any in the given download parameters; empty string if none
 */
fun getSavedCookieStr(downloadParams: String?): String {
    val downloadParamsMap = parseDownloadParams(downloadParams)
    return if (downloadParamsMap.containsKey(HEADER_COOKIE_KEY))
        downloadParamsMap[HEADER_COOKIE_KEY] ?: ""
    else ""
}

/**
 * Copy the cookie string, if it exists, from the given download parameters to the given HTTP headers
 *
 * @param downloadParams Download parameters to extract the cookie string from
 * @param headers        HTTP headers to copy the cookie string to, if it exists
 */
fun addSavedCookiesToHeader(
    downloadParams: String?,
    headers: MutableList<Pair<String, String>>
) {
    val cookieStr = getSavedCookieStr(downloadParams)
    if (cookieStr.isNotEmpty()) headers.add(Pair(HEADER_COOKIE_KEY, cookieStr))
}

/**
 * Save the given referrer and the relevant cookie string as download parameters
 * to each image of the given list for future use during download
 *
 * @param imgs     List of images to save download params to
 * @param referrer Referrer to set
 */
fun setDownloadParams(imgs: List<ImageFile>, referrer: String) {
    val params: MutableMap<String, String> = HashMap()
    for (img in imgs) {
        params.clear()
        val cookieStr = getCookies(img.url)
        if (cookieStr.isNotEmpty()) params[HEADER_COOKIE_KEY] = cookieStr
        params[HEADER_REFERER_KEY] = referrer
        img.downloadParams = serializeToJson<Map<String, String>>(params, MAP_STRINGS)
    }
}

/**
 * Get the image extension from the given ImHentai / Hentaifox format code
 *
 * @param imgFormat Format map provided by the site
 * @param i         index to look up
 * @return Image extension (without the dot), if found; empty string if not
 */
fun getExtensionFromFormat(imgFormat: Map<String, String>, i: Int): String {
    val format = imgFormat[(i + 1).toString() + ""]
    return if (format != null) {
        when (format[0]) {
            'p' -> "png"
            'g' -> "gif"
            'j' -> "jpg"
            'w' -> "webp"
            else -> "jpg"
        }
    } else ""
}

/**
 * Extract a list of Chapters from the given list of links, for the given Content ID
 *
 * @param chapterLinks List of HTML links to extract Chapters from
 * @param contentId    Content ID to associate with all extracted Chapters
 * @return Chapters detected from the given list of links, associated with the given Content ID
 */
fun getChaptersFromLinks(chapterLinks: List<Element>, contentId: Long): List<Chapter> {
    return getChaptersFromLinks(chapterLinks, contentId, null, null)
}

/**
 * Extract a list of Chapters from the given list of links, for the given Content ID
 *
 * @param chapterLinks List of HTML links to extract Chapters from
 * @param contentId    Content ID to associate with all extracted Chapters
 * @param dateCssQuery CSS query to select the chapter upload date (optional)
 * @param datePattern  Pattern to parse the chapter upload date (optional)
 * @return Chapters detected from the given list of links, associated with the given Content ID
 */
fun getChaptersFromLinks(
    chapterLinks: List<Element>,
    contentId: Long,
    dateCssQuery: String?,
    datePattern: String?
): List<Chapter> {
    val result: MutableList<Chapter> = ArrayList()
    val urls: MutableSet<String> = HashSet()

    // First extract data and filter URL duplicates
    val chapterData: MutableList<Triple<String, String, Long>> = ArrayList()
    for (e in chapterLinks) {
        val url = e.attr("href").trim()
        var name = e.attr("title").trim()
        if (name.isEmpty()) name = cleanup(e.ownText()).trim()
        var epoch = 0L
        if (!dateCssQuery.isNullOrEmpty() && !datePattern.isNullOrEmpty()) {
            e.selectFirst(dateCssQuery)?.let { dateElement ->
                val dateStr = dateElement.text().split("-")
                if (dateStr.size > 1) epoch = parseDateToEpoch(dateStr[1], datePattern)
            }
        }
        // Make sure we're not adding duplicates
        if (!urls.contains(url)) {
            urls.add(url)
            chapterData.add(Triple(url, name, epoch))
        }
    }
    chapterData.reverse() // Put unique results in their chronological order
    // Build the final list
    for ((order, chapter) in chapterData.withIndex()) {
        val chp = Chapter(order, chapter.first, chapter.second)
        chp.uploadDate = chapter.third
        chp.setContentId(contentId)
        result.add(chp)
    }
    return result
}

/**
 * Extract the last but N useful part of the path of the given URL
 * e.g. if the url is "http://aa.com/look/at/me" or "http://aa.com/look/at/me/" :
 *      the result will be "me" if N is 1
 *      the result will be "at" if N is 2
 *
 * @param url URL to extract from
 * @param index Index of the part to extract (0-indexed; counted from the end)
 * @return Last but N useful part of the path of the given URL
 */
private fun getLastPathPart(url: String, index: Int = 0): String {
    var workUrl = url.trim()
    if (workUrl.endsWith("/")) workUrl = workUrl.substring(0, workUrl.length - 1)
    val parts = workUrl.split("/")
    if (index > parts.size - 1) return workUrl
    return parts[parts.size - 1 - index]
}

/**
 * Find extra chapters within the given "detected" list, that are not among the "stored list", based on their URL
 *
 * @param storedChapters   Stored list of chapters to compare against
 * @param detectedChapters Detected list of chapters where to find extra ones
 * @return Extra chapters that exist within the "detected" list, if any
 */
fun getExtraChaptersbyUrl(
    storedChapters: List<Chapter>,
    detectedChapters: List<Chapter>,
    lastPartIndex: (String) -> Int = { _ -> 0 }
): List<Chapter> {
    val storedChps = storedChapters.groupBy { getLastPathPart(it.url, lastPartIndex(it.url)) }
    val detectedChps = detectedChapters.groupBy { getLastPathPart(it.url, lastPartIndex(it.url)) }

    var tmpList: MutableList<Chapter> = ArrayList()
    val storedUrlParts = storedChps.keys
    for ((key, chps) in detectedChps) {
        if (!storedUrlParts.contains(key) && chps.isNotEmpty()) tmpList.add(chps[0])
    }

    // Only keep the latest contiguous chapters (no in-between chapters)
    tmpList = tmpList.sortedBy { c -> c.order }.toMutableList()

    val lastStoredUrl = storedChapters.sortedBy { c -> c.order }
        .map { c -> getLastPathPart(c.url) }.lastOrNull() ?: return tmpList

    val lastStoredOnlineOrder = detectedChapters
        .filter { c -> getLastPathPart(c.url) == lastStoredUrl }
        .map { obj: Chapter -> obj.order }
        .lastOrNull()

    return if (null == lastStoredOnlineOrder) tmpList
    else tmpList.filter { c -> c.order > lastStoredOnlineOrder }
}

/**
 * Find extra chapter IDs within the given "detected" list, that are not among the "stored list"
 *
 * @param storedChapters Stored list of chapters to compare against
 * @param detectedIds    Detected list of chapter IDs where to find extra ones
 * @return Extra chapter IDs that exist within the "detected" list, if any
 */
fun getExtraChaptersbyId(
    storedChapters: List<Chapter>,
    detectedIds: List<String>
): List<String> {
    val result: MutableList<String> = ArrayList()
    val storedIds: MutableSet<String> = HashSet()
    for (c in storedChapters) storedIds.add(c.uniqueId)
    for (detectedId in detectedIds) {
        if (!storedIds.contains(detectedId)) {
            result.add(detectedId)
        }
    }
    return result
}

/**
 * Return the highest image order within the given chapters
 *
 * @param chapters List of chapters to process
 * @return Highest image order within the given chapters; 0 if not found
 */
fun getMaxImageOrder(chapters: List<Chapter>): Int {
    if (chapters.isNotEmpty()) {
        val order = chapters.flatMap { c -> c.imageList }.maxOfOrNull { img -> img.order }
        if (order != null) return order
    }
    return 0
}

/**
 * Return the highest chapter order within the given chapters
 *
 * @param chapters List of chapters to process
 * @return Highest chapter order within the given chapters; 0 if not found
 */
fun getMaxChapterOrder(chapters: List<Chapter>): Int {
    if (chapters.isNotEmpty()) {
        val optOrder = chapters.maxOfOrNull { c -> c.order }
        if (optOrder != null) return optOrder
    }
    return 0
}

/**
 * Extract the image URL from the given HTML element
 *
 * @param e HTML element to extract the URL from
 * @return Image URL contained in the given HTML element
 */
fun getImgSrc(e: Element): String {
    var result = e.attr("data-src").trim()
    if (result.isEmpty()) result = e.attr("data-lazy-src").trim()
    if (result.isEmpty()) result = e.attr("data-lazysrc").trim()
    if (result.isEmpty()) result = e.attr("src").trim()
    if (result.isEmpty()) result =
        e.attr("data-cfsrc").trim() // Cloudflare-served image
    return result
}

/**
 * Generate the user agent corresponding to the given site
 *
 * @param site Site to generate the user-agent for
 * @return User agent corresponding to the given site
 */
fun getUserAgent(site: Site): String {
    return getUserAgent(
        site.useMobileAgent,
        site.useHentoidAgent,
        site.useWebviewAgent
    )
}

fun cleanup(data: String?): String {
    if (null == data) return BaseContentParser.NO_TITLE
    return StringEscapeUtils.unescapeHtml4(
        removeNonPrintableChars(data.trim()).replace('’', '\'')
    )
}