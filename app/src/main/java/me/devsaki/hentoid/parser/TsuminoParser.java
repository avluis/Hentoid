package me.devsaki.hentoid.parser;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.AttributeType;
import me.devsaki.hentoid.database.enums.Site;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.HttpClientHelper;

/**
 * Created by Shiro on 1/22/2016.
 */
public class TsuminoParser {

    public static Content parseContent(String html) {
        Document doc = Jsoup.parse(html);
        Elements content = doc.select("div.book-line");

        if (content.size() > 0) {
            String url = doc
                    .select("div.book-page-cover a")
                    .attr("href")
                    .replace("/Read/View", "");

            String coverUrl = Site.TSUMINO.getUrl()
                    + doc.select("img.book-page-image").attr("src");

            String title = content
                    .select(":has(div.book-info:containsOwn(Title))")
                    .select("div.book-data")
                    .text();

            int qtyPages =
                    Integer.parseInt(content
                            .select(":has(div.book-info:containsOwn(Pages))")
                            .select("div.book-data")
                            .text()
            );

            AttributeMap attributes = new AttributeMap();

            Elements artistElements = content
                    .select(":has(div.book-info:containsOwn(Artist))")
                    .select("a.book-tag");
            parseAttributes(attributes, AttributeType.ARTIST, artistElements);

            Elements tagElements = content
                    .select(":has(div.book-info:containsOwn(Tags))")
                    .select("a.book-tag");
            parseAttributes(attributes, AttributeType.TAG, tagElements);

            Elements serieElements = content
                    .select(":has(div.book-info:containsOwn(Parody))")
                    .select("a.book-tag");
            parseAttributes(attributes, AttributeType.SERIE, serieElements);

            Elements characterElements = content
                    .select(":has(div.book-info:containsOwn(Characters))")
                    .select("a.book-tag");
            parseAttributes(attributes, AttributeType.CHARACTER, characterElements);


            return new Content(
                    title,
                    url,
                    coverUrl,
                    attributes,
                    qtyPages,
                    Site.TSUMINO
            );
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

    public static List<String> parseImageList(Content content) throws Exception {
        String url = content.getReaderUrl();
        int qtyPages = content.getQtyPages();
        List<String> imageUrlList = new ArrayList<>();

        for(int i = 0; i <= qtyPages; i++) {
            String httpDoc = HttpClientHelper.call(url + '/' + i);
            String imageUrl = Jsoup
                    .parse(httpDoc)
                    .select("img.reader-img")
                    .attr("src");
            imageUrlList.add(imageUrl);
            Log.d("TEST", httpDoc);
        }

        return imageUrlList;
    }
}
