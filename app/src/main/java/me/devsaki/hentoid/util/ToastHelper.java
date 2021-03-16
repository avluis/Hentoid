package me.devsaki.hentoid.util;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.devsaki.hentoid.core.HentoidApp;
import timber.log.Timber;

public class ToastHelper {

    private ToastHelper() {
        throw new IllegalStateException("Utility class");
    }


    public static void toast(@StringRes int resource) {
        toast(HentoidApp.getInstance(), resource);
    }

    public static void toast(@NonNull String message) {
        toast(HentoidApp.getInstance(), message);
    }

    public static void toast(@NonNull Context context, @StringRes int resource) {
        toast(context, resource, Toast.LENGTH_SHORT);
    }

    public static void toast(@NonNull Context context, @NonNull String message) {
        toast(context, message, Toast.LENGTH_SHORT);
    }

    public static void toast(@NonNull Context context, @StringRes int resource, @Duration int duration) {
        toast(context, context.getString(resource), duration);
    }

    public static void toast(@NonNull Context context, @NonNull String message, @Duration int duration) {
        if (message.isEmpty()) {
            Timber.e("You must provide a String or Resource ID!");
            return;
        }

        Toast toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    @IntDef({Toast.LENGTH_SHORT, Toast.LENGTH_LONG})
    @Retention(RetentionPolicy.SOURCE)
    @interface Duration {
    }
}
