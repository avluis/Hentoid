package me.devsaki.hentoid.parsers;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.util.HttpHelper.getOnlineDocument;

/**
 * Created by neko on 08/07/2015.
 * Handles parsing of content from hitomi.la
 */
public class HitomiParser implements ImageListParser {

    // Reproduction of the Hitomi.la Javascript to find the hostname of the image server (see subdomain_from_url@reader.js)
    private final static int NUMBER_OF_FRONTENDS = 2;
    private final static String HOSTNAME_SUFFIX = "a";
    private final static char HOSTNAME_PREFIX_BASE = 97;

    public List<ImageFile> parseImageList(Content content) throws Exception {
        List<ImageFile> result = new ArrayList<>();

        Document doc = getOnlineDocument(content.getReaderUrl());
        if (doc != null) {
            Timber.d("Parsing: %s", content.getReaderUrl());
            Elements imgElements = doc.select(".img-url");

            if (null == imgElements || 0 == imgElements.size()) {
                Timber.w("No images found @ %s", content.getReaderUrl());
                return result;
            }

            // New Hitomi image URLs starting from june 2018
            //  If book ID is even, starts with 'aa'; else starts with 'ba'
            int referenceId = Integer.parseInt(content.getUniqueSiteId()) % 10;
            if (1 == referenceId)
                referenceId = 0; // Yes, this is what Hitomi actually does (see common.js)
            String imageSubdomain = ((char) (HOSTNAME_PREFIX_BASE + (referenceId % NUMBER_OF_FRONTENDS))) + HOSTNAME_SUFFIX;

            Map<String, String> downloadParams = new HashMap<>();
            // Add referer information to downloadParams for future image download
            downloadParams.put(HttpHelper.HEADER_REFERER_KEY, content.getReaderUrl());
            String downloadParamsStr = JsonHelper.serializeToJson(downloadParams);

            int order = 1;
            for (Element element : imgElements) {
                ImageFile img = ParseHelper.urlToImageFile("https:" + replaceSubdomainWith(element.text(), imageSubdomain), order++);
                img.setDownloadParams(downloadParamsStr);
                result.add(img);
            }
        } else {
            Timber.w("Document null @ %s", content.getReaderUrl());
            throw new Exception("Document null @ " + content.getReaderUrl());
        }

        return result;
    }

    private String replaceSubdomainWith(String url, String newSubdomain) {
        // Get the beginning and end of subdomain
        int subdomainBegin = 2; // Just after '//'
        int subdomainEnd = url.indexOf(".hitomi");

        return url.substring(0, subdomainBegin) + newSubdomain + url.substring(subdomainEnd);
    }
}
