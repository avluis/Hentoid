package me.devsaki.hentoid.activities.bundles;

import android.os.Bundle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Helper class to transfer payload data to {@link me.devsaki.hentoid.viewholders.ImageFileItem}
 * through a Bundle
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class ImageItemBundle {
    private static final String KEY_FAV_STATE = "favourite";
    private static final String KEY_CHP_ORDER = "chapterOrder";

    private ImageItemBundle() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Bundle bundle = new Bundle();

        public void setIsFavourite(boolean value) {
            bundle.putBoolean(KEY_FAV_STATE, value);
        }

        public void setChapterOrder(int value) {
            bundle.putInt(KEY_CHP_ORDER, value);
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
        public Boolean isFavourite() {
            if (bundle.containsKey(KEY_FAV_STATE)) return bundle.getBoolean(KEY_FAV_STATE);
            else return null;
        }

        @Nullable
        public Integer getChapterOrder() {
            if (bundle.containsKey(KEY_CHP_ORDER)) return bundle.getInt(KEY_CHP_ORDER);
            else return null;
        }
    }
}
