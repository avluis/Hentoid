package me.devsaki.hentoid.json.sources;

import androidx.annotation.NonNull;

import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.StringHelper;

/**
 * Data structure for Pixiv's "user details" mobile endpoint
 */
@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class PixivUserMetadata {

    private Boolean error;
    private String message;
    private UserBody body;

    private static class UserBody {
        private UserData user_details;
    }

    private static class UserData {
        private String user_id;
        private String user_name;
        private ProfileImgData profile_img;

        String getId() {
            return StringHelper.protect(user_id);
        }

        String getName() {
            return StringHelper.protect(user_name);
        }

        String getCoverUrl() {
            if (null == profile_img) return "";
            return StringHelper.protect(profile_img.main);
        }
    }

    private static class ProfileImgData {
        private String main;
    }

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Determine the prefix the user is navigating with (i.e. with or without language path)
        content.setSite(Site.PIXIV);

        if (error || null == body || null == body.user_details)
            return content.setStatus(StatusContent.IGNORED);
        UserData data = body.user_details;

        content.setTitle(StringHelper.removeNonPrintableChars(data.getName()));
        content.setUniqueSiteId(data.getId());

        String urlValue = "user/" + data.getId();
        content.setUrl(urlValue);

        content.setCoverImageUrl(data.getCoverUrl());
//        content.setUploadDate(

        AttributeMap attributes = new AttributeMap();

        Attribute attribute = new Attribute(AttributeType.ARTIST, data.getName(), Site.PIXIV.getUrl() + "user/" + data.getId(), Site.PIXIV);
        attributes.add(attribute);

        content.putAttributes(attributes);

        if (updateImages) content.setImageFiles(Collections.emptyList());

        return content;
    }
}
