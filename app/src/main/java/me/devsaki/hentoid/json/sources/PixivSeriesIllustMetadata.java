package me.devsaki.hentoid.json.sources;

import com.annimon.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Chapter;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.util.StringHelper;

/**
 * Data structure for Pixiv's "series-content" mobile endpoint
 */
@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class PixivSeriesIllustMetadata {

    private Boolean error;
    private String message;
    private SeriesContentBody body;

    private static class SeriesContentBody {
        private List<SeriesContent> series_contents;
    }

    private static class SeriesContent {
        private String id;
        private String title;

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }
    }

    public boolean isError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public List<ImmutablePair<String, String>> getIllustIdTitles() {
        List<ImmutablePair<String, String>> result = new ArrayList<>();
        if (null == body || null == body.series_contents) return result;
        return Stream.of(body.series_contents).map(c -> new ImmutablePair<>(c.getId(), c.getTitle())).toList();
    }

    public List<Chapter> getChapters(long contentId) {
        List<Chapter> result = new ArrayList<>();
        if (null == body || null == body.series_contents) return result;
        int order = 0;
        for (SeriesContent sc : body.series_contents) {
            String forgedUrl = Site.PIXIV.getUrl() + "artworks/" + sc.getId();
            result.add(
                    new Chapter(order++, forgedUrl, StringHelper.removeNonPrintableChars(sc.getTitle()))
                            .setUniqueId(sc.getId())
                            .setContentId(contentId)
            );
        }
        return result;
    }
}
