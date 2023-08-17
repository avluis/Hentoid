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
    private static final String KEY_CONTENT_PURGE_KEEPCOVERS = "contentPurgeKeepCovers";
    private static final String KEY_GROUP_IDS = "groupIds";
    private static final String KEY_QUEUE_IDS = "queueIds";
    private static final String KEY_DELETE_ALL_QUEUE_RECORDS = "deleteAllQueueRecords";
    private static final String KEY_DELETE_GROUPS_ONLY = "deleteGroupsOnly";
    private static final String KEY_DELETE_ALL_CONTENT_EXCEPT_FAVS_B = "deleteAllContentExceptFavsB";
    private static final String KEY_DELETE_ALL_CONTENT_EXCEPT_FAVS_G = "deleteAllContentExceptFavsG";
    private static final String KEY_DL_PREPURGE = "downloadPrepurge";

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

        public void setContentPurgeKeepCovers(boolean value) {
            builder.putBoolean(KEY_CONTENT_PURGE_KEEPCOVERS, value);
        }

        public void setGroupIds(List<Long> value) {
            builder.putLongArray(KEY_GROUP_IDS, Helper.getPrimitiveArrayFromList(value));
        }

        public void setQueueIds(List<Long> value) {
            builder.putLongArray(KEY_QUEUE_IDS, Helper.getPrimitiveArrayFromList(value));
        }

        public void setDeleteAllQueueRecords(boolean value) {
            builder.putBoolean(KEY_DELETE_ALL_QUEUE_RECORDS, value);
        }

        public void setDeleteGroupsOnly(boolean value) {
            builder.putBoolean(KEY_DELETE_GROUPS_ONLY, value);
        }

        public void setDeleteAllContentExceptFavsBooks(boolean value) {
            builder.putBoolean(KEY_DELETE_ALL_CONTENT_EXCEPT_FAVS_B, value);
        }

        public void setDeleteAllContentExceptFavsGroups(boolean value) {
            builder.putBoolean(KEY_DELETE_ALL_CONTENT_EXCEPT_FAVS_G, value);
        }

        public void setDownloadPrepurge(boolean value) {
            builder.putBoolean(KEY_DL_PREPURGE, value);
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

        public boolean getContentPurgeKeepCovers() {
            return data.getBoolean(KEY_CONTENT_PURGE_KEEPCOVERS, false);
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

        public boolean isDeleteAllQueueRecords() {
            return data.getBoolean(KEY_DELETE_ALL_QUEUE_RECORDS, false);
        }

        public boolean isDeleteGroupsOnly() {
            return data.getBoolean(KEY_DELETE_GROUPS_ONLY, false);
        }

        public boolean isDeleteAllContentExceptFavsBooks() {
            return data.getBoolean(KEY_DELETE_ALL_CONTENT_EXCEPT_FAVS_B, false);
        }

        public boolean isDeleteAllContentExceptFavsGroups() {
            return data.getBoolean(KEY_DELETE_ALL_CONTENT_EXCEPT_FAVS_G, false);
        }

        public boolean isDownloadPrepurge() {
            return data.getBoolean(KEY_DL_PREPURGE, false);
        }
    }
}
