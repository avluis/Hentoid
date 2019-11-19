package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;

public class ImportActivityBundle {
    private static final String KEY_REFRESH = "refresh";
    private static final String KEY_REFRESH_RENAME = "rename";
    private static final String KEY_REFRESH_CLEAN_ABSENT = "cleanAbsent";
    private static final String KEY_REFRESH_CLEAN_NO_IMAGES = "cleanNoImages";
    private static final String KEY_REFRESH_CLEAN_UNREADABLE = "cleanUnreadable";

    private ImportActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setRefresh(boolean refresh) {
            bundle.putBoolean(KEY_REFRESH, refresh);
        }

        public void setRefreshRename(boolean rename) {
            bundle.putBoolean(KEY_REFRESH_RENAME, rename);
        }

        public void setRefreshCleanAbsent(boolean refresh) {
            bundle.putBoolean(KEY_REFRESH_CLEAN_ABSENT, refresh);
        }

        public void setRefreshCleanNoImages(boolean refresh) {
            bundle.putBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, refresh);
        }

        public void setRefreshCleanUnreadable(boolean refresh) {
            bundle.putBoolean(KEY_REFRESH_CLEAN_UNREADABLE, refresh);
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

        public boolean getRefresh() {
            return bundle.getBoolean(KEY_REFRESH, false);
        }

        public boolean getRefreshRename() {
            return bundle.getBoolean(KEY_REFRESH_RENAME, false);
        }

        public boolean getRefreshCleanAbsent() {
            return bundle.getBoolean(KEY_REFRESH_CLEAN_ABSENT, false);
        }

        public boolean getRefreshCleanNoImages() {
            return bundle.getBoolean(KEY_REFRESH_CLEAN_NO_IMAGES, false);
        }

        public boolean getRefreshCleanUnreadable() {
            return bundle.getBoolean(KEY_REFRESH_CLEAN_UNREADABLE, false);
        }
    }
}
