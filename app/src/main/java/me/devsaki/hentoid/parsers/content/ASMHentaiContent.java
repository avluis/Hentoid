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
import me.devsaki.hentoid.util.Helper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class ASMHentaiContent extends BaseContentParser {
    @Selector(value = "div.cover a", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = "div.cover a img", attr = "src")
    private String coverUrl;
    @Selector(value = "div.info h1:first-child", defValue = "<no title>")
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
        String theUrl = canonicalUrl.isEmpty() ? url : canonicalUrl;
        if (theUrl.isEmpty())
            return result.setSite(Site.ASMHENTAI).setStatus(StatusContent.IGNORED);

        result.setSite(theUrl.toLowerCase().contains("comics") ? Site.ASMHENTAI_COMICS : Site.ASMHENTAI);
        if (galleryUrl.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        // Remove the host from the URL
        galleryUrl = galleryUrl.substring(galleryUrl.indexOf("/gallery/") + 8, galleryUrl.length() - 2);
        result.setUrl(galleryUrl);
        result.setCoverImageUrl("https:" + coverUrl);

        result.setTitle(Helper.removeNonPrintableChars(title));
        result.setQtyPages(Integer.parseInt(pages.get(0).replace("Pages: ", "")));

        AttributeMap attributes = new AttributeMap();

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, "badge", Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, "badge", Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, "badge", Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, "badge", Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, "badge", Site.ASMHENTAI);

        result.addAttributes(attributes);

        return result;
    }
}
