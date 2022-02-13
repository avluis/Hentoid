package me.devsaki.hentoid.customssiv;

import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Helper {

    private Helper() {
        throw new IllegalStateException("Utility class");
    }

    static void assertNonUiThread() {
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
}
