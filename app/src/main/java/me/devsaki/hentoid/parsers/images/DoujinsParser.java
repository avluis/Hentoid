package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.exception.ParseException;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

public class DoujinsParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        List<Element> images = doc.select("img.doujin");

        return parseImages(images);
    }

    public static List<String> parseImages(List<Element> images) {
        List<String> result = new ArrayList<>();

        if (images != null) for (Element e : images) result.add(e.attr("data-file"));

        return result;
    }
}
