package me.devsaki.hentoid.parsers.content;

import android.webkit.CookieManager;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.EHentaiGalleriesMetadata;
import me.devsaki.hentoid.json.sources.EHentaiGalleryQuery;
import me.devsaki.hentoid.retrofit.sources.EHentaiServer;
import timber.log.Timber;

public class ExhentaiContent extends BaseContentParser {

    @Nullable
    public Content toContent(@Nonnull String url) {
        CookieManager mgr = CookieManager.getInstance();
        String cookiesStr = mgr.getCookie(".exhentai.org");

        String[] galleryUrlParts = url.split("/");
        EHentaiGalleryQuery query = new EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5]);

        try {
            EHentaiGalleriesMetadata metadata = EHentaiServer.EXHENTAI_API.getGalleryMetadata(query, cookiesStr).execute().body();
            return metadata.toContent(url, Site.EXHENTAI);
        } catch (IOException e) {
            Timber.e(e, "Error parsing content.");
            Content result = new Content();
            result.setSite(Site.EXHENTAI).setStatus(StatusContent.IGNORED);
            return result;
        }
    }
}
