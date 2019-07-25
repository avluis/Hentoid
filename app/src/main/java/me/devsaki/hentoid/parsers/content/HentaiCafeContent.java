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
import pl.droidsonroids.jspoon.annotation.Selector;

public class HentaiCafeContent implements ContentParser {
    @Selector(value = "div.x-main.full article", attr = "id", defValue = "")
    private String galleryUrl;
    @Selector(value = "div.entry-content img", attr = "src")
    private Element coverImg;
    @Selector("div.x-column.x-sm.x-1-2.last h3")
    private String title;
    @Selector(value = "div.x-column.x-sm.x-1-2.last a[href*='/artist/']")
    private List<Element> artists;
    @Selector(value = "div.x-column.x-sm.x-1-2.last a[href*='/tag/']")
    private List<Element> tags;


    public Content toContent(@Nonnull String url) {
        Content result = new Content();

        result.setSite(Site.HENTAICAFE);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty()) return result.setStatus(StatusContent.IGNORED);

        result.setUrl(theUrl.replace("post-", "/?p="));

        String coverUrl = coverImg.attr("src");
        if (coverUrl.isEmpty()) coverUrl = coverImg.attr("data-cfsrc"); // Cloudflare-served image
        result.setCoverImageUrl(coverUrl);

        result.setTitle(title);
        result.setQtyPages(-1);

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true, Site.HENTAICAFE);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.HENTAICAFE);

        result.addAttributes(attributes);

        return result;
    }
}
