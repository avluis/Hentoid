package me.devsaki.hentoid.customssiv;

import android.os.Looper;

public final class Helper {

    private Helper() {
        throw new IllegalStateException("Utility class");
    }

    static void assertNonUiThread() {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            throw new IllegalStateException("This should not be run on the UI thread");
        }
    }
}
