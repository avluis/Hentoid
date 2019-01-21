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

import static me.devsaki.hentoid.enums.Site.TSUMINO;

public class TsuminoContent {
    @Selector(value = "div.book-page-cover a", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = "img.book-page-image", attr = "src", defValue = "")
    private String coverUrl;
    @Selector("div#Title")
    private String title;
    @Selector(value="div#Pages", defValue = "")
    private String pages;
    @Selector(value = "div#Artist a")
    private List<Element> artists;
    @Selector(value = "div#Group a")
    private List<Element> circles;
    @Selector(value = "div#Tag a")
    private List<Element> tags;
    @Selector(value = "div#Parody a")
    private List<Element> series;
    @Selector(value = "div#Character a")
    private List<Element> characters;
    @Selector(value = "div#Category a")
    private List<Element> categories;


    public Content toContent() {
        Content result = new Content();

        result.setSite(Site.TSUMINO);
        if (galleryUrl.isEmpty()) return result;

        result.setUrl(galleryUrl.replace("/Read/View", ""));
        result.setCoverImageUrl(TSUMINO.getUrl() + coverUrl);
        result.setTitle(title);
        result.setQtyPages((pages.length() > 0) ? Integer.parseInt(pages) : 0);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, true);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, true);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, true);

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}
