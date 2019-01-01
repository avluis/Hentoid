package me.devsaki.hentoid.parsers;

import android.webkit.URLUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONTokener;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devsaki.hentoid.database.domains.Content;
import timber.log.Timber;

/**
 * Created by avluis on 07/26/2016.
 * Handles parsing of content from Hentai Cafe
 */
public class HentaiCafeParser extends BaseParser {

    @Override
    protected List<String> parseImages(Content content) throws IOException {
        List<String> result = new ArrayList<>();

        Document doc = getOnlineDocument(content.getReaderUrl());
        if (doc != null) {
            Elements links = doc.select("a.x-btn");

            Elements contents;
            Element js;
            int pages = 0;

            if (links != null) {
                if (links.size() > 1) {
                    Timber.d("Multiple chapters found!");
                }

                for (int i = 0; i < links.size(); i++) {

                    String url = links.get(i).attr("href");

                    if (URLUtil.isValidUrl(url)) {
                        Timber.d("Chapter Links: %s", links.get(i).attr("href"));
                        try {
//                            doc = Jsoup.connect(links.get(i).attr("href")).timeout(TIMEOUT).get();
                            doc = getOnlineDocument(links.get(i).attr("href"));
                            if (doc != null) {
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
                                                result.add(array.getJSONObject(j).getString("url"));
                                            } catch (JSONException e) {
                                                Timber.e(e, "Error while reading from array");
                                            }
                                        }
                                    } else {
                                        Timber.e("Error while parsing pages");
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
        }

        return result;
    }

    private static JSONArray getJSONArrayFromString(String s) {
        Pattern pattern = Pattern.compile(".*\\[\\{ *(.*) *\\}\\].*");
        Matcher matcher = pattern.matcher(s);

        Timber.d("Match found? %s", matcher.find());

        if (matcher.groupCount() > 0) {
            String results = matcher.group(1);
            results = "[{" + results + "}]";
            try {
                return (JSONArray) new JSONTokener(results).nextValue();
            } catch (JSONException e) {
                Timber.e(e, "Couldn't build JSONArray from the provided string");
            }
        }

        return null;
    }
}
