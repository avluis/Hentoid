package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class MrmContent extends BaseContentParser {
    @Selector(value = "article h1", defValue = "")
    private String title;
    @Selector(".entry-header .entry-meta .entry-categories a")
    private List<Element> categories;
    @Selector(value = ".entry-header .entry-terms a[href*='/lang/']")
    private List<Element> languages;
    @Selector(value = ".entry-header .entry-terms a[href*='/genre/']")
    private List<Element> genres;
    @Selector(value = ".entry-header .entry-tags a[href*='/tag/']")
    private List<Element> tags;
    @Selector(value = ".entry-content img")
    private List<Element> images;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.MRM);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        content.setUrl(url.replace(Site.MRM.getUrl(), "").split("/")[0]);
        if (!title.isEmpty()) {
            title = StringHelper.removeNonPrintableChars(title.trim());
            content.setTitle(title);
        } else content.setTitle(NO_TITLE);

        if (!images.isEmpty()) content.setCoverImageUrl(ParseHelper.getImgSrc(images.get(0)));

        AttributeMap attributes = new AttributeMap();
        // On MRM, most titles are formatted "[Artist] Title" although there's no actual artist field on the book page
        if (title.startsWith("[")) {
            int closingBracketIndex = title.indexOf(']');
            if (closingBracketIndex > -1) {
                Attribute attribute = new Attribute(AttributeType.ARTIST, title.substring(1, closingBracketIndex), "", Site.MRM);
                attributes.add(attribute);
            }
        }
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, Site.MRM);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, Site.MRM);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, genres, false, Site.MRM);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.MRM);
        content.putAttributes(attributes);

        if (updateImages) content.setImageFiles(Collections.emptyList());

        return content;
    }
}
