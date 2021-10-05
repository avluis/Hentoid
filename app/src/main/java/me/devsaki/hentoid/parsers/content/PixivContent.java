package me.devsaki.hentoid.parsers.content;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.sources.PixivIllustMetadata;
import me.devsaki.hentoid.json.sources.PixivSeriesMetadata;
import me.devsaki.hentoid.retrofit.sources.PixivServer;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

public class PixivContent extends BaseContentParser {

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        boolean isBook = true;

        String[] urlParts = url.split("/");
        String id = urlParts[urlParts.length - 1];
        String entity = urlParts[urlParts.length - 2];

        if (entity.equals("illust")) { // fetch Call / single gallery
            Uri uri = Uri.parse(url);
            id = uri.getQueryParameter("illust_id");
        } else if (entity.equals("series") || entity.equals("series_content")) { // Series
            isBook = false;
        }

        try {
            if (id != null && !id.isEmpty()) {
                String cookieStr = HttpHelper.getCookies(
                        url, null,
                        Site.PIXIV.useMobileAgent(), Site.PIXIV.useHentoidAgent(), Site.PIXIV.useWebviewAgent()
                );

                if (isBook) {
                    PixivIllustMetadata metadata = PixivServer.API.getIllustMetadata(id, cookieStr).execute().body();
                    if (metadata != null) return metadata.update(content, url, updateImages);
                } else {
                    PixivSeriesMetadata seriesData = PixivServer.API.getSeriesMetadata(id, cookieStr).execute().body();
                    if (seriesData != null) return seriesData.update(content, url, updateImages);
                }
            }
        } catch (IOException e) {
            Timber.e(e, "Error parsing content.");
        }
        return new Content().setSite(Site.PIXIV).setStatus(StatusContent.IGNORED);
    }
}
