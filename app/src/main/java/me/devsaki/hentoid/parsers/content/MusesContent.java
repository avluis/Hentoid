package me.devsaki.hentoid.parsers.content;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import pl.droidsonroids.jspoon.annotation.Selector;

public class MusesContent implements ContentParser {
    @Selector(value = "head [rel=canonical]", attr = "href", defValue = "")
    private String galleryUrl;
    @Selector("head title")
    private String title;
    @Selector(value = ".gallery img", attr = "data-src", defValue = "")
    private List<String> thumbs;

    @Nullable
    public Content toContent() {
        Content result = new Content();

        result.setSite(Site.MUSES);
        if (galleryUrl.isEmpty()) return result;
        if (0 == thumbs.size()) return result;

        result.setUrl(galleryUrl);
        result.setCoverImageUrl(Site.MUSES.getUrl() + thumbs.get(0));
        if (title.contains("|"))
            result.setTitle(title.substring(0, title.lastIndexOf("|") - 1));
        else
            result.setTitle(title);

        AttributeMap attributes = new AttributeMap();

        result.addAttributes(attributes);

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

        // TODO - tags are shown not on the album page but on the picture page (!)


        return result;
    }
}
