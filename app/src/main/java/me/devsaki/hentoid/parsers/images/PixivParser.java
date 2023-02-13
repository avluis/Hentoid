package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.ContentHelper.KEY_DL_PARAMS_NB_CHAPTERS;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.PixivIllustMetadata;
import me.devsaki.hentoid.json.sources.PixivIllustPagesMetadata;
import me.devsaki.hentoid.json.sources.PixivSeriesIllustMetadata;
import me.devsaki.hentoid.json.sources.PixivUserIllustMetadata;
import me.devsaki.hentoid.parsers.ParseHelper;
import me.devsaki.hentoid.retrofit.sources.PixivServer;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.download.DownloadRateLimiter;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.exception.PreparationInterruptedException;
import me.devsaki.hentoid.util.network.HttpHelper;
import retrofit2.Response;
import timber.log.Timber;

public class PixivParser extends BaseImageListParser {

    private static final int MAX_QUERY_WINDOW = 30;

    @Override
    protected List<ImageFile> parseImageListImpl(@NonNull Content onlineContent, @Nullable Content storedContent) throws Exception {
        EventBus.getDefault().register(this);
        processedUrl = onlineContent.getGalleryUrl();

        List<ImageFile> result;
        try {
            result = getPages(onlineContent, storedContent);
        } finally {
            EventBus.getDefault().unregister(this);
        }

        return result;
    }

    private List<ImageFile> getPages(@NonNull Content onlineContent, @Nullable Content storedContent) throws Exception {

        try {
            boolean useMobileAgent = Site.PIXIV.useMobileAgent();
            boolean useHentoidAgent = Site.PIXIV.useHentoidAgent();
            boolean useWebviewAgent = Site.PIXIV.useWebviewAgent();

            String cookieStr = HttpHelper.getCookies(
                    onlineContent.getGalleryUrl(), null,
                    useMobileAgent, useHentoidAgent, useWebviewAgent);

            // API calls seem to be protected against request spam; 1 is arbitrary
            DownloadRateLimiter.INSTANCE.setRateLimit(1);

            if (onlineContent.getUrl().contains("/series/"))
                return parseSeries(onlineContent, storedContent, cookieStr);
            else if (onlineContent.getUrl().contains("/artworks/"))
                return parseIllust(onlineContent, cookieStr);
            else if (onlineContent.getUrl().contains("users/"))
                return parseUser(onlineContent, storedContent, cookieStr);
        } catch (Exception e) {
            Timber.d(e);
            throw new EmptyResultException(StringHelper.protect(e.getMessage()));
        }
        return Collections.emptyList();
    }

    private List<ImageFile> parseIllust(@NonNull Content content, @NonNull String cookieStr) throws Exception {
        DownloadRateLimiter.INSTANCE.take();
        PixivIllustPagesMetadata galleryMetadata = PixivServer.api.getIllustPages(content.getUniqueSiteId(), cookieStr).execute().body();
        if (null == galleryMetadata || galleryMetadata.isError()) {
            String message = "";
            if (galleryMetadata != null) message = galleryMetadata.getMessage();
            throw new EmptyResultException(message);
        }
        return ParseHelper.urlsToImageFiles(galleryMetadata.getPageUrls(), content.getCoverImageUrl(), StatusContent.SAVED);
    }

    private List<ImageFile> parseSeries(@NonNull Content onlineContent, @Nullable Content storedContent, @NonNull String cookieStr) throws Exception {
        String[] seriesIdParts = onlineContent.getUniqueSiteId().split("/");
        String seriesId = seriesIdParts[seriesIdParts.length - 1];
        if (seriesId.contains("?")) {
            seriesId = seriesId.substring(0, seriesId.indexOf("?"));
        }

        // Retrieve the number of Illusts
        String nbChaptersStr = ContentHelper.parseDownloadParams(onlineContent.getDownloadParams()).get(KEY_DL_PARAMS_NB_CHAPTERS);
        if (null == nbChaptersStr || !StringHelper.isNumeric(nbChaptersStr))
            throw new IllegalArgumentException("Chapter count not saved");
        int nbChapters = Integer.parseInt(nbChaptersStr);

        // List all Illust IDs (API is paged, hence the loop)
        List<Chapter> chapters = new ArrayList<>();
        while (chapters.size() < nbChapters) {
            DownloadRateLimiter.INSTANCE.take();
            if (processHalted.get()) break;
            int chaptersToRead = Math.min(nbChapters - chapters.size(), MAX_QUERY_WINDOW);
            DownloadRateLimiter.INSTANCE.take();
            PixivSeriesIllustMetadata seriesContentMetadata = PixivServer.api.getSeriesIllusts(seriesId, chaptersToRead, chapters.size(), cookieStr).execute().body();
            if (null == seriesContentMetadata || seriesContentMetadata.isError()) {
                String message = "Unreachable series illust";
                if (seriesContentMetadata != null)
                    message = seriesContentMetadata.getMessage();
                throw new IllegalArgumentException(message);
            }
            chapters.addAll(seriesContentMetadata.getChapters(onlineContent.getId()));
        }
        // Put back chapters in reading order
        Collections.reverse(chapters);


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
        int imgOffset = ParseHelper.getMaxImageOrder(storedChapters) + 1;

        // Retrieve all Illust detailed info
        List<ImageFile> result = new ArrayList<>();
        result.add(ImageFile.newCover(onlineContent.getCoverImageUrl(), StatusContent.SAVED));
        Set<Attribute> attrs = new HashSet<>();
        for (Chapter ch : extraChapters) {
            DownloadRateLimiter.INSTANCE.take();
            PixivIllustMetadata illustMetadata = PixivServer.api.getIllustMetadata(ch.getUniqueId(), cookieStr).execute().body();
            if (null == illustMetadata || illustMetadata.isError()) {
                String message = "Unreachable illust";
                if (illustMetadata != null)
                    message = illustMetadata.getMessage();
                throw new IllegalArgumentException(message);
            }

            List<Attribute> chapterAttrs = illustMetadata.getAttributes();
            attrs.addAll(chapterAttrs);

            List<ImageFile> chapterImages = illustMetadata.getImageFiles();
            for (ImageFile img : chapterImages)
                img.setOrder(imgOffset++).computeName(4).setChapter(ch);

            result.addAll(chapterImages);

            if (processHalted.get()) break;
            progressPlus();
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        onlineContent.putAttributes(attrs);
        onlineContent.setUpdatedProperties(true);

        progressComplete();
        return result;
    }

    private List<ImageFile> parseUser(@NonNull Content onlineContent, @Nullable Content storedContent, @NonNull String cookieStr) throws Exception {
        String[] userIdParts = onlineContent.getUniqueSiteId().split("/");
        String userId = userIdParts[userIdParts.length - 1];
        if (userId.contains("?")) {
            userId = userId.substring(0, userId.indexOf("?"));
        }

        // Retrieve the list of Illusts IDs (=chapters)
        DownloadRateLimiter.INSTANCE.take();
        PixivUserIllustMetadata userIllustsMetadata = PixivServer.api.getUserIllusts(userId, cookieStr).execute().body();
        if (null == userIllustsMetadata || userIllustsMetadata.isError()) {
            String message = "Unreachable user illusts";
            if (userIllustsMetadata != null)
                message = userIllustsMetadata.getMessage();
            throw new IllegalArgumentException(message);
        }

        // Detect extra chapters
        List<String> illustIds = userIllustsMetadata.getIllustIds();
        List<Chapter> storedChapters = null;
        if (storedContent != null) {
            storedChapters = storedContent.getChapters();
            if (storedChapters != null) storedChapters = Stream.of(storedChapters).toList();
        }
        if (null == storedChapters) storedChapters = Collections.emptyList();
        else illustIds = ParseHelper.getExtraChaptersbyId(storedChapters, illustIds);

        // Work on detected extra chapters
        progressStart(onlineContent, storedContent, illustIds.size());

        // Start numbering extra images & chapters right after the last position of stored and chaptered images & chapters
        int imgOffset = ParseHelper.getMaxImageOrder(storedChapters) + 1;
        int chpOffset = ParseHelper.getMaxChapterOrder(storedChapters) + 1;

        // Cycle through all Illusts
        List<ImageFile> result = new ArrayList<>();
        result.add(ImageFile.newCover(onlineContent.getCoverImageUrl(), StatusContent.SAVED));
        Set<Attribute> attrs = new HashSet<>();
        for (String illustId : illustIds) {
            DownloadRateLimiter.INSTANCE.take();
            Response<PixivIllustMetadata> resp = PixivServer.api.getIllustMetadata(illustId, cookieStr).execute();
            if (resp.code() >= 400)
                throw new IllegalArgumentException(String.format("Unreachable illust : code=%s (%s)", resp.code(), resp.message()));

            PixivIllustMetadata illustMetadata = resp.body();
            if (null == illustMetadata || illustMetadata.isError()) {
                String message;
                if (illustMetadata != null)
                    message = illustMetadata.getMessage();
                else
                    message = "no metadata";
                throw new IllegalArgumentException(String.format("Unreachable illust : %s", message));
            }

            List<Attribute> chapterAttrs = illustMetadata.getAttributes();
            attrs.addAll(chapterAttrs);

            Chapter chp = new Chapter(chpOffset++, illustMetadata.getUrl(), illustMetadata.getTitle()).setUniqueId(illustMetadata.getId()).setContentId(onlineContent.getId());
            List<ImageFile> chapterImages = illustMetadata.getImageFiles();
            for (ImageFile img : chapterImages)
                img.setOrder(imgOffset++).computeName(4).setChapter(chp);

            result.addAll(chapterImages);

            if (processHalted.get()) break;
            progressPlus();
        }
        // If the process has been halted manually, the result is incomplete and should not be returned as is
        if (processHalted.get()) throw new PreparationInterruptedException();

        onlineContent.putAttributes(attrs);
        onlineContent.putAttributes(attrs);
        onlineContent.setUpdatedProperties(true);

        progressComplete();
        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageList is overriden directly
        return null;
    }
}