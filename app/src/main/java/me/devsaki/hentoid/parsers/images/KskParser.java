package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.annimon.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Settings;
import me.devsaki.hentoid.util.exception.ParseException;
import me.devsaki.hentoid.util.network.HttpHelper;

public class KskParser extends BaseImageListParser {

    public static class KskInfo {
        public List<KskImage> original;
        public List<KskImage> resampled;
    }

    public static class KskImage {
        public String n;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        // Fetch the book gallery page
        Document doc = getOnlineDocument(content.getGalleryUrl(), headers, Site.KSK.useHentoidAgent(), Site.KSK.useWebviewAgent());
        if (null == doc)
            throw new ParseException("Document unreachable : " + content.getGalleryUrl());

        List<Element> thumbs = doc.select("#previews>main>div img");
        if (!thumbs.isEmpty()) return parseImages(thumbs);
        else return parseImages(doc.select("#cover a").attr("href"), content);
    }

    public static List<String> parseImages(@NonNull List<Element> thumbs) {
        String prefix = Settings.INSTANCE.isKskDownloadOriginal() ? "original" : "resampled";
        return Stream.of(thumbs)
                .map(ParseHelper::getImgSrc)
                .map(q -> HttpHelper.fixUrl(q, Site.KSK.getUrl()))
                .map(r -> r.replace("/t/", "/" + prefix + "/"))
                .map(r -> r.replace("/320/", "/"))
                .toList();
    }

    public static List<String> parseImages(@NonNull String readerUrl, @NonNull Content content) throws IOException, ParseException {
        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        String url = HttpHelper.fixUrl(readerUrl, Site.KSK.getUrl());
        Document doc = getOnlineDocument(url, headers, Site.KSK.useHentoidAgent(), Site.KSK.useWebviewAgent());
        if (null == doc)
            throw new ParseException("Document unreachable : " + readerUrl);

        List<String> result = new ArrayList<>();
        List<Element> scripts = doc.select("script");
        for (Element e : scripts) {
            String txt = (e.childNodeSize() > 0) ? e.childNode(0).toString() : "";
            if (txt.contains("resampled")) {
                int firstBrace = txt.indexOf('{');
                int lastElt = txt.lastIndexOf("],");
                if (firstBrace > -1 && lastElt > -1) {
                    String json = txt.substring(firstBrace, lastElt + 1) + "}";
                    KskInfo galleryInfo = JsonHelper.jsonToObject(json, KskInfo.class);
                    List<KskImage> images = Settings.INSTANCE.isKskDownloadOriginal() ? galleryInfo.original : galleryInfo.resampled;
                    String prefix = Settings.INSTANCE.isKskDownloadOriginal() ? "original" : "resampled";
                    String baseUrl = url.replace("read", prefix);
                    int lastSlash = baseUrl.lastIndexOf('/');
                    baseUrl = baseUrl.substring(0, lastSlash + 1);
                    for (KskImage img : images) result.add(baseUrl + img.n);
                    break;
                }
            }
        }
        return result;
    }
}
