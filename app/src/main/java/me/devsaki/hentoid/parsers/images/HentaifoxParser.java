package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.parsers.ParseHelper.getExtensionFromFormat;
import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.exception.ParseException;
import timber.log.Timber;

public class HentaifoxParser extends BaseImageListParser {

    // Hentaifox have two image servers; each hosts the exact same files
    private static final String[] HOSTS = new String[]{"i.hentaifox.com", "i2.hentaifox.com"};

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        List<Element> thumbs = doc.select(".g_thumb img");
        List<Element> scripts = doc.select("body script");

        return parseImages(content, thumbs, scripts);
    }

    public static List<String> parseImages(@NonNull Content content, @NonNull List<Element> thumbs, @NonNull List<Element> scripts) {
        content.populateUniqueSiteId();
        List<String> result = new ArrayList<>();

        // Parse the image format list to get the correct extensions
        // (thumbs extensions are _always_ jpg whereas images might be png; verified on one book)
        Map<String, String> imageFormats = null;
        for (Element s : scripts) {
            try {
                int jsonBeginIndex = s.data().indexOf("'{\"1\"");
                if (jsonBeginIndex > -1) {
                    imageFormats = JsonHelper.jsonToObject(s.data().substring(jsonBeginIndex + 1).replace("\"}');", "\"}").replace("\n", ""), JsonHelper.MAP_STRINGS);
                    break;
                }
            } catch (IOException e) {
                Timber.w(e);
            }
        }

        if (!thumbs.isEmpty() && imageFormats != null) {
            String thumbUrl = ParseHelper.getImgSrc(thumbs.get(0));
            String thumbPath = thumbUrl.substring(thumbUrl.indexOf("hentaifox.com") + 14, thumbUrl.lastIndexOf("/") + 1);

            // Forge all page URLs
            for (int i = 0; i < content.getQtyPages(); i++) {
                String imgUrl = "https://" + HOSTS[Helper.getRandomInt(HOSTS.length)] + "/" +
                        thumbPath +
                        (i + 1) + "." + getExtensionFromFormat(imageFormats, i);
                result.add(imgUrl);
            }
        }

        return result;
    }
}
