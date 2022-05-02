package me.devsaki.hentoid.json.sources;

import androidx.core.util.Pair;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
 * Data structure for Pixiv's "illust details" desktop website header data
 */
@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class PixivPreloadMetadata {

    private Map<String, IllustData> illust;
    private Map<String, UserData> user;

    private static class IllustData {
        private String illustId;
        private String illustTitle;
        private Date uploadDate;
        private Integer pageCount;
        private Map<String, String> urls;
        private TagsData tags;
        private String userId;
        private String userName;

        List<Pair<String, String>> getTags() {
            if (tags != null) return tags.getTags();
            else return Collections.emptyList();
        }

        String getThumbUrl() {
            if (null == urls) return "";
            String result = urls.get("thumb");
            if (null == result) result = urls.get("small");
            return StringHelper.protect(result);
        }

        public String getIllustId() {
            return illustId;
        }

        public String getTitle() {
            return illustTitle;
        }

        public Date getUploadDate() {
            return uploadDate;
        }

        public Integer getPageCount() {
            return pageCount;
        }

        public String getUserId() {
            return userId;
        }

        public String getUserName() {
            return userName;
        }
    }

    private static class TagsData {
        private List<TagData> tags;

        List<Pair<String, String>> getTags() {
            return Stream.of(tags).map(TagData::getTag).toList();
        }
    }

    private static class TagData {
        private String tag;
        private String romaji;
        private Map<String, String> translation;

        Pair<String, String> getTag() {
            String label = translation.get("en");
            if (null == label) label = romaji;
            if (null == label) label = tag;
            return new Pair<>(tag, label);
        }
    }

    private static class UserData {
        private String userId;
        private String name;
    }

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Determine the prefix the user is navigating with (i.e. with or without language path)
        String pixivPrefix = Site.PIXIV.getUrl();
        String[] urlParts = url.replace(Site.PIXIV.getUrl(), "").split("/");
        String secondPath = urlParts[0];
        if (!secondPath.equals("user") && !secondPath.equals("artworks"))
            pixivPrefix += secondPath + "/";

        content.setSite(Site.PIXIV);

        if (illust.isEmpty()) return content.setStatus(StatusContent.IGNORED);
        IllustData illustData = Stream.of(illust.values()).toList().get(0);

        content.setUrl(url.replace(Site.PIXIV.getUrl(), ""));
        content.setTitle(StringHelper.removeNonPrintableChars(illustData.getTitle()));
        content.setUniqueSiteId(illustData.illustId);

        content.setQtyPages(illustData.getPageCount());
        content.setCoverImageUrl(illustData.getThumbUrl());
        content.setUploadDate(illustData.getUploadDate().getTime()); // to test

        AttributeMap attributes = new AttributeMap();

        Attribute attribute = new Attribute(AttributeType.ARTIST, illustData.userName, pixivPrefix + "user/" + illustData.userId, Site.LUSCIOUS);
        attributes.add(attribute);

        for (Pair<String, String> tag : illustData.getTags()) {
            String name = StringHelper.removeNonPrintableChars(tag.second);
            AttributeType type = AttributeType.TAG;
            attribute = new Attribute(type, name, pixivPrefix + "tags/" + tag.first, Site.LUSCIOUS);
            attributes.add(attribute);
        }
        content.putAttributes(attributes);

        if (updateImages) content.setImageFiles(Collections.emptyList());

        return content;
    }
}
