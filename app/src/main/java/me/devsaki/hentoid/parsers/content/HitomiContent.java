package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class HitomiContent implements ContentParser {
    @Selector(value = "h1 a[href*='/reader/']", attr="href", defValue = "")
    private String galleryUrl;
    @Selector(value = ".cover img", attr="src")
    private String coverUrl;
    @Selector("h1 a[href*='/reader/']")
    private String title;
    @Selector(".thumbnail-container")
    private List<Element> pages;
    @Selector(value = "div.gallery h2 a[href^='/artist']")
    private List<Element> artists;
    @Selector(value = "div.gallery tr a[href^='/group']")
    private List<Element> circles;
    @Selector(value = "div.gallery tr a[href^='/tag']")
    private List<Element> tags;
    @Selector(value = "div.gallery tr a[href^='/serie']")
    private List<Element> series;
    @Selector(value = "div.gallery tr a[href^='/character']")
    private List<Element> characters;
    @Selector(value = "div.gallery tr a[href^='/index-']")
    private List<Element> languages;
    @Selector(value = "div.gallery tr a[href^='/type']")
    private List<Element> categories;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        result.setSite(Site.HITOMI);
        result.setUrl(theUrl.replace("/reader", ""));
        result.setCoverImageUrl("https:"+ coverUrl);
        result.setTitle(title);
        result.setQtyPages(pages.size());

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, true, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, true, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, true, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, true, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, true, Site.HITOMI);

        result.addAttributes(attributes);

        return result;
    }
}
