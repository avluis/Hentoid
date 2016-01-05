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
            String mangaUrlTEMP = Site.NHENTAI.getUrl() + urlTEMP;
            String coverImageUrlTEMP = elements.select("div#cover").select("img").attr("src");
            Integer qtyPagesTEMP = doc.select("a.gallerythumb").size();

            HashMap<AttributeType, List<Attribute>> attributesTEMP = new HashMap<AttributeType, List<Attribute>>();

            Elements baseElements = elements.select("div#info");
            Log.d(TAG, "ARTISTS");
            Elements artistsElements = baseElements.select("div.field-name:containsOwn(Artists:)").select("a.tag");
            attributesTEMP.put(AttributeType.ARTIST, parseAttributes(artistsElements, AttributeType.ARTIST));
            Log.d(TAG, "LANGUAGE");
            Elements languageElements = baseElements.select("div.field-name:containsOwn(Language:)").select("a.tag");
            attributesTEMP.put(AttributeType.LANGUAGE, parseAttributes(languageElements, AttributeType.LANGUAGE));
            Log.d(TAG, "TAGS");
            Elements tagElements = baseElements.select("div.field-name:containsOwn(Tags:)").select("a.tag");
            attributesTEMP.put(AttributeType.TAG, parseAttributes(tagElements, AttributeType.TAG));
            Log.d(TAG, "CIRCLE");
            Elements circleElements = baseElements.select("div.field-name:containsOwn(Groups:)").select("a.tag");
            attributesTEMP.put(AttributeType.CIRCLE, parseAttributes(circleElements, AttributeType.CIRCLE));
            Log.d(TAG, "CATEGORY");
            Elements categoryElements = baseElements.select("div.field-name:containsOwn(Category:)").select("a.tag");
            attributesTEMP.put(AttributeType.CATEGORY, parseAttributes(categoryElements, AttributeType.CATEGORY));


            result = new Content(
                    titleTEMP,
                    urlTEMP,
                    mangaUrlTEMP,
                    coverImageUrlTEMP,
                    attributesTEMP,
                    qtyPagesTEMP,
                    null,
                    true,
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
            Log.d(TAG, "url: " + element.attr("href"));
            Log.d(TAG, "name: " + element.ownText());
        }
        return attributes;
    }

    //Incomplete
    public static List<String> parseImageList(String html) {
        Document doc = Jsoup.parse(html);
        List<String> imagesUrl = new ArrayList<>();
        return imagesUrl;
    }
}
