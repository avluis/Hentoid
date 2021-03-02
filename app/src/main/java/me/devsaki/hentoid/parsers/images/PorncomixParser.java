package me.devsaki.hentoid.parsers.images;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.network.HttpHelper;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

public class PorncomixParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        Element mangaPagesContainer = doc.selectFirst(".reading-content script");
        List<Element> galleryPages = doc.select("#dgwt-jg-2 a"); // same for zone
        List<Element> galleryPages2 = doc.select(".unite-gallery img"); // same for zone
        List<Element> bestPages = doc.select("#gallery-2 a");

        return parseImages(mangaPagesContainer, galleryPages, galleryPages2, bestPages);
    }

    public static List<String> parseImages(
            Element mangaPagesContainer,
            List<Element> galleryPages,
            List<Element> galleryPages2,
            List<Element> bestPages) {
        List<String> result = new ArrayList<>();

        if (mangaPagesContainer != null) {
            String pageArray = Helper.replaceEscapedChars(mangaPagesContainer.childNode(0).toString().replace("\"", "").replace("\\/", "/"));
            String[] pages = pageArray.substring(pageArray.indexOf('[') + 1, pageArray.lastIndexOf(']')).split(",");
            result.addAll(Stream.of(pages).distinct().toList()); // Preloaded images list may contain duplicates
        } else if (galleryPages != null && !galleryPages.isEmpty()) {
            for (Element e : galleryPages)
                result.add(e.attr("href"));
        } else if (galleryPages2 != null && !galleryPages2.isEmpty()) {
            for (Element e : galleryPages2)
                result.add(e.attr("data-image"));
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
