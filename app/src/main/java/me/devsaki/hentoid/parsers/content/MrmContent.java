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


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.MRM);
        if (url.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        result.setUrl(url.replace(Site.MRM.getUrl(), "").split("/")[0]);
        if (!title.isEmpty()) {
            result.setTitle(Helper.removeNonPrintableChars(title));
        } else result.setTitle("<no title>");

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, Site.MRM);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, Site.MRM);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, genres, false, Site.MRM);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.MRM);
        result.addAttributes(attributes);

        return result;
    }
}
