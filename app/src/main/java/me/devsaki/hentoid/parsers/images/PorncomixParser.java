package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;

public class PorncomixParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl(), null, Site.PORNCOMIX.useHentoidAgent(), Site.PORNCOMIX.useWebviewAgent());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        /*
        Element mangaPagesContainer = doc.selectFirst(".reading-content script");
        List<Element> galleryPages = doc.select("#dgwt-jg-2 a"); // same for zone
        List<Element> galleryPages2 = doc.select(".unite-gallery img"); // same for zone
        List<Element> bestPages = doc.select("#gallery-2 a");
         */

        List<Element> pagesNavigator = doc.select(".select-pagination select option");

        return parseImages2(content, pagesNavigator);
    }

    public static List<String> parseImages(
            Element mangaPagesContainer,
            List<Element> galleryPages,
            List<Element> galleryPages2,
            List<Element> bestPages) {
        List<String> result = new ArrayList<>();

        if (mangaPagesContainer != null) {
            String pageArray = StringHelper.replaceEscapedChars(mangaPagesContainer.childNode(0).toString().replace("\"", "").replace("\\/", "/"));
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

    public List<String> parseImages2(@NonNull Content content, @NonNull List<Element> pages) throws Exception {
        List<String> result = new ArrayList<>();

        List<String> pageUrls = Stream.of(pages).map(e -> e.attr("data-redirect")).withoutNulls().distinct().toList();

        progressStart(content.getId(), pageUrls.size());

        for (String pageUrl : pageUrls) {
            if (processHalted) break;
            Document doc = getOnlineDocument(pageUrl, null, Site.PORNCOMIX.useHentoidAgent(), Site.PORNCOMIX.useWebviewAgent());
            if (doc != null) {
                Element imageElement = doc.selectFirst(".entry-content img");
                if (imageElement != null) result.add(ParseHelper.getImgSrc(imageElement));
            }

            progressPlus();
        }

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted) throw new PreparationInterruptedException();

        progressComplete();

        return result;
    }
}
