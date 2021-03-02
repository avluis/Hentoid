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

public class ToonilyContent extends BaseContentParser {
    @Selector(value = "head [property=og:image]", attr = "content")
    private String coverUrl;
    @Selector(value = ".breadcrumb a")
    private List<Element> breadcrumbs;
    @Selector(value = ".author-content a")
    private List<Element> author;
    @Selector(value = ".artist-content a")
    private List<Element> artist;


    public Content update(@NonNull final Content content, @Nonnull String url) {
        content.setSite(Site.TOONILY);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        content.setUrl(url.replace(Site.TOONILY.getUrl(), ""));
        content.setCoverImageUrl(coverUrl);
        String title = NO_TITLE;
        if (breadcrumbs != null && !breadcrumbs.isEmpty()) {
            title = Helper.removeNonPrintableChars(breadcrumbs.get(breadcrumbs.size() - 1).text());
        }
        content.setTitle(title);
        content.populateUniqueSiteId();

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artist, false, Site.TOONILY);
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, author, false, Site.TOONILY);
        content.putAttributes(attributes);

        return content;
    }
}
