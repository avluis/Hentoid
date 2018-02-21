package me.devsaki.hentoid.parsers;

import android.webkit.URLUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import timber.log.Timber;

import static me.devsaki.hentoid.enums.Site.HENTAICAFE;

/**
 * Created by avluis on 07/26/2016.
 * Handles parsing of content from Hentai Cafe
 */
public class HentaiCafeParser {

    private static final int TIMEOUT = 5000; // 5 seconds

    public static Content parseContent(String urlString) throws IOException {
        Document doc = Jsoup.connect(urlString).timeout(TIMEOUT).get();

        Elements content = doc.select("div.entry-content.content");

        if (urlString.contains(HENTAICAFE.getUrl() + "/78-2/") ||           // ignore tags page
                urlString.contains(HENTAICAFE.getUrl() + "/artists/")) {    // ignore artist page

            return null;
        }

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

            String tags = info.substring(0, info.indexOf("<br>")).replace(HENTAICAFE.getUrl(), "");

            String artists = info.substring(info.indexOf("Artists: "));
            artists = artists.substring(0, artists.indexOf("<br>")).replace(HENTAICAFE.getUrl(), "");

            Elements tagElements = Jsoup.parse(tags).select("a");

            Elements artistElements = Jsoup.parse(artists).select("a");

            parseAttributes(attributes, AttributeType.TAG, tagElements);
            parseAttributes(attributes, AttributeType.ARTIST, artistElements);

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
                    .setQtyPages(-1)
                    .setStatus(StatusContent.SAVED)
                    .setSite(HENTAICAFE);
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

    public static List<String> parseImageList(Content content) {
        String galleryUrl = content.getReaderUrl();
        List<String> imgUrls = new ArrayList<>();
        Timber.d("Gallery URL: %s", galleryUrl);

        Document readerDoc = null;
        Elements links = null;
        try {
            readerDoc = Jsoup.connect(galleryUrl).timeout(TIMEOUT).get();
        } catch (IOException e) {
            Timber.e(e, "Error parsing content page");
        }

        if (readerDoc != null) {
            links = readerDoc.select("a.x-btn");

            if (links.size() > 1) {
                Timber.d("Multiple chapters found!");
            }
        }

        Document doc;
        Elements contents;
        Element js;
        int pages = 0;

        if (links != null) {
            for (int i = 0; i < links.size(); i++) {

                String url = links.get(i).attr("href");

                if (URLUtil.isValidUrl(url)) {
                    Timber.d("Chapter Links: %s", links.get(i).attr("href"));
                    try {
                        doc = Jsoup.connect(links.get(i).attr("href")).timeout(TIMEOUT).get();
                        contents = doc.select("article#content");
                        js = contents.select("script").last();

                        if (contents.size() > 0) {
                            pages += Integer.parseInt(
                                    doc.select("div.text").first().text().replace(" ⤵", ""));
                            Timber.d("Pages: %s", pages);

                            JSONArray array = getJSONArrayFromString(js.toString());
                            if (array != null) {
                                for (int j = 0; j < array.length(); j++) {
                                    try {
                                        imgUrls.add(array.getJSONObject(j).getString("url"));
                                    } catch (JSONException e) {
                                        Timber.e(e, "Error while reading from array");
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        Timber.e(e, "JSOUP Error");
                    }
                }
            }
            Timber.d("Total Pages: %s", pages);
            content.setQtyPages(pages);
        }
        Timber.d("%s", imgUrls);

        return imgUrls;
    }

    private static JSONArray getJSONArrayFromString(String s) {
        Pattern pattern = Pattern.compile(".*\\[\\{ *(.*) *\\}\\].*");
        Matcher matcher = pattern.matcher(s);

        Timber.d("Match found? %s", matcher.find());

        String results = matcher.group(1);
        results = "[{" + results + "}]";
        try {
            return (JSONArray) new JSONTokener(results).nextValue();
        } catch (JSONException e) {
            Timber.e(e, "Couldn't build JSONArray from the provided string");
        }

        return null;
    }
}
