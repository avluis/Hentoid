package me.devsaki.hentoid.workers.data;

import android.os.Bundle;

import androidx.work.Data;

import javax.annotation.Nonnull;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.UpdateDownloadWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class UpdateDownloadData {
    private static final String KEY_URL = "url";

    private UpdateDownloadData() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Data.Builder builder = new Data.Builder();
        private final Bundle bundle = new Bundle();

        public Builder setUrl(String data) {
            builder.putString(KEY_URL, data);
            bundle.putString(KEY_URL, data);
            return this;
        }

        public Data getData() {
            return builder.build();
        }

        public Bundle getBundle() {
            return bundle;
        }
    }

    public static final class Parser {

        private final Data data;
        private final Bundle bundle;

        public Parser(@Nonnull Data data) {
            this.data = data;
            this.bundle = null;
        }

        public Parser(@Nonnull Bundle bundle) {
            this.bundle = bundle;
            this.data = null;
        }

        public String getUrl() {
            if (data != null)
                return data.getString(KEY_URL);
            else if (bundle != null)
                return bundle.getString(KEY_URL);
            else return "";
        }
    }
}
