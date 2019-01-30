package me.devsaki.hentoid.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

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

    // For use whenever Toast messages could stack (e.g., repeated calls to Toast.makeText())
    public static void toast(String text) {
        Context context = HentoidApp.getAppContext();
        if (context != null) {
            toast(context, text);
        }
    }

    public static void toast(int resource) {
        Context context = HentoidApp.getAppContext();
        if (context != null) {
            toast(context, context.getResources().getString(resource));
        }
    }

    public static void toast(Context context, String text) {
        toast(context, text, DURATION.SHORT);
    }

    public static void toast(Context context, int resource) {
        toast(context, resource, DURATION.SHORT);
    }

    public static void toast(Context context, String text, DURATION duration) {
        toast(context, text, -1, duration);
    }

    public static void toast(Context context, int resource, DURATION duration) {
        toast(context, null, resource, duration);
    }

    @SuppressLint("ShowToast")
    private static void toast(@NonNull Context context, @Nullable String text, int res,
                              DURATION duration) {
        String message = null;
        if (text != null) {
            message = text;
        } else if (res != -1) {
            message = context.getString(res);
        } else {
            Throwable noResource = new Throwable("You must provide a String or Resource ID!");
            try {
                throw noResource;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        int time;
        switch (duration) {
            case LONG:
                time = Toast.LENGTH_LONG;
                break;
            case SHORT:
            default:
                time = Toast.LENGTH_SHORT;
                break;
        }

        try {
            toast.getView().isShown();
            toast.setText(message);
        } catch (Exception e) {
            Timber.d("toast is null, creating one instead;");
            toast = Toast.makeText(context, message, time);
        }

        toast.show();
    }

    public enum DURATION {SHORT, LONG}
}
