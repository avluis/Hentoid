package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Element;

import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class HentaiCafeContent {
    @Selector(value = "div.x-main.full article", attr="id", defValue = "")
    private String galleryUrl;
    @Selector(value = "div.x-column.x-sm.x-1-2 img", attr="src")
    private String coverUrl;
    @Selector("div.x-column.x-sm.x-1-2.last h3")
    private String title;
    @Selector(value = "div.x-column.x-sm.x-1-2.last a[href*='/artist/']")
    private List<Element> artists;
    @Selector(value = "div.x-column.x-sm.x-1-2.last a[href*='/tag/']")
    private List<Element> tags;


    public Content toContent()
    {
        Content result = new Content();

        result.setSite(Site.HENTAICAFE);
        if (galleryUrl.isEmpty()) return result;

        result.setUrl(galleryUrl.replace("post-", "/?p="));
        result.setCoverImageUrl(coverUrl);
        result.setTitle(title);
        result.setQtyPages(-1);

        AttributeMap attributes = new AttributeMap();
        result.setAttributes(attributes);

        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true);

        result.populateAuthor();
        result.setStatus(StatusContent.SAVED);

        return result;
    }
}
