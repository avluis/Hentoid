package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;

public class PorncomixParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        processedUrl = content.getGalleryUrl();

        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl(), null, Site.PORNCOMIX.useHentoidAgent(), Site.PORNCOMIX.useWebviewAgent());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        List<String> result = parseComixImages(content, doc);
        if (result.isEmpty()) result = parseXxxToonImages(content, doc);

        return result;
    }

    public List<String> parseComixImages(@NonNull Content content, @NonNull Document doc) throws Exception {
        List<Element> pagesNavigator = doc.select(".select-pagination select option");
        if (pagesNavigator.isEmpty()) return Collections.emptyList();

        List<String> pageUrls = Stream.of(pagesNavigator).map(e -> e.attr("data-redirect")).withoutNulls().distinct().toList();
        List<String> result = new ArrayList<>();
        progressStart(content, null, pageUrls.size());

        for (String pageUrl : pageUrls) {
            if (processHalted.get()) break;
            doc = getOnlineDocument(pageUrl, null, Site.PORNCOMIX.useHentoidAgent(), Site.PORNCOMIX.useWebviewAgent());
            if (doc != null) {
                Element imageElement = doc.selectFirst(".entry-content img");
                if (imageElement != null) result.add(ParseHelper.getImgSrc(imageElement));
            }

            progressPlus();
        }

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        progressComplete();
        return result;
    }

    public List<String> parseXxxToonImages(@NonNull Content content, @NonNull Document doc) throws Exception {
        List<Element> pages = doc.select("figure.msnry_items a");
        if (pages.isEmpty()) return Collections.emptyList();

        return Stream.of(pages).map(e -> e.attr("href")).withoutNulls().distinct().toList();
    }
}
