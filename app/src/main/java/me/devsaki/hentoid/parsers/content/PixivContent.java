package me.devsaki.hentoid.parsers.content;

import androidx.annotation.NonNull;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.PixivIllustMetadata;
import me.devsaki.hentoid.retrofit.sources.PixivServer;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

public class PixivContent extends BaseContentParser {

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String bookId = "";

        String[] urlParts = url.split("/");
        String id = urlParts[urlParts.length - 1];
        String entity = urlParts[urlParts.length - 2];

        if (entity.equals("artworks")) bookId = id;

        if (!bookId.isEmpty()) {
            try {
                String cookieStr = HttpHelper.getCookies(
                        url,
                        null,
                        Site.PIXIV.useMobileAgent(),
                        Site.PIXIV.useHentoidAgent(),
                        Site.PIXIV.useWebviewAgent()
                );

                PixivIllustMetadata metadata = PixivServer.API.getIllustMetadata(bookId, cookieStr).execute().body();
                if (metadata != null) return metadata.update(content, url, updateImages);
            } catch (IOException e) {
                Timber.e(e, "Error parsing content.");
            }
        }
        return new Content().setSite(Site.PIXIV).setStatus(StatusContent.IGNORED);
    }
}
