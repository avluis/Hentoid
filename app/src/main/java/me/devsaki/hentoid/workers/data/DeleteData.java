package me.devsaki.hentoid.workers.data;

import androidx.work.Data;

import java.util.List;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.util.Helper;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.DeleteWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class DeleteData {
    private static final String KEY_CONTENT_IDS = "contentIds";
    private static final String KEY_CONTENT_PURGE_IDS = "contentPurgeIds";
    private static final String KEY_GROUP_IDS = "groupIds";
    private static final String KEY_QUEUE_IDS = "queueIds";
    private static final String KEY_DELETE_GROUPS_ONLY = "deleteGroupsOnly";
    private static final String KEY_DELETE_ALL_CONTENT_EXCEPT_FAVS = "deleteAllContentExceptFavs";

    private DeleteData() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Data.Builder builder = new Data.Builder();

        public void setContentIds(List<Long> value) {
            builder.putLongArray(KEY_CONTENT_IDS, Helper.getPrimitiveArrayFromList(value));
        }

        public void setContentPurgeIds(List<Long> value) {
            builder.putLongArray(KEY_CONTENT_PURGE_IDS, Helper.getPrimitiveArrayFromList(value));
        }

        public void setGroupIds(List<Long> value) {
            builder.putLongArray(KEY_GROUP_IDS, Helper.getPrimitiveArrayFromList(value));
        }

        public void setQueueIds(List<Long> value) {
            builder.putLongArray(KEY_QUEUE_IDS, Helper.getPrimitiveArrayFromList(value));
        }

        public void setDeleteGroupsOnly(boolean value) {
            builder.putBoolean(KEY_DELETE_GROUPS_ONLY, value);
        }

        public void setDeleteAllContentExceptFavs(boolean value) {
            builder.putBoolean(KEY_DELETE_ALL_CONTENT_EXCEPT_FAVS, value);
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

        public long[] getContentIds() {
            long[] storedValue = data.getLongArray(KEY_CONTENT_IDS);
            if (null != storedValue) return storedValue;
            else return new long[]{};
        }

        public long[] getContentPurgeIds() {
            long[] storedValue = data.getLongArray(KEY_CONTENT_PURGE_IDS);
            if (null != storedValue) return storedValue;
            else return new long[]{};
        }

        public long[] getGroupIds() {
            long[] storedValue = data.getLongArray(KEY_GROUP_IDS);
            if (null != storedValue) return storedValue;
            else return new long[]{};
        }

        public long[] getQueueIds() {
            long[] storedValue = data.getLongArray(KEY_QUEUE_IDS);
            if (null != storedValue) return storedValue;
            else return new long[]{};
        }

        public boolean isDeleteGroupsOnly() {
            return data.getBoolean(KEY_DELETE_GROUPS_ONLY, false);
        }

        public boolean isDeleteAllContentExceptFavs() {
            return data.getBoolean(KEY_DELETE_ALL_CONTENT_EXCEPT_FAVS, false);
        }
    }
}
