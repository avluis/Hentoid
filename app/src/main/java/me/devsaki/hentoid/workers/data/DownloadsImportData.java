package me.devsaki.hentoid.workers.data;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;

import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.DownloadsImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class DownloadsImportData {
    private static final String KEY_FILE_URI = "file_uri";
    private static final String KEY_QUEUE_POSITION = "queue_position";
    private static final String KEY_IMPORT_AS_STREAMED = "as_streamed";

    private DownloadsImportData() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Data.Builder builder = new Data.Builder();

        public void setFileUri(Uri data) {
            builder.putString(KEY_FILE_URI, data.toString());
        }

        public void setQueuePosition(int data) {
            builder.putInt(KEY_QUEUE_POSITION, data);
        }

        public void setImportAsStreamed(boolean data) {
            builder.putBoolean(KEY_IMPORT_AS_STREAMED, data);
        }

        public Data getData() {
            return builder.build();
        }
    }

    public static final class Parser {

        private final Data data;

        public Parser(@NonNull Data data) {
            this.data = data;
        }

        public String getFileUri() {
            return StringHelper.protect(data.getString(KEY_FILE_URI));
        }

        public int getQueuePosition() {
            return data.getInt(KEY_QUEUE_POSITION, Preferences.Default.QUEUE_NEW_DOWNLOADS_POSITION);
        }

        public boolean getImportAsStreamed() {
            return data.getBoolean(KEY_IMPORT_AS_STREAMED, false);
        }
    }
}
