package me.devsaki.hentoid.parsers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.LogHelper;

import static me.devsaki.hentoid.enums.Site.HITOMI;

/**
 * Created by neko on 08/07/2015.
 * Handles parsing of content from hitomi.la
 * </p>
 * TODO: Address or wait for fix on Jack bug (debug builds only):
 * https://code.google.com/p/android/issues/detail?id=82691
 */
public class HitomiParser {
    private static final String TAG = LogHelper.makeLogTag(HitomiParser.class);

    public static Content parseContent(String urlString) throws IOException {
        Document doc = Jsoup.connect(urlString).get();
        Elements content = doc.select(".content");
        if (content.size() > 0) {
            String coverImageUrl = "https:" + content.select(".cover img").attr("src");
            Element info = content.select(".gallery").first();
            Element titleElement = info.select("h1").first();
            String url = titleElement.select("a").first().attr("href").replace("/reader", "");
            String title = titleElement.text();

            AttributeMap attributes = new AttributeMap();
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

            return new Content()
                    .setTitle(title)
                    .setUrl(url)
                    .setCoverImageUrl(coverImageUrl)
                    .setAttributes(attributes)
                    .setQtyPages(pages)
                    .setStatus(StatusContent.SAVED)
                    .setSite(HITOMI);
        }

        return null;
    }

    private static void parseAttributes(AttributeMap map, AttributeType type, Elements elements) {
        for (Element a : elements) {
            map.add(new Attribute()
                    .setType(type)
                    .setUrl(a.attr("href"))
                    .setName(a.text()));
        }
    }

    public static List<String> parseImageList(Content content) {
        String html;
        List<String> imgUrls = null;
        try {
            html = HttpClientHelper.call(content.getReaderUrl());
            Document doc = Jsoup.parse(html);
            Elements imgElements = doc.select(".img-url");
            imgUrls = new ArrayList<>(imgElements.size());

            for (Element element : imgElements) {
                imgUrls.add("https:" + element.text());
            }
        } catch (Exception e) {
            LogHelper.e(TAG, "Could not connect to the requested resource: ", e);
        }
        LogHelper.d(TAG, imgUrls);

        return imgUrls;
    }
}
