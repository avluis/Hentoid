package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

/**
 * Parser for Simply Hentai HTML content
 */
public class SimplyContent extends BaseContentParser {
    @Selector(value = "head [property=og:image]", attr = "content", defValue = "")
    private String coverUrl;
    @Selector(value = ".album-info h1", defValue = "")
    private String title;
    @Selector(value = ".album-info .col-5 div")
    private Element ulDateContainer;

    @Selector(value = ".album-info a[href*='/language/']")
    private List<Element> languageTags;
    @Selector(value = ".album-info a[href*='/character/']")
    private List<Element> characterTags;
    @Selector(value = ".album-info a[href*='/series/']")
    private List<Element> seriesTags;
    @Selector(value = ".album-info a[href*='/artist/']")
    private List<Element> artistsTags;
    @Selector(value = ".album-info a[href*='/tag/']")
    private List<Element> tags;


    public Content update(@NonNull final Content content, @NonNull String url, boolean updateImages) {
        content.setSite(Site.SIMPLY);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        content.setCoverImageUrl(coverUrl);

        content.setRawUrl(url);
        if (!title.isEmpty()) {
            content.setTitle(StringHelper.removeNonPrintableChars(title));
        } else content.setTitle(NO_TITLE);

        if (ulDateContainer != null)
            content.setUploadDate(Helper.parseDateToEpoch(ulDateContainer.ownText(), "M/d/yyyy")); // e.g. 10/23/2022, 12/8/2022

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.LANGUAGE, languageTags, false, Site.SIMPLY);
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characterTags, false, Site.SIMPLY);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, seriesTags, false, Site.SIMPLY);
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artistsTags, false, Site.SIMPLY);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.SIMPLY);
        content.putAttributes(attributes);

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
