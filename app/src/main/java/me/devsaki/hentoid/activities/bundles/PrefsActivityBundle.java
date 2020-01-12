package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;

public class PrefsActivityBundle {
    private static final String KEY_IS_VIEWER_PREFS = "isViewer";

    private PrefsActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setIsViewerPrefs(boolean isViewerPrefs) {
            bundle.putBoolean(KEY_IS_VIEWER_PREFS, isViewerPrefs);
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
    }
}
