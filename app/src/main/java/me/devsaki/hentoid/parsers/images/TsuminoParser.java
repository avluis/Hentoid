package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.exception.CaptchaException;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

/**
 * Created by Shiro on 1/22/2016.
 * Handles parsing of content from tsumino
 */
public class TsuminoParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        // Fetch the reader page
        Document doc = getOnlineDocument(content.getReaderUrl(), headers, Site.TSUMINO.useHentoidAgent(), Site.TSUMINO.useWebviewAgent());
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
