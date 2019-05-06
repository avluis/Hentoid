package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;

public class BaseWebActivityBundle {
    private static final String KEY_URL = "url";

    private BaseWebActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setUrl(String url) {
            bundle.putString(KEY_URL, url);
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

        public String getUrl() {
            return bundle.getString(KEY_URL, "");
        }
    }
}
