package me.devsaki.hentoid.parsers.images;

import static me.devsaki.hentoid.util.network.HttpHelper.getOnlineDocument;

import android.util.Pair;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.json.sources.PixivGalleryMetadata;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.exception.EmptyResultException;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

public class PixivParser extends BaseImageListParser {

    @Override
    protected List<String> parseImages(@NonNull Content content) throws Exception {
        EventBus.getDefault().register(this);

        List<String> result = Collections.emptyList();
        try {
            boolean useMobileAgent = Site.PIXIV.useMobileAgent();
            boolean useHentoidAgent = Site.PIXIV.useHentoidAgent();
            boolean useWebviewAgent = Site.PIXIV.useWebviewAgent();

            String cookieStr = HttpHelper.getCookies(
                    content.getGalleryUrl(),
                    null,
                    useMobileAgent,
                    useHentoidAgent,
                    useWebviewAgent);

            List<Pair<String, String>> headers = new ArrayList<>();
            headers.add(new Pair<>(HttpHelper.HEADER_COOKIE_KEY, cookieStr));

            String metadataUrl = Site.PIXIV.getUrl() + "ajax/illust/" + content.getUniqueSiteId() + "/pages?lang=en";
            Document galleryMetadataDoc = getOnlineDocument(metadataUrl, headers, useHentoidAgent, useWebviewAgent);
            if (galleryMetadataDoc != null) {
                PixivGalleryMetadata galleryMetadata = JsonHelper.jsonToObject(galleryMetadataDoc.toString(), PixivGalleryMetadata.class);
                if (galleryMetadata.isError())
                    throw new EmptyResultException(galleryMetadata.getMessage());
                result = galleryMetadata.getPageUrls();
            }
        } catch (Exception e) {
            Timber.w(e);
            throw new EmptyResultException(StringHelper.protect(e.getMessage()));
        }
        return result;
    }
}
