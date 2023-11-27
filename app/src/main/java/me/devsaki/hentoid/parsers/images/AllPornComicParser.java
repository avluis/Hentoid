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

public class AllPornComicParser extends BaseImageListParser {

    @Override
    protected boolean isChapterUrl(@NonNull String url) {
        return Stream.of(url.split("/")).filterNot(String::isEmpty).count() > 4;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();
        processedUrl = content.getGalleryUrl();

        List<Pair<String, String>> headers = fetchHeaders(content);

        // 1. Scan the gallery page for chapter URLs
        List<String> chapterUrls = new ArrayList<>();
        Document doc = getOnlineDocument(content.getGalleryUrl(), headers, Site.ALLPORNCOMIC.useHentoidAgent(), Site.ALLPORNCOMIC.useWebviewAgent());
        if (doc != null) {
            List<Element> chapters = doc.select("[class^=wp-manga-chapter] a");
            for (Element e : chapters) {
                String link = e.attr("href");
                if (!chapterUrls.contains(link))
                    chapterUrls.add(link); // Make sure we're not adding duplicates
            }
        }
        Collections.reverse(chapterUrls); // Put the chapters in the correct reading order

        progressStart(content, null, chapterUrls.size());

        // 2. Open each chapter URL and get the image data until all images are found
        for (String url : chapterUrls) {
            result.addAll(parseImages(url, null, headers));
            if (processHalted.get()) break;
            progressPlus();
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        progressComplete();
        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull String chapterUrl, String downloadParams, List<Pair<String, String>> headers) throws Exception {
        if (null == headers) headers = fetchHeaders(chapterUrl, downloadParams);
        if (processedUrl.isEmpty()) processedUrl = chapterUrl;

        Document doc = getOnlineDocument(processedUrl, headers, Site.ALLPORNCOMIC.useHentoidAgent(), Site.ALLPORNCOMIC.useWebviewAgent());
        if (doc != null) {
            List<Element> images = doc.select("[class^=page-break] img");
            return Stream.of(images).map(ParseHelper::getImgSrc).filterNot(String::isEmpty).toList();
        }
        return Collections.emptyList();
    }
}
