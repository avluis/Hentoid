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
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class Manhwa18Content extends BaseContentParser {
    @Selector(value = ".series-cover div div", attr = "style", defValue = "")
    private String cover;
    @Selector(value = ".series-name a", defValue = "")
    private Element title;
    @Selector(value = ".series-information a[href*=tac-gia]")
    private List<Element> artists;
    @Selector(value = ".series-information a[href*=genre]")
    private List<Element> tags;

    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.MANHWA18);

        content.setUrl(url.replace(Site.MANHWA18.getUrl(), "").replace("/gallery", ""));

        if (cover != null) {
            cover = cover.replace("background-image:", "")
                    .replace("url('", "")
                    .replace("')", "")
                    .replace(";", "")
                    .trim();
            content.setCoverImageUrl(cover);
        }

        String titleStr = StringHelper.removeNonPrintableChars(title.text());
        titleStr = ParseHelper.removeTextualTags(titleStr);
        content.setTitle(titleStr);

        if (updateImages) content.setImageFiles(Collections.emptyList());

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.MANHWA18);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, "badge", Site.MANHWA18);
        content.putAttributes(attributes);

        return content;
    }
}
