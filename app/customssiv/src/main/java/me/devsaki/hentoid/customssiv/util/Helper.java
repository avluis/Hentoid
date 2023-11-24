package me.devsaki.hentoid.customssiv.util;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Helper {

    private Helper() {
        throw new IllegalStateException("Utility class");
    }

    public static void assertNonUiThread() {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            throw new IllegalStateException("This should not be run on the UI thread");
        }
    }

    /**
     * Copy all data from the given InputStream to the given OutputStream
     *
     * @param in  InputStream to read data from
     * @param out OutputStream to write data to
     * @throws IOException If something horrible happens during I/O
     */
    public static void copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        // Transfer bytes from in to out
        byte[] buf = new byte[FileHelper.FILE_IO_BUFFER_SIZE];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    public static float getScreenDpi(@NonNull Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;

        WindowManager wMgr = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        float generalDpi;
        if (Build.VERSION.SDK_INT >= 34) {
            generalDpi = wMgr.getCurrentWindowMetrics().getDensity() * 160;
        } else {
            DisplayMetrics metrics3 = new DisplayMetrics();
            wMgr.getDefaultDisplay().getRealMetrics(metrics3);
            generalDpi = metrics3.densityDpi;
        }

        // Dimensions retrieved by metrics.xdpi/ydpi might be expressed as ppi (as per specs) and not dpi (as per naming)
        // In that case, values are off scale => fallback to general dpi
        return ((Math.abs(generalDpi - averageDpi) / averageDpi) > 1) ? generalDpi : averageDpi;
    }
}
