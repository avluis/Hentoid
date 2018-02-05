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

import static me.devsaki.hentoid.enums.Site.PURURIN;

/**
 * Created by robb_w on 01/31/2018.
 * Handles parsing of content from pururin.io
 */
public class PururinParser {
    private static final String TAG = LogHelper.makeLogTag(PururinParser.class);

    public static Content parseContent(String urlString) throws IOException {
        Document doc = Jsoup.connect(urlString).get();

        Elements content = doc.select("div.gallery-info");
        if (content.size() > 0) {
            String url = doc.select("div.cover")
                    .select("a")
                    .attr("href")
                    .replace("http://pururin.io/read", "");

            String coverUrl = doc.select("div.cover")
                    .select("a")
                    .select("img")
                    .attr("src");

            String title = doc.select("div.title").first().text();

            Elements rows = content.select("tr");
            AttributeMap attributes = new AttributeMap();

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
                    pages = Integer.parseInt(element.select("td").get(1).text().replace(" Pages",""));
                }
            }

            String author = "";
            if (attributes.containsKey(AttributeType.ARTIST) && attributes.get(AttributeType.ARTIST).size() > 0) author = attributes.get(AttributeType.ARTIST).get(0).getName();
            if (author.equals("")) // Try and get Circle
            {
                if (attributes.containsKey(AttributeType.CIRCLE) && attributes.get(AttributeType.CIRCLE).size() > 0) author = attributes.get(AttributeType.CIRCLE).get(0).getName();
            }

            return new Content()
                    .setTitle(title)
                    .setAuthor(author)
                    .setUrl(url)
                    .setCoverImageUrl(coverUrl)
                    .setAttributes(attributes)
                    .setQtyPages(pages)
                    .setStatus(StatusContent.SAVED)
                    .setSite(PURURIN);
        }

        return null;
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

    public static List<String> parseImageList(Content content) {
        int pages = content.getQtyPages();
        String readerUrl = content.getReaderUrl();
        List<String> imgUrls = new ArrayList<>();

        Document doc;
        Elements js;
        String ext;
        int startPos, endPos;

        try {
            //doc = Jsoup.connect(readerUrl).get();
            doc = Jsoup.parse(HttpClientHelper.call(content.getReaderUrl()));
            js = doc.select("script");

            for (Element a : js) {
                if (a.toString().contains("\"image\":")) // That's the one
                {
                    String[] parts = a.toString().split(",");

                    for (String s : parts)
                    {
                        if (s.startsWith("\"image\":"))
                        {
                            startPos = s.indexOf("http");
                            endPos = s.indexOf("\"}");
                            imgUrls.add(s.substring(startPos,endPos).replace("\\/","/"));
                        }
                    }

                    break;
                }
            }

            /*
            String imgUrl = "http:" +
                    doc.select("div.images-holder")
                            .select("img")
                            .attr("src");

            ext = imgUrl.substring(imgUrl.length() - 4);

            for (int i = 0; i < pages; i++) {
                String img = imgUrl.substring(0, imgUrl.length() - 4) + (i + 1) + ext;
                imgUrls.add(img);
            }
            */

        } catch (Exception e) {
            LogHelper.e(TAG, e, "Error while attempting to connect to: " + readerUrl);
        }
        LogHelper.d(TAG, imgUrls);

        return imgUrls;
    }
}
