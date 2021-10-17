package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.activities.ImageViewerActivity}
 * through a Bundle
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class ImageViewerActivityBundle {
    private static final String KEY_CONTENT_ID = "contentId";
    private static final String KEY_SEARCH_PARAMS = "searchParams";
    private static final String KEY_IMAGE_INDEX = "imageIndex";
    private static final String KEY_IMAGE_NUMBER = "imageNumber";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_FORCE_SHOW_GALLERY = "forceShowGallery";

    private ImageViewerActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setContentId(long contentId) {
            bundle.putLong(KEY_CONTENT_ID, contentId);
        }

        public void setSearchParams(Bundle params) {
            bundle.putBundle(KEY_SEARCH_PARAMS, params);
        }

        public void setImageIndex(int imageIndex) {
            bundle.putInt(KEY_IMAGE_INDEX, imageIndex);
        }

        public void setPageNumber(int imageNumber) {
            bundle.putInt(KEY_IMAGE_NUMBER, imageNumber);
        }

        public void setScale(float scale) {
            bundle.putFloat(KEY_SCALE, scale);
        }

        public void setForceShowGallery(boolean value) {
            bundle.putBoolean(KEY_FORCE_SHOW_GALLERY, value);
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

        public long getContentId() {
            return bundle.getLong(KEY_CONTENT_ID, 0);
        }

        public Bundle getSearchParams() {
            return bundle.getBundle(KEY_SEARCH_PARAMS);
        }

        public int getImageIndex() {
            return bundle.getInt(KEY_IMAGE_INDEX, -1);
        }

        public int getPageNumber() {
            return bundle.getInt(KEY_IMAGE_NUMBER, -1);
        }

        public float getScale() {
            return bundle.getFloat(KEY_SCALE, -1);
        }

        public boolean isForceShowGallery() {
            return bundle.getBoolean(KEY_FORCE_SHOW_GALLERY, false);
        }
    }
}
