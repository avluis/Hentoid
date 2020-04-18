package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.exception.ParseException;

import static me.devsaki.hentoid.util.HttpHelper.getOnlineDocument;

public class PorncomixParser extends BaseParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        Element mangaPagesContainer = doc.selectFirst(".reading-content script");
        List<Element> galleryPages = doc.select("#dgwt-jg-2 a"); // same for zone
        List<Element> bestPages = doc.select("#gallery-2 a");

        return parseImages(mangaPagesContainer, galleryPages, bestPages);
    }

    public static List<String> parseImages(Element mangaPagesContainer, List<Element> galleryPages, List<Element> bestPages) {
        List<String> result = new ArrayList<>();

        if (mangaPagesContainer != null) {
            String pageArray = Helper.replaceUnicode(mangaPagesContainer.childNode(0).toString().replace("\"", "").replace("\\/", "/"));
            String[] pages = pageArray.substring(pageArray.indexOf('[') + 1, pageArray.lastIndexOf(']')).split(",");
            result.addAll(Arrays.asList(pages));
        } else if (galleryPages != null && !galleryPages.isEmpty()) {
            for (Element e : galleryPages)
                result.add(e.attr("href"));
        } else if (bestPages != null && !bestPages.isEmpty()) {
            String imgUrl;
            String imgExt;
            for (Element e : bestPages) {
                imgUrl = e.attr("src");
                imgExt = HttpHelper.getExtensionFromUri(imgUrl);
                imgUrl = imgUrl.substring(0, imgUrl.lastIndexOf('-')) + "." + imgExt;
                result.add(imgUrl);
            }
        }

        return result;
    }
}
