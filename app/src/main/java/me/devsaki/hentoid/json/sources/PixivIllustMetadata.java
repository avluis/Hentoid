package me.devsaki.hentoid.json.sources;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;
import com.squareup.moshi.Types;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.StringHelper;

/**
 * Data structure for Pixiv's "illust details" mobile endpoint
 */
@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class PixivIllustMetadata {

    private static final Type UGOIRA_FRAME_TYPE = Types.newParameterizedType(Pair.class, String.class, Integer.class);
    public static final Type UGOIRA_FRAMES_TYPE = Types.newParameterizedType(List.class, UGOIRA_FRAME_TYPE);

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

        List<ImageFile> getImageFiles() {
            if (illust_details != null) return illust_details.getImageFiles();
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

        public String getUgoiraSrc() {
            if (null == illust_details) return "";
            return illust_details.getUgoiraSrc();
        }

        public List<Pair<String, Integer>> getUgoiraFrames() {
            if (null == illust_details) return Collections.emptyList();
            return illust_details.getUgoiraFrames();
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

        private UgoiraData ugoira_meta;

        String getThumbUrl() {
            return StringHelper.protect(url_s);
        }

        List<Pair<String, String>> getTags() {
            if (null == display_tags) return Collections.emptyList();
            return Stream.of(display_tags).map(TagData::getTag).toList();
        }

        List<ImageFile> getImageFiles() {
            int pageCount = 0;
            if (page_count != null && StringHelper.isNumeric(page_count))
                pageCount = Integer.parseInt(page_count);

            // TODO include cover in the page list (getThumbUrl) ?
            if (1 == pageCount) {
                ImageFile img;
                if (null == ugoira_meta) { // One single page
                    img = ParseHelper.urlToImageFile(url_big, 1, 1, StatusContent.SAVED);
                } else { // One single ugoira
                    img = ParseHelper.urlToImageFile(ugoira_meta.src, 1, 1, StatusContent.SAVED);
                    Map<String, String> downloadParams = new HashMap<>();
                    String framesJson = JsonHelper.serializeToJson(ugoira_meta.getFrames(), UGOIRA_FRAMES_TYPE);
                    downloadParams.put(ContentHelper.KEY_DL_PARAMS_UGOIRA_FRAMES, framesJson);
                    img.setDownloadParams(JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS));
                }
                return Stream.of(img).toList();
            } else { // Classic page list
                if (null == manga_a) return Collections.emptyList();
                int order = 1;
                List<ImageFile> result = new ArrayList<>();
                for (PageData pd : manga_a) {
                    result.add(ParseHelper.urlToImageFile(pd.getUrl(), order++, manga_a.size(), StatusContent.SAVED));
                }
                return result;
            }
        }

        String getCanonicalUrl() {
            if (null == meta) return "";
            return meta.getCanonicalUrl();
        }

        String getUgoiraSrc() {
            if (null == ugoira_meta) return "";
            return ugoira_meta.src;
        }

        List<Pair<String, Integer>> getUgoiraFrames() {
            if (null == ugoira_meta) return Collections.emptyList();
            return ugoira_meta.getFrames();
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

    private static class UgoiraData {
        private String src;
        private String mime_type;
        private List<UgoiraFrameData> frames;

        public List<Pair<String, Integer>> getFrames() {
            if (null == frames) return Collections.emptyList();
            return Stream.of(frames).map(f -> new Pair<>(f.file, f.delay)).toList();
        }
    }

    private static class UgoiraFrameData {
        private String file;
        private Integer delay;
    }

    public boolean isError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public List<ImageFile> getImageFiles() {
        if (error || null == body || null == body.illust_details) return Collections.emptyList();
        return body.getImageFiles();
    }

    public String getUrl() {
        if (error || null == body) return "";
        return body.getCanonicalUrl();
    }

    public String getTitle() {
        if (error || null == body) return "";
        return body.getTitle();
    }

    public String getId() {
        if (error || null == body) return "";
        return body.getIllustId();
    }

    public List<Attribute> getAttributes() {
        List<Attribute> result = new ArrayList<>();

        if (error || null == body || null == body.illust_details) return result;
        IllustBody illustData = body;

        Attribute attribute = new Attribute(AttributeType.ARTIST, illustData.getUserName(), Site.PIXIV.getUrl() + "user/" + illustData.getUserId(), Site.PIXIV);
        result.add(attribute);

        for (Pair<String, String> tag : illustData.getTags()) {
            String name = StringHelper.removeNonPrintableChars(tag.second);
            AttributeType type = AttributeType.TAG;
            attribute = new Attribute(type, name, Site.PIXIV.getUrl() + "tags/" + tag.first, Site.PIXIV);
            result.add(attribute);
        }
        return result;
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

        content.setCoverImageUrl(illustData.getThumbUrl());
        content.setUploadDate(illustData.getUploadTimestamp());

        content.putAttributes(getAttributes());

        if (updateImages) {
            content.setImageFiles(illustData.getImageFiles());
            content.setQtyPages(illustData.getPageCount());
        }

        return content;
    }
}
