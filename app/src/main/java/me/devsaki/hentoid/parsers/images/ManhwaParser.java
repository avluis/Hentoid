package me.devsaki.hentoid.parsers.images;

import android.util.Pair;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import com.annimon.stream.Optional;
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
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

/**
 * Created by robb_w on 2020/11
 * Handles parsing of content from manhwahentai.me
 */
public class ManhwaParser extends BaseImageListParser {

    @Override
    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        String readerUrl = content.getReaderUrl();

        if (!URLUtil.isValidUrl(readerUrl))
            throw new IllegalArgumentException("Invalid gallery URL : " + readerUrl);

        Timber.d("Gallery URL: %s", readerUrl);

        EventBus.getDefault().register(this);

        List<ImageFile> result;
        try {
            result = parseImageFiles(content);
            ParseHelper.setDownloadParams(result, content.getSite().getUrl());
        } finally {
            EventBus.getDefault().unregister(this);
        }

        Timber.d("%s", result);

        return result;
    }

    private List<ImageFile> parseImageFiles(@NonNull Content content) throws Exception {
        List<ImageFile> result = new ArrayList<>();

        List<Pair<String, String>> headers = new ArrayList<>();
        HttpHelper.addCurrentCookiesToHeader(content.getGalleryUrl(), headers);

        // If the stored content has chapters already, save them for comparison
        List<Chapter> storedChapters = content.getChapters();
        if (storedChapters != null) storedChapters = Stream.of(storedChapters).toList();
        else storedChapters = Collections.emptyList();

        // 1- Detect chapters on gallery page
        List<Chapter> chapters = new ArrayList<>();
        Document doc = getOnlineDocument(content.getGalleryUrl(), headers, Site.MANHWA.useHentoidAgent(), Site.MANHWA.useWebviewAgent());
        if (doc != null) {
            List<Element> chapterLinks = doc.select("[class^=wp-manga-chapter] a");
            Collections.reverse(chapterLinks); // Put the chapters in the correct reading order
            chapters = ParseHelper.getChaptersFromLinks(chapterLinks, content.getId());
        }

        // Use chapter folder as a differentiator (as the whole URL may evolve)
        List<Chapter> extraChapters = ParseHelper.getExtraChapters(storedChapters, chapters);

        progressStart(content.getId(), extraChapters.size());

        // Start numbering extra images right after the last position of stored and chaptered images
        int orderOffset = 0;
        if (!storedChapters.isEmpty()) {
            Optional<Integer> optOrder = Stream.of(storedChapters)
                    .map(Chapter::getImageFiles)
                    .withoutNulls()
                    .flatMap(Stream::of)
                    .map(ImageFile::getOrder)
                    .max(Integer::compareTo);
            if (optOrder.isPresent()) orderOffset = optOrder.get();
        }

        // 2- Open each chapter URL and get the image data until all images are found
        for (Chapter chp : extraChapters) {
            if (processHalted) break;
            doc = getOnlineDocument(chp.getUrl(), headers, Site.MANHWA.useHentoidAgent(), Site.MANHWA.useWebviewAgent());
            if (doc != null) {
                List<Element> images = doc.select(".reading-content img");
                List<String> urls = Stream.of(images).map(i -> i.attr("src").trim()).filterNot(String::isEmpty).toList();
                result.addAll(ParseHelper.urlsToImageFiles(urls, orderOffset + result.size() + 1, StatusContent.SAVED, chp, 1000));
            }
            progressPlus();
        }
        progressComplete();

        // Add cover
        result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));

        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted) throw new PreparationInterruptedException();

        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageList is overriden directly
        return null;
    }
}
