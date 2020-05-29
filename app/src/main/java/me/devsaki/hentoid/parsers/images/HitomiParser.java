package me.devsaki.hentoid.parsers.images;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.HitomiGalleryInfo;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.exception.ParseException;
import okhttp3.Response;
import timber.log.Timber;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

/**
 * Created by neko on 08/07/2015.
 * Handles parsing of content from hitomi.la
 */
public class HitomiParser implements ImageListParser {

    // Reproduction of the Hitomi.la Javascript to find the hostname of the image server (see subdomain_from_url@reader.js)
    private static final int NUMBER_OF_FRONTENDS = 3;
    private static final String HOSTNAME_SUFFIX = "a";
    private static final char HOSTNAME_PREFIX_BASE = 97;

    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        String pageUrl = content.getReaderUrl();

        Document doc = getOnlineDocument(pageUrl);
        if (null == doc) throw new ParseException("Document unreachable : " + pageUrl);

        Timber.d("Parsing: %s", pageUrl);

        List<ImageFile> result = new ArrayList<>();
        result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));

        String galleryJsonUrl = "https://ltn.hitomi.la/galleries/" + content.getUniqueSiteId() + ".js";

        // Get the gallery JSON
        List<Pair<String, String>> headers = new ArrayList<>();
        headers.add(new Pair<>(HttpHelper.HEADER_REFERER_KEY, pageUrl));
        Response response = HttpHelper.getOnlineResource(galleryJsonUrl, headers, Site.HITOMI.canKnowHentoidAgent());
        if (null == response.body()) throw new IOException("Empty body");

        String json = response.body().string().replace("var galleryinfo = ", "");
        HitomiGalleryInfo gallery = JsonHelper.jsonToObject(json, HitomiGalleryInfo.class);

        Map<String, String> downloadParams = new HashMap<>();
        // Add referer information to downloadParams for future image download
        downloadParams.put(HttpHelper.HEADER_REFERER_KEY, pageUrl);
        String downloadParamsStr = JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS);

        ImageFile img;
        int order = 1;
        boolean isHashAvailable;
        for (HitomiGalleryInfo.HitomiGalleryPage page : gallery.getFiles()) {
            isHashAvailable = (page.getHash() != null && !page.getHash().isEmpty());
            if (1 == page.getHaswebp() && isHashAvailable && Preferences.isDlHitomiWebp())
                img = buildWebpPicture(page, order++, gallery.getFiles().size());
            else if (isHashAvailable)
                img = buildHashPicture(page, order++, gallery.getFiles().size());
            else img = buildSimplePicture(content, page, order++, gallery.getFiles().size());
            img.setDownloadParams(downloadParamsStr);
            result.add(img);
        }

        return result;
    }

    private ImageFile buildWebpPicture(@NonNull HitomiGalleryInfo.HitomiGalleryPage page, int order, int maxPages) {
        return buildHashPicture(page, order, maxPages, "webp", "webp");
    }

    private ImageFile buildHashPicture(@NonNull HitomiGalleryInfo.HitomiGalleryPage page, int order, int maxPages) {
        return buildHashPicture(page, order, maxPages, "images", FileHelper.getExtension(page.getName()));
    }

    private ImageFile buildHashPicture(@NonNull HitomiGalleryInfo.HitomiGalleryPage page, int order, int maxPages, String folder, String extension) {
        String hash = page.getHash();
        String componentA = hash.substring(hash.length() - 1);
        String componentB = hash.substring(hash.length() - 3, hash.length() - 1);

        String imageSubdomain = subdomainFromGalleryId(Integer.valueOf(componentB, 16));
        String pageUrl = "https://" + imageSubdomain + ".hitomi.la/" + folder + "/" + componentA + "/" + componentB + "/" + hash + "." + extension;

        return ParseHelper.urlToImageFile(pageUrl, order, maxPages, StatusContent.SAVED);
    }

    private ImageFile buildSimplePicture(@NonNull Content content, @NonNull HitomiGalleryInfo.HitomiGalleryPage page, int order, int maxPages) {
        // New Hitomi image URLs starting from june 2018
        //  If book ID is even, starts with 'aa'; else starts with 'ba'
        int referenceId = Integer.parseInt(content.getUniqueSiteId()) % 10;
        String imageSubdomain = subdomainFromGalleryId(referenceId);
        String pageUrl = "https://" + imageSubdomain + ".hitomi.la/galleries/" + content.getUniqueSiteId() + "/" + page.getName();

        return ParseHelper.urlToImageFile(pageUrl, order, maxPages, StatusContent.SAVED);
    }

    private String subdomainFromGalleryId(int referenceId) {
        return ((char) (HOSTNAME_PREFIX_BASE + (referenceId % NUMBER_OF_FRONTENDS))) + HOSTNAME_SUFFIX;
    }

    public Optional<ImageFile> parseBackupUrl(@NonNull String url, int order, int maxPages) {
        // Hitomi does not use backup URLs
        return Optional.of(new ImageFile(order, url, StatusContent.SAVED, maxPages));
    }
}
