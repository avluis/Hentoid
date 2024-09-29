package me.devsaki.hentoid.parsers.images

import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.getImgSrc
import me.devsaki.hentoid.parsers.getUserAgent
import me.devsaki.hentoid.retrofit.DeviantArtServer
import me.devsaki.hentoid.util.exception.ParseException
import me.devsaki.hentoid.util.network.getCookies
import me.devsaki.hentoid.util.network.getOnlineDocument
import org.jsoup.nodes.Element
import timber.log.Timber
import kotlin.math.floor
import kotlin.math.log10

class DeviantArtParser : BaseImageListParser() {
    companion object {
        private const val DEVIATIONS_PER_REQUEST = 50

        // Thumb, hi-res (download) URL, display URL
        fun parseDeviation(body: Element): Triple<String, String, String> {
            val dlLink = body.selectFirst("a[download]")?.attr("href") ?: ""
            var imgLink = ""
            var thumbLink = ""
            body.selectFirst("img[fetchpriority=high]")?.let {
                imgLink = getImgSrc(it)
                thumbLink = it.attr("srcSet").split(" ")[0]
            }
            return Triple(thumbLink, dlLink, imgLink)
        }

        fun parseGallery(body: Element): Content {
            val scripts = body.select("script").reversed()
            scripts.forEach {
                val str = it.toString()
                if (str.length > 50000) {
                    str.lines().reversed().forEach { line ->
                        if (line.contains("INITIAL_STATE")) return parseGalleryLine(line)
                    }
                }
            }
            return Content(status = StatusContent.IGNORED)
        }

        fun parseGalleryLine(line: String): Content {
            val content = Content()
            content.site = Site.DEVIANTART
            try {
                val tokenStartIdx = line.indexOf("\\\"csrfToken\\\":\\\"") + 16
                val tokenEndIdx = line.indexOf("\\\",", tokenStartIdx)
                val token = line.substring(tokenStartIdx, tokenEndIdx)

                val foldIdStartIdx = line.indexOf("\\\"folderId\\\":") + 13
                val foldIdEndIdx = line.indexOf(",", foldIdStartIdx)
                val folderId = line.substring(foldIdStartIdx, foldIdEndIdx).toInt()

                val userNameStartIdx = line.indexOf("\\\"username\\\":\\\"", foldIdEndIdx) + 15
                val userNameEndIdx = line.indexOf("\\\",", userNameStartIdx)
                val userName = line.substring(userNameStartIdx, userNameEndIdx)

                content.title = userName
                content.url = "$userName/gallery"
                val imgs = parseXhrGallection(
                    userName,
                    "gallery",
                    folderId,
                    token,
                    "20230710"
                )
                content.status = StatusContent.SAVED
                content.setImageFiles(imgs)
                content.qtyPages = imgs.count { it.isReadable }
                content.coverImageUrl = imgs.find { it.isCover }?.url ?: ""
            } catch (e: Exception) {
                Timber.w(e)
                return Content(status = StatusContent.IGNORED)
            }
            return content
        }

        fun parseXhrGallection(
            username: String,
            type: String,
            folderId: Int,
            token: String,
            daMinorVersion: String
        ): List<ImageFile> {
            val cookieStr = getCookies(
                Site.DEVIANTART.url,
                null,
                Site.DEVIANTART.useMobileAgent,
                Site.DEVIANTART.useHentoidAgent,
                Site.DEVIANTART.useHentoidAgent
            )

            val result: MutableList<ImageFile> = ArrayList()
            var hasMore = false
            var nextIndex = 0
            do {
                val call = DeviantArtServer.API.getUserGallection(
                    username,
                    type,
                    nextIndex,
                    DEVIATIONS_PER_REQUEST,
                    folderId,
                    token,
                    daMinorVersion,
                    cookieStr,
                    getUserAgent(Site.DEVIANTART)
                )
                val response = call.execute()
                if (response.isSuccessful) {
                    response.body()?.let {
                        result.addAll(it.getImages())
                        hasMore = it.hasMore
                        nextIndex = it.nextOffset ?: 0
                    }
                } else {
                    throw ParseException("Call to getUserGallection failed $username $folderId $nextIndex")
                }
            } while (hasMore && nextIndex > 0)

            var idx = 1
            result.forEach { imageFile ->
                if (!imageFile.isCover) {
                    imageFile.order = idx++
                    imageFile.computeName(floor(log10(result.size.toDouble()) + 1).toInt())
                }
            }

            return result
        }
    }

    override fun parseImageListImpl(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()
        processedUrl = onlineContent.galleryUrl

        if (processedUrl.contains("/art/")) {
            val urls = getOnlineDocument(
                processedUrl,
                fetchHeaders(onlineContent),
                Site.DEVIANTART.useHentoidAgent,
                Site.DEVIANTART.useWebviewAgent
            )?.let {
                parseDeviation(it.body())
            }

            urls?.let {
                // Thumb
                if (urls.first.isNotEmpty())
                    result.add(ImageFile.newCover(urls.first, StatusContent.SAVED))
                // Image
                val img = ImageFile.fromImageUrl(1, urls.first, StatusContent.SAVED, 1)
                img.backupUrl = urls.second
                result.add(img)
            }
        }
        if (processedUrl.endsWith("/gallery")) {
            getOnlineDocument(
                processedUrl,
                fetchHeaders(onlineContent),
                Site.DEVIANTART.useHentoidAgent,
                Site.DEVIANTART.useWebviewAgent
            )?.let {
                result.addAll(parseGallery(it.body()).imageList)
            }
        }
        return result
    }

    override fun parseImagePage(
        url: String,
        requestHeaders: List<Pair<String, String>>
    ): Pair<String, String?> {
        getOnlineDocument(
            url,
            requestHeaders,
            Site.DEVIANTART.useHentoidAgent,
            Site.DEVIANTART.useWebviewAgent
        )?.let {
            val urls = parseDeviation(it.body())
            return Pair(urls.second, urls.third)
        }
        return Pair("", null)
    }

    override fun parseImages(content: Content): List<String> {
        // Already overrides parseImageListImpl
        return emptyList()
    }
}