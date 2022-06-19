package me.devsaki.hentoid.workers.data;

import androidx.annotation.Nullable;
import androidx.work.Data;

import javax.annotation.Nonnull;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.MetadataImportWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class MetadataImportData {
    private static final String KEY_JSON_URI = "jsonUri";
    private static final String KEY_ADD = "add";
    private static final String KEY_IMPORT_LIBRARY = "importLibrary";
    private static final String KEY_EMPTY_BOOKS_OPTION = "emptyBooksOption";
    private static final String KEY_IMPORT_QUEUE = "importQueue";
    private static final String KEY_IMPORT_CUSTOM_GROUPS = "importCustomGroups";
    private static final String KEY_IMPORT_BOOKMARKS = "importBookmarks";

    private MetadataImportData() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Data.Builder builder = new Data.Builder();

        public void setJsonUri(String data) {
            builder.putString(KEY_JSON_URI, data);
        }

        public void setIsAdd(boolean data) {
            builder.putBoolean(KEY_ADD, data);
        }

        public void setIsImportLibrary(boolean data) {
            builder.putBoolean(KEY_IMPORT_LIBRARY, data);
        }

        public void setEmptyBooksOption(int data) {
            builder.putInt(KEY_EMPTY_BOOKS_OPTION, data);
        }

        public void setIsImportQueue(boolean data) {
            builder.putBoolean(KEY_IMPORT_QUEUE, data);
        }

        public void setIsImportCustomGroups(boolean data) {
            builder.putBoolean(KEY_IMPORT_CUSTOM_GROUPS, data);
        }

        public void setIsImportBookmarks(boolean data) {
            builder.putBoolean(KEY_IMPORT_BOOKMARKS, data);
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
        public String getJsonUri() {
            return data.getString(KEY_JSON_URI);
        }

        public boolean isAdd() {
            return data.getBoolean(KEY_ADD, false);
        }

        public boolean isImportLibrary() {
            return data.getBoolean(KEY_IMPORT_LIBRARY, false);
        }

        public int getEmptyBooksOption() {
            return data.getInt(KEY_EMPTY_BOOKS_OPTION, -1);
        }

        public boolean isImportQueue() {
            return data.getBoolean(KEY_IMPORT_QUEUE, false);
        }

        public boolean isImportCustomGroups() {
            return data.getBoolean(KEY_IMPORT_CUSTOM_GROUPS, false);
        }

        public boolean isImportBookmarks() {
            return data.getBoolean(KEY_IMPORT_BOOKMARKS, false);
        }
    }
}
