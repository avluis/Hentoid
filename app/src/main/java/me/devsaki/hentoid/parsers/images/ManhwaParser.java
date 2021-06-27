package me.devsaki.hentoid.parsers.images;

import android.util.Pair;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
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
        ParseHelper.addCurrentCookiesToHeader(content.getGalleryUrl(), headers);

        List<Chapter> chapters = content.getChapters();
        if (null == chapters || chapters.isEmpty()) return result;

        progressStart(content.getId(), chapters.size());

        // Open each chapter URL and get the image data until all images are found
        for (Chapter chp : chapters) {
            if (processHalted) break;
            final Document doc = getOnlineDocument(chp.getUrl(), headers, Site.MANHWA.useHentoidAgent(), Site.MANHWA.useWebviewAgent());
            if (doc != null) {
                List<Element> images = doc.select(".reading-content img");
                List<String> urls = Stream.of(images).map(i -> i.attr("src").trim()).filterNot(String::isEmpty).toList();
                result.addAll(ParseHelper.urlsToImageFiles(urls, result.size() + 1, StatusContent.SAVED, chp));
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
