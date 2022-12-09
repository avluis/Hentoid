package me.devsaki.hentoid.workers.data;

import androidx.work.Data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.workers.PrimaryImportWorker;

/**
 * Helper class to transfer data from any Activity to {@link PrimaryImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class UpdateJsonData {
    private static final String KEY_IDS = "content_ids";
    private static final String KEY_UPDATE_GROUPS = "update_groups";

    private UpdateJsonData() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Data.Builder builder = new Data.Builder();

        public void setContentIds(long[] data) {
            builder.putLongArray(KEY_IDS, data);
        }

        public void setUpdateGroups(boolean data) {
            builder.putBoolean(KEY_UPDATE_GROUPS, data);
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

        @Nullable
        public long[] getContentIds() {
            return data.getLongArray(KEY_IDS);
        }

        public boolean getUpdateGroups() {
            return data.getBoolean(KEY_UPDATE_GROUPS, false);
        }
    }
}
