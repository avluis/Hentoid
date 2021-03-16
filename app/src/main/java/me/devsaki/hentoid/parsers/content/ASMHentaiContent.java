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


    public Content update(@NonNull final Content content, @Nonnull String url) {
        String theUrl = canonicalUrl.isEmpty() ? url : canonicalUrl;
        if (theUrl.isEmpty())
            return new Content().setSite(Site.ASMHENTAI).setStatus(StatusContent.IGNORED);

        content.setSite(theUrl.toLowerCase().contains("comics") ? Site.ASMHENTAI_COMICS : Site.ASMHENTAI);
        if (galleryUrl.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        // Remove the host from the URL
        galleryUrl = galleryUrl.substring(galleryUrl.indexOf("/gallery/") + 8, galleryUrl.length() - 2);
        content.setUrl(galleryUrl);
        content.setCoverImageUrl("https:" + coverUrl);

        content.setTitle(Helper.removeNonPrintableChars(title));
        content.setQtyPages(Integer.parseInt(pages.get(0).replace("Pages: ", "")));

        AttributeMap attributes = new AttributeMap();

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, "badge", Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, "badge", Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, "badge", Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, "badge", Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, "badge", Site.ASMHENTAI);

        content.putAttributes(attributes);

        return content;
    }
}
