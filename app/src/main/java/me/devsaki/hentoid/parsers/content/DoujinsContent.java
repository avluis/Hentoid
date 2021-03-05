package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.parsers.images.DoujinsParser;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
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


    public Content update(@NonNull final Content content, @Nonnull String url) {
        content.setSite(Site.DOUJINS);
        if (url.isEmpty()) return content.setStatus(StatusContent.IGNORED);

        content.setUrl(url.replace(Site.DOUJINS.getUrl(), ""));

        if (breadcrumbs != null && !breadcrumbs.isEmpty()) {
            Element e = breadcrumbs.get(breadcrumbs.size() - 1);
            content.setTitle(Helper.removeNonPrintableChars(e.text()));
        }

        if (images != null && !images.isEmpty()) {
            // Cover = thumb from the 1st page
            String coverUrl = images.get(0).attr("data-thumb2");
            content.setCoverImageUrl(coverUrl);

            // Images
            int index = 0;
            List<ImageFile> imgs = new ArrayList<>();
            // Cover
            ImageFile cover = new ImageFile(index++, content.getCoverImageUrl(), StatusContent.SAVED, images.size());
            cover.setIsCover(true);
            imgs.add(cover);
            // Images
            for (Element e : images)
                imgs.add(new ImageFile(index++, e.attr("data-file"), StatusContent.SAVED, images.size()));
            content.setImageFiles(imgs);
        }

        List<String> imageUrls = DoujinsParser.parseImages(images);
        content.setQtyPages(imageUrls.size() - 1); // Don't count the cover
        content.setImageFiles(ParseHelper.urlsToImageFiles(imageUrls, content.getCoverImageUrl(), StatusContent.SAVED));

        // Deduplicate tags
        AttributeMap attributes = new AttributeMap();
        ParseHelper.parseAttributes(attributes, AttributeType.ARTIST, artists, false, Site.DOUJINS);
        ParseHelper.parseAttributes(attributes, AttributeType.TAG, tags, false, Site.DOUJINS);
        content.putAttributes(attributes);

        return content;
    }
}
