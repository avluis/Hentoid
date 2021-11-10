package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.ImportWorker}
 * through a Bundle
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class ImportActivityBundle {
    private static final String KEY_REFRESH = "refresh";
    private static final String KEY_REFRESH_RENAME = "rename";
    private static final String KEY_REFRESH_CLEAN_NO_JSON = "cleanNoJson";
    private static final String KEY_REFRESH_CLEAN_NO_IMAGES = "cleanNoImages";

    private ImportActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setRefresh(boolean refresh) {
            bundle.putBoolean(KEY_REFRESH, refresh);
        }

        public void setRefreshRename(boolean rename) {
            bundle.putBoolean(KEY_REFRESH_RENAME, rename);
        }

        public void setRefreshCleanNoJson(boolean refresh) {
            bundle.putBoolean(KEY_REFRESH_CLEAN_NO_JSON, refresh);
        }

        public void setRefreshCleanNoImages(boolean refresh) {
            bundle.putBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, refresh);
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

        public boolean getRefresh() {
            return bundle.getBoolean(KEY_REFRESH, false);
        }

        public boolean getRefreshRename() {
            return bundle.getBoolean(KEY_REFRESH_RENAME, false);
        }

        public boolean getRefreshCleanNoJson() {
            return bundle.getBoolean(KEY_REFRESH_CLEAN_NO_JSON, false);
        }

        public boolean getRefreshCleanNoImages() {
            return bundle.getBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, false);
        }
    }
}
