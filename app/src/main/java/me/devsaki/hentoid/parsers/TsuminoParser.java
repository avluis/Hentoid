package me.devsaki.hentoid.parsers;

import android.webkit.CookieManager;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.LogHelper;

import static me.devsaki.hentoid.enums.Site.TSUMINO;

/**
 * Created by Shiro on 1/22/2016.
 * Handles parsing of content from tsumino
 */
public class TsuminoParser {
    private static final String TAG = LogHelper.makeLogTag(TsuminoParser.class);

    public static Content parseContent(String urlString) throws IOException {
        Document doc = Jsoup.connect(urlString).get();

        Elements content = doc.select("div.book-line");
        if (content.size() > 0) {
            String url = doc
                    .select("div.book-page-cover a")
                    .attr("href")
                    .replace("/Read/View", "");

            String coverUrl = TSUMINO.getUrl()
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
                    .select(":has(div.book-info:containsOwn(Tag))")
                    .select("a.book-tag");
            parseAttributes(attributes, AttributeType.TAG, tagElements);

            Elements seriesElements = content
                    .select(":has(div.book-info:containsOwn(Parody))")
                    .select("a.book-tag");
            parseAttributes(attributes, AttributeType.SERIE, seriesElements);

            Elements characterElements = content
                    .select(":has(div.book-info:containsOwn(Characters))")
                    .select("a.book-tag");
            parseAttributes(attributes, AttributeType.CHARACTER, characterElements);

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
                    .setQtyPages(qtyPages)
                    .setStatus(StatusContent.SAVED)
                    .setSite(TSUMINO);
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
        List<String> imgUrls = new ArrayList<>();

        Document doc;
        Elements contents;
        String dataUrl,
                dataOpt,
                dataObj;

        try {
            doc = Jsoup.parse(HttpClientHelper.call(content.getReaderUrl()));
            contents = doc.select("#image-container");

            dataUrl = contents.attr("data-url");
            dataOpt = contents.attr("data-opt");
            dataObj = contents.attr("data-obj");

            LogHelper.d(TAG, "Data URL: " + TSUMINO.getUrl() + dataUrl + ", Data Opt: " + dataOpt +
                    ", Data Obj: " + dataObj);

            String request = sendPostRequest(dataUrl, dataOpt);
            imgUrls = buildImageUrls(dataObj, request);
        } catch (Exception e) {
            LogHelper.e(TAG, e, "Couldn't complete html/parse request: ");
        }
        LogHelper.d(TAG, imgUrls);

        return imgUrls;
    }

    private static String sendPostRequest(String dataUrl, String dataOpt) {
        final CookieManager cookieManager = CookieManager.getInstance();
        String url = TSUMINO.getUrl() + dataUrl;
        HttpURLConnection http = null;
        Map<String, String> data = new HashMap<>();

        data.put("q", dataOpt);
        String dataJson = new GsonBuilder().create().toJson(data, Map.class);

        String cookie = cookieManager.getCookie(url);
        if (cookie == null || cookie.isEmpty()) {
            cookie = Helper.getSessionCookie();
        }

        try {
            http = (HttpURLConnection) ((new URL(url).openConnection()));
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type", "application/json");
            http.setRequestProperty("Accept", "application/json");
            http.setRequestProperty("Cookie", cookie);
            http.setRequestMethod("POST");
            http.connect();

            OutputStream stream = http.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, "UTF-8"));

            writer.write(dataJson);
            writer.close();
            stream.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    http.getInputStream(), "UTF-8"));

            String line;
            StringBuilder builder = new StringBuilder();

            while ((line = br.readLine()) != null) {
                builder.append(line);
            }
            br.close();

            return builder.toString();
        } catch (UnsupportedEncodingException e) {
            LogHelper.e(TAG, e, "Encoding option is not supported for this URL");
        } catch (IOException e) {
            LogHelper.e(TAG, e, "IO Exception while attempting request");
        } finally {
            if (http != null) {
                http.disconnect();
            }
        }

        return null;
    }

    private static List<String> buildImageUrls(String data, String request) {
        List<String> imgUrls = new ArrayList<>();
        String imgUrl = TSUMINO.getUrl() + data + "?name=";

        JsonElement parser = new JsonParser().parse(request);
        JsonElement urls = parser.getAsJsonObject().get("reader_page_urls");

        for (int i = 0; i < urls.getAsJsonArray().size(); i++) {
            try {
                imgUrls.add(imgUrl + URLEncoder.encode(
                        urls.getAsJsonArray().get(i).getAsString(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LogHelper.e(TAG, e, "Failed to encode URL");
            }
        }

        return imgUrls;
    }
}
