package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.network.HttpHelper;

/**
 * Handles parsing of chapters and pages from Multporn
 */
public class MultpornParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        List<String> result = new ArrayList<>();
        processedUrl = content.getGalleryUrl();

        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(content.getDownloadParams(), headers);

        Document doc = getOnlineDocument(content.getGalleryUrl(), headers, Site.ALLPORNCOMIC.useHentoidAgent(), Site.ALLPORNCOMIC.useWebviewAgent());
        if (doc != null) {
            String juiceboxUrl = getJuiceboxRequestUrl(doc.select("head script"));
            result.addAll(getImagesUrls(juiceboxUrl, processedUrl));
        }

        return result;
    }

    public static String getJuiceboxRequestUrl(@NonNull List<Element> scripts) {
        for (Element e : scripts) {
            String scriptText = e.toString().toLowerCase().replace("\\/", "/");
            int juiceIndex = scriptText.indexOf("/juicebox/xml/");
            if (juiceIndex > -1) {
                int juiceEndIndex = scriptText.indexOf("\"", juiceIndex);
                return HttpHelper.fixUrl(scriptText.substring(juiceIndex, juiceEndIndex), Site.MULTPORN.getUrl());
            }
        }
        return "";
    }

    public static List<String> getImagesUrls(@NonNull String juiceboxUrl, @NonNull String galleryUrl) throws IOException {
        List<String> result = new ArrayList<>();

        List<Pair<String, String>> headers = new ArrayList<>();
        HttpHelper.addCurrentCookiesToHeader(juiceboxUrl, headers);
        headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, galleryUrl));
        Document doc = getOnlineDocument(juiceboxUrl, headers, Site.MULTPORN.useHentoidAgent(), Site.MULTPORN.useWebviewAgent());
        if (doc != null) {
            List<Element> images = doc.select("juicebox image");
            if (images.isEmpty()) images = doc.select("juicebox img");
            for (Element img : images) {
                String link = img.attr("linkURL");
                if (!result.contains(link))
                    result.add(link); // Make sure we're not adding duplicates
            }
        }
        return result;
    }
}
