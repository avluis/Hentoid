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

public class Hentai2ReadContent extends BaseContentParser {
    @Selector(value = "div.img-container img[src*=cover]", attr = "src")
    private String coverUrl;
    @Selector(value = "span[property^=name]")
    private List<Element> title;
    @Selector("ul.list li")
    private List<Element> properties;
    @Selector(value = "li.dropdown a[data-mid]", attr = "data-mid", defValue = "")
    private String uniqueId;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.HENTAI2READ);
        if (url.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        result.setUrl(url.replace(Site.HENTAI2READ.getUrl(), ""));
        result.setCoverImageUrl(coverUrl);
        if (!title.isEmpty()) {
            String titleStr = title.get(title.size() - 1).text();
            result.setTitle(!titleStr.isEmpty() ? Helper.removeNonPrintableChars(titleStr) : "");
        } else result.setTitle("<no title>");
        result.setUniqueSiteId(uniqueId);

        AttributeMap attributes = new AttributeMap();
        String currentProperty = "";
        for (Element e : properties) {
            for (Element child : e.children()) {
                if (child.nodeName().equals("b"))
                    currentProperty = child.text().toLowerCase().trim();
                else if (child.nodeName().equals("a")) {
                    switch (currentProperty) {
                        case "page":
                            String qtyPages = child.text().substring(0, child.text().indexOf(" page")).replace(",", "");
                            result.setQtyPages(Integer.parseInt(qtyPages));
                            break;
                        case "parody":
                            ParseHelper.parseAttribute(attributes, AttributeType.SERIE, child, false, Site.HENTAI2READ);
                            break;
                        case "artist":
                            ParseHelper.parseAttribute(attributes, AttributeType.ARTIST, child, false, Site.HENTAI2READ);
                            break;
                        case "language":
                            ParseHelper.parseAttribute(attributes, AttributeType.LANGUAGE, child, false, Site.HENTAI2READ);
                            break;
                        case "character":
                            ParseHelper.parseAttribute(attributes, AttributeType.CHARACTER, child, false, Site.HENTAI2READ);
                            break;
                        case "content":
                        case "category":
                            ParseHelper.parseAttribute(attributes, AttributeType.TAG, child, false, Site.HENTAI2READ);
                            break;
                        default:
                            // Other cases aren't interesting
                    }
                }
            }
        }
        result.addAttributes(attributes);

        return result;
    }
}
