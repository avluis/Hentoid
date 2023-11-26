package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import timber.log.Timber;

public class Manhwa18Parser extends BaseImageListParser {

    @Override
    public List<ImageFile> parseImageListImpl(@NonNull Content onlineContent, @Nullable Content storedContent) throws Exception {
        String readerUrl = onlineContent.getReaderUrl();

        if (!URLUtil.isValidUrl(readerUrl))
            throw new IllegalArgumentException("Invalid gallery URL : " + readerUrl);

        processedUrl = onlineContent.getGalleryUrl();
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

        List<Pair<String, String>> headers = fetchHeaders(onlineContent);

        // 1. Scan the gallery page for chapter URLs
        List<Chapter> chapters;
        Document doc = getOnlineDocument(onlineContent.getGalleryUrl(), headers, Site.MANHWA18.useHentoidAgent(), Site.MANHWA18.useWebviewAgent());
        if (null == doc) return result;

        List<Element> chapterLinks = doc.select("div ul a[href*=chap]");
        if (chapterLinks.isEmpty()) chapterLinks = doc.select("div ul a[href*=ch-]");
        chapters = ParseHelper.getChaptersFromLinks(chapterLinks, onlineContent.getId(), "div.chapter-time", "dd/MM/yyyy");

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
        long minEpoch = Long.MAX_VALUE;
        int storedOrderOffset = ParseHelper.getMaxChapterOrder(storedChapters);
        for (Chapter chp : extraChapters) {
            chp.setOrder(++storedOrderOffset);
            if (chp.getUploadDate() > 0) minEpoch = Math.min(minEpoch, chp.getUploadDate());
            result.addAll(parseChapterImageFiles(onlineContent, chp, imgOffset + result.size() + 1, headers));
            if (processHalted.get()) break;
            progressPlus();
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        if (minEpoch > 0) {
            onlineContent.setUploadDate(minEpoch);
            onlineContent.setUpdatedProperties(true);
        }
        progressComplete();

        // Add cover if it's a first download
        if (storedChapters.isEmpty())
            result.add(ImageFile.newCover(onlineContent.getCoverImageUrl(), StatusContent.SAVED));

        return result;
    }

    @Override
    public List<ImageFile> parseChapterImageListImpl(@NonNull Chapter chapter, @NonNull Content content) throws Exception {
        String url = chapter.getUrl();

        if (!URLUtil.isValidUrl(url))
            throw new IllegalArgumentException("Invalid gallery URL : " + url);

        if (processedUrl.isEmpty()) processedUrl = url;
        Timber.d("Chapter URL: %s", url);

        EventBus.getDefault().register(this);

        List<ImageFile> result;
        try {
            result = parseChapterImageFiles(content, chapter, 1, null);
            ParseHelper.setDownloadParams(result, content.getSite().getUrl());
        } finally {
            EventBus.getDefault().unregister(this);
        }

        return result;
    }

    private List<ImageFile> parseChapterImageFiles(@NonNull Content content, @NonNull Chapter chp, int targetOrder, List<Pair<String, String>> headers) throws Exception {
        if (null == headers) headers = fetchHeaders(content);
        Document doc = getOnlineDocument(chp.getUrl(), headers, content.getSite().useHentoidAgent(), content.getSite().useWebviewAgent());
        if (doc != null) {
            List<Element> images = doc.select("#chapter-content img");
            List<String> imageUrls = Stream.of(images).map(ParseHelper::getImgSrc).toList();
            if (!imageUrls.isEmpty())
                return ParseHelper.urlsToImageFiles(imageUrls, targetOrder, StatusContent.SAVED, 1000, chp);
            else
                Timber.i("Chapter parsing failed for %s : no pictures found", chp.getUrl());
        } else {
            Timber.i("Chapter parsing failed for %s : no response", chp.getUrl());
        }
        return Collections.emptyList();
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        // We won't use that as parseImageListImpl is overriden directly
        return null;
    }

    @Override
    protected List<String> parseImages(@NonNull String chapterUrl, String downloadParams, List<Pair<String, String>> headers) throws Exception {
        /// We won't use that as parseChapterImageListImpl is overriden directly
        return null;
    }
}
