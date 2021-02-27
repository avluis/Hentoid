package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import pl.droidsonroids.jspoon.annotation.Selector;

import static me.devsaki.hentoid.enums.Site.TSUMINO;

public class TsuminoContent extends BaseContentParser {
    @Selector(value = "div.book-page-cover a", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = "img.book-page-image", attr = "src", defValue = "")
    private String coverUrl;
    @Selector(value = "div#Title", defValue = "")
    private String title;
    @Selector(value = "div#Pages", defValue = "")
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


    public Content update(@NonNull final Content content, @Nonnull String url) {
        content.setSite(TSUMINO);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        content.setUrl(theUrl.replace("/Read/Index", ""));
        if (!coverUrl.startsWith("http")) coverUrl = TSUMINO.getUrl() + coverUrl;
        content.setCoverImageUrl(coverUrl);
        content.setTitle(Helper.removeNonPrintableChars(title));
        content.setQtyPages((pages.length() > 0) ? Integer.parseInt(pages) : 0);

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, TSUMINO);
        content.addAttributes(attributes);

        return content;
    }
}
