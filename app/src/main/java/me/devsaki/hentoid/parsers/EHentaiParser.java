package me.devsaki.hentoid.parsers;

import android.util.Pair;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

public class EHentaiParser extends BaseParser {

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        List<String> result = new ArrayList<>();

        /*
         * 1- Detect the number of pages of the gallery
         *
         * 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
         *
         * 3- Open all pages and grab the URL of the displayed image
         */

        // 1- Detect the number of pages of the gallery
        Element e;
        List<Pair<String, String>> cookies = new ArrayList<>();
        cookies.add(new Pair<>("cookie","nw=1")); // nw=1 (always) avoids the Offensive Content popup (equivalent to clicking the "Never warn me again" link)
        Document doc = getOnlineDocument(content.getGalleryUrl(), cookies);
        if (doc != null) {
            Elements elements = doc.select("table.ptt a");
            if (null == elements || 0 == elements.size()) return result;

            int tabId = (1 == elements.size()) ? 0 : elements.size() - 2;
            int nbGalleryPages = Integer.parseInt(elements.get(tabId).text());

            // 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
            List<String> pageUrls = new ArrayList<>();

            fetchPageUrls(doc, pageUrls);

            if (nbGalleryPages > 1) {
                for (int i = 1; i < nbGalleryPages; i++) {
                    doc = getOnlineDocument(content.getGalleryUrl() + "/?p=" + i);
                    if (doc != null) fetchPageUrls(doc, pageUrls);
                }
            }

            // 3- Open all pages and grab the URL of the displayed image
            for (String s : pageUrls) {
                doc = getOnlineDocument(s);
                if (doc != null) {
                    elements = doc.select("img#img");
                    if (elements != null && elements.size() > 0) {
                        e = elements.first();
                        result.add(e.attr("src"));
                    }
                }
            }
        }

        return result;
    }

    private void fetchPageUrls(Document doc, List<String> pageUrls) {
        Elements imageLinks = doc.getElementsByClass("gdtm");

        for (Element e : imageLinks) {
            e = e.select("div").first().select("a").first();
            pageUrls.add(e.attr("href"));
        }
    }
}
