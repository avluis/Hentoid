package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.KskParser;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class KskContent extends BaseContentParser {
    @Selector(value = "#cover img")
    private Element cover;
    @Selector(value = "#metadata h1", defValue = "")
    private String title;
    @Selector("#metadata time")
    private List<Element> uploadDate;
    @Selector("#metadata a")
    private List<Element> information;
    @Selector(value = "#previews>main>div img")
    private List<Element> thumbs;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.KSK);
        if (url.isEmpty()) return content.setStatus(StatusContent.IGNORED);

        content.setRawUrl(url);

        content.populateUniqueSiteId();
        if (cover != null) content.setCoverImageUrl(ParseHelper.getImgSrc(cover));
        content.setTitle(StringHelper.removeNonPrintableChars(title));

        if (null == information || information.isEmpty()) return content;

        int qtyPages = thumbs.size();
        AttributeMap attributes = new AttributeMap();

        if (uploadDate != null && !uploadDate.isEmpty()) {
            String timestamp = uploadDate.get(0).attr("data-timestamp").trim();
            if (!timestamp.isEmpty() && StringHelper.isNumeric(timestamp))
                content.setUploadDate(Long.parseLong(timestamp));
        }

        for (Element e : information) {
            if (!e.children().isEmpty()) { // Tags
                String link = e.attr("href").toLowerCase().trim();
                String name = e.child(0).text().trim();

                if (link.startsWith("/artists"))
                    parseAttribute(e, attributes, AttributeType.ARTIST, name);
                if (link.startsWith("/circles"))
                    parseAttribute(e, attributes, AttributeType.CIRCLE, name);
                if (link.startsWith("/parodies"))
                    parseAttribute(e, attributes, AttributeType.SERIE, name);
                if (link.startsWith("/tags"))
                    parseAttribute(e, attributes, AttributeType.TAG, name);
            }
        }
        content.putAttributes(attributes);

        if (updateImages) {
            List<String> pagesUrl = KskParser.parseImages(thumbs);

            content.setImageFiles(ParseHelper.urlsToImageFiles(pagesUrl, content.getCoverImageUrl(), StatusContent.SAVED));
            content.setQtyPages(qtyPages);
        }

        return content;
    }

    private static void parseAttribute(
            @NonNull Element element,
            @NonNull AttributeMap map,
            @NonNull AttributeType type,
            final String nam
    ) {
        String name = StringHelper.removeNonPrintableChars(nam);
        if (name.isEmpty() || name.equals("-") || name.equals("/")) return;
        Attribute attribute = new Attribute(type, name, element.attr("href"), Site.KSK);
        map.add(attribute);
    }
}
