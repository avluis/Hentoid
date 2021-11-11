package me.devsaki.hentoid.parsers.content;

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

public class EhentaiContent extends BaseContentParser {

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String[] galleryUrlParts = url.split("/");
        EHentaiGalleryQuery query = new EHentaiGalleryQuery(galleryUrlParts[4], galleryUrlParts[5]);

        try {
            EHentaiGalleriesMetadata metadata = EHentaiServer.EHENTAI_API.getGalleryMetadata(query, null).execute().body();
            if (metadata != null) return metadata.update(content, url, Site.EHENTAI, updateImages);
        } catch (IOException e) {
            Timber.e(e, "Error parsing content.");
        }
        return new Content().setSite(Site.EXHENTAI).setStatus(StatusContent.IGNORED);
    }
}
