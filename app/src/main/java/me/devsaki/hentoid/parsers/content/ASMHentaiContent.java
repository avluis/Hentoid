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

public class ASMHentaiContent implements ContentParser {
    @Selector(value = "head [rel=canonical]", attr = "href", defValue = "")
    private String pageUrl;
    @Selector(value = "div.cover a", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = "div.cover a img", attr = "src")
    private String coverUrl;
    @Selector("div.info h1:first-child")
    private String title;
    @Selector("div.pages h3")
    private List<String> pages;
    @Selector(value = "div.info div.tags a[href^='/artist']")
    private List<Element> artists;
    @Selector(value = "div.info div.tags a[href^='/tag']")
    private List<Element> tags;
    @Selector(value = "div.info div.tags a[href^='/parod']")
    private List<Element> series;
    @Selector(value = "div.info div.tags a[href^='/character']")
    private List<Element> characters;
    @Selector(value = "div.info div.tags a[href^='/language']")
    private List<Element> languages;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();
        String theUrl = pageUrl.isEmpty() ? url : pageUrl;
        if (theUrl.isEmpty())
            return result.setSite(Site.ASMHENTAI).setStatus(StatusContent.IGNORED);

        result.setSite(theUrl.toLowerCase().contains("comics") ? Site.ASMHENTAI_COMICS : Site.ASMHENTAI);
        if (galleryUrl.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        result.setUrl(galleryUrl.substring(0, galleryUrl.length() - 2).replace("/gallery", ""));
        result.setCoverImageUrl("https:" + coverUrl);

        result.setTitle(title);
        result.setQtyPages(Integer.parseInt(pages.get(0).replace("Pages: ", "")));

        AttributeMap attributes = new AttributeMap();

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true, Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, true, Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, true, Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, true, Site.ASMHENTAI);

        result.addAttributes(attributes);

        return result;
    }
}
