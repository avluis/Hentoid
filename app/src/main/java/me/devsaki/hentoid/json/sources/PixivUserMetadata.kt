package me.devsaki.hentoid.json.sources

import com.squareup.moshi.Json
import me.devsaki.hentoid.database.domains.Attribute
import me.devsaki.hentoid.database.domains.AttributeMap
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.Site
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.parsers.cleanup
import me.devsaki.hentoid.util.StringHelper

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
            get() = StringHelper.protect(userId)
        val name: String
            get() = StringHelper.protect(userName)
        val coverUrl: String
            get() = if (null == profileImg) "" else StringHelper.protect(profileImg.main)

    }

    data class ProfileImgData(
        val main: String
    )

    fun update(content: Content, updateImages: Boolean): Content {
        // Determine the prefix the user is navigating with (i.e. with or without language path)
        content.setSite(Site.PIXIV)
        if (error || null == body || null == body.userDetails)
            return content.setStatus(StatusContent.IGNORED)
        val data: UserData = body.userDetails
        content.setTitle(cleanup(data.name))
        content.uniqueSiteId = data.id
        content.setUrl("users/" + data.id)
        content.setCoverImageUrl(data.coverUrl)
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
            content.setQtyPages(0)
        }
        return content
    }
}