package me.devsaki.hentoid.customssiv.util

import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.WindowManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs


/**
 * Crashes if called on the UI thread
 * To be used as a marker wherever processing in a background thread is mandatory
 */
fun assertNonUiThread() {
    check(Looper.getMainLooper().thread !== Thread.currentThread()) { "This should not be run on the UI thread" }
}

/**
 * Copy all data from the given InputStream to the given OutputStream
 *
 * @param in  InputStream to read data from
 * @param out OutputStream to write data to
 * @throws IOException If something horrible happens during I/O
 */
@Throws(IOException::class)
fun copy(`in`: InputStream, out: OutputStream) {
    // Transfer bytes from in to out
    val buf = ByteArray(FILE_IO_BUFFER_SIZE)
    var len: Int
    while ((`in`.read(buf).also { len = it }) > 0) {
        out.write(buf, 0, len)
    }
    out.flush()
}

fun getScreenDpi(context: Context): Float {
    val metrics = context.resources.displayMetrics
    val averageDpi = (metrics.xdpi + metrics.ydpi) / 2

    val generalDpi: Float = if (Build.VERSION.SDK_INT >= 34) {
        val wMgr = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wMgr.currentWindowMetrics.density * 160
    } else {
        context.resources.configuration.densityDpi.toFloat()
    }

    // Dimensions retrieved by metrics.xdpi/ydpi might be expressed as ppi (as per specs) and not dpi (as per naming)
    // In that case, values are off scale => fallback to general dpi
    return if (((abs((generalDpi - averageDpi).toDouble()) / averageDpi) > 1)) generalDpi else averageDpi
}