package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class HitomiContent {
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


    public Content toContent()
    {
        Content result = new Content();
        if (galleryUrl.isEmpty()) return result;

        result.setSite(Site.HITOMI);
        result.setUrl(galleryUrl.replace("/reader", ""));
        result.setCoverImageUrl("https:"+ coverUrl);
        result.setTitle(title);
        result.setQtyPages(pages.size());

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, true);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, true);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, true);

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}
