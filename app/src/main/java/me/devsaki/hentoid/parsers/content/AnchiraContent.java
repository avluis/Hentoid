package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class AnchiraContent extends BaseContentParser {

    @Selector(value = "#metadata h2", defValue = NO_TITLE)
    private String title;

    @Selector(value = "#gallery img")
    private List<Element> imgs;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Hitomi now uses an empty template that is populated by Javascript -> parsing is entirely done by HitomiParser
        content.setSite(Site.ANCHIRA);

        content.setRawUrl(url);
        content.putAttributes(new AttributeMap());

        content.setTitle(title);

        if (!imgs.isEmpty()) content.setCoverImageUrl(ParseHelper.getImgSrc(imgs.get(0)));

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
