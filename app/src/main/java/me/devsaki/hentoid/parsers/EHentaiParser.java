package me.devsaki.hentoid.parsers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

public class EHentaiParser extends BaseParser {

    @Override
    protected Content parseContent(Document doc) {
        // Nothing here; that part is directly handled in EHentaiActivity
        // NB : that will become the norm once a refactoring is done
        return new Content();
    }

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        List<String> result = new ArrayList<>();

        // TODO see ehViewer > GalleryDetailParser and GalleryPageParser
        /**
         * 1- Detect the number of pages of the gallery
         *
         * 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
         *
         * 3- Open all pages and grab the URL of the displayed image
         */

        // 1- Detect the number of pages of the gallery
        Element e;
        Document doc = Jsoup.connect(content.getGalleryUrl()).get();
        Elements elements = doc.select("table.ptt");
        if (null == elements || 0 == elements.size()) return result;

        e = elements.first();
        e = e.select("tbody").first().select("tr").first();
        int nbGalleryPages = e.children().size() - 2;

        // 2- Browse the gallery and fetch the URL for every page (since all of them have a different temporary key...)
        List<String> pageUrls = new ArrayList<>();

        fetchPageUrls(doc, pageUrls);

        if (nbGalleryPages > 1) {
            for (int i = 1; i < nbGalleryPages; i++) {
                doc = Jsoup.connect(content.getGalleryUrl() + "/?p=" + i).get();
                fetchPageUrls(doc, pageUrls);
            }
        }

        // 3- Open all pages and grab the URL of the displayed image
        for (String s : pageUrls)
        {
            doc = Jsoup.connect(s).get();
            elements = doc.select("img#img");
            if (elements != null && elements.size() > 0)
            {
                e = elements.first();
                result.add(e.attr("src"));
            }
        }

        return result;
    }

    private void fetchPageUrls(Document doc, List<String> pageUrls) {
        Elements imageLinks = doc.getElementsByClass("gdtm");

        for (Element e : imageLinks)
        {
            e = e.select("div").first().select("a").first();
            pageUrls.add(e.attr("href"));
        }
    }
}
