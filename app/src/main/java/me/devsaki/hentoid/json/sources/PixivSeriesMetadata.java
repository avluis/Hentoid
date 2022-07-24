package me.devsaki.hentoid.json.sources;

import static me.devsaki.hentoid.util.ContentHelper.KEY_DL_PARAMS_NB_CHAPTERS;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.StringHelper;

/**
 * Data structure for Pixiv's "series" mobile endpoint
 */
@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class PixivSeriesMetadata {

    private Boolean error;
    private String message;
    private SeriesBody body;

    private static class SeriesBody {
        private SeriesData series;
    }

    private static class SeriesData {
        private String id;
        private String userId;
        private String title;
        private String coverImage;
        private String workCount;

        String getNbIllust() {
            if (workCount != null && StringHelper.isNumeric(workCount)) return workCount;
            else return "0";
        }

        String getId() {
            return StringHelper.protect(id);
        }

        String getUserId() {
            return StringHelper.protect(userId);
        }

        String getTitle() {
            return StringHelper.protect(title);
        }

        String getCoverUrl() {
            return StringHelper.protect(coverImage);
        }
    }

    @Nullable
    public Content update(@NonNull final Content content, @Nonnull String url, boolean updateImages) {
        // Determine the prefix the user is navigating with (i.e. with or without language path)
        content.setSite(Site.PIXIV);

        if (error || null == body || null == body.series)
            return content.setStatus(StatusContent.IGNORED);
        SeriesData data = body.series;

        content.setTitle(StringHelper.removeNonPrintableChars(data.getTitle()));
        content.setUniqueSiteId(data.getId());

        content.setUrl("user/" + data.getUserId() + "/series/" + data.getId());

        content.setCoverImageUrl(data.getCoverUrl());
//        content.setUploadDate(

        Map<String, String> downloadParams = new HashMap<>();
        downloadParams.put(KEY_DL_PARAMS_NB_CHAPTERS, data.getNbIllust());
        content.setDownloadParams(JsonHelper.serializeToJson(downloadParams, JsonHelper.MAP_STRINGS));

        if (updateImages) {
            content.setImageFiles(Collections.emptyList());
            content.setQtyPages(0);
        }

        return content;
    }
}
