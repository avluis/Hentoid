package me.devsaki.hentoid.json.sources;

import java.util.Collections;
import java.util.List;

/**
 * Data structure for Pixiv's "user illusts" mobile endpoint
 */
@SuppressWarnings({"unused, MismatchedQueryAndUpdateOfCollection", "squid:S1172", "squid:S1068"})
public class PixivUserIllustMetadata {

    private Boolean error;
    private String message;
    private PixivUserIllusts body;

    public List<String> getIllustIds() {
        if (null == body || null == body.user_illust_ids) return Collections.emptyList();
        return body.user_illust_ids;
    }

    public boolean isError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    private static class PixivUserIllusts {
        private List<String> user_illust_ids;
    }
}
