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

public class AnchiraContent extends BaseContentParser {

    @Selector(value = "#metadata h2", defValue = NO_TITLE)
    private String title;

    @Selector(value = "#metadata span")
    private List<Element> extraData;

    @Selector(value = "#metadata a[href*='/?s=artist:']")
    private List<Element> artists;

    @Selector(value = "#metadata a[href*='/?s=parody:']")
    private List<Element> parodies;

    @Selector(value = "#metadata a[href*='/?s=tag:']")
    private List<Element> tags;

    @Selector(value = "#gallery img")
    private List<Element> imgs;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Hitomi now uses an empty template that is populated by Javascript -> parsing is entirely done by HitomiParser
        content.setSite(Site.ANCHIRA);
        content.setRawUrl(url);
        if (null == imgs) return content.setStatus(StatusContent.IGNORED);

        content.setTitle(title);

        if (!imgs.isEmpty()) content.setCoverImageUrl(ParseHelper.getImgSrc(imgs.get(0)));

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, true, Site.ANCHIRA);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, parodies, true, Site.ANCHIRA);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, true, Site.ANCHIRA);
        content.putAttributes(attributes);

        for (Element e : extraData) {
            String txt = e.text().toLowerCase();
            if (txt.contains("page")) {
                txt = StringHelper.keepDigits(txt);
                if (StringHelper.isNumeric(txt)) {
                    content.setQtyPages(Integer.parseInt(txt));
                    break;
                }
            }
        }

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
