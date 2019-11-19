package me.devsaki.hentoid.util;

import android.os.Handler;

import com.annimon.stream.function.Consumer;

/**
 * Utility class for debouncing values to consumer functions
 * <p>
 * This is backed by a Handler that uses the current thread's looper. Behavior is undefined for
 * threads without a looper.
 *
 * @param <T> type of value that will be debounced
 * @see android.os.Looper
 */
public class Debouncer<T> {

    private final Handler handler = new Handler();

    private final long delay;

    private final Consumer<T> callback;

    public Debouncer(long delay, Consumer<T> callback) {
        this.delay = delay;
        this.callback = callback;
    }

    public void clear() {
        handler.removeCallbacksAndMessages(null);
    }

    public void submit(T t) {
        clear();
        handler.postDelayed(() -> callback.accept(t), delay);
    }
}
