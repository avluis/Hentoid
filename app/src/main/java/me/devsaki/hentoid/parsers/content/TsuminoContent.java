package me.devsaki.hentoid.parsers.content;

import static me.devsaki.hentoid.enums.Site.TSUMINO;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class TsuminoContent extends BaseContentParser {
    @Selector(value = "div.book-page-cover a", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = "img.book-page-image")
    private Element cover;
    @Selector(value = "div#Title", defValue = "")
    private String title;
    @Selector(value = "div#Uploaded", defValue = "")
    private String uploadDate;
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


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(TSUMINO);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        content.setUrl(theUrl.replace("/Read/Index", ""));
        String coverUrl = (cover != null) ? ParseHelper.getImgSrc(cover) : "";
        if (!coverUrl.startsWith("http")) coverUrl = TSUMINO.getUrl() + coverUrl;
        content.setCoverImageUrl(coverUrl);
        content.setTitle(StringHelper.removeNonPrintableChars(title));

        content.setUploadDate(Helper.parseDateToEpoch(uploadDate, "yyyy MMMM dd")); // e.g. 2021 December 13

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, TSUMINO);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, TSUMINO);
        content.putAttributes(attributes);

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages((pages.length() > 0) ? Integer.parseInt(pages) : 0);
        }

        return content;
    }
}
