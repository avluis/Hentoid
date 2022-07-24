package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.jsoup.nodes.Document;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.network.HttpHelper;

/**
 * Handles parsing of content from pururin.to
 */
public class PururinParser extends BaseImageListParser {

    private static final String IMAGE_PATH = "//cdn.pururin.to/assets/images/data/";

    public static class PururinInfo {
        String image_extension;
        String id;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();

        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        String url = content.getReaderUrl();
        String protocol = HttpHelper.getProtocol(url) + ":";

        // The whole algorithm is in app.js
        // 1- Get image extension from gallery data (JSON on HTML body)
        // 2- Generate image URL from  imagePath constant, gallery ID, page number and extension

        // 1- Get image extension from gallery data (JSON on HTML body)
        Document doc = getOnlineDocument(url, headers, Site.PURURIN.useHentoidAgent(), Site.PURURIN.useWebviewAgent());
        if (doc != null) {
            String json = doc.select("gallery-read").attr("encoded");
            PururinInfo info = JsonHelper.jsonToObject(new String(StringHelper.decode64(json)), PururinInfo.class);

            // 2- Get imagePath from app.js => it is constant anyway, and app.js is 3 MB long => put it there as a const
            for (int i = 0; i < content.getQtyPages(); i++) {
                result.add(protocol + IMAGE_PATH + info.id + File.separator + (i + 1) + "." + info.image_extension);
            }
        }

        return result;
    }
}
