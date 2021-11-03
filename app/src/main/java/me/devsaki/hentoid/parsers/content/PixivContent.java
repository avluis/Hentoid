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
import me.devsaki.hentoid.json.sources.PixivUserMetadata;
import me.devsaki.hentoid.retrofit.sources.PixivServer;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.network.HttpHelper;
import timber.log.Timber;

public class PixivContent extends BaseContentParser {

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        String[] urlParts = url.split("/");
        String id = urlParts[urlParts.length - 1];
        if (id.contains("?")) {
            id = id.substring(0, id.indexOf("?"));
        }
        String entity = urlParts[urlParts.length - 2];

        Uri uri = Uri.parse(url);
        switch (entity) {
            case "artworks":  // fetch Call / single gallery
            case "illust":  // fetch Call / single gallery
                if (!StringHelper.isNumeric(id))
                    id = uri.getQueryParameter("illust_id");
                break;
            case "user":  // User
                if (!StringHelper.isNumeric(id))
                    id = uri.getQueryParameter("id");
                break;
            default:
                // Nothing specific
        }

        try {
            if (id != null && !id.isEmpty()) {
                String cookieStr = HttpHelper.getCookies(
                        url, null,
                        Site.PIXIV.useMobileAgent(), Site.PIXIV.useHentoidAgent(), Site.PIXIV.useWebviewAgent()
                );

                switch (entity) {
                    case "artworks":
                    case "illust":
                        PixivIllustMetadata metadata = PixivServer.API.getIllustMetadata(id, cookieStr).execute().body();
                        if (metadata != null) return metadata.update(content, url, updateImages);
                        break;
                    case "series_content":
                    case "series":
                        PixivSeriesMetadata seriesData = PixivServer.API.getSeriesMetadata(id, cookieStr).execute().body();
                        if (seriesData != null)
                            return seriesData.update(content, url, updateImages);
                        break;
                    case "user":
                        PixivUserMetadata userData = PixivServer.API.getUserMetadata(id, cookieStr).execute().body();
                        if (userData != null) return userData.update(content, url, updateImages);
                        break;
                    default:
                        // Nothing specific
                }
            }
        } catch (IOException e) {
            Timber.e(e, "Error parsing content.");
        }
        return new Content().setSite(Site.PIXIV).setStatus(StatusContent.IGNORED);
    }
}
