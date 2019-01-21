package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class PandaContent {
    @Selector(value = "div#imgholder a img", attr = "src", defValue = "")
    private String coverUrl;
    @Selector("div#mangainfo div h1")
    private String title;
    @Selector(value = "select#pageMenu option", attr = "value")
    private List<String> pages;
    @Selector("div#mangainfo div h2 a")
    private Element series;


    public Content toContent() {
        Content result = new Content();
        result.setSite(Site.PANDA);
        if (coverUrl.isEmpty()) return result;

        if (pages.size() > 0) {
            result.setUrl(pages.get(0));
            result.setCoverImageUrl(coverUrl);
            result.setTitle(title);
            String lastPage = pages.get(pages.size() - 1);
            int qtyPages = Integer.parseInt(lastPage.substring(lastPage.lastIndexOf('/') + 1));
            result.setQtyPages(qtyPages);

            AttributeMap attributes = new AttributeMap();
            result.setAttributes(attributes);

            Attribute attribute = new Attribute(AttributeType.SERIE, series.text().substring(0, series.text().toLowerCase().lastIndexOf("manga") - 1), series.attr("href"));
            attributes.add(attribute);
        }

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}
