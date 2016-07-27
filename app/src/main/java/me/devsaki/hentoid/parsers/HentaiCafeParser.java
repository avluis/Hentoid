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
 * Created by avluis on 07/26/2016.
 * Handles parsing of content from Hentai Cafe
 */
public class HentaiCafeParser {
    private static final String TAG = LogHelper.makeLogTag(HentaiCafeParser.class);

    public static Content parseContent(String urlString) throws IOException {
        Document doc = Jsoup.connect(urlString).get();

        Elements content = doc.select("div.entry-content.content");
        if (content.size() > 0) {
            String url = doc.select("div.x-main.full")
                    .select("article")
                    .attr("id")
                    .replace("post-", "/?p=");

            String coverUrl = doc.select("div.x-column.x-sm.x-1-2")
                    .select("img")
                    .attr("src");

            String title = doc.select("div.x-column.x-sm.x-1-2.last")
                    .select("h3")
                    .first()
                    .text();

            AttributeMap attributes = new AttributeMap();

            String info = content.select("div.x-column.x-sm.x-1-2.last")
                    .select("p").html();

            String tags = info.substring(0, info.indexOf("<br>"))
                    .replace(Site.HENTAICAFE.getUrl(), "");

            String artists = info.substring(info.indexOf("Artists: "));
            artists = artists.substring(0, artists.indexOf("<br>"))
                    .replace(Site.HENTAICAFE.getUrl(), "");

            Elements tagElements = Jsoup.parse(tags).select("a");

            Elements artistElements = Jsoup.parse(artists).select("a");

            parseAttributes(attributes, AttributeType.TAG, tagElements);
            parseAttributes(attributes, AttributeType.ARTIST, artistElements);

            return new Content()
                    .setTitle(title)
                    .setUrl(url)
                    .setCoverImageUrl(coverUrl)
                    .setAttributes(attributes)
                    .setQtyPages(-1)
                    .setStatus(StatusContent.SAVED)
                    .setSite(Site.HENTAICAFE);
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

    // TODO: WIP
    public static List<String> parseImageList(Content content) {
        String readerUrl = content.getReaderUrl();
        LogHelper.d(TAG, readerUrl);
        List<String> imgUrls = new ArrayList<>();
        LogHelper.d(TAG, imgUrls);

        return imgUrls;
    }
}
