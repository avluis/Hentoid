package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class ASMHentaiContent extends BaseContentParser {
    @Selector(value = "div.cover a img")
    private Element cover;
    @Selector(value = "div.info h1:first-child", defValue = NO_TITLE)
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


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String theUrl = canonicalUrl.isEmpty() ? url : canonicalUrl;
        if (theUrl.isEmpty())
            return new Content().setSite(Site.ASMHENTAI).setStatus(StatusContent.IGNORED);

        content.setSite(theUrl.toLowerCase().contains("comics") ? Site.ASMHENTAI_COMICS : Site.ASMHENTAI);
        content.setRawUrl(theUrl);

        if (cover != null)
            content.setCoverImageUrl("https:" + ParseHelper.getImgSrc(cover));

        content.setTitle(StringHelper.removeNonPrintableChars(title));

        AttributeMap attributes = new AttributeMap();

        final String BADGE_CONST = "badge";
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, BADGE_CONST, Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, BADGE_CONST, Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, BADGE_CONST, Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, BADGE_CONST, Site.ASMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, BADGE_CONST, Site.ASMHENTAI);

        content.putAttributes(attributes);

        if (updateImages) {
            content.setQtyPages(Integer.parseInt(pages.get(0).replace("Pages: ", "")));
            content.setImageFiles(Collections.emptyList());
        }

        return content;
    }
}
