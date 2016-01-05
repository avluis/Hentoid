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
            String urlTEMP = elements.select("div#cover").select("a").attr("href").replace("1/","");
            String mangaUrlTEMP = Site.NHENTAI.getUrl() + urlTEMP;
            String coverImageUrlTEMP = elements.select("div#cover").select("img").attr("src");
            HashMap<AttributeType, List<Attribute>> attributesTEMP = new HashMap<AttributeType, List<Attribute>>();
            Integer qtyPagesTEMP = doc.select("a.gallerythumb").size();

            

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
            attribute.setUrl("");
            attribute.setName("");
            attributes.add(attribute);
        }
        return attributes;
    }
}
