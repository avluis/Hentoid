package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

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


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.ALLPORNCOMIC);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        content.setCoverImageUrl(coverUrl);

        content.setUrl(url.replace(Site.ALLPORNCOMIC.getUrl(), ""));
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
