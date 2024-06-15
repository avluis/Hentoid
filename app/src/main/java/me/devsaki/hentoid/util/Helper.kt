package me.devsaki.hentoid.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.graphics.drawable.InsetDrawable
import android.os.Debug
import android.os.Looper
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.annotation.DimenRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.MenuCompat
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.whitfin.siphash.SipHasher
import me.devsaki.hentoid.R
import me.devsaki.hentoid.core.BOOKMARKS_JSON_FILE_NAME
import me.devsaki.hentoid.core.HentoidApp.Companion.getInstance
import me.devsaki.hentoid.core.RENAMING_RULES_JSON_FILE_NAME
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.json.JsonContentCollection
import me.devsaki.hentoid.util.file.FILE_IO_BUFFER_SIZE
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.Random
import kotlin.math.floor
import kotlin.math.min

private val rand = Random()

private val SIP_KEY = "0123456789ABCDEF".toByteArray()


/**
 * Retreive the given dimension value as DP, not pixels
 *
 * @param context Context to use to access resources
 * @param id      Dimension resource ID to get the value from
 * @return Given dimension value as DP
 */
fun dimensAsDp(context: Context, @DimenRes id: Int): Int {
    return (context.resources.getDimension(id) / context.resources.displayMetrics.density).toInt()
}

/**
 * Convert the given dimension value from DP to pixels
 *
 * @param context Context to use to access resources
 * @param dpValue Dimension value as DP
 * @return Given dimension value as pixels
 */
fun dimensAsPx(context: Context, dpValue: Int): Int {
    val r = context.resources
    return Math.round(
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue.toFloat(),
            r.displayMetrics
        )
    )
}

/**
 * Inclusively coerce the given value between the given min and max values
 *
 * @param value Value to coerce
 * @param min   Min limit (inclusive)
 * @param max   Max limit (inclusive)
 * @return Given value inclusively coerced between the given min and max
 */
fun coerceIn(value: Float, min: Float, max: Float): Float {
    return if (value < min) min
    else min(value.toDouble(), max.toDouble()).toFloat()
}

/**
 * Duplicate the given InputStream as many times as given
 *
 * @param stream           Initial InputStream to duplicate
 * @param numberDuplicates Number of duplicates to create
 * @return List containing the given number of duplicated InputStreams
 * @throws IOException If anything goes wrong during the duplication
 */
@Throws(IOException::class)
fun duplicateInputStream(stream: InputStream, numberDuplicates: Int): List<InputStream> {
    val result: MutableList<InputStream> = ArrayList()

    ByteArrayOutputStream().use { baos ->
        copy(stream, baos)
        for (i in 0 until numberDuplicates) result.add(ByteArrayInputStream(baos.toByteArray()))
    }
    return result
}

/**
 * Copy plain text to the device's clipboard
 *
 * @param context Context to be used
 * @param text    Text to copy
 * @return True if the copy has succeeded; false if not
 */
fun copyPlainTextToClipboard(context: Context, text: String): Boolean {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
    if (clipboard != null) {
        val clip = ClipData.newPlainText(context.getString(R.string.menu_share_title), text)
        clipboard.setPrimaryClip(clip)
        return true
    } else return false
}

/**
 * Share the given text titled with the given subject
 *
 * @param context Context to be used
 * @param subject Share subject
 * @param text    Text to share
 */
fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent()
    intent.setAction(Intent.ACTION_SEND)
    intent.setType("text/plain")
    intent.putExtra(Intent.EXTRA_SUBJECT, subject)
    intent.putExtra(Intent.EXTRA_TEXT, text)

    context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_to)))
}

/**
 * Fix for a crash on 5.1.1
 * https://stackoverflow.com/questions/41025200/android-view-inflateexception-error-inflating-class-android-webkit-webview
 * As fallback solution _only_ since it breaks other stuff in the webview (choice in SELECT tags for instance)
 *
 * @param context Context to fix
 * @return Fixed context
 */
fun getFixedContext(context: Context): Context {
    return context.createConfigurationContext(Configuration())
}

/**
 * Crashes if called on the UI thread
 * To be used as a marker wherever processing in a background thread is mandatory
 */
fun assertNonUiThread() {
    check(Looper.getMainLooper().thread !== Thread.currentThread()) { "This should not be run on the UI thread" }
}

/**
 * Format the given duration using the HH:MM:SS format
 *
 * @param ms Duration to format, in milliseconds
 * @return FormattedDuration
 */
fun formatDuration(ms: Long): String {
    val seconds = floor((ms / 1000f).toDouble()).toLong()
    val h = floor((seconds / 3600f).toDouble()).toInt()
    val m = floor(((seconds - 3600f * h) / 60).toDouble()).toInt()
    val s = seconds - (60L * m) - (3600L * h)

    var hStr = h.toString()
    if (1 == hStr.length) hStr = "0$hStr"
    var mStr = m.toString()
    if (1 == mStr.length) mStr = "0$mStr"
    var sStr = s.toString()
    if (1 == sStr.length) sStr = "0$sStr"

    return if (h > 0) "$hStr:$mStr:$sStr"
    else "$mStr:$sStr"
}

/**
 * Build an 64-bit SIP hash from the given data
 *
 * @param data Data to hash
 * @return Hash built from the given data
 */
fun hash64(data: ByteArray): Long {
    return SipHasher.hash(SIP_KEY, data)
}

/**
 * Compute the weighted average of the given operands
 * - Left part is the value
 * - Right part is the coefficient
 *
 * @param operands List of (value, coefficient) pairs
 * @return Weigthed average of the given operands; 0 if uncomputable
 */
fun weightedAverage(operands: List<Pair<Float, Float>>): Float {
    if (operands.isEmpty()) return 0f

    var numerator = 0f
    var denominator = 0f
    for ((first, second) in operands) {
        numerator += (first * second)
        denominator += second
    }
    return if ((denominator > 0)) numerator / denominator else 0f
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

/**
 * Generate an ID for a RecyclerView ViewHolder without any ID to assign to
 *
 * @return Generated ID
 */
fun generateIdForPlaceholder(): Long {
    // Make sure nothing collides with an actual ID; nobody has 1M books; it should be fine
    return 1e6.toLong() + rand.nextLong()
}

/**
 * Try to enrich the given Menu to make its associated icons displayable
 * Fails silently (with a log) if not possible
 * Inspired by https://material.io/components/menus/android#dropdown-menus
 *
 * @param context Context to use
 * @param menu    Menu to display
 */
@SuppressLint("RestrictedApi")
fun tryShowMenuIcons(context: Context, menu: Menu) {
    try {
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
            val iconMarginPx = context.resources.getDimension(R.dimen.icon_margin).toInt()
            for (item in menu.visibleItems) {
                if (item.icon != null) item.setIcon(
                    InsetDrawable(
                        item.icon, iconMarginPx, 0, iconMarginPx, 0
                    )
                )
            }
        }
    } catch (e: Exception) {
        Timber.i(e)
    }
    MenuCompat.setGroupDividerEnabled(menu, true)
}

/**
 * Pauses the calling thread for the given number of milliseconds
 * For safety reasons, this method crahses if called from the main thread
 *
 * @param millis Number of milliseconds to pause the calling thread for
 */
fun pause(millis: Int) {
    assertNonUiThread()
    try {
        Thread.sleep(millis.toLong())
    } catch (e: InterruptedException) {
        Timber.d(e)
        Thread.currentThread().interrupt()
    }
}

/**
 * Generates a random positive integer bound to the given argument (excluded)
 * NB : This method uses a Random class instanciated once, which is better than
 * calling `new Random().nextInt`
 *
 * @param maxExclude Upper bound (excluded)
 * @return Random positive integer (zero included) bound to the given argument (excluded)
 */
fun getRandomInt(maxExclude: Int): Int {
    return rand.nextInt(maxExclude)
}

// TODO doc
fun parseDatetimeToEpoch(date: String, pattern: String): Long {
    val dateClean = date.trim { it <= ' ' }
        .replace("(?<=\\d)(st|nd|rd|th)".toRegex(), "")
    val formatter = DateTimeFormatter
        .ofPattern(pattern)
        .withResolverStyle(ResolverStyle.LENIENT)
        .withLocale(Locale.ENGLISH) // To parse english expressions (e.g. month name)
        .withZone(ZoneId.systemDefault())

    try {
        return Instant.from(formatter.parse(dateClean)).toEpochMilli()
    } catch (e: DateTimeParseException) {
        Timber.w(e)
    }
    return 0
}

// https://www.threeten.org/threetenbp/apidocs/org/threeten/bp/format/DateTimeFormatter.html#ofPattern(java.lang.String)
fun parseDateToEpoch(date: String, pattern: String): Long {
    val dateClean = date.trim { it <= ' ' }
        .replace("(?<=\\d)(st|nd|rd|th)".toRegex(), "")
    val formatter = DateTimeFormatterBuilder()
        .appendPattern(pattern)
        .parseDefaulting(ChronoField.NANO_OF_DAY, 0) // To allow passing dates without time
        .toFormatter()
        .withResolverStyle(ResolverStyle.LENIENT)
        .withLocale(Locale.ENGLISH) // To parse english expressions (e.g. month name)
        .withZone(ZoneId.systemDefault())

    try {
        return Instant.from(formatter.parse(dateClean)).toEpochMilli()
    } catch (e: DateTimeParseException) {
        Timber.w(e)
    }
    return 0
}

fun formatEpochToDate(epoch: Long, pattern: String?): String {
    return formatEpochToDate(epoch, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}

fun formatEpochToDate(epoch: Long, formatter: DateTimeFormatter?): String {
    if (0L == epoch) return ""
    val i = Instant.ofEpochMilli(epoch)
    return i.atZone(ZoneId.systemDefault()).format(formatter)
}

/**
 * Update the JSON file that stores bookmarks with the current bookmarks
 *
 * @param context Context to be used
 * @param dao     DAO to be used
 * @return True if the bookmarks JSON file has been updated properly; false instead
 */
fun updateBookmarksJson(context: Context, dao: CollectionDAO): Boolean {
    assertNonUiThread()
    val bookmarks = dao.selectAllBookmarks()

    val contentCollection = JsonContentCollection()
    contentCollection.bookmarks = bookmarks

    val rootFolder =
        getDocumentFromTreeUriString(context, Preferences.getStorageUri(StorageLocation.PRIMARY_1))
            ?: return false

    try {
        jsonToFile(
            context, contentCollection,
            JsonContentCollection::class.java, rootFolder, BOOKMARKS_JSON_FILE_NAME
        )
    } catch (e: IOException) {
        // NB : IllegalArgumentException might happen for an unknown reason on certain devices
        // even though all the file existence checks are in place
        // ("Failed to determine if primary:.Hentoid/queue.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/queue.json at /storage/emulated/0/.Hentoid/queue.json")
        Timber.e(e)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.recordException(e)
        return false
    } catch (e: IllegalArgumentException) {
        Timber.e(e)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.recordException(e)
        return false
    }
    return true
}

/**
 * Update the JSON file that stores renaming rules with the current rules
 *
 * @param context Context to be used
 * @param dao     DAO to be used
 * @return True if the rules JSON file has been updated properly; false instead
 */
fun updateRenamingRulesJson(context: Context, dao: CollectionDAO): Boolean {
    assertNonUiThread()
    val rules = dao.selectRenamingRules(AttributeType.UNDEFINED, null)

    val contentCollection = JsonContentCollection()
    contentCollection.renamingRules = rules

    val rootFolder =
        getDocumentFromTreeUriString(context, Preferences.getStorageUri(StorageLocation.PRIMARY_1))
            ?: return false

    try {
        jsonToFile(
            context, contentCollection,
            JsonContentCollection::class.java, rootFolder, RENAMING_RULES_JSON_FILE_NAME
        )
    } catch (e: IOException) {
        // NB : IllegalArgumentException might happen for an unknown reason on certain devices
        // even though all the file existence checks are in place
        // ("Failed to determine if primary:.Hentoid/queue.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/queue.json at /storage/emulated/0/.Hentoid/queue.json")
        Timber.e(e)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.recordException(e)
        return false
    } catch (e: IllegalArgumentException) {
        Timber.e(e)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.recordException(e)
        return false
    }
    return true
}

fun logException(t: Throwable) {
    val log: MutableList<LogEntry> = ArrayList()
    log.add(LogEntry(StringHelper.protect(t.message)))
    log.add(LogEntry(getStackTraceString(t)))

    val logInfo = LogInfo("latest-crash")
    logInfo.setEntries(log)
    logInfo.setHeaderName("latest-crash")
    getInstance().writeLog(logInfo)
}

fun getStackTraceString(t: Throwable): String {
    // Don't replace this with Log.getStackTraceString() - it hides
    // UnknownHostException, which is not what we want.
    val sw = StringWriter(256)
    val pw = PrintWriter(sw, false)
    t.printStackTrace(pw)
    pw.flush()
    return sw.toString()
}

// Hack waiting for https://github.com/material-components/material-components-android/issues/2726
fun removeLabels(slider: Slider) {
    slider.labelBehavior = LabelFormatter.LABEL_GONE
    try {
        val ensureLabelsRemoved =
            slider.javaClass.superclass.getDeclaredMethod("ensureLabelsRemoved")
        ensureLabelsRemoved.isAccessible = true
        ensureLabelsRemoved.invoke(slider)
    } catch (e: Exception) {
        Timber.w(e)
    }
}

/**
 * Set the given view's margins, in pixels
 *
 * @param view   View to update the margins for
 * @param left   Left margin (pixels)
 * @param top    Top margin (pixels)
 * @param right  Right margin (pixels)
 * @param bottom Bottom margin (pixels)
 */
fun setMargins(view: View, left: Int, top: Int, right: Int, bottom: Int) {
    if (view.layoutParams is MarginLayoutParams) {
        val p = view.layoutParams as MarginLayoutParams
        p.setMargins(left, top, right, bottom)
    }
}

fun getCenter(view: View): Point? {
    if (view.layoutParams is MarginLayoutParams) {
        val p = view.layoutParams as MarginLayoutParams
        return Point(p.leftMargin + view.width / 2, p.topMargin + view.height / 2)
    }
    return null
}

fun getPrefsIndex(res: Resources, valuesRes: Int, value: String): Int {
    val values = res.getStringArray(valuesRes)
    for ((index, `val`) in values.withIndex()) {
        if (`val` == value) return index
    }
    return -1
}

fun getAppHeapBytes(): Pair<Long, Long> {
    val nativeHeapSize = Debug.getNativeHeapSize()
    val nativeHeapFreeSize = Debug.getNativeHeapFreeSize()
    val usedMemInBytes = nativeHeapSize - nativeHeapFreeSize
    return Pair(usedMemInBytes, nativeHeapFreeSize)
}

fun getSystemHeapBytes(context: Context): Pair<Long, Long> {
    val memoryInfo = ActivityManager.MemoryInfo()
    (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memoryInfo)
    val nativeHeapSize = memoryInfo.totalMem
    val nativeHeapFreeSize = memoryInfo.availMem
    val usedMemInBytes = nativeHeapSize - nativeHeapFreeSize
    return Pair(usedMemInBytes, nativeHeapFreeSize)
}

fun getAppTotalRamBytes(): Long {
    val memInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memInfo)

    val javaMem = memInfo.getMemoryStat("summary.java-heap").toLong() * 1024
    val natiMem = memInfo.getMemoryStat("summary.native-heap").toLong() * 1024
    //val totalMem = memInfo.getMemoryStat("summary.total-pss").toLong() * 1024

    return javaMem + natiMem
}