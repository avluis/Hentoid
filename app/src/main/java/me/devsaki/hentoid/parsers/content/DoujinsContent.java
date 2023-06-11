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
import me.devsaki.hentoid.parsers.images.DoujinsParser;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.StringHelper;
import pl.droidsonroids.jspoon.annotation.Selector;

public class DoujinsContent extends BaseContentParser {
    @Selector(value = ".folder-title a")
    private List<Element> breadcrumbs;
    @Selector("img.doujin")
    private List<Element> images;
    @Selector(value = "a[href*='/artists/']")
    private List<Element> artists;
    @Selector(value = "a[href*='/searches?tag_id=']") // To deduplicate
    private List<Element> tags;
    @Selector(value = "#content .folder-message")
    private List<Element> contentInfo;


    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        content.setSite(Site.DOUJINS);
        if (url.isEmpty()) return content.setStatus(StatusContent.IGNORED);

        content.setRawUrl(url);

        if (breadcrumbs != null && !breadcrumbs.isEmpty()) {
            Element e = breadcrumbs.get(breadcrumbs.size() - 1);
            content.setTitle(StringHelper.removeNonPrintableChars(e.text()));
        }

        if (contentInfo != null && !contentInfo.isEmpty()) {
            for (Element e : contentInfo) {
                String txt = e.text().toLowerCase();
                if (txt.contains("•") && !txt.contains("translated")) { // e.g. March 16th, 2022 • 25 images
                    String[] parts = e.text().split("•");
                    content.setUploadDate(Helper.parseDateToEpoch(parts[0], "MMMM d',' yyyy"));
                    break;
                }
            }
        }

        if (images != null && !images.isEmpty()) {
            // Cover = thumb from the 1st page
            String coverUrl = images.get(0).attr("data-thumb2");
            content.setCoverImageUrl(coverUrl);

            if (updateImages) {
                List<String> imageUrls = DoujinsParser.parseImages(images);
                content.setQtyPages(imageUrls.size() - 1); // Don't count the cover
                content.setImageFiles(ParseHelper.urlsToImageFiles(imageUrls, content.getCoverImageUrl(), StatusContent.SAVED));
            }
        } else if (updateImages) {
            content.setQtyPages(0);
            content.setImageFiles(Collections.emptyList());
        }

        // Deduplicate tags
        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.DOUJINS);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.DOUJINS);
        content.putAttributes(attributes);

        return content;
    }
}
