package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class FakkuContent {
    @Selector(value = "head [property=og:url]", attr = "content", defValue = "")
    private String galleryUrl;
    @Selector(value = "head [property=og:image]", attr = "content")
    private String coverUrl;
    @Selector(".content-name h1")
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

    @Nullable
    public Content toContent() {
        if (greenButton != null) {
            // Check if book is available
            for (Element e : greenButton) {
                if (e.text().toLowerCase().contains("subscribe") || e.text().toLowerCase().contains("purchase"))
                    return null;
            }
        }

        Content result = new Content();

        result.setSite(Site.FAKKU2);
        if (galleryUrl.isEmpty()) return result;

        result.setUrl(galleryUrl.replace(Site.FAKKU2.getUrl() + "/hentai/", ""));
        result.setCoverImageUrl(coverUrl);
        result.setTitle(title);

        AttributeMap attributes = new AttributeMap();
        int qtyPages = 0;
        String elementName;
        for (Element e : information) {
            elementName = e.child(0).text().trim().toLowerCase();
            if (elementName.equals("language")) {
                ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, e.child(1).children(), true, Site.FAKKU2); // Language elements are A links
            }
            if (elementName.equals("pages")) {
                qtyPages = Integer.parseInt(e.child(1).text().toLowerCase().replace("pages", "").replace("page", "").trim());
            }
        }
        result.setQtyPages(qtyPages);

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true, Site.FAKKU2);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.FAKKU2);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, series, true, Site.FAKKU2);

        result.addAttributes(attributes);

        return result;
    }
}
