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

/**
 * Created by robb_w on 01/31/2018.
 * Handles parsing of content from pururin.io
 */
public class PururinParser extends BaseParser {

    @Override
    protected Content parseContent(Document doc) {
        Content result = new Content();

        Elements content = doc.select("div.gallery-info");
        if (content.size() > 0) {
            String url = doc.select("div.cover")
                    .select("a")
                    .attr("href")
                    .replace("http://pururin.io/read", "");
            result.setUrl(url);

            String coverUrl = doc.select("div.cover")
                    .select("a")
                    .select("img")
                    .attr("src");
            result.setCoverImageUrl(coverUrl);

            String title = doc.select("div.title").first().text();
            result.setTitle(title);

            Elements rows = content.select("tr");
            AttributeMap attributes = new AttributeMap();
            result.setAttributes(attributes);

            int pages = 0;

            for (Element element : rows) {
                Element td = element.select("td").first();
                if (td.html().startsWith("Artist")) {
                    parseAttributes(attributes, AttributeType.ARTIST, element.select("a"));
                } else if (td.html().startsWith("Circle")) {
                    parseAttributes(attributes, AttributeType.CIRCLE, element.select("a"));
                } else if (td.html().startsWith("Parody")) {
                    parseAttributes(attributes, AttributeType.SERIE, element.select("a"));
                } else if (td.html().startsWith("Character")) {
                    parseAttributes(attributes, AttributeType.CHARACTER, element.select("a"));
                } else if (td.html().startsWith("Contents")) {
                    parseAttributes(attributes, AttributeType.TAG, element.select("a"));
                } else if (td.html().startsWith("Language")) {
                    parseAttributes(attributes, AttributeType.LANGUAGE, element.select("a"));
                } else if (td.html().startsWith("Category")) {
                    parseAttributes(attributes, AttributeType.CATEGORY, element.select("a"));
                } else if (td.html().startsWith("Pages")) {
                    pages = Integer.parseInt(element.select("td").get(1).text().replace(" Pages", ""));
                }
            }

            result.setQtyPages(pages)
                    .setSite(Site.PURURIN);
        }

        return result;
    }


    @Override
    protected List<String> parseImages(Content content) throws Exception {
        List<String> result = new ArrayList<>();

        Document doc = Jsoup.connect(content.getReaderUrl()).get();
        Elements js = doc.select("script");
        int startPos, endPos;

        for (Element a : js) {
            if (a.toString().contains("\"image\":")) // That's the one
            {
                String[] parts = a.toString().split(",");

                for (String s : parts) {
                    if (s.startsWith("\"image\":")) {
                        startPos = s.indexOf("http");
                        endPos = s.indexOf("\"}");
                        result.add(s.substring(startPos, endPos).replace("\\/", "/"));
                    }
                }

                break;
            }
        }

        return result;
    }
}
