package me.devsaki.hentoid.json.sources;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;

/**
 * Data structure for Pixiv's "illust details" mobile endpoint
 */
@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class PixivIllustMetadata {

    private Boolean error;
    private String message;
    private IllustBody body;

    private static class IllustBody {
        private IllustDetails illust_details;
        private AuthorDetails author_details;

        List<Pair<String, String>> getTags() {
            if (illust_details != null) return illust_details.getTags();
            else return Collections.emptyList();
        }

        List<String> getImageUrls() {
            if (illust_details != null) return illust_details.getImageUrls();
            else return Collections.emptyList();
        }

        String getThumbUrl() {
            if (illust_details != null) return illust_details.getThumbUrl();
            return "";
        }

        public String getIllustId() {
            if (illust_details != null) return illust_details.id;
            return "";
        }

        public String getTitle() {
            if (illust_details != null) return illust_details.title;
            return "";
        }

        public Long getUploadTimestamp() {
            if (illust_details != null) return illust_details.upload_timestamp;
            return 0L;
        }

        public Integer getPageCount() {
            if (illust_details != null && illust_details.manga_a != null) {
                return illust_details.manga_a.size();
            }
            return 0;
        }

        public String getUserId() {
            if (null == author_details) return "";
            return author_details.getId();
        }

        public String getUserName() {
            if (null == author_details) return "";
            return author_details.getName();
        }

        public String getCanonicalUrl() {
            if (null == illust_details) return "";
            return illust_details.getCanonicalUrl();
        }
    }

    private static class IllustDetails {
        private String id;
        private String title;
        private Long upload_timestamp;
        private String page_count;

        private List<String> tags;
        private List<PageData> manga_a;
        private List<TagData> display_tags;
        private MetaData meta;
        private String url_s;
        private String url_big;

        String getThumbUrl() {
            return StringHelper.protect(url_s);
        }

        List<Pair<String, String>> getTags() {
            if (null == display_tags) return Collections.emptyList();
            return Stream.of(display_tags).map(TagData::getTag).toList();
        }

        List<String> getImageUrls() {
            int pageCount = 0;
            if (page_count != null && StringHelper.isNumeric(page_count))
                pageCount = Integer.parseInt(page_count);

            if (1 == pageCount) {
                return Stream.of(url_big).toList();
            } else {
                if (null == manga_a) return Collections.emptyList();
                return Stream.of(manga_a).map(PageData::getUrl).toList();
            }
        }

        String getCanonicalUrl() {
            if (null == meta) return "";
            return meta.getCanonicalUrl();
        }
    }

    private static class PageData {
        private Integer page;
        private String url;
        private String url_small;
        private String url_big;

        String getUrl() {
            String result = url_big;
            if (null == result) result = url;
            return StringHelper.protect(result);
        }
    }

    private static class TagData {
        private String tag;
        private String romaji;
        private String translation;

        Pair<String, String> getTag() {
            String label = translation;
            if (null == label) label = romaji;
            if (null == label) label = tag;
            return new Pair<>(tag, label);
        }
    }

    private static class MetaData {
        private String canonical;

        public String getCanonicalUrl() {
            return StringHelper.protect(canonical);
        }
    }

    private static class AuthorDetails {
        private String user_id;
        private String user_name;

        public String getId() {
            return user_id;
        }

        public String getName() {
            return user_name;
        }
    }

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Determine the prefix the user is navigating with (i.e. with or without language path)
        content.setSite(Site.PIXIV);

        if (error || null == body || null == body.illust_details)
            return content.setStatus(StatusContent.IGNORED);
        IllustBody illustData = body;

        content.setTitle(StringHelper.removeNonPrintableChars(illustData.getTitle()));
        content.setUniqueSiteId(illustData.getIllustId());

        String urlValue = illustData.getCanonicalUrl();
        if (urlValue.isEmpty()) urlValue = url;
        content.setUrl(urlValue.replace(Site.PIXIV.getUrl(), ""));

        content.setQtyPages(illustData.getPageCount());
        content.setCoverImageUrl(illustData.getThumbUrl());
        content.setUploadDate(illustData.getUploadTimestamp());

        AttributeMap attributes = new AttributeMap();

        Attribute attribute = new Attribute(AttributeType.ARTIST, illustData.getUserName(), Site.PIXIV.getUrl() + "user/" + illustData.getUserId(), Site.LUSCIOUS);
        attributes.add(attribute);

        for (Pair<String, String> tag : illustData.getTags()) {
            String name = StringHelper.removeNonPrintableChars(tag.second);
            AttributeType type = AttributeType.TAG;
            attribute = new Attribute(type, name, Site.PIXIV.getUrl() + "tags/" + tag.first, Site.LUSCIOUS);
            attributes.add(attribute);
        }
        content.putAttributes(attributes);

        List<ImageFile> images = ParseHelper.urlsToImageFiles(illustData.getImageUrls(), illustData.getThumbUrl(), StatusContent.SAVED);
        if (updateImages) content.setImageFiles(images);

        return content;
    }
}
