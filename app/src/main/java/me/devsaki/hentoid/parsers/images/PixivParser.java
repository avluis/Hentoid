package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.ContentHelper.KEY_DL_PARAMS_NB_CHAPTERS;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.PixivGalleryMetadata;
import me.devsaki.hentoid.json.sources.PixivIllustMetadata;
import me.devsaki.hentoid.json.sources.PixivSeriesContentMetadata;
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
            List<String> pages = getPages(content);
            result = ParseHelper.urlsToImageFiles(pages, content.getCoverImageUrl(), StatusContent.SAVED);
        } finally {
            EventBus.getDefault().unregister(this);
        }

        progress.complete();

        return result;
    }

    private List<String> getPages(@NonNull Content content) throws Exception {

        try {
            boolean useMobileAgent = Site.PIXIV.useMobileAgent();
            boolean useHentoidAgent = Site.PIXIV.useHentoidAgent();
            boolean useWebviewAgent = Site.PIXIV.useWebviewAgent();

            String cookieStr = HttpHelper.getCookies(
                    content.getGalleryUrl(), null,
                    useMobileAgent, useHentoidAgent, useWebviewAgent);

            if (content.getUrl().contains("/series/")) {
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
                    PixivSeriesContentMetadata seriesContentMetadata = PixivServer.API.getSeriesIllust(content.getUniqueSiteId(), chaptersToRead, chapters.size(), cookieStr).execute().body();
                    if (null == seriesContentMetadata || seriesContentMetadata.isError()) {
                        String message = "Unreachable series illust";
                        if (seriesContentMetadata != null)
                            message = seriesContentMetadata.getMessage();
                        throw new IllegalArgumentException(message);
                    }
                    chapters.addAll(seriesContentMetadata.getChapters(content.getId()));
                }

                // Retrieve all Illust detailed info
                List<String> result = new ArrayList<>();
                for (Chapter ch : chapters) {
                    if (processHalted) break;
                    PixivIllustMetadata illustMetadata = PixivServer.API.getIllustMetadata(ch.getUniqueId(), cookieStr).execute().body();
                    if (null == illustMetadata || illustMetadata.isError()) {
                        String message = "Unreachable illust";
                        if (illustMetadata != null)
                            message = illustMetadata.getMessage();
                        throw new IllegalArgumentException(message);
                    }
                    result.addAll(illustMetadata.getPageUrls());
                    progress.advance();
                }
                return result;
            } else if (content.getUrl().contains("/artworks/")) { // Single Illust
                PixivGalleryMetadata galleryMetadata = PixivServer.API.getIllustPages(content.getUniqueSiteId(), cookieStr).execute().body();
                if (null == galleryMetadata || galleryMetadata.isError()) {
                    String message = "";
                    if (galleryMetadata != null) message = galleryMetadata.getMessage();
                    throw new EmptyResultException(message);
                }
                return galleryMetadata.getPageUrls();
            }
        } catch (Exception e) {
            Timber.w(e);
            throw new EmptyResultException(StringHelper.protect(e.getMessage()));
        }
        return Collections.emptyList();
    }

    @Override
    protected List<String> parseImages(@NonNull Content content) {
        /// We won't use that as parseImageList is overriden directly
        return null;
    }
}
