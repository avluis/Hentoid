package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

/**
 * Created by robb_w on 2020/05
 * Handles parsing of content from hentai2read.com
 */
public class Hentai2ReadParser extends BaseParser {

    private static final String IMAGE_PATH = "https://static.hentaicdn.com/hentai";

    public static class H2RInfo {
        List<String> images;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();

        String downloadParamsStr = content.getDownloadParams();
        if (null == downloadParamsStr || downloadParamsStr.isEmpty()) {
            Timber.e("Download parameters not set");
            return result;
        }

        Map<String, String> downloadParams;
        try {
            downloadParams = JsonHelper.jsonToObject(downloadParamsStr, JsonHelper.MAP_STRINGS);
        } catch (IOException e) {
            Timber.e(e);
            return result;
        }

        List<Pair<String, String>> headers = new ArrayList<>();
        String cookieStr = downloadParams.get(HttpHelper.HEADER_COOKIE_KEY);
        if (null != cookieStr)
            headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));

        // Open each chapter URL and get the image data until all images are found
        int chapterNum = 0;
        while (result.size() < content.getQtyPages()) {
            String url = content.getGalleryUrl() + ++chapterNum + "/";
            Document doc = getOnlineDocument(url, headers, Site.HENTAI2READ.canKnowHentoidAgent());
            if (doc != null) {
                List<Element> scripts = doc.select("script");
                boolean foundScript = false;
                for (Element e : scripts)
                    if (e.childNodeSize() > 0 && e.childNode(0).toString().contains("'images' :")) {
                        String jsonStr = e.childNode(0).toString().replace("\n", "").trim().replace("var gData = ", "").replace("};", "}");
                        H2RInfo info = JsonHelper.jsonToObject(jsonStr, H2RInfo.class);
                        for (String img : info.images) result.add(IMAGE_PATH + img);
                        foundScript = true;
                    }
                if (!foundScript) break;
            }
        }

        return result;
    }
}
