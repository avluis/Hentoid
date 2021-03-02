package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.exception.ParseException;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

public class MusesParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();

        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        List<Element> thumbs = doc.select(".gallery img");

        String[] thumbParts;
        for (Element e : thumbs) {
            String src = e.attr("data-src");
            if (null == src || src.isEmpty()) continue;

            thumbParts = src.split("/");
            if (thumbParts.length > 3) {
                thumbParts[2] = "fl"; // Large dimensions; there's also a medium variant available (fm)
                String imgUrl = Site.MUSES.getUrl() + "/" + thumbParts[1] + "/" + thumbParts[2] + "/" + thumbParts[3];
                result.add(imgUrl);
            }
        }

        return result;
    }
}
