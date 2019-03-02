package me.devsaki.hentoid.parsers;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import timber.log.Timber;

import static me.devsaki.hentoid.util.HttpHelper.getOnlineDocument;

/**
 * Created by neko on 08/07/2015.
 * Handles parsing of content from hitomi.la
 */
public class HitomiParser extends BaseParser {

    // Reproduction of the Hitomi.la Javascript to find the hostname of the image server (see subdomain_from_url@reader.js)
    private final static int NUMBER_OF_FRONTENDS = 2;
    private final static String HOSTNAME_SUFFIX = "a";
    private final static char HOSTNAME_PREFIX_BASE = 97;

    @Override
    protected List<String> parseImages(Content content) throws Exception {
        List<String> result = new ArrayList<>();

        Document doc = getOnlineDocument(content.getReaderUrl());
        if (doc != null) {
            Timber.d("Parsing: %s", content.getReaderUrl());
            Elements imgElements = doc.select(".img-url");

            if (null == imgElements || 0 == imgElements.size())
            {
                Timber.w("No images found @ %s", content.getReaderUrl()); // TODO catch this in download queue
                return result;
            }

            // New Hitomi image URLs starting from june 2018
            //  If book ID is even, starts with 'aa'; else starts with 'ba'
            int referenceId = Integer.parseInt(content.getUniqueSiteId()) % 10;
            if (1 == referenceId)
                referenceId = 0; // Yes, this is what Hitomi actually does (see common.js)
            String imageSubdomain = Character.toString((char) (HOSTNAME_PREFIX_BASE + (referenceId % NUMBER_OF_FRONTENDS))) + HOSTNAME_SUFFIX;

            for (Element element : imgElements) {
                result.add("https:" + replaceSubdomainWith(element.text(), imageSubdomain));
            }
        } else {
            Timber.w("Document null @ %s", content.getReaderUrl()); // TODO catch this in download queue
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
