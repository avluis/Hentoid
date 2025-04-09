package me.devsaki.hentoid.customssiv.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Copy all data from the given InputStream to the given OutputStream
 *
 * @param in  InputStream to read data from
 * @param out OutputStream to write data to
 * @throws IOException If something horrible happens during I/O
 */
@Throws(IOException::class)
internal fun copy(`in`: InputStream, out: OutputStream) {
    // Transfer bytes from in to out
    val buf = ByteArray(FILE_IO_BUFFER_SIZE)
    var len: Int
    while ((`in`.read(buf).also { len = it }) > 0) {
        out.write(buf, 0, len)
    }
    out.flush()
}

@Throws(IOException::class)
internal fun copy(`in`: InputStream, out: ByteBuffer) {
    // Transfer bytes from in to out
    val buf = ByteArray(FILE_IO_BUFFER_SIZE)
    var len: Int
    while ((`in`.read(buf).also { len = it }) > 0) {
        out.put(buf, 0, len)
    }
}

internal fun getScreenDpi(context: Context): Float {
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

internal fun getScreenDimensionsPx(context: Context): Point {
    val wMgr = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    if (Build.VERSION.SDK_INT >= 30) {
        wMgr.currentWindowMetrics.bounds.apply { return Point(width(), height()) }
    } else {
        val result = Point()
        wMgr.defaultDisplay.getRealSize(result)
        return result
    }
}