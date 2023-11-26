package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;

public class MrmParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();
        processedUrl = content.getGalleryUrl();

        List<Pair<String, String>> headers = fetchHeaders(content);

        // 1. Scan the gallery page for chapter URLs
        // NB : We can't just guess the URLs by starting to 1 and increment them
        // because the site provides "subchapters" (e.g. 4.6, 2.5)
        List<String> chapterUrls = new ArrayList<>();
        Document doc = getOnlineDocument(content.getGalleryUrl(), headers, Site.MRM.useHentoidAgent(), Site.MRM.useWebviewAgent());
        if (doc != null) {
            Element chapterContainer = doc.select("div.entry-pagination").first();
            if (chapterContainer != null) {
                for (Element e : chapterContainer.children()) {
                    if (e.hasClass("current"))
                        chapterUrls.add(content.getGalleryUrl()); // current chapter
                    else if (e.hasAttr("href")) chapterUrls.add(e.attr("href"));
                }
            }
        }
        if (chapterUrls.isEmpty()) chapterUrls.add(content.getGalleryUrl()); // "one-shot" book
        progressStart(content, null, chapterUrls.size());

        // 2. Open each chapter URL and get the image data until all images are found
        for (String url : chapterUrls) {
            result.addAll(parseImages(url, null, headers));
            if (processHalted.get()) break;
            progressPlus();
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        if (!result.isEmpty()) content.setCoverImageUrl(result.get(0));

        progressComplete();
        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull String chapterUrl, String downloadParams, List<Pair<String, String>> headers) throws Exception {
        if (null == headers) headers = fetchHeaders(chapterUrl, downloadParams);
        if (processedUrl.isEmpty()) processedUrl = chapterUrl;

        Document doc = getOnlineDocument(processedUrl, headers, Site.MRM.useHentoidAgent(), Site.MRM.useWebviewAgent());
        if (doc != null) {
            List<Element> images = doc.select(".entry-content img");
            return Stream.of(images).map(ParseHelper::getImgSrc).filterNot(String::isEmpty).toList();
        }
        return Collections.emptyList();
    }
}
