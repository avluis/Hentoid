package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.MultpornParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class MultpornContent extends BaseContentParser {
    @Selector(value = "head link[rel=shortlink]", attr = "href", defValue = "")
    private String shortlink;
    @Selector(value = "#page-title", defValue = "")
    private String title;
    @Selector(value = "head meta[name=dcterms.date]", attr = "content", defValue = "")
    private String publishingDate;
    @Selector(value = "head script")
    private List<Element> headScripts;

    @Selector(value = ".links a[href^='/characters']")
    private List<Element> characterTags;
    @Selector(value = ".links a[href^='/hentai']")
    private List<Element> seriesTags1;
    @Selector(value = ".links a[href^='/comics']")
    private List<Element> seriesTags2;
    @Selector(value = ".links a[href^='/authors']")
    private List<Element> artistsTags;
    @Selector(value = ".links a[href^='/category']")
    private List<Element> tags;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.MULTPORN);
        if (url.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        content.setUrl(url.replace(Site.MULTPORN.getUrl(), ""));
        if (!title.isEmpty()) {
            content.setTitle(StringHelper.removeNonPrintableChars(title));
        } else content.setTitle(NO_TITLE);

        String[] shortlinkParts = shortlink.split("/");
        content.setUniqueSiteId(shortlinkParts[shortlinkParts.length - 1]);

        if (!publishingDate.isEmpty()) // e.g. 2018-11-12T20:04-05:00
            content.setUploadDate(Helper.parseDatetimeToEpoch(publishingDate, "yyyy-MM-dd'T'HH:mmXXX"));

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.CHARACTER, characterTags, false, Site.MULTPORN);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, seriesTags1, false, Site.MULTPORN);
        ParseHelper.parseAttributes(attributes, AttributeType.SERIE, seriesTags2, false, Site.MULTPORN);
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artistsTags, false, Site.MULTPORN);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.MULTPORN);
        content.putAttributes(attributes);

        String juiceboxRequestUrl = MultpornParser.getJuiceboxRequestUrl(headScripts);
        try {
            List<String> imagesUrls = MultpornParser.getImagesUrls(juiceboxRequestUrl, url);
            if (!imagesUrls.isEmpty()) {
                content.setCoverImageUrl(imagesUrls.get(0));
                if (updateImages) {
                    content.setImageFiles(ParseHelper.urlsToImageFiles(imagesUrls, imagesUrls.get(0), StatusContent.SAVED));
                    content.setQtyPages(imagesUrls.size());
                }
            }
        } catch (IOException e) {
            Timber.w(e);
            return new Content().setStatus(StatusContent.IGNORED);
        }

        return content;
    }
}
