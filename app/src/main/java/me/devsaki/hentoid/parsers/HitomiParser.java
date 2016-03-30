package me.devsaki.hentoid.parsers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;

/**
 * Created by neko on 08/07/2015.
 * Handles parsing of content from hitomi.la
 */
public class HitomiParser {

    public static Content parseContent(String html) {
        Document doc = Jsoup.parse(html);
        Elements content = doc.select(".content");
        if (content.size() > 0) {
            String coverImageUrlTEMP = "https:" + content.select(".cover img").attr("src");
            Element info = content.select(".gallery").first();
            Element title = info.select("h1").first();
            String urlTEMP = title.select("a").first().attr("href").replace("/reader", "");
            String titleTEMP = title.text();

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
                    .setTitle(titleTEMP)
                    .setUrl(urlTEMP)
                    .setCoverImageUrl(coverImageUrlTEMP)
                    .setAttributes(attributes)
                    .setQtyPages(pages)
                    .setStatus(StatusContent.SAVED)
                    .setSite(Site.HITOMI);
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

    public static List<String> parseImageList(String html) {
        Document doc = Jsoup.parse(html);
        Elements imgs = doc.select(".img-url");
        List<String> imagesUrl = new ArrayList<>(imgs.size());
        for (Element element : imgs) {
            imagesUrl.add("https:" + element.text());
        }
        return imagesUrl;
    }
}