package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.activities.QueueActivity}
 * through a Bundle
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class QueueActivityBundle {
    private static final String KEY_IS_ERROR = "isError";
    private static final String KEY_CONTENT_HASH = "contentHash";

    private QueueActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setIsErrorsTab(boolean isError) {
            bundle.putBoolean(KEY_IS_ERROR, isError);
        }

        public void setContentHash(long contentHash) {
            bundle.putLong(KEY_CONTENT_HASH, contentHash);
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

        public boolean isErrorsTab() {
            return bundle.getBoolean(KEY_IS_ERROR, false);
        }

        public long contentHash() {
            return bundle.getLong(KEY_CONTENT_HASH, 0);
        }
    }
}
