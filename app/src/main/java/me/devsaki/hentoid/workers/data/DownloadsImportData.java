package me.devsaki.hentoid.workers.data;

import android.net.Uri;

import androidx.work.Data;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.util.StringHelper;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.DownloadsImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class DownloadsImportData {
    private static final String KEY_FILE_URI = "file_uri";

    private DownloadsImportData() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Data.Builder builder = new Data.Builder();

        public void setFileUri(Uri data) {
            builder.putString(KEY_FILE_URI, data.toString());
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

        public String getFileUri() {
            return StringHelper.protect(data.getString(KEY_FILE_URI));
        }
    }
}
