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

public class PururinContent {
    @Selector(value = "head [property=og:url]", attr = "content", defValue = "")
    private String galleryUrl;
    @Selector(value = "head [property=og:image]", attr = "content")
    private String coverUrl;
    @Selector("div.title")
    private List<String> title;
    @Selector("table.table-gallery-info tr td")
    private List<String> pages;
    @Selector(value = "table.table-gallery-info a[href*='/tags/artist']")
    private List<Element> artists;
    @Selector(value = "table.table-gallery-info a[href*='/tags/circle']")
    private List<Element> circles;
    @Selector(value = "table.table-gallery-info a[href*='/tags/contents']")
    private List<Element> tags;
    @Selector(value = "table.table-gallery-info a[href*='/tags/parody']")
    private List<Element> series;
    @Selector(value = "table.table-gallery-info a[href*='/tags/character']")
    private List<Element> characters;
    @Selector(value = "table.table-gallery-info a[href*='/tags/language']")
    private List<Element> languages;
    @Selector(value = "table.table-gallery-info a[href*='/tags/category']")
    private List<Element> categories;


    private String getProtocol() {
        return galleryUrl.startsWith("https") ? "https" : "http";
    }

    public Content toContent() {
        Content result = new Content();

        result.setSite(Site.PURURIN);
        if (galleryUrl.isEmpty()) return result;

        result.setUrl(galleryUrl.replace(getProtocol() + "://pururin.io/gallery", ""));
        result.setCoverImageUrl(getProtocol() + ":" + coverUrl);
        result.setTitle(title.size() > 0 ? title.get(0) : "");
        int qtyPages = 0;
        boolean pagesFound = false;
        for (String s : pages) {
            if (pagesFound) {
                qtyPages = Integer.parseInt(ParseHelper.removeBrackets(s));
                break;
            }
            if (s.trim().equalsIgnoreCase("pages")) pagesFound = true;
        }
        result.setQtyPages(qtyPages);

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
