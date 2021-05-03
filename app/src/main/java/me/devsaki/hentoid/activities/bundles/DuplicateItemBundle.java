package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Helper class to transfer payload data to {@link me.devsaki.hentoid.viewholders.DuplicateItem}
 * through a Bundle
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class DuplicateItemBundle {
    private static final String KEY_KEEP = "keep";

    private DuplicateItemBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setKeep(Boolean value) {
            bundle.putBoolean(KEY_KEEP, value);
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
        public Boolean getKeep() {
            if (bundle.containsKey(KEY_KEEP))
                return bundle.getBoolean(KEY_KEEP);
            else return null;
        }
    }
}
