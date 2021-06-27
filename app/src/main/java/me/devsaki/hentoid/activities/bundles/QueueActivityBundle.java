package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import me.devsaki.hentoid.enums.Site;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.activities.QueueActivity}
 * through a Bundle
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class QueueActivityBundle {
    private static final String KEY_IS_ERROR = "isError";
    private static final String KEY_CONTENT_HASH = "contentHash";
    private static final String KEY_REVIVE_DOWNLOAD = "reviveDownload";
    private static final String KEY_REVIVE_OLD_COOKIE = "reviveOldCookie";

    private QueueActivityBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setIsErrorsTab(boolean isError) {
            bundle.putBoolean(KEY_IS_ERROR, isError);
        }

        public void setContentHash(long contentHash) {
            bundle.putLong(KEY_CONTENT_HASH, contentHash);
        }

        public void setReviveDownload(Site site) {
            bundle.putInt(KEY_REVIVE_DOWNLOAD, site.getCode());
        }

        public void setReviveOldCookie(String cookie) {
            bundle.putString(KEY_REVIVE_OLD_COOKIE, cookie);
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

        public boolean isErrorsTab() {
            return bundle.getBoolean(KEY_IS_ERROR, false);
        }

        public long contentHash() {
            return bundle.getLong(KEY_CONTENT_HASH, 0);
        }

        @Nullable
        public Site getRevivedSite() {
            int siteId = bundle.getInt(KEY_REVIVE_DOWNLOAD, -1);
            if (siteId > -1) return Site.searchByCode(siteId);
            else return null;
        }

        public String getOldCookie() {
            return bundle.getString(KEY_REVIVE_OLD_COOKIE, "");
        }
    }
}
