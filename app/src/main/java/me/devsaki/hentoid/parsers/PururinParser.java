package me.devsaki.hentoid.parsers;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

import org.jsoup.nodes.Document;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;

import static me.devsaki.hentoid.util.HttpHelper.getOnlineDocument;

/**
 * Created by robb_w on 01/31/2018.
 * Handles parsing of content from pururin.io
 */
public class PururinParser extends BaseParser {

    private static final String IMAGE_PATH = "//cdn.pururin.io/assets/images/data/";

    private class PururinInfo {
        @Expose
        String image_extension;
        @Expose
        String id;
    }

    @Override
    protected List<String> parseImages(Content content) throws Exception {
        List<String> result = new ArrayList<>();
        String url = content.getReaderUrl();
        String protocol = url.substring(0, 5);
        if ("https".equals(protocol)) protocol = "https:";

        // The whole algorithm is in app.js
        // 1- Get image extension from gallery data (JSON on HTML body)
        // 2- Generate image URL from  imagePath constant, gallery ID, page number and extension

        // 1- Get image extension from gallery data (JSON on HTML body)
        Document doc = getOnlineDocument(url);
        if (doc != null) {
            String json = doc.select("gallery-read").attr(":gallery");
            PururinInfo info = new Gson().fromJson(json, PururinInfo.class);

            // 2- Get imagePath from app.js => it is constant anyway, and app.js is 3 MB long => put it there as a const
            for (int i = 0; i < content.getQtyPages(); i++) {
                result.add(protocol + IMAGE_PATH + info.id + File.separator + (i + 1) + "." + info.image_extension);
            }
        }

        return result;
    }
}
