package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.exception.CaptchaException;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

/**
 * Created by Shiro on 1/22/2016.
 * Handles parsing of content from tsumino
 */
public class TsuminoParser extends BaseParser {

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

        // Fetch the reader page
        Document doc = getOnlineDocument(content.getReaderUrl(), headers, Site.TSUMINO.useHentoidAgent());
        if (null != doc) {
            Elements captcha = doc.select(".g-recaptcha");
            if (captcha != null && !captcha.isEmpty())
                throw new CaptchaException();

            Element contents = doc.select("#image-container").first();
            if (null != contents) {
                String imgTemplate = contents.attr("data-cdn");
                return buildImageUrls(imgTemplate, content);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> buildImageUrls(String imgTemplate, Content content) {
        List<String> imgUrls = new ArrayList<>();

        for (int i = 0; i < content.getQtyPages(); i++)
            imgUrls.add(imgTemplate.replace("[PAGE]", i + 1 + ""));

        return imgUrls;
    }
}
