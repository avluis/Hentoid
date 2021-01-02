package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class ImhentaiContent implements ContentParser {
    @Selector(value = "div.left_cover img", attr = "src")
    private String cover;
    @Selector(value = "div.right_details h1", defValue = "")
    private String title;
    @Selector("li.pages")
    private String pages;
    @Selector(value = "ul.galleries_info a[href*='/artist']")
    private List<Element> artists;
    @Selector(value = "ul.galleries_info a[href*='/group']")
    private List<Element> circles;
    @Selector(value = "ul.galleries_info a[href*='/tag']")
    private List<Element> tags;
    @Selector(value = "ul.galleries_info a[href*='/language']")
    private List<Element> languages;
    @Selector(value = "ul.galleries_info a[href*='/category']")
    private List<Element> categories;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.IMHENTAI);

        result.setUrl(url.replace(Site.IMHENTAI.getUrl(), "").replace("/gallery", ""));

        result.setCoverImageUrl(cover);
        String str = !title.isEmpty() ? Helper.removeNonPrintableChars(title) : "";
        str = ParseHelper.removeTextualTags(str);
        result.setTitle(str);
        str = pages.replace("Pages", "").replace("pages", "").replace(":", "").trim();
        result.setQtyPages(Integer.parseInt(str));

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.IMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, false, Site.IMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.IMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, Site.IMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, Site.IMHENTAI);
        result.addAttributes(attributes);

        return result;
    }
}
