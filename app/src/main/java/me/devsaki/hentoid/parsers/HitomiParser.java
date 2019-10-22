package me.devsaki.hentoid.parsers;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.squareup.moshi.Types;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.HitomiGalleryPage;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import okhttp3.Response;
import timber.log.Timber;

import static me.devsaki.hentoid.util.HttpHelper.getOnlineDocument;

/**
 * Created by neko on 08/07/2015.
 * Handles parsing of content from hitomi.la
 */
public class HitomiParser implements ImageListParser {

    // Reproduction of the Hitomi.la Javascript to find the hostname of the image server (see subdomain_from_url@reader.js)
    private static final int NUMBER_OF_FRONTENDS = 3;
    private static final String HOSTNAME_SUFFIX = "a";
    private static final char HOSTNAME_PREFIX_BASE = 97;

    public List<ImageFile> parseImageList(Content content) throws Exception {
        String pageUrl = content.getReaderUrl();

        Document doc = getOnlineDocument(pageUrl);
        if (null == doc) throw new Exception("Document unreachable : " + pageUrl);

        Timber.d("Parsing: %s", pageUrl);
        Elements imgElements = doc.select(".img-url");

//        if (!imgElements.isEmpty()) return fromOldGallery(content, pageUrl, imgElements);
//        else return fromGallery(content, pageUrl);

        return fromGallery(content, pageUrl);
    }


    // Old layout; is still reachable but links to ultra slow hosts
    private List<ImageFile> fromOldGallery(@NonNull Content content, @NonNull String pageUrl, @NonNull Elements imgElements) {
        List<ImageFile> result = new ArrayList<>();

        // New Hitomi image URLs starting from june 2018
        //  If book ID is even, starts with 'aa'; else starts with 'ba'
        int referenceId = Integer.parseInt(content.getUniqueSiteId()) % 10;
        String imageSubdomain = subdomainFromGalleryId(referenceId);

        Map<String, String> downloadParams = new HashMap<>();
        // Add referer information to downloadParams for future image download
        downloadParams.put(HttpHelper.HEADER_REFERER_KEY, pageUrl);
        String downloadParamsStr = JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS);

        int order = 1;
        for (Element element : imgElements) {
            ImageFile img = ParseHelper.urlToImageFile("https:" + replaceSubdomainWith(element.text(), imageSubdomain), order++);
            img.setDownloadParams(downloadParamsStr);
            result.add(img);
        }

        return result;
    }

    // New layout as of 10/2019
    private List<ImageFile> fromGallery(@NonNull Content content, @NonNull String pageUrl) throws IOException {
        List<ImageFile> result = new ArrayList<>();

        String galleryJsonUrl = "https://ltn.hitomi.la/galleries/" + content.getUniqueSiteId() + ".js";

        // Get the gallery JSON
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, pageUrl));
        Response response = HttpHelper.getOnlineResource(galleryJsonUrl, headers, Site.HITOMI.canKnowHentoidAgent());
        if (null == response.body()) throw new IOException("Empty body");

        String json = response.body().string().replace("var galleryinfo = ", "");
        Type listPagesType = Types.newParameterizedType(List.class, HitomiGalleryPage.class);
        List<HitomiGalleryPage> gallery = JsonHelper.jsonToObject(json, listPagesType);

        Map<String, String> downloadParams = new HashMap<>();
        // Add referer information to downloadParams for future image download
        downloadParams.put(HttpHelper.HEADER_REFERER_KEY, pageUrl);
        String downloadParamsStr = JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS);

        ImageFile img;
        int order = 1;
        for (HitomiGalleryPage page : gallery) {
            if (1 == page.getHaswebp()) img = buildWebpPicture(content, page, order++);
            else if (page.getHash() != null && !page.getHash().isEmpty())
                img = buildHashPicture(page, order++);
            else img = buildSimplePicture(content, page, order++);
            img.setDownloadParams(downloadParamsStr);
            result.add(img);
        }

        return result;
    }

    private ImageFile buildWebpPicture(@NonNull Content content, @NonNull HitomiGalleryPage page, int order) {
        // New Hitomi image URLs starting from june 2018
        //  If book ID is even, starts with 'aa'; else starts with 'ba'
        int referenceId = Integer.parseInt(content.getUniqueSiteId()) % 10;
        String imageSubdomain = subdomainFromGalleryId(referenceId);
        String pageUrl = "https://" + imageSubdomain + ".hitomi.la/webp/" + content.getUniqueSiteId() + "/" + page.getName() + ".webp";

        return ParseHelper.urlToImageFile(pageUrl, order);
    }

    private ImageFile buildHashPicture(@NonNull HitomiGalleryPage page, int order) {
        String hash = page.getHash();
        String componentA = hash.substring(hash.length() - 1);
        String componentB = hash.substring(hash.length() - 3, hash.length() - 1);

        String imageSubdomain = subdomainFromGalleryId(Integer.valueOf(componentB, 16));
        String pageUrl = "https://" + imageSubdomain + ".hitomi.la/images/" + componentA + "/" + componentB + "/" + hash + "." + FileHelper.getExtension(page.getName());

        return ParseHelper.urlToImageFile(pageUrl, order);
    }

    private ImageFile buildSimplePicture(@NonNull Content content, @NonNull HitomiGalleryPage page, int order) {
        // New Hitomi image URLs starting from june 2018
        //  If book ID is even, starts with 'aa'; else starts with 'ba'
        int referenceId = Integer.parseInt(content.getUniqueSiteId()) % 10;
        String imageSubdomain = subdomainFromGalleryId(referenceId);
        String pageUrl = "https://" + imageSubdomain + ".hitomi.la/galleries/" + content.getUniqueSiteId() + "/" + page.getName();

        return ParseHelper.urlToImageFile(pageUrl, order);
    }

    private String subdomainFromGalleryId(int referenceId) {
        return ((char) (HOSTNAME_PREFIX_BASE + (referenceId % NUMBER_OF_FRONTENDS))) + HOSTNAME_SUFFIX;
    }

    private String replaceSubdomainWith(String url, String newSubdomain) {
        // Get the beginning and end of subdomain
        int subdomainBegin = 2; // Just after '//'
        int subdomainEnd = url.indexOf(".hitomi");

        return url.substring(0, subdomainBegin) + newSubdomain + url.substring(subdomainEnd);
    }

    public ImageFile parseBackupUrl(String url, int order) {
        // Hitomi does not use backup URLs
        return null;
    }
}
