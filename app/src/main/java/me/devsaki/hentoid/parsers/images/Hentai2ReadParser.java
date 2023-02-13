package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import android.webkit.URLUtil;

import androidx.core.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import timber.log.Timber;

/**
 * Handles parsing of content from hentai2read.com
 */
public class Hentai2ReadParser extends BaseImageListParser {

    public static final String IMAGE_PATH = "https://static.hentaicdn.com/hentai";

    public static class H2RInfo {
        public String title;
        public List<String> images;
    }

    @Override
    public List<ImageFile> parseImageListImpl(@NonNull Content onlineContent, @Nullable Content storedContent) throws Exception {
        String readerUrl = onlineContent.getReaderUrl();
        processedUrl = onlineContent.getGalleryUrl();

        if (!URLUtil.isValidUrl(readerUrl))
            throw new IllegalArgumentException("Invalid gallery URL : " + readerUrl);

        Timber.d("Gallery URL: %s", readerUrl);

        EventBus.getDefault().register(this);

        List<ImageFile> result;
        try {
            result = parseImageFiles(onlineContent, storedContent);
            ParseHelper.setDownloadParams(result, onlineContent.getSite().getUrl());
        } finally {
            EventBus.getDefault().unregister(this);
        }

        return result;
    }

    private List<ImageFile> parseImageFiles(@NonNull Content onlineContent, @Nullable Content storedContent) throws Exception {
        List<ImageFile> result = new ArrayList<>();

        List<Pair<String, String>> headers = new ArrayList<>();
        ParseHelper.addSavedCookiesToHeader(onlineContent.getDownloadParams(), headers);

        // 1. Scan the gallery page for chapter URLs
        // NB : We can't just guess the URLs by starting to 1 and increment them
        // because the site provides "subchapters" (e.g. 4.6, 2.5)
        List<Chapter> chapters;
        Document doc = getOnlineDocument(onlineContent.getGalleryUrl(), headers, Site.HENTAI2READ.useHentoidAgent(), Site.HENTAI2READ.useWebviewAgent());
        if (null == doc) return result;

        List<Element> chapterLinks = doc.select(".nav-chapters a[href^=" + onlineContent.getGalleryUrl() + "]");
        chapters = ParseHelper.getChaptersFromLinks(chapterLinks, onlineContent.getId());

        // If the stored content has chapters already, save them for comparison
        List<Chapter> storedChapters = null;
        if (storedContent != null) {
            storedChapters = storedContent.getChapters();
            if (storedChapters != null)
                storedChapters = Stream.of(storedChapters).toList(); // Work on a copy
        }
        if (null == storedChapters) storedChapters = Collections.emptyList();

        // Use chapter folder as a differentiator (as the whole URL may evolve)
        List<Chapter> extraChapters = ParseHelper.getExtraChaptersbyUrl(storedChapters, chapters);

        progressStart(onlineContent, storedContent, extraChapters.size());

        // Start numbering extra images right after the last position of stored and chaptered images
        int imgOffset = ParseHelper.getMaxImageOrder(storedChapters);

        // 2. Open each chapter URL and get the image data until all images are found
        for (Chapter chp : extraChapters) {
            if (processHalted.get()) break;
            doc = getOnlineDocument(chp.getUrl(), headers, Site.HENTAI2READ.useHentoidAgent(), Site.HENTAI2READ.useWebviewAgent());
            if (doc != null) {
                List<Element> scripts = doc.select("script");
                H2RInfo info = getDataFromScripts(scripts);
                if (info != null) {
                    List<String> imageUrls = Stream.of(info.images).map(s -> IMAGE_PATH + s).toList();
                    if (!imageUrls.isEmpty())
                        result.addAll(ParseHelper.urlsToImageFiles(imageUrls, imgOffset + result.size() + 1, StatusContent.SAVED, 1000, chp));
                } else
                    Timber.i("Chapter parsing failed for %s : no pictures found", chp.getUrl());
            } else {
                Timber.i("Chapter parsing failed for %s : no response", chp.getUrl());
            }
            progressPlus();
        }
        progressComplete();

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        return result;
    }

    public static H2RInfo getDataFromScripts(List<Element> scripts) throws IOException {
        for (Element e : scripts) {
            if (e.childNodeSize() > 0 && e.childNode(0).toString().contains("'images' :")) {
                String jsonStr = e.childNode(0).toString().replace("\n", "").trim().replace("var gData = ", "").replace("};", "}");
                return JsonHelper.jsonToObject(jsonStr, H2RInfo.class);
            }
        }
        return null;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageListImpl is overriden directly
        return null;
    }
}
