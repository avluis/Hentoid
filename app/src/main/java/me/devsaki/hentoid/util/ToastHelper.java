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

    public static void toast(String message) {
        toast(HentoidApp.getInstance(), message, Toast.LENGTH_SHORT);
    }

    public static void toast(@StringRes int resource) {
        toast(HentoidApp.getInstance(), resource);
    }

    public static void toast(@StringRes int resource, Object... args) {
        toast(HentoidApp.getInstance(), resource, args);
    }

    public static void toast(@NonNull Context context, @StringRes int resource, Object... args) {
        toast(context, resource, Toast.LENGTH_SHORT, args);
    }

    public static void toastLong(@NonNull Context context, @StringRes int resource, Object... args) {
        toast(context, resource, Toast.LENGTH_LONG, args);
    }

    private static void toast(@NonNull Context context, @StringRes int resource, @Duration int duration, Object... args) {
        toast(context, context.getString(resource, args), duration);
    }

    private static void toast(@NonNull Context context, @NonNull String message, @Duration int duration) {
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
