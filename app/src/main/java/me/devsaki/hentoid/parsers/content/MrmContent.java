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


    public Content update(@NonNull final Content content, @Nonnull String url) {
        content.setSite(Site.MRM);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        content.setUrl(url.replace(Site.MRM.getUrl(), "").split("/")[0]);
        if (!title.isEmpty()) {
            content.setTitle(Helper.removeNonPrintableChars(title));
        } else content.setTitle("<no title>");

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, Site.MRM);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, Site.MRM);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, genres, false, Site.MRM);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.MRM);
        content.addAttributes(attributes);

        return content;
    }
}
