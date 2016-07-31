package me.devsaki.hentoid.parsers;

import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.HttpClientHelper;
import me.devsaki.hentoid.util.LogHelper;

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

            Elements seriesElements = content
                    .select(":has(div.book-info:containsOwn(Parody))")
                    .select("a.book-tag");
            parseAttributes(attributes, AttributeType.SERIE, seriesElements);

            Elements characterElements = content
                    .select(":has(div.book-info:containsOwn(Characters))")
                    .select("a.book-tag");
            parseAttributes(attributes, AttributeType.CHARACTER, characterElements);

            return new Content()
                    .setTitle(title)
                    .setUrl(url)
                    .setCoverImageUrl(coverUrl)
                    .setAttributes(attributes)
                    .setQtyPages(qtyPages)
                    .setStatus(StatusContent.SAVED)
                    .setSite(Site.TSUMINO);
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
        String baseUrl = Site.TSUMINO.getUrl() + '/';

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

            LogHelper.d(TAG, "Data URL: " + Site.TSUMINO.getUrl() + dataUrl + ", Data Opt: " +
                    dataOpt + ", Data Obj: " + dataObj);

            JSONObject jsonObject = sendPostRequest(dataUrl, dataOpt);
            LogHelper.d(TAG, jsonObject);

            imgUrls = buildImageUrls(baseUrl, dataObj, jsonObject);

        } catch (Exception e) {
            LogHelper.d(TAG, "Couldn't complete html/parse request: ", e);
        }

        return imgUrls;
    }

    private static JSONObject sendPostRequest(String dataUrl, String dataOpt) {
        String url = Site.TSUMINO.getUrl() + dataUrl;
        HttpURLConnection http;
        Map<String, String> data = new HashMap<>();

        data.put("q", dataOpt);
        String dataJson = new GsonBuilder().create().toJson(data, Map.class);

        try {
            http = (HttpURLConnection) ((new URL(url).openConnection()));
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type", "application/json");
            http.setRequestProperty("Accept", "application/json");
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

            return new JSONObject(builder.toString());
        } catch (UnsupportedEncodingException e) {
            LogHelper.d(TAG, "Encoding option is not supported for this URL: ", e);
        } catch (IOException e) {
            LogHelper.d(TAG, "IO Exception while attempting request: ", e);
        } catch (JSONException e) {
            LogHelper.d(TAG, "Could not build JSON from String response: ", e);
        }

        return null;
    }

    // TODO: WIP: Parse JSON response
    private static List<String> buildImageUrls(String url, String data, JSONObject json) {
        LogHelper.d(TAG, "URL: " + url, ", Data: " + data, ", JSON: " + json);
        return null;
    }
}
