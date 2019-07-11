package me.devsaki.hentoid.parsers.content;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.HttpHelper;
import pl.droidsonroids.jspoon.annotation.Selector;
import timber.log.Timber;

public class MusesContent implements ContentParser {
    @Selector(value = "head [rel=canonical]", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector("head title")
    private String title;
    @Selector(value = ".gallery img", attr = "data-src", defValue = "")
    private List<String> thumbs;
    @Selector(value = ".gallery a", attr = "href", defValue = "")
    private List<String> thumbLinks;

    @Nullable
    public Content toContent(@Nonnull String url) {
        // Gallery pages are the only ones whose gallery links end with numbers
        // The others are album lists
        for (int i = 0; i < thumbLinks.size(); i++) {
            if (!thumbLinks.get(i).endsWith("/" + (i + 1))) return new Content().setStatus(StatusContent.IGNORED);
        }

        Content result = new Content();

        result.setSite(Site.MUSES);
        String theUrl = galleryUrl.isEmpty() ? url : galleryUrl;
        if (theUrl.isEmpty() || 0 == thumbs.size()) return result.setStatus(StatusContent.IGNORED);
        
        result.setUrl(theUrl.replace(Site.MUSES.getUrl(), ""));
        result.setCoverImageUrl(Site.MUSES.getUrl() + thumbs.get(0));
        if (title.contains("|"))
            result.setTitle(title.substring(0, title.lastIndexOf("|") - 1));
        else
            result.setTitle(title);

        result.setQtyPages(thumbs.size()); // We infer there are as many thumbs as actual book pages on the gallery summary webpage

        String[] thumbParts;
        int index = 1;
        List<ImageFile> images = new ArrayList<>();
        for (String s : thumbs) {
            thumbParts = s.split("/");
            if (thumbParts.length > 3) {
                thumbParts[2] = "fl"; // Large dimensions; there's also a medium variant available (fm)
                String imgUrl = Site.MUSES.getUrl() + "/" + thumbParts[1] + "/" + thumbParts[2] + "/" + thumbParts[3];
                images.add(new ImageFile(index, imgUrl, StatusContent.SAVED)); // We infer actual book page images have the same format as their thumbs
                index++;
            }
        }
        result.addImageFiles(images);

        // Tags are not shown on the album page, but on the picture page (!)
        AttributeMap attributes = new AttributeMap();
        try {
            Document doc = HttpHelper.getOnlineDocument(Site.MUSES.getUrl() + thumbLinks.get(0));
            if (doc != null) {
                Elements elements = doc.select(".album-tags a[href*='/search/tag']");
                if (elements.size() > 0)
                    ParseHelper.parseAttributes(attributes, AttributeType.TAG, elements, true, Site.MUSES);
            }
        } catch (IOException e) {
            Timber.e(e);
        }
        result.addAttributes(attributes);


        return result;
    }
}
