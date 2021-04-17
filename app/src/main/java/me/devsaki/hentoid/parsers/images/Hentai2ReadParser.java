package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

/**
 * Created by robb_w on 2020/05
 * Handles parsing of content from hentai2read.com
 */
public class Hentai2ReadParser extends BaseImageListParser {

    private static final String IMAGE_PATH = "https://static.hentaicdn.com/hentai";

    public static class H2RInfo {
        List<String> images;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();

        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        // 1. Scan the gallery page for chapter URLs
        // NB : We can't just guess the URLs by starting to 1 and increment them
        // because the site provides "subchapters" (e.g. 4.6, 2.5)
        List<String> chapterUrls = new ArrayList<>();
        Document doc = getOnlineDocument(content.getGalleryUrl(), headers, Site.HENTAI2READ.useHentoidAgent(), Site.HENTAI2READ.useWebviewAgent());
        if (doc != null) {
            List<Element> chapters = doc.select(".nav-chapters a[href^=" + content.getGalleryUrl() + "]");
            for (Element e : chapters) chapterUrls.add(e.attr("href"));
        }
        Collections.reverse(chapterUrls); // Put the chapters in the correct reading order

        progressStart(content.getUrl(), chapterUrls.size());

        // 2. Open each chapter URL and get the image data until all images are found
        for (String url : chapterUrls) {
            if (processHalted) break;
            doc = getOnlineDocument(url, headers, Site.HENTAI2READ.useHentoidAgent(), Site.HENTAI2READ.useWebviewAgent());
            if (doc != null) {
                List<Element> scripts = doc.select("script");
                for (Element e : scripts)
                    if (e.childNodeSize() > 0 && e.childNode(0).toString().contains("'images' :")) {
                        String jsonStr = e.childNode(0).toString().replace("\n", "").trim().replace("var gData = ", "").replace("};", "}");
                        H2RInfo info = JsonHelper.jsonToObject(jsonStr, H2RInfo.class);
                        for (String img : info.images) result.add(IMAGE_PATH + img);
                        break;
                    }
            }
            progressPlus();
        }
        progressComplete();

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted) throw new PreparationInterruptedException();

        return result;
    }
}
