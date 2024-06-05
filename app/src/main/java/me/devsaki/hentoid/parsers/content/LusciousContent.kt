package me.devsaki.hentoid.parsers.content

import android.net.Uri
import me.devsaki.hentoid.activities.sources.LusciousActivity.Companion.GALLERY_FILTER
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.json.sources.LusciousQueryParam
import me.devsaki.hentoid.retrofit.sources.LusciousServer
import me.devsaki.hentoid.util.Helper
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.jsonToObject
import timber.log.Timber
import java.io.IOException

class LusciousContent : BaseContentParser() {
    override fun update(content: Content, url: String, updateImages: Boolean): Content {
        val bookId: String =
            if (url.contains(GALLERY_FILTER[0])) { // Triggered by a graphQL request
                val vars = Uri.parse(url).getQueryParameter("variables")
                if (vars.isNullOrEmpty()) {
                    Timber.w("No variable field found in %s", url)
                    return Content().setSite(Site.LUSCIOUS).setStatus(StatusContent.IGNORED)
                }
                try {
                    jsonToObject(vars, LusciousQueryParam::class.java)!!.id
                } catch (e: Exception) {
                    Timber.w(e)
                    return Content().setSite(Site.LUSCIOUS).setStatus(StatusContent.IGNORED)
                }
            } else if (StringHelper.isNumeric(url)) { // Book ID is directly provided
                url
            } else { // Triggered by the loading of the page itself
                // ID is the last numeric part of the URL
                // e.g. /albums/lewd_title_ch_1_3_42116/ -> 42116 is the ID
                val lastIndex = url.lastIndexOf('_')
                url.substring(lastIndex + 1, url.length - 1)
            }
        val query: MutableMap<String, String> = HashMap()
        query["id"] = Helper.getRandomInt(10).toString() + ""
        query["operationName"] = "AlbumGet"
        query["query"] =
            " query AlbumGet(\$id: ID!) { album { get(id: \$id) { ... on Album { ...AlbumStandard } ... on MutationError { errors { code message } } } } } fragment AlbumStandard on Album { __typename id title labels description created modified like_status number_of_favorites rating status marked_for_deletion marked_for_processing number_of_pictures number_of_animated_pictures slug is_manga url download_url permissions cover { width height size url } created_by { id url name display_name user_title avatar { url size } } content { id title url } language { id title url } tags { id category text url count } genres { id title slug url } audiences { id title url url } last_viewed_picture { id position url } } " // Yeah...
        query["variables"] = "{\"id\":\"$bookId\"}"
        try {
            val metadata = LusciousServer.api.getBookMetadata(query).execute().body()
            if (metadata != null) return metadata.update(content, updateImages)
        } catch (e: IOException) {
            Timber.e(e, "Error parsing content.")
        }
        return Content().setSite(Site.LUSCIOUS).setStatus(StatusContent.IGNORED)
    }
}