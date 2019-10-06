package me.devsaki.hentoid.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class PendingIntentCompat {

    private PendingIntentCompat() {
        throw new IllegalStateException("Utility class");
    }

    public static PendingIntent getForegroundService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PendingIntent.getForegroundService(context, 0, intent, 0);
        } else {
            return PendingIntent.getService(context, 0, intent, 0);
        }
    }
}
