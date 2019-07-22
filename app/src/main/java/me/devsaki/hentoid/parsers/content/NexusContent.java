package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class NexusContent implements ContentParser {
    @Selector(value = "head [property=og:url]", attr = "content", defValue = "")
    private String galleryUrl;
    @Selector(value = "head [property=og:image]", attr = "content")
    private String coverUrl;
    @Selector("h1.title")
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
    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.NEXUS);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        result.setUrl(theUrl.replace(Site.NEXUS.getUrl() + "/view", ""));
        result.setCoverImageUrl(coverUrl);
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true, Site.NEXUS);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.NEXUS);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, true, Site.NEXUS);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, language, true, Site.NEXUS);

        result.addAttributes(attributes);

        result.setQtyPages(thumbs.size()); // We infer there are as many thumbs as actual book pages on the gallery summary webpage

        return result;
    }
}
