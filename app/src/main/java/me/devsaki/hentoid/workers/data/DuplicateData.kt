package me.devsaki.hentoid.workers.data

import androidx.work.Data

/**
 * Helper class to transfer data from any Activity to {@link me.devsaki.hentoid.workers.DuplicateDetectorWorker}
 * through a Data object
 * <p>
 * Use Builder class to set data; use Parser class to get data
 */
private const val USE_TITLE = "title"
private const val USE_COVER = "cover"
private const val USE_ARTIST = "artist"
private const val USE_SAME_LANGUAGE = "sameLanguage"
private const val IGNORE_CHAPTERS = "ignoreChapters"
private const val USE_SENSITIVITY = "sensitivity"

class DuplicateData {

    class Builder {
        private val builder = Data.Builder()
        fun setUseTitle(value: Boolean) {
            builder.putBoolean(USE_TITLE, value)
        }

        fun setUseCover(value: Boolean) {
            builder.putBoolean(USE_COVER, value)
        }

        fun setUseArtist(value: Boolean) {
            builder.putBoolean(USE_ARTIST, value)
        }

        fun setUseSameLanguage(value: Boolean) {
            builder.putBoolean(USE_SAME_LANGUAGE, value)
        }

        fun setIgnoreChapters(value: Boolean) {
            builder.putBoolean(IGNORE_CHAPTERS, value)
        }

        fun setSensitivity(value: Int) {
            builder.putInt(USE_SENSITIVITY, value)
        }

        val data: Data
            get() = builder.build()
    }


    class Parser(private val data: Data) {

        val useTitle: Boolean
            get() = data.getBoolean(USE_TITLE, false)
        val useCover: Boolean
            get() = data.getBoolean(USE_COVER, false)
        val useArtist: Boolean
            get() = data.getBoolean(USE_ARTIST, false)
        val useSameLanguage: Boolean
            get() = data.getBoolean(USE_SAME_LANGUAGE, false)
        val ignoreChapters: Boolean
            get() = data.getBoolean(IGNORE_CHAPTERS, false)
        val sensitivity: Int
            get() = data.getInt(USE_SENSITIVITY, 1)
    }

}