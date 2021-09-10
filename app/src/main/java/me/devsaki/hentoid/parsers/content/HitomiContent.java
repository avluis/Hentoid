package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.Collections;
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

public class HitomiContent extends BaseContentParser {
    @Selector(value = "h1 a[href*='/reader/']", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector(value = ".cover img", attr = "src", defValue = "")
    private String coverUrl;
    @Selector(value = "h1 a[href*='/reader/']", defValue = NO_TITLE)
    private String title;
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

    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);
        if (coverUrl.isEmpty() && title.equals(NO_TITLE))
            return new Content().setStatus(StatusContent.IGNORED);

        content.setSite(Site.HITOMI);
        content.setUrl(theUrl.replace(Site.HITOMI.getUrl(), "").replace("/reader", ""));
        content.setCoverImageUrl("https:" + coverUrl);
        content.setTitle(StringHelper.removeNonPrintableChars(title));

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characters, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, Site.HITOMI);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, Site.HITOMI);

        content.putAttributes(attributes);

        if (updateImages) content.setImageFiles(Collections.emptyList());

        return content;
    }
}
