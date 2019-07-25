package me.devsaki.hentoid.util;

import android.content.Context;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import android.widget.Toast;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import me.devsaki.hentoid.HentoidApp;
import timber.log.Timber;

public class ToastUtil {

    private static Toast toast;

    public static void cancelToast() {
        if (toast != null) {
            toast.cancel();
            toast = null;
        }
    }

    public static void toast(@StringRes int resource) {
        toast(HentoidApp.getAppContext(), resource);
    }

    public static void toast(@NonNull String message) {
        toast(HentoidApp.getAppContext(), message);
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

        cancelToast();
        toast = Toast.makeText(context, message, duration);
        toast.show();
    }

    @IntDef({Toast.LENGTH_SHORT, Toast.LENGTH_LONG})
    @Retention(RetentionPolicy.SOURCE)
    @interface Duration {
    }
}
