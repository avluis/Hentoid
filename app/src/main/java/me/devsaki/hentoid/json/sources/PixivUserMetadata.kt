package me.devsaki.hentoid.json.sources

import com.squareup.moshi.Json
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup

/**
 * Data structure for Pixiv's "user details" mobile endpoint
 */
@SuppressWarnings("unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068")
data class PixivUserMetadata(
    private val error: Boolean,
    private val message: String,
    private val body: UserBody?
) {

    data class UserBody(
        @Json(name = "user_details")
        val userDetails: UserData?
    )

    data class UserData(
        @Json(name = "user_id")
        private val userId: String,
        @Json(name = "user_name")
        private val userName: String,
        @Json(name = "profile_img")
        private val profileImg: ProfileImgData?
    ) {
        val id: String
            get() = userId
        val name: String
            get() = userName
        val coverUrl: String
            get() = profileImg?.main ?: ""

    }

    data class ProfileImgData(
        val main: String
    )

    fun update(content: Content, updateImages: Boolean): Content {
        // Determine the prefix the user is navigating with (i.e. with or without language path)
        content.site = Site.PIXIV
        if (error || null == body || null == body.userDetails) {
            content.status = StatusContent.IGNORED
            return content
        }
        val data: UserData = body.userDetails
        content.title = cleanup(data.name)
        content.uniqueSiteId = data.id
        content.url = "users/" + data.id
        content.coverImageUrl = data.coverUrl
        //        content.setUploadDate(
        val attributes = AttributeMap()
        val attribute = Attribute(
            AttributeType.ARTIST,
            data.name,
            Site.PIXIV.url + "user/" + data.id,
            Site.PIXIV
        )
        attributes.add(attribute)
        content.putAttributes(attributes)
        if (updateImages) {
            content.setImageFiles(emptyList())
            content.qtyPages = 0
        }
        return content
    }
}