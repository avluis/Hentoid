package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.activities.PrefsActivity}
 * through a Bundle
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class PrefsActivityBundle {
    private static final String KEY_IS_VIEWER_PREFS = "isViewer";
    private static final String KEY_IS_DOWNLOADER_PREFS = "isDownloader";
    private static final String KEY_IS_STORAGE_PREFS = "isStorage";

    private PrefsActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setIsViewerPrefs(boolean isViewerPrefs) {
            bundle.putBoolean(KEY_IS_VIEWER_PREFS, isViewerPrefs);
        }

        public void setIsDownloaderPrefs(boolean isDownloaderPrefs) {
            bundle.putBoolean(KEY_IS_DOWNLOADER_PREFS, isDownloaderPrefs);
        }

        public void setIsStoragePrefs(boolean value) {
            bundle.putBoolean(KEY_IS_STORAGE_PREFS, value);
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

        public boolean isViewerPrefs() {
            return bundle.getBoolean(KEY_IS_VIEWER_PREFS, false);
        }

        public boolean isDownloaderPrefs() {
            return bundle.getBoolean(KEY_IS_DOWNLOADER_PREFS, false);
        }

        public boolean isStoragePrefs() {
            return bundle.getBoolean(KEY_IS_STORAGE_PREFS, false);
        }
    }
}
