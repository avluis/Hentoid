package me.devsaki.hentoid.parsers.images

import androidx.core.util.Pair
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.ImageFile
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.retrofit.sources.LusciousServer
import me.devsaki.hentoid.util.Helper
import timber.log.Timber
import java.io.IOException

class LusciousParser : BaseImageListParser() {
    override fun isChapterUrl(url: String): Boolean {
        return false
    }

    override fun parseImages(content: Content): List<String> {
        // We won't use that as parseImageListImpl is overriden directly
        return emptyList()
    }

    override fun parseImages(
        chapterUrl: String,
        downloadParams: String?,
        headers: List<Pair<String, String>>?
    ): List<String> {
        // Nothing; no chapters for this source
        return emptyList()
    }

    override fun parseImageListImpl(
        onlineContent: Content,
        storedContent: Content?
    ): List<ImageFile> {
        val result: MutableList<ImageFile> = ArrayList()

        result.add(ImageFile.newCover(onlineContent.coverImageUrl, StatusContent.SAVED))
        getPages(onlineContent, onlineContent.uniqueSiteId, 1, result)

        progressComplete()

        return result
    }

    private fun getPages(
        content: Content,
        bookId: String,
        pageNumber: Int,
        imageFiles: MutableList<ImageFile>
    ) {
        val query: MutableMap<String, String> = HashMap()
        query["id"] = Helper.getRandomInt(10).toString() + ""
        query["operationName"] = "AlbumListOwnPictures"
        query["query"] =
            " query AlbumListOwnPictures(\$input: PictureListInput!) { picture { list(input: \$input) { info { ...FacetCollectionInfo } items { ...PictureStandardWithoutAlbum } } } } fragment FacetCollectionInfo on FacetCollectionInfo { page has_next_page has_previous_page total_items total_pages items_per_page url_complete url_filters_only } fragment PictureStandardWithoutAlbum on Picture { __typename id title created like_status number_of_comments number_of_favorites status width height resolution aspect_ratio url_to_original url_to_video is_animated position tags { id category text url } permissions url thumbnails { width height size url } } " // Yeah...
        query["variables"] =
            "{\"input\":{\"filters\":[{\"name\":\"album_id\",\"value\":\"$bookId\"}],\"display\":\"position\",\"page\":$pageNumber}}"
        try {
            val response = LusciousServer.api.getGalleryMetadata(query).execute()
            if (response.isSuccessful) {
                val metadata = response.body()
                if (null == metadata) {
                    Timber.e("No metadata found @ ID %s", bookId)
                    return
                }
                imageFiles.addAll(metadata.toImageFileList(imageFiles.size - 1)) // Don't count cover in the offset
                if (metadata.nbPages > pageNumber) {
                    progressStart(content, null, metadata.nbPages)
                    progressPlus()
                    getPages(content, bookId, pageNumber + 1, imageFiles)
                } else {
                    content.setImageFiles(imageFiles)
                }
            } else {
                val httpCode = response.code()
                val errorMsg =
                    if (response.errorBody() != null) response.errorBody().toString() else ""
                Timber.e("Request unsuccessful (HTTP code %s) : %s", httpCode, errorMsg)
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
    }
}