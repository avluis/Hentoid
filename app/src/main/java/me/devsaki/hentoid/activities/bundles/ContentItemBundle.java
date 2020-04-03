package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Helper class to transfer payload data to {@link me.devsaki.hentoid.viewholders.ContentItem}
 * through a Bundle
 *
 * Use Builder class to set data; use Parser class to get data
 */
public class ContentItemBundle {
    private static final String KEY_DELETE_PROCESSING = "is_being_deleted";
    private static final String KEY_FAV_PROCESSING = "is_being_favourited";
    private static final String KEY_FAV_STATE = "favourite";
    private static final String KEY_READS = "reads";

    private ContentItemBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setIsBeingFavourited(boolean isBeingFavourited) {
            bundle.putBoolean(KEY_FAV_PROCESSING, isBeingFavourited);
        }

        public void setIsBeingDeleted(boolean isBeingDeleted) {
            bundle.putBoolean(KEY_DELETE_PROCESSING, isBeingDeleted);
        }

        public void setIsFavourite(boolean isFavourite) {
            bundle.putBoolean(KEY_FAV_STATE, isFavourite);
        }

        public void setReads(long reads) {
            bundle.putLong(KEY_READS, reads);
        }

        public boolean isEmpty() {
            return bundle.isEmpty();
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

        @Nullable
        public Boolean isBeingFavourited() {
            if (bundle.containsKey(KEY_FAV_PROCESSING))
                return bundle.getBoolean(KEY_FAV_PROCESSING);
            else return null;
        }

        @Nullable
        public Boolean isBeingDeleted() {
            if (bundle.containsKey(KEY_DELETE_PROCESSING))
                return bundle.getBoolean(KEY_DELETE_PROCESSING);
            else return null;
        }

        @Nullable
        public Boolean isFavourite() {
            if (bundle.containsKey(KEY_FAV_STATE)) return bundle.getBoolean(KEY_FAV_STATE);
            else return null;
        }

        @Nullable
        public Long getReads() {
            if (bundle.containsKey(KEY_READS)) return bundle.getLong(KEY_READS);
            else return null;
        }
    }
}
