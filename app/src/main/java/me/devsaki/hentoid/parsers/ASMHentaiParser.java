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
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.LogHelper;

/**
 * Created by avluis on 07/24/2016.
 * Handles parsing of content from asmhentai.com
 */
public class ASMHentaiParser {
    private static final String TAG = LogHelper.makeLogTag(ASMHentaiParser.class);

    public static Content parseContent(String urlString) throws IOException {
        Document doc = Jsoup.connect(urlString).get();

        Elements content = doc.select("div.info");
        if (content.size() > 0) {
            String url = doc.select("div.cover")
                    .select("a")
                    .attr("href")
                    .replace("/gallery", "");
            url = url.substring(0, url.length() - 2);
            LogHelper.d(TAG, url);

            String coverUrl = "http:"
                    + doc.select("div.cover")
                    .select("a")
                    .select("img")
                    .attr("src");
            LogHelper.d(TAG, coverUrl);

            String title = doc.select("div.info")
                    .select("h1")
                    .first()
                    .text();
            LogHelper.d(TAG, title);

            int pages = Integer.parseInt(doc.select("div.pages")
                    .select("h3")
                    .text()
                    .replace("Pages: ", ""));
            LogHelper.d(TAG, pages);

            AttributeMap attributes = new AttributeMap();

            Elements artistElements = content
                    .select("div.tags:contains(Artists)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.ARTIST, artistElements);

            Elements tagElements = content
                    .select("div.tags:contains(Tags)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.TAG, tagElements);

            Elements seriesElements = content
                    .select("div.tags:contains(Parody)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.SERIE, seriesElements);

            Elements characterElements = content
                    .select("div.tags:contains(Characters)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.CHARACTER, characterElements);

            LogHelper.d(TAG, attributes);

            return new Content()
                    .setTitle(title)
                    .setUrl(url)
                    .setCoverImageUrl(coverUrl)
                    .setAttributes(attributes)
                    .setQtyPages(pages)
                    .setStatus(StatusContent.SAVED)
                    .setSite(Site.ASMHENTAI);
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

    // TODO: WIP
    public static List<String> parseImageList(String html) {
        List<String> imgUrls = new ArrayList<>();

        Document doc = Jsoup.parse(html);
        LogHelper.d(TAG, doc);

        return imgUrls;
    }
}
