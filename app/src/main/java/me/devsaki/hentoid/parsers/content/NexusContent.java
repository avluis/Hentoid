package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class NexusContent extends BaseContentParser {
    @Selector(value = "head [property=og:url]", attr = "content", defValue = "")
    private String galleryUrl;
    @Selector(value = "body img[src*='/cover']", attr = "src", defValue = "")
    private String coverUrl;
    @Selector(value = "h1.title", defValue = "<no title>")
    private String title;
    @Selector("table.view-page-details")
    private List<Element> information;
    @Selector(value = "table.view-page-details a[href*='q=artist']")
    private List<Element> artists;
    @Selector(value = "table.view-page-details a[href*='q=tag']")
    private List<Element> tags;
    @Selector(value = "table.view-page-details a[href*='q=parody']")
    private List<Element> series;
    @Selector(value = "table.view-page-details a[href*='q=language']")
    private List<Element> language;
    @Selector(value = ".card-image img")
    private List<Element> thumbs;

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url) {
        content.setSite(Site.NEXUS);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty() || null == thumbs)
            return new Content().setStatus(StatusContent.IGNORED);

        content.setUrl(theUrl.replace(Site.NEXUS.getUrl() + "/view", ""));
        content.setCoverImageUrl(coverUrl);
        content.setTitle(StringHelper.removeNonPrintableChars(title));

        AttributeMap attributes = new AttributeMap();

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.NEXUS);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.NEXUS);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, Site.NEXUS);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, language, false, Site.NEXUS);

        content.putAttributes(attributes);

        content.setQtyPages(thumbs.size()); // We infer there are as many thumbs as actual book pages on the gallery summary webpage

        return content;
    }
}
