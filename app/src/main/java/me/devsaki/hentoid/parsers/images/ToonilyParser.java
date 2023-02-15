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
import me.devsaki.hentoid.util.download.DownloadHelper;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

/**
 * Handles parsing of content from toonily.com
 */
public class ToonilyParser extends BaseImageListParser {

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
        HttpHelper.addCurrentCookiesToHeader(onlineContent.getGalleryUrl(), headers);

        // 1- Detect chapters on gallery page
        List<Chapter> chapters = new ArrayList<>();
        String reason = "";
        Document doc = getOnlineDocument(onlineContent.getGalleryUrl(), headers, Site.TOONILY.useHentoidAgent(), Site.TOONILY.useWebviewAgent());
        if (doc != null) {
            String canonicalUrl = DownloadHelper.getCanonicalUrl(doc);
            // Retrieve the chapters page chunk
            doc = HttpHelper.postOnlineDocument(
                    canonicalUrl + "ajax/chapters/",
                    headers,
                    Site.TOONILY.useHentoidAgent(), Site.TOONILY.useWebviewAgent(),
                    "",
                    HttpHelper.POST_MIME_TYPE
            );
            if (doc != null) {
                List<Element> chapterLinks = doc.select("[class^=wp-manga-chapter] a");
                chapters = ParseHelper.getChaptersFromLinks(chapterLinks, onlineContent.getId());
            } else {
                reason = "Chapters page couldn't be downloaded @ " + canonicalUrl;
            }
        } else {
            reason = "Index page couldn't be downloaded @ " + onlineContent.getGalleryUrl();
        }
        if (chapters.isEmpty())
            throw new EmptyResultException("Unable to detect chapters : " + reason);

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
        int storedOrderOffset = ParseHelper.getMaxChapterOrder(storedChapters);
        for (Chapter chp : extraChapters) {
            chp.setOrder(++storedOrderOffset);
            doc = getOnlineDocument(chp.getUrl(), headers, Site.TOONILY.useHentoidAgent(), Site.TOONILY.useWebviewAgent());
            if (doc != null) {
                List<Element> images = doc.select(".reading-content img");
                List<String> imageUrls = new ArrayList<>();
                for (Element e : images) {
                    String url = ParseHelper.getImgSrc(e);
                    if (!url.isEmpty()) imageUrls.add(url);
                }
                if (!imageUrls.isEmpty())
                    result.addAll(ParseHelper.urlsToImageFiles(imageUrls, imgOffset + result.size() + 1, StatusContent.SAVED, 1000, chp));
                else
                    Timber.i("Chapter parsing failed for %s : no pictures found", chp.getUrl());
            } else {
                Timber.i("Chapter parsing failed for %s : no response", chp.getUrl());
            }
            if (processHalted.get()) break;
            progressPlus();
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        // Add cover if it's a first download
        if (storedChapters.isEmpty())
            result.add(ImageFile.newCover(onlineContent.getCoverImageUrl(), StatusContent.SAVED));

        progressComplete();
        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageListImpl is overriden directly
        return null;
    }
}
