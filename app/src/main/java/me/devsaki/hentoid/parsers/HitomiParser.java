package me.devsaki.hentoid.parsers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.HttpClientHelper;
import timber.log.Timber;

/**
 * Created by neko on 08/07/2015.
 * Handles parsing of content from hitomi.la
 */
public class HitomiParser extends BaseParser {

    // Reproduction of the Hitomi.la Javascript to find the hostname of the image server (see subdomain_from_url@reader.js)
    private final static int NUMBER_OF_FRONTENDS = 2;
    private final static String HOSTNAME_SUFFIX = "a";
    private final static char HOSTNAME_PREFIX_BASE = 97;

    @Override
    protected Content parseContent(Document doc) {
        Content result = null;

        Elements content = doc.select(".content");
        if (content.size() > 0) {
            result = new Content();

            String coverImageUrl = "https:" + content.select(".cover img").attr("src");
            result.setCoverImageUrl(coverImageUrl);
            Element info = content.select(".gallery").first();
            Element titleElement = info.select("h1").first();
            String url = titleElement.select("a").first().attr("href").replace("/reader", "");
            result.setUrl(url);
            String title = titleElement.text();
            result.setTitle(title);

            AttributeMap attributes = new AttributeMap();
            result.setAttributes(attributes);
            parseAttributes(attributes, AttributeType.ARTIST, info.select("h2").select("a"));

            Elements rows = info.select("tr");

            for (Element element : rows) {
                Element td = element.select("td").first();
                if (td.html().startsWith("Group")) {
                    parseAttributes(attributes, AttributeType.CIRCLE, element.select("a"));
                } else if (td.html().startsWith("Series")) {
                    parseAttributes(attributes, AttributeType.SERIE, element.select("a"));
                } else if (td.html().startsWith("Character")) {
                    parseAttributes(attributes, AttributeType.CHARACTER, element.select("a"));
                } else if (td.html().startsWith("Tags")) {
                    parseAttributes(attributes, AttributeType.TAG, element.select("a"));
                } else if (td.html().startsWith("Language")) {
                    parseAttributes(attributes, AttributeType.LANGUAGE, element.select("a"));
                } else if (td.html().startsWith("Type")) {
                    parseAttributes(attributes, AttributeType.CATEGORY, element.select("a"));
                }
            }
            int pages = doc.select(".thumbnail-container").size();

            result.setQtyPages(pages)
                    .setSite(Site.HITOMI);
        }

        return result;
    }


    @Override
    protected List<String> parseImages(Content content) throws Exception {
        List<String> result = new ArrayList<>();

        String url = content.getReaderUrl();
        String html = HttpClientHelper.call(url);
        Timber.d("Parsing: %s", url);
        Document doc = Jsoup.parse(html);
        Elements imgElements = doc.select(".img-url");
        // New Hitomi image URLs starting from june 2018
        //  If book ID is even, starts with 'aa'; else starts with 'ba'
        int referenceId = Integer.parseInt(content.getUniqueSiteId()) % 10;
        if (1 == referenceId) referenceId = 0; // Yes, this is what Hitomi actually does (see common.js)
        String imageHostname = Character.toString((char) (HOSTNAME_PREFIX_BASE + (referenceId % NUMBER_OF_FRONTENDS) )) + HOSTNAME_SUFFIX;

        for (Element element : imgElements) {
            result.add("https:" + element.text().replace("//g.", "//" + imageHostname + "."));
        }

        return result;
    }
}
