package me.devsaki.hentoid.parsers.images;

import android.util.Pair;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

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
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

/**
 * Created by avluis on 07/26/2016.
 * Handles parsing of content from Hentai Cafe
 */
public class HentaiCafeParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();
        String pageUrl = content.getGalleryUrl();
        int pages = 0;

        // hCafe needs the PHPSESSID cookie set to succeed the redirection to the book page (see issue #370)
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, "PHPSESSID="+content.getTitle().hashCode()));
        Document doc = getOnlineDocument(pageUrl, headers, true);
        if (null == doc) throw new ParseException("Document unreachable : " + pageUrl);

        Timber.d("Parsing: %s", pageUrl);
        Elements links = doc.select("a.x-btn");
        if (links.isEmpty()) throw new ParseException("No links found @ " + pageUrl);

        if (links.size() > 1) Timber.d("Multiple chapters found!");
        progressStart(links.size());

        for (Element link : links) {
            String url = link.attr("href");
            // Reconstitute the reader URL piece by piece if needed
            // NB : some pages require it (e.g. 2606)
            if (url.equals("#") && doc != null) {
                // Get the canonical link
                Elements canonicalLink = doc.select("head [rel=canonical]");
                if (canonicalLink != null) {
                    // Remove artist name
                    String artist = content.getAuthor().replace(" ", "-").toLowerCase() + "-";
                    String canonicalUrl = canonicalLink.get(0).attr("href").replace(artist, "");
                    // Get the last part
                    String[] parts = canonicalUrl.split("/");
                    String canonicalName = parts[parts.length - 1].replace("-", "_");
                    url = content.getReaderUrl().replace("$1", canonicalName); // $1 has to be replaced by the textual unique site ID without the author name
                }
            }

            if (URLUtil.isValidUrl(url)) {
                Timber.d("Chapter Links: %s", url);
                try {
                    doc = getOnlineDocument(url);
                    if (doc != null) {
                        Elements scripts = doc.select("article#content").select("script");
                        for (Element script : scripts) {
                            String scriptStr = script.toString();
                            if (scriptStr.contains("\"created\"")) { // That's the one
                                JSONArray array = getJSONArrayFromString(scriptStr);
                                if (array != null) {
                                    for (int i = 0; i < array.length(); i++)
                                        result.add(array.getJSONObject(i).getString("url"));
                                    pages += array.length();
                                } else {
                                    Timber.e("Error while parsing pages");
                                }
                                break;
                            }
                        }
                    }
                } catch (JSONException e) {
                    Timber.e(e, "Error while reading from array");
                } catch (IOException e) {
                    Timber.e(e, "JSOUP Error");
                }
            }
            progressPlus();
        }

        Timber.d("Total Pages: %s", pages);
        content.setQtyPages(pages);

        progressComplete();

        return result;
    }

    private static JSONArray getJSONArrayFromString(String s) {
        @SuppressWarnings("RegExpRedundantEscape")
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
