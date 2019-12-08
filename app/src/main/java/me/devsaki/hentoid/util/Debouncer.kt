package me.devsaki.hentoid.util

import android.os.Handler

import com.annimon.stream.function.Consumer

/**
 * Utility class for debouncing values to consumer functions
 *
 *
 * This is backed by a Handler that uses the current thread's looper. Behavior is undefined for
 * threads without a looper.
 *
 * @param <T> type of value that will be debounced
 * @see android.os.Looper
</T> */
class Debouncer<T>(private val delay: Long, private val callback: Consumer<T>) {

    private val handler = Handler()

    fun clear() {
        handler.removeCallbacksAndMessages(null)
    }

    fun submit(t: T) {
        clear()
        handler.postDelayed({ callback.accept(t) }, delay)
    }
}
