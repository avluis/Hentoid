package me.devsaki.hentoid.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;

/**
 * Created by neko on 08/07/2015.
 */
public class HitomiParser {

    private final static String TAG = HitomiParser.class.getName();

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

            HashMap<AttributeType, List<Attribute>> attributes = new HashMap<>();
            attributes.put(AttributeType.ARTIST, parseAttributes(info.select("h2").select("a"), AttributeType.ARTIST));

            Elements rows = info.select("tr");

            for (Element element : rows) {
                Element td = element.select("td").first();
                if (td.html().startsWith("Group")) {
                    attributes.put(AttributeType.CIRCLE, parseAttributes(element.select("a"), AttributeType.CIRCLE));
                } else if (td.html().startsWith("Serie")) {
                    attributes.put(AttributeType.SERIE, parseAttributes(element.select("a"), AttributeType.SERIE));
                } else if (td.html().startsWith("Character")) {
                    attributes.put(AttributeType.CHARACTER, parseAttributes(element.select("a"), AttributeType.CHARACTER));
                } else if (td.html().startsWith("Tags")) {
                    attributes.put(AttributeType.TAG, parseAttributes(element.select("a"), AttributeType.TAG));
                } else if (td.html().startsWith("Language")) {
                    attributes.put(AttributeType.LANGUAGE, parseAttributes(element.select("a"), AttributeType.LANGUAGE));
                } else if (td.html().startsWith("Type")) {
                    attributes.put(AttributeType.CATEGORY, parseAttributes(element.select("a"), AttributeType.CATEGORY));
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

    private static List<Attribute> parseAttributes(Elements elements, AttributeType attributeType) {
        List<Attribute> attributes = new ArrayList<>(elements.size());
        for (Element a : elements) {
            Attribute attribute = new Attribute();
            attribute.setType(attributeType);
            attribute.setUrl(a.attr("href"));
            attribute.setName(a.text());
            attributes.add(attribute);
        }
        return attributes;
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