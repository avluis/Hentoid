package me.devsaki.hentoid.parser;

import android.util.Log;

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
 * Created by Shiro on 1/5/2016.
 */
public class NhentaiParser {

    private final static String TAG = NhentaiParser.class.getName();

    public static Content parseContent(String html) {
        Content result = null;
        Document doc = Jsoup.parse(html);
        Elements elements = doc.select("div#bigcontainer");

        if (elements.size() > 0)
        {
            String titleTEMP = elements.select("div#info").select("h1").text();
            String urlTEMP = elements.select("div#cover").select("a").attr("href").replace("1/", "").replace("/g", "");
            String coverImageUrlTEMP = elements.select("div#cover").select("img").attr("src");
            Integer qtyPagesTEMP = doc.select("a.gallerythumb").size();

            HashMap<AttributeType, List<Attribute>> attributesTEMP = new HashMap<AttributeType, List<Attribute>>();

            Elements baseElements = elements.select("div#info");
            Elements artistsElements = baseElements.select("div.field-name:containsOwn(Artists:)").select("a.tag");
            attributesTEMP.put(AttributeType.ARTIST, parseAttributes(artistsElements, AttributeType.ARTIST));
            Elements languageElements = baseElements.select("div.field-name:containsOwn(Language:)").select("a.tag");
            attributesTEMP.put(AttributeType.LANGUAGE, parseAttributes(languageElements, AttributeType.LANGUAGE));
            Elements tagElements = baseElements.select("div.field-name:containsOwn(Tags:)").select("a.tag");
            attributesTEMP.put(AttributeType.TAG, parseAttributes(tagElements, AttributeType.TAG));
            Elements circleElements = baseElements.select("div.field-name:containsOwn(Groups:)").select("a.tag");
            attributesTEMP.put(AttributeType.CIRCLE, parseAttributes(circleElements, AttributeType.CIRCLE));
            Elements categoryElements = baseElements.select("div.field-name:containsOwn(Category:)").select("a.tag");
            attributesTEMP.put(AttributeType.CATEGORY, parseAttributes(categoryElements, AttributeType.CATEGORY));


            result = new Content(
                    titleTEMP,
                    urlTEMP,
                    coverImageUrlTEMP,
                    attributesTEMP,
                    qtyPagesTEMP,
                    Site.NHENTAI
            );
        }

        return result;
    }

    private static List<Attribute> parseAttributes(Elements elements, AttributeType attributeType) {
        List<Attribute> attributes = new ArrayList<>(elements.size());
        for (Element element : elements) {
            Attribute attribute = new Attribute();
            attribute.setType(attributeType);
            attribute.setUrl(element.attr("href"));
            attribute.setName(element.ownText());
            attributes.add(attribute);
        }
        return attributes;
    }

    //Incomplete
    public static List<String> parseImageList(String html, int qtyPages) {
        Document doc = Jsoup.parse(html);
        List<String> imagesUrl = new ArrayList<>();
        String imageUrlTemplate = "http:" + doc.select("section#image-container").select("img").attr("src").replace("1.jpg", "");

        for(Integer i = 1; i != qtyPages; i++)
        {
            imagesUrl.add(imageUrlTemplate + i.toString() + ".jpg");
        }
        return imagesUrl;
    }
}
