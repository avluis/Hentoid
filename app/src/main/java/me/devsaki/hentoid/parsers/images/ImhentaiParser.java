package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.parsers.ParseHelper.getExtensionFromFormat;
import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import timber.log.Timber;

public class ImhentaiParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();

        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        String url = content.getReaderUrl();

        // The whole algorithm is in app.js
        // 1- Get image extension from gallery data (JSON on HTML body)
        // 2- Generate image URL from  imagePath constant, gallery ID, page number and extension

        // 1- Get image extension from gallery data (JSON on HTML body)
        Document doc = getOnlineDocument(url, headers, Site.IMHENTAI.useHentoidAgent(), Site.IMHENTAI.useWebviewAgent());
        if (doc != null) {
            List<Element> thumbs = doc.select(".gthumb img");
            List<Element> scripts = doc.select("body script");

            // Parse the image format list to get the whole list and the correct extensions
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
                String thumbPath = thumbUrl.substring(0, thumbUrl.lastIndexOf("/") + 1);

                // Forge all page URLs
                for (int i = 0; i < content.getQtyPages(); i++) {
                    String imgUrl = thumbPath + (i + 1) + "." + getExtensionFromFormat(imageFormats, i);
                    result.add(imgUrl);
                }
            }
        }

        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull String chapterUrl, String downloadParams, List<Pair<String, String>> headers) throws Exception {
        // Nothing; no chapters for this source
        return null;
    }
}
