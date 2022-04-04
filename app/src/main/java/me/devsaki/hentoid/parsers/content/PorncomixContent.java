package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
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

public class PorncomixContent extends BaseContentParser {
    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private String coverUrl;
    @Selector(value = "head [property=og:title]", attr = "content", defValue = "")
    private String title;
    @Selector(value = "head script.yoast-schema-graph")
    private Element metadata;

    @Selector(value = ".wp-manga-tags-list a[href*='tag']")
    private List<Element> mangaTags;
    @Selector(value = ".item-tags a[href*='tag']")
    private List<Element> galleryTags;
    @Selector(value = ".bb-tags a[href*='label']")
    private List<Element> zoneTags;
    @Selector(value = ".video-tags a[href*='tag']")
    private List<Element> bestTags;

    /*
    @Selector(value = "#single-pager")
    private Element mangaThumbsContainer;
    @Selector(value = "#dgwt-jg-2")
    private Element galleryThumbsContainer; // same for zone
    @Selector(value = "#gallery-2")
    private Element bestThumbsContainer;
     */

    @Selector(value = ".reading-content script")
    private Element mangaPagesContainer;
    @Selector(value = "#dgwt-jg-2 a")
    private List<Element> galleryPages; // same for zone
    @Selector(value = ".unite-gallery img")
    private List<Element> galleryPages2;
    @Selector(value = "#gallery-2 a")
    private List<Element> bestPages;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.PORNCOMIX);

        title = title.trim();
        if (title.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
        content.setTitle(StringHelper.removeNonPrintableChars(title.trim()));

        content.setUrl(url);
        content.setCoverImageUrl(coverUrl);

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

        String artist = "";
        if (content.getUrl().contains("/manga")) {
            String[] titleParts = title.split("-");
            artist = titleParts[0].trim();
        }

        /*
        if (mangaThumbsContainer != null) result.setQtyPages(mangaThumbsContainer.childNodeSize());
        else if (galleryThumbsContainer != null) result.setQtyPages(galleryThumbsContainer.childNodeSize());
        else if (bestThumbsContainer != null) result.setQtyPages(bestThumbsContainer.childNodeSize());
         */

        AttributeMap attributes = new AttributeMap();
        attributes.add(new Attribute(AttributeType.ARTIST, artist, artist, Site.PORNCOMIX));
        if (mangaTags != null && !mangaTags.isEmpty())
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, mangaTags, false, Site.PORNCOMIX);
        else if (galleryTags != null && !galleryTags.isEmpty())
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, galleryTags, false, Site.PORNCOMIX);
        else if (zoneTags != null && !zoneTags.isEmpty())
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, zoneTags, false, Site.PORNCOMIX);
        else if (bestTags != null && !bestTags.isEmpty())
            ParseHelper.parseAttributes(attributes, AttributeType.TAG, bestTags, false, Site.PORNCOMIX);
        content.putAttributes(attributes);

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
