package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

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

public class HentaiCafeContent extends BaseContentParser {
    @Selector(value = "div.x-main.full article", attr = "id", defValue = "")
    private String galleryUrl;
    @Selector(value = "div.entry-content img", attr = "src")
    private Element coverImg;
    @Selector(value = "div.x-column.x-sm.x-1-2.last h3", defValue = "<no title>")
    private String title;
    @Selector(value = "div.x-column.x-sm.x-1-2.last a[href*='/artist/']")
    private List<Element> artists;
    @Selector(value = "div.x-column.x-sm.x-1-2.last a[href*='/tag/']")
    private List<Element> tags;


    public Content update(@NonNull final Content content, @Nonnull String url) {
        content.setSite(Site.HENTAICAFE);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty()) return content.setStatus(StatusContent.IGNORED);

        content.setUrl(theUrl.replace("post-", "/?p="));

        String coverUrl = coverImg.attr("src");
        if (coverUrl.isEmpty()) coverUrl = coverImg.attr("data-cfsrc"); // Cloudflare-served image
        content.setCoverImageUrl(coverUrl);

        content.setTitle(Helper.removeNonPrintableChars(title));

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.HENTAICAFE);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.HENTAICAFE);

        content.addAttributes(attributes);

        return content;
    }
}
