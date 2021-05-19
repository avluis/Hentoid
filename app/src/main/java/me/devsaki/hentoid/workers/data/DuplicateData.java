package me.devsaki.hentoid.workers.data;

import androidx.work.Data;

import javax.annotation.Nonnull;

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.DuplicateDetectorWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
public class DuplicateData {
    private static final String USE_TITLE = "title";
    private static final String USE_COVER = "cover";
    private static final String USE_ARTIST = "artist";
    private static final String USE_SAME_LANGUAGE = "sameLanguage";
    private static final String IGNORE_CHAPTERS = "ignoreChapters";
    private static final String USE_SENSITIVITY = "sensitivity";

    private DuplicateData() {
        throw new UnsupportedOperationException();
    }

    public static final class Builder {

        private final Data.Builder builder = new Data.Builder();

        public void setUseTitle(boolean value) {
            builder.putBoolean(USE_TITLE, value);
        }

        public void setUseCover(boolean value) {
            builder.putBoolean(USE_COVER, value);
        }

        public void setUseArtist(boolean value) {
            builder.putBoolean(USE_ARTIST, value);
        }

        public void setUseSameLanguage(boolean value) {
            builder.putBoolean(USE_SAME_LANGUAGE, value);
        }

        public void setIgnoreChapters(boolean value) {
            builder.putBoolean(IGNORE_CHAPTERS, value);
        }

        public void setSensitivity(int value) {
            builder.putInt(USE_SENSITIVITY, value);
        }

        public Data getData() {
            return builder.build();
        }
    }

    public static final class Parser {

        private final Data data;

        public Parser(@Nonnull Data data) {
            this.data = data;
        }

        public boolean getUseTitle() {
            return data.getBoolean(USE_TITLE, false);
        }

        public boolean getUseCover() {
            return data.getBoolean(USE_COVER, false);
        }

        public boolean getUseArtist() {
            return data.getBoolean(USE_ARTIST, false);
        }

        public boolean getUseSameLanguage() {
            return data.getBoolean(USE_SAME_LANGUAGE, false);
        }

        public boolean getIgnoreChapters() {
            return data.getBoolean(IGNORE_CHAPTERS, false);
        }

        public int getSensitivity() {
            return data.getInt(USE_SENSITIVITY, 1);
        }
    }
}
