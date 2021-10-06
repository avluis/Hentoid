package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.ContentHelper.KEY_DL_PARAMS_NB_CHAPTERS;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

public class PixivParser extends BaseImageListParser {

    private static final int MAX_QUERY_WINDOW = 30;

    private final ParseProgress progress = new ParseProgress();

    @Override
    public List<ImageFile> parseImageList(@NonNull Content content) throws Exception {
        EventBus.getDefault().register(this);

        List<ImageFile> result;
        try {
            result = getPages(content);
        } finally {
            EventBus.getDefault().unregister(this);
        }

        progress.complete();

        return result;
    }

    private List<ImageFile> getPages(@NonNull Content content) throws Exception {

        try {
            boolean useMobileAgent = Site.PIXIV.useMobileAgent();
            boolean useHentoidAgent = Site.PIXIV.useHentoidAgent();
            boolean useWebviewAgent = Site.PIXIV.useWebviewAgent();

            String cookieStr = HttpHelper.getCookies(
                    content.getGalleryUrl(), null,
                    useMobileAgent, useHentoidAgent, useWebviewAgent);

            if (content.getUrl().contains("/series/")) return parseSeries(content, cookieStr);
            else if (content.getUrl().contains("/artworks/"))
                return parseIllust(content, cookieStr);
            else if (content.getUrl().contains("user/"))
                return parseUser(content, cookieStr);
        } catch (Exception e) {
            Timber.w(e);
            throw new EmptyResultException(StringHelper.protect(e.getMessage()));
        }
        return Collections.emptyList();
    }

    private List<ImageFile> parseIllust(@NonNull Content content, @NonNull String cookieStr) throws Exception {
        PixivIllustPagesMetadata galleryMetadata = PixivServer.API.getIllustPages(content.getUniqueSiteId(), cookieStr).execute().body();
        if (null == galleryMetadata || galleryMetadata.isError()) {
            String message = "";
            if (galleryMetadata != null) message = galleryMetadata.getMessage();
            throw new EmptyResultException(message);
        }
        return ParseHelper.urlsToImageFiles(galleryMetadata.getPageUrls(), content.getCoverImageUrl(), StatusContent.SAVED);
    }

    private List<ImageFile> parseSeries(@NonNull Content content, @NonNull String cookieStr) throws IOException {
        String[] seriesIdParts = content.getUniqueSiteId().split("/");
        String seriesId = seriesIdParts[seriesIdParts.length - 1];
        if (seriesId.contains("?")) {
            seriesId = seriesId.substring(0, seriesId.indexOf("?"));
        }

        // Retrieve the number of Illusts
        String nbChaptersStr = ContentHelper.parseDownloadParams(content.getDownloadParams()).get(KEY_DL_PARAMS_NB_CHAPTERS);
        if (null == nbChaptersStr || !StringHelper.isNumeric(nbChaptersStr))
            throw new IllegalArgumentException("Chapter count not saved");
        int nbChapters = Integer.parseInt(nbChaptersStr);

        if (!progress.hasStarted())
            progress.start(content.getId(), nbChapters);

        // Page to list all Illust IDs
        List<Chapter> chapters = new ArrayList<>();
        while (chapters.size() < nbChapters) {
            if (processHalted) break;
            int chaptersToRead = Math.min(nbChapters - chapters.size(), MAX_QUERY_WINDOW);
            PixivSeriesIllustMetadata seriesContentMetadata = PixivServer.API.getSeriesIllusts(seriesId, chaptersToRead, chapters.size(), cookieStr).execute().body();
            if (null == seriesContentMetadata || seriesContentMetadata.isError()) {
                String message = "Unreachable series illust";
                if (seriesContentMetadata != null)
                    message = seriesContentMetadata.getMessage();
                throw new IllegalArgumentException(message);
            }
            chapters.addAll(seriesContentMetadata.getChapters(content.getId()));
        }
        // Put back chapters in reading order
        Collections.reverse(chapters);

        // Retrieve all Illust detailed info
        List<ImageFile> result = new ArrayList<>();
        result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));
        int order = 1;
        for (Chapter ch : chapters) {
            if (processHalted) break;
            PixivIllustMetadata illustMetadata = PixivServer.API.getIllustMetadata(ch.getUniqueId(), cookieStr).execute().body();
            if (null == illustMetadata || illustMetadata.isError()) {
                String message = "Unreachable illust";
                if (illustMetadata != null)
                    message = illustMetadata.getMessage();
                throw new IllegalArgumentException(message);
            }
            List<String> pageUrls = illustMetadata.getPageUrls();
            result.addAll(ParseHelper.urlsToImageFiles(pageUrls, order, StatusContent.SAVED, ch, 1000));
            order += pageUrls.size();
            progress.advance();
        }
        return result;
    }

    private List<ImageFile> parseUser(@NonNull Content content, @NonNull String cookieStr) throws IOException {
        String[] userIdParts = content.getUniqueSiteId().split("/");
        String userId = userIdParts[userIdParts.length - 1];
        if (userId.contains("?")) {
            userId = userId.substring(0, userId.indexOf("?"));
        }

        // Retrieve the list of Illusts IDs
        PixivUserIllustMetadata userIllustsMetadata = PixivServer.API.getUserIllusts(userId, cookieStr).execute().body();
        if (null == userIllustsMetadata || userIllustsMetadata.isError()) {
            String message = "Unreachable user illusts";
            if (userIllustsMetadata != null)
                message = userIllustsMetadata.getMessage();
            throw new IllegalArgumentException(message);
        }

        List<String> illustIds = userIllustsMetadata.getIllustIds();
        if (!progress.hasStarted())
            progress.start(content.getId(), illustIds.size());

        // Cycle through all Illusts
        List<ImageFile> result = new ArrayList<>();
        result.add(ImageFile.newCover(content.getCoverImageUrl(), StatusContent.SAVED));
        int imgOrder = 1;
        int chpOrder = 0;
        for (String illustId : illustIds) {
            if (processHalted) break;
            PixivIllustMetadata illustMetadata = PixivServer.API.getIllustMetadata(illustId, cookieStr).execute().body();
            if (null == illustMetadata || illustMetadata.isError()) {
                String message = "Unreachable illust";
                if (illustMetadata != null)
                    message = illustMetadata.getMessage();
                throw new IllegalArgumentException(message);
            }

            Chapter chp = new Chapter(chpOrder++, illustMetadata.getUrl(), illustMetadata.getTitle()).setUniqueId(illustMetadata.getId()).setContentId(content.getId());
            List<String> pageUrls = illustMetadata.getPageUrls();
            result.addAll(ParseHelper.urlsToImageFiles(pageUrls, imgOrder, StatusContent.SAVED, chp, 1000));
            imgOrder += pageUrls.size();
            progress.advance();
        }
        return result;
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageList is overriden directly
        return null;
    }
}
