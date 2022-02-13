package me.devsaki.hentoid.parsers.content;

import android.webkit.CookieManager;

import androidx.annotation.NonNull;

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
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        CookieManager mgr = CookieManager.getInstance();
        String cookiesStr = mgr.getCookie(".exhentai.org");

        String[] galleryUrlParts = url.split("/");
        if (galleryUrlParts.length > 5) {
            EHentaiGalleryQuery query = new EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5]);

            try {
                EHentaiGalleriesMetadata metadata = EHentaiServer.exentaiApi.getGalleryMetadata(query, cookiesStr).execute().body();
                if (metadata != null)
                    return metadata.update(content, url, Site.EXHENTAI, updateImages);
            } catch (IOException e) {
                Timber.e(e, "Error parsing content.");
            }
        }
        return new Content().setSite(Site.EXHENTAI).setStatus(StatusContent.IGNORED);
    }
}
