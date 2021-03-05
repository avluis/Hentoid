package me.devsaki.hentoid.workers.data;

import androidx.work.Data;

import javax.annotation.Nonnull;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.ImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class ImportData {
    private static final String KEY_REFRESH = "refresh";
    private static final String KEY_REFRESH_RENAME = "rename";
    private static final String KEY_REFRESH_CLEAN_NO_JSON = "cleanNoJson";
    private static final String KEY_REFRESH_CLEAN_NO_IMAGES = "cleanNoImages";

    private ImportData() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Data.Builder builder = new Data.Builder();

        public void setRefresh(boolean refresh) {
            builder.putBoolean(KEY_REFRESH, refresh);
        }

        public void setRefreshRename(boolean rename) {
            builder.putBoolean(KEY_REFRESH_RENAME, rename);
        }

        public void setRefreshCleanNoJson(boolean refresh) {
            builder.putBoolean(KEY_REFRESH_CLEAN_NO_JSON, refresh);
        }

        public void setRefreshCleanNoImages(boolean refresh) {
            builder.putBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, refresh);
        }

        public Data getData() {
            return builder.build();
        }
    }

    public static final class Parser {

        private final Data data;

        public Parser(@Nonnull Data data) {
            this.data = data;
        }

        public boolean getRefresh() {
            return data.getBoolean(KEY_REFRESH, false);
        }

        public boolean getRefreshRename() {
            return data.getBoolean(KEY_REFRESH_RENAME, false);
        }

        public boolean getRefreshCleanNoJson() {
            return data.getBoolean(KEY_REFRESH_CLEAN_NO_JSON, false);
        }

        public boolean getRefreshCleanNoImages() {
            return data.getBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, false);
        }
    }
}
