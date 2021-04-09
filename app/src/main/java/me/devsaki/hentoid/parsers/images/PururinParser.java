package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

/**
 * Created by robb_w on 01/31/2018.
 * Handles parsing of content from pururin.io
 */
public class PururinParser extends BaseImageListParser {

    private static final String IMAGE_PATH = "//cdn.pururin.io/assets/images/data/";

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
        String protocol = url.substring(0, 5);
        if ("https".equals(protocol)) protocol = "https:";

        // The whole algorithm is in app.js
        // 1- Get image extension from gallery data (JSON on HTML body)
        // 2- Generate image URL from  imagePath constant, gallery ID, page number and extension

        // 1- Get image extension from gallery data (JSON on HTML body)
        Document doc = getOnlineDocument(url, headers, Site.PURURIN.useHentoidAgent(), Site.PURURIN.useWebviewAgent());
        if (doc != null) {
            String json = doc.select("gallery-read").attr(":gallery");
            PururinInfo info = JsonHelper.jsonToObject(json, PururinInfo.class);

            // 2- Get imagePath from app.js => it is constant anyway, and app.js is 3 MB long => put it there as a const
            for (int i = 0; i < content.getQtyPages(); i++) {
                result.add(protocol + IMAGE_PATH + info.id + File.separator + (i + 1) + "." + info.image_extension);
            }
        }

        return result;
    }
}
