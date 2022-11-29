package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.HdPornComicsParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class HdPornComicsContent extends BaseContentParser {
    @Selector(value = "h1", defValue = NO_TITLE)
    private String title;
    @Selector(value = "head meta[property=\"article:published_time\"]", attr = "content")
    private String uploadDate;
    @Selector(value = "head link[rel='shortlink']", attr = "href")
    private String shortlink;
    @Selector(value = "#imgBox img")
    private Element cover;
    @Selector(value = "#infoBox a[href*='/artist/']")
    private List<Element> artists;
    @Selector(value = "#infoBox a[href*='/tag/']")
    private List<Element> tags;
    @Selector(value = "figure a picture img")
    private List<Element> pages;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.HDPORNCOMICS);
        if (url.isEmpty()) return content.setStatus(StatusContent.IGNORED);

        content.setRawUrl(url);
        content.setTitle(StringHelper.removeNonPrintableChars(title));

        if (shortlink != null && !shortlink.isEmpty()) {
            int equalIndex = shortlink.lastIndexOf('=');
            if (equalIndex > -1) content.setUniqueSiteId(shortlink.substring(equalIndex + 1));
        }
        content.setUploadDate(Helper.parseDateToEpoch(uploadDate, "yyyy-MM-dd'T'HH:mm:ssXXX")); // e.g. 2021-08-08T20:53:49+00:00

        String coverUrl = ParseHelper.getImgSrc(cover);
        content.setCoverImageUrl(coverUrl);

        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.HDPORNCOMICS);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.HDPORNCOMICS);
        content.putAttributes(attributes);

        if (updateImages) {
            List<String> imgs = HdPornComicsParser.parseImages(pages);
            content.setImageFiles(ParseHelper.urlsToImageFiles(imgs, coverUrl, StatusContent.SAVED));
            content.setQtyPages(imgs.size() - 1);  // Don't count the cover
        }

        return content;
    }
}
