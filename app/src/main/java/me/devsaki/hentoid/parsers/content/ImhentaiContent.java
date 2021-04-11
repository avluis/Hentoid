package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class ImhentaiContent extends BaseContentParser {
    @Selector(value = "div.left_cover img")
    private Element cover;
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


    public Content update(@NonNull final Content content, @Nonnull String url) {
        content.setSite(Site.IMHENTAI);

        content.setUrl(url.replace(Site.IMHENTAI.getUrl(), "").replace("/gallery", ""));

        if (cover != null) {
            String coverUrl = cover.attr("src");
            if (coverUrl.isEmpty()) coverUrl = cover.attr("data-cfsrc"); // Cloudflare-served image
            content.setCoverImageUrl(coverUrl);
        }
        String str = !title.isEmpty() ? Helper.removeNonPrintableChars(title) : "";
        str = ParseHelper.removeTextualTags(str);
        content.setTitle(str);
        str = pages.replace("Pages", "").replace("pages", "").replace(":", "").trim();
        content.setQtyPages(Integer.parseInt(str));

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.IMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CIRCLE, circles, false, Site.IMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.IMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languages, false, Site.IMHENTAI);
        ParseHelper.parseAttributes(attributes, AttributeType.CATEGORY, categories, false, Site.IMHENTAI);
        content.putAttributes(attributes);

        return content;
    }
}
