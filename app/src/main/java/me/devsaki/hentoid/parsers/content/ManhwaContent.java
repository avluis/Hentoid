package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.activities.sources.ManhwaActivity;
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

public class ManhwaContent extends BaseContentParser {

    private static final Pattern GALLERY_PATTERN = Pattern.compile(ManhwaActivity.GALLERY_PATTERN);

    @Selector(value = "head [property=og:image]", attr = "content")
    private String coverUrl;
    @Selector(value = ".breadcrumb a")
    private List<Element> breadcrumbs;
    @Selector(value = "head script.yoast-schema-graph")
    private Element metadata;
    @Selector(value = ".author-content a")
    private List<Element> author;
    @Selector(value = ".artist-content a")
    private List<Element> artist;

    @Selector(value = "#chapter-heading")
    private Element chapterTitle;
    @Selector(value = ".reading-content img")
    private List<Element> chapterImgs;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.MANHWA);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
        content.setRawUrl(url);

        if (GALLERY_PATTERN.matcher(url).find()) return updateGallery(content, url, updateImages);
        else return updateSingleChapter(content, url, updateImages);
    }

    public Content updateSingleChapter(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String title = StringHelper.removeNonPrintableChars(chapterTitle.text());
        content.setTitle(title);
        String[] urlParts = url.split("/");
        if (urlParts.length > 1)
            content.setUniqueSiteId(urlParts[urlParts.length - 2]);
        else
            content.setUniqueSiteId(urlParts[0]);

        if (updateImages) {
            List<String> imgUrls = Stream.of(chapterImgs).map(ParseHelper::getImgSrc).toList();
            String coverUrl = "";
            if (!imgUrls.isEmpty()) coverUrl = imgUrls.get(0);
            content.setImageFiles(ParseHelper.urlsToImageFiles(imgUrls, coverUrl, StatusContent.SAVED));
            content.setQtyPages(imgUrls.size());
        }

        return content;
    }

    public Content updateGallery(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setCoverImageUrl(coverUrl);
        String title = NO_TITLE;
        if (breadcrumbs != null && !breadcrumbs.isEmpty()) {
            title = StringHelper.removeNonPrintableChars(breadcrumbs.get(breadcrumbs.size() - 1).text());
        }
        content.setTitle(title);
        content.populateUniqueSiteId();

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

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artist, false, Site.MANHWA);
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, author, false, Site.MANHWA);
        content.putAttributes(attributes);

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
