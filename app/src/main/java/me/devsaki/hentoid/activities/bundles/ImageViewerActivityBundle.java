package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ImageViewerActivityBundle {
    private static final String KEY_URIS_STR = "urisStr";
    private static final String KEY_CONTENT_ID = "contentId";

    private ImageViewerActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setContentId(long contentId) {
            bundle.putLong(KEY_CONTENT_ID, contentId);
        }

        public void setUrisStr(List<String> uris) {
            ArrayList<String> uriList = new ArrayList<>(uris);
            bundle.putStringArrayList(KEY_URIS_STR, uriList);
        }

        public Bundle getBundle() {
            return bundle;
        }
    }

    public static final class Parser {

        private final Bundle bundle;

        public Parser(@Nonnull Bundle bundle) {
            this.bundle = bundle;
        }

        @Nullable
        public List<String> getUrisStr() {
            return bundle.getStringArrayList(KEY_URIS_STR);
        }

        public long getContentId() {
            return bundle.getLong(KEY_CONTENT_ID, 0);
        }
    }
}
