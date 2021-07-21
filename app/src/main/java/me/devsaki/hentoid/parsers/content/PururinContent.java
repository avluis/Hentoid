package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class PururinContent extends BaseContentParser {
    @Selector(value = "head [property=og:image]", attr = "content")
    private String coverUrl;
    @Selector(value = "div.title", defValue = "")
    private List<String> title;
    @Selector("table.table-gallery-info tr td")
    private List<String> pages;
    @Selector(value = "table.table-gallery-info a[href*='/tags/artist']")
    private List<Element> artists;
    @Selector(value = "table.table-gallery-info a[href*='/tags/circle']")
    private List<Element> circles;
    @Selector(value = "table.table-gallery-info a[href*='/tags/content']")
    private List<Element> tags;
    @Selector(value = "table.table-gallery-info a[href*='/tags/parody']")
    private List<Element> series;
    @Selector(value = "table.table-gallery-info a[href*='/tags/character']")
    private List<Element> characters;
    @Selector(value = "table.table-gallery-info a[href*='/tags/language']")
    private List<Element> languages;
    @Selector(value = "table.table-gallery-info a[href*='/tags/category']")
    private List<Element> categories;


    private String getProtocol(String url) {
        return url.startsWith("https") ? "https" : "http";
    }

    public Content update(@NonNull final Content content, @Nonnull String url) {
        content.setSite(Site.PURURIN);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

        content.setUrl(url.replace(getProtocol(url) + "://pururin.to/gallery", ""));
        content.setCoverImageUrl(getProtocol(url) + ":" + coverUrl);
        content.setTitle(!title.isEmpty() ? StringHelper.removeNonPrintableChars(title.get(0)) : "");
        int qtyPages = 0;
        boolean pagesFound = false;
        for (String s : pages) {
            if (pagesFound) {
                qtyPages = Integer.parseInt(ParseHelper.removeBrackets(s));
                break;
            }
            if (s.trim().equalsIgnoreCase("pages")) pagesFound = true;
        }
        content.setQtyPages(qtyPages);

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.PURURIN);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, false, Site.PURURIN);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.PURURIN);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, Site.PURURIN);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, Site.PURURIN);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, Site.PURURIN);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, Site.PURURIN);
        content.putAttributes(attributes);

        return content;
    }
}
