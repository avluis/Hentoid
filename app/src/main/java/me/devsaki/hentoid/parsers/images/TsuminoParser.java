package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.exception.CaptchaException;
import me.devsaki.hentoid.util.exception.ParseException;

public class TsuminoParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        // Fetch the reader page
        Document doc = getOnlineDocument(content.getReaderUrl(), headers, Site.TSUMINO.useHentoidAgent(), Site.TSUMINO.useWebviewAgent());
        if (null != doc) {
            Elements captcha = doc.select(".g-recaptcha");
            if (!captcha.isEmpty()) throw new CaptchaException();

            int nbPages = 0;
            Element nbPagesE = doc.select("h1").first();
            if (null != nbPagesE) {
                List<ImmutableTriple<Integer, Integer, Integer>> digits = StringHelper.locateDigits(nbPagesE.text());
                if (!digits.isEmpty()) nbPages = digits.get(digits.size() - 1).right;
            }
            if (-1 == nbPages) throw new ParseException("Couldn't find the number of pages");

            Element contents = doc.select("#image-container").first();
            if (null != contents) {
                String imgTemplate = contents.attr("data-cdn");
                return buildImageUrls(imgTemplate, nbPages);
            }
        }

        return Collections.emptyList();
    }

    private static List<String> buildImageUrls(String imgTemplate, int nbPages) {
        List<String> imgUrls = new ArrayList<>();

        for (int i = 0; i < nbPages; i++)
            imgUrls.add(imgTemplate.replace("[PAGE]", i + 1 + ""));

        return imgUrls;
    }

    @Override
    protected List<String> parseImages(@NonNull String chapterUrl, String downloadParams, List<Pair<String, String>> headers) throws Exception {
        // Nothing; no chapters for this source
        return null;
    }

    @Override
    protected boolean isChapterUrl(@NonNull String url) {
        return false;
    }
}
