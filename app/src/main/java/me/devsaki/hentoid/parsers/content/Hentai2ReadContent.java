package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class Hentai2ReadContent extends BaseContentParser {
    @Selector(value = "div.img-container img[src*=cover]")
    private Element cover;
    @Selector(value = "span[property^=name]")
    private List<Element> title;
    @Selector("ul.list li")
    private List<Element> properties;
    @Selector(value = "li.dropdown a[data-mid]", attr = "data-mid", defValue = "")
    private String uniqueId;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.HENTAI2READ);
        if (url.isEmpty()) return content.setStatus(StatusContent.IGNORED);

        content.setUrl(url.replace(Site.HENTAI2READ.getUrl(), ""));
        if (cover != null)
            content.setCoverImageUrl(ParseHelper.getImgSrc(cover));
        if (!title.isEmpty()) {
            String titleStr = title.get(title.size() - 1).text();
            content.setTitle(!titleStr.isEmpty() ? StringHelper.removeNonPrintableChars(titleStr) : "");
        } else content.setTitle(NO_TITLE);
        content.setUniqueSiteId(uniqueId);

        AttributeMap attributes = new AttributeMap();
        String currentProperty = "";
        if (properties != null)
            for (Element e : properties) {
                for (Element child : e.children()) {
                    if (child.nodeName().equals("b"))
                        currentProperty = child.text().toLowerCase().trim();
                    else if (child.nodeName().equals("a")) {
                        switch (currentProperty) {
                            /*
                            Apparently, we can't trust that figure as some books have less actual chapters/pages than advertised
                            case "page":
                                String qtyPagesStr = child.text().substring(0, child.text().indexOf(" page")).replace(",", "");
                                qtyPages = Integer.parseInt(qtyPagesStr);
                                break;
                             */
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
        content.putAttributes(attributes);

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
