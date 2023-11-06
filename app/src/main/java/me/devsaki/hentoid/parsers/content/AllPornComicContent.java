package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.activities.sources.AllPornComicActivity;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.YoastGalleryMetadata;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class AllPornComicContent extends BaseContentParser {

    private static final Pattern GALLERY_PATTERN = Pattern.compile(AllPornComicActivity.GALLERY_PATTERN);

    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private String coverUrl;
    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private String title;
    @Selector(value = "head script.yoast-schema-graph")
    private Element metadata;

    @Selector(value = ".post-content a[href*='characters']")
    private List<Element> characterTags;
    @Selector(value = ".post-content a[href*='series']")
    private List<Element> seriesTags;
    @Selector(value = ".post-content a[href*='porncomic-artist']")
    private List<Element> artistsTags;
    @Selector(value = ".post-content a[href*='porncomic-genre']")
    private List<Element> tags;
    @Selector(value = "[class^=page-break] img")
    private List<Element> chapterImages;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.ALLPORNCOMIC);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
        content.setRawUrl(url);

        content.setCoverImageUrl(coverUrl);

        if (!title.isEmpty()) {
            content.setTitle(StringHelper.removeNonPrintableChars(title));
        } else content.setTitle(NO_TITLE);

        if (metadata != null && metadata.childNodeSize() > 0) {
            try {
                YoastGalleryMetadata galleryMeta = JsonHelper.jsonToObject(metadata.childNode(0).toString(), YoastGalleryMetadata.class);
                String publishDate = galleryMeta.getDatePublished(); // e.g. 2021-01-27T15:20:38+00:00
                if (!publishDate.isEmpty())
                    content.setUploadDate(Helper.parseDatetimeToEpoch(publishDate, "yyyy-MM-dd'T'HH:mm:ssXXX"));
            } catch (IOException e) {
                Timber.i(e);
            }
        }

        if (GALLERY_PATTERN.matcher(url).find()) return updateGallery(content, updateImages);
        else return updateSingleChapter(content, updateImages);
    }

    public Content updateSingleChapter(@NonNull final Content content, boolean updateImages) {
        if (updateImages) {
            List<String> imgUrls = Stream.of(chapterImages).map(ParseHelper::getImgSrc).filterNot(String::isEmpty).toList();
            content.setImageFiles(ParseHelper.urlsToImageFiles(imgUrls, coverUrl, StatusContent.SAVED));
            content.setQtyPages(imgUrls.size()); // Don't count the cover
        }

        return content;
    }

    public Content updateGallery(@NonNull final Content content, boolean updateImages) {
        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characterTags, false, Site.ALLPORNCOMIC);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, seriesTags, false, Site.ALLPORNCOMIC);
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artistsTags, false, Site.ALLPORNCOMIC);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.ALLPORNCOMIC);
        content.putAttributes(attributes);

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
