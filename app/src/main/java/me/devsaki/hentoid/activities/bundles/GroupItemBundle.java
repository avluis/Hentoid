package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Helper class to transfer payload data to {@link me.devsaki.hentoid.viewholders.GroupDisplayItem}
 * through a Bundle
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class GroupItemBundle {
    private static final String KEY_PICTURE = "picture";
    private static final String KEY_FAV = "favourite";

    private GroupItemBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setCoverUri(String uri) {
            bundle.putString(KEY_PICTURE, uri);
        }

        public void setFavourite(boolean isFavourite) {
            bundle.putBoolean(KEY_FAV, isFavourite);
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
        public String getCoverUri() {
            if (bundle.containsKey(KEY_PICTURE)) return bundle.getString(KEY_PICTURE);
            else return null;
        }

        @Nullable
        public Boolean isFavourite() {
            if (bundle.containsKey(KEY_FAV)) return bundle.getBoolean(KEY_FAV);
            else return null;
        }
    }
}
