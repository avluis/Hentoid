package me.devsaki.hentoid.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

<<<<<<< HEAD
import me.devsaki.hentoid.components.CustomMultiMap;
=======
import me.devsaki.hentoid.util.AttributeMap;
>>>>>>> parent of 3f46b56... Revert "Replaced HashMap usages with new AttributeMap class"
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;

/**
 * Created by neko on 08/07/2015.
 */
public class HitomiParser {

    public static Content parseContent(String html) {
        Content result = null;
        Document doc = Jsoup.parse(html);
        Elements content = doc.select(".content");
        if (content.size() > 0) {
            String coverImageUrlTEMP = "https:" + content.select(".cover").select("img").attr("src");
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
                } else if (td.html().startsWith("Serie")) {
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

            result = new Content(
                    titleTEMP,
                    urlTEMP,
                    coverImageUrlTEMP,
                    attributes,
                    pages,
                    Site.HITOMI
            );
        }
        return result;
    }

    private static void parseAttributes(AttributeMap map, AttributeType type, Elements elements) {
        for (Element a : elements) {
            Attribute attribute = new Attribute();
            attribute.setType(type);
            attribute.setUrl(a.attr("href"));
            attribute.setName(a.text());
            map.add(attribute);
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