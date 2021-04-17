package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Helper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class FakkuContent extends BaseContentParser {
    @Selector(value = "head [property=og:url]", attr = "content", defValue = "")
    private String galleryUrl;
    @Selector(value = "head [property=og:image]", attr = "content")
    private String coverUrl;
    @Selector(value = ".content-name h1", defValue = "<no title>")
    private String title;
    @Selector(".content-right .row")
    private List<Element> information;
    @Selector(value = ".content-right a[href*='/artist']")
    private List<Element> artists;
    @Selector(value = ".content-right a[href*='/tag']")
    private List<Element> tags;
    @Selector(value = ".content-right a[href*='/serie']")
    private List<Element> series;
    @Selector(value = "a.button")
    private List<Element> greenButton;

    private final String[] BLOCKED_CONTENT_CAPTIONS = new String[]{"subscribe", "purchase", "buy", "order", "trial", "buy", "game", "install"};

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url) {
        if (greenButton != null) {
            // Check if book is available
            for (Element e : greenButton) {
                String text = e.text().toLowerCase();
                for (String c : BLOCKED_CONTENT_CAPTIONS)
                    if (text.contains(c))
                        return new Content().setStatus(StatusContent.IGNORED);
            }
        }

        content.setSite(Site.FAKKU2);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty()) return content.setStatus(StatusContent.IGNORED);

        content.setUrl(theUrl.replace(Site.FAKKU2.getUrl() + "/hentai/", ""));
        content.setCoverImageUrl(coverUrl);
        content.setTitle(Helper.removeNonPrintableChars(title));

        AttributeMap attributes = new AttributeMap();
        int qtyPages = 0;
        String elementName;
        if (information != null)
            for (Element e : information) {
                elementName = e.child(0).text().trim().toLowerCase();
                if (elementName.equals("language")) {
                    ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, e.child(1).children(), false, Site.FAKKU2); // Language elements are A links
                }
                if (elementName.equals("pages")) {
                    qtyPages = Integer.parseInt(e.child(1).text().toLowerCase().replace("pages", "").replace("page", "").trim());
                }
            }
        content.setQtyPages(qtyPages);

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.FAKKU2);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.FAKKU2);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, false, Site.FAKKU2);

        content.putAttributes(attributes);

        return content;
    }
}
