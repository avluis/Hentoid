package me.devsaki.hentoid.customssiv.util

import android.content.Context
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
internal class Debouncer<T>(
    context: Context,
    private val delayMs: Long,
    private val callback: Consumer<T>
) {

    private val handler = Handler(context.mainLooper)

    fun clear() {
        handler.removeCallbacksAndMessages(null)
    }

    fun submit(t: T) {
        clear()
        handler.postDelayed({ callback.accept(t) }, delayMs)
    }
}
