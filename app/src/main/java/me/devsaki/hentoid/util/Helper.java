package me.devsaki.hentoid.util;

import static android.content.Context.CLIPBOARD_SERVICE;
import static me.devsaki.hentoid.util.JsonHelperKt.jsonToFile;
import static me.devsaki.hentoid.util.file.FileHelperKt.FILE_IO_BUFFER_SIZE;
import static me.devsaki.hentoid.util.file.FileHelperKt.getDocumentFromTreeUriString;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.InsetDrawable;
import android.os.Debug;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.view.MenuCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import io.whitfin.siphash.SipHasher;
import kotlin.Pair;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.domains.RenamingRule;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StorageLocation;
import me.devsaki.hentoid.json.JsonContentCollection;
import timber.log.Timber;

/**
 * Generic utility class
 */
public final class Helper {

    private Helper() {
        throw new IllegalStateException("Utility class");
    }

    private static final Random rand = new Random();

    private static final byte[] SIP_KEY = "0123456789ABCDEF".getBytes();


    /**
     * Retreive the given dimension value as DP, not pixels
     *
     * @param context Context to use to access resources
     * @param id      Dimension resource ID to get the value from
     * @return Given dimension value as DP
     */
    public static int dimensAsDp(@NonNull final Context context, @DimenRes int id) {
        return (int) (context.getResources().getDimension(id) / context.getResources().getDisplayMetrics().density);
    }

    /**
     * Convert the given dimension value from DP to pixels
     *
     * @param context Context to use to access resources
     * @param dpValue Dimension value as DP
     * @return Given dimension value as pixels
     */
    public static int dimensAsPx(@NonNull final Context context, int dpValue) {
        Resources r = context.getResources();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue, r.getDisplayMetrics()));
    }

    /**
     * Create a Collections.List from the given array of primitive values
     *
     * @param input Array of primitive values to transform
     * @return Given values contained in a Collections.List
     */
    @kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin does it natively
    public static List<Long> getListFromPrimitiveArray(long[] input) {
        List<Long> list = new ArrayList<>(input.length);
        for (long n : input) list.add(n);
        return list;
    }

    @kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin does it natively
    public static Set<Long> getSetFromPrimitiveArray(long[] input) {
        Set<Long> set = new HashSet<>(input.length);
        for (long n : input) set.add(n);
        return set;
    }

    /**
     * Create a Collections.List from the given array of primitive values
     *
     * @param input Array of primitive values to transform
     * @return Given values contained in a Collections.List
     */
    @kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin does it natively
    public static List<Integer> getListFromPrimitiveArray(int[] input) {
        List<Integer> list = new ArrayList<>(input.length);
        for (int n : input) list.add(n);
        return list;
    }

    @kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin does it natively
    public static Set<Integer> getSetFromPrimitiveArray(int[] input) {
        Set<Integer> list = new HashSet<>(input.length);
        for (int n : input) list.add(n);
        return list;
    }

    /**
     * Create an array of primitive types from the given List of values
     *
     * @param input List of values to transform
     * @return Given values as an array of primitive types
     */
    @kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin does it natively
    public static long[] getPrimitiveArrayFromList(List<Long> input) {
        long[] ret = new long[input.size()];
        Iterator<Long> iterator = input.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    @kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin does it natively
    public static int[] getPrimitiveArrayFromListInt(List<Integer> input) {
        int[] ret = new int[input.size()];
        Iterator<Integer> iterator = input.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    @kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin does it natively
    public static int[] getPrimitiveArrayFromSet(Set<Integer> input) {
        int[] ret = new int[input.size()];
        Iterator<Integer> iterator = input.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    @kotlin.SinceKotlin(version = "99999.0") // Java only; Kotlin does it natively
    public static long[] getPrimitiveLongArrayFromSet(Set<Long> input) {
        long[] ret = new long[input.size()];
        Iterator<Long> iterator = input.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    /**
     * Inclusively coerce the given value between the given min and max values
     *
     * @param value Value to coerce
     * @param min   Min limit (inclusive)
     * @param max   Max limit (inclusive)
     * @return Given value inclusively coerced between the given min and max
     */
    public static float coerceIn(float value, float min, float max) {
        if (value < min) return min;
        else return Math.min(value, max);
    }

    /**
     * Duplicate the given InputStream as many times as given
     *
     * @param stream           Initial InputStream to duplicate
     * @param numberDuplicates Number of duplicates to create
     * @return List containing the given number of duplicated InputStreams
     * @throws IOException If anything goes wrong during the duplication
     */
    public static List<InputStream> duplicateInputStream(@NonNull InputStream stream, int numberDuplicates) throws IOException {
        List<InputStream> result = new ArrayList<>();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            copy(stream, baos);

            for (int i = 0; i < numberDuplicates; i++)
                result.add(new ByteArrayInputStream(baos.toByteArray()));
        }

        return result;
    }

    /**
     * Copy plain text to the device's clipboard
     *
     * @param context Context to be used
     * @param text    Text to copy
     * @return True if the copy has succeeded; false if not
     */
    public static boolean copyPlainTextToClipboard(@NonNull Context context, @NonNull String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(context.getString(R.string.menu_share_title), text);
            clipboard.setPrimaryClip(clip);
            return true;
        } else return false;
    }

    /**
     * Share the given text titled with the given subject
     *
     * @param context Context to be used
     * @param subject Share subject
     * @param text    Text to share
     */
    public static void shareText(@NonNull Context context, @NonNull String subject, @NonNull String text) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, text);

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_to)));
    }

    /**
     * Fix for a crash on 5.1.1
     * https://stackoverflow.com/questions/41025200/android-view-inflateexception-error-inflating-class-android-webkit-webview
     * As fallback solution _only_ since it breaks other stuff in the webview (choice in SELECT tags for instance)
     *
     * @param context Context to fix
     * @return Fixed context
     */
    public static Context getFixedContext(Context context) {
        return context.createConfigurationContext(new Configuration());
    }

    /**
     * Crashes if called on the UI thread
     * To be used as a marker wherever processing in a background thread is mandatory
     */
    public static void assertNonUiThread() {
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            throw new IllegalStateException("This should not be run on the UI thread");
        }
    }

    /**
     * Format the given duration using the HH:MM:SS format
     *
     * @param ms Duration to format, in milliseconds
     * @return FormattedDuration
     */
    public static String formatDuration(long ms) {
        long seconds = (long) Math.floor(ms / 1000f);
        int h = (int) Math.floor(seconds / 3600f);
        int m = (int) Math.floor((seconds - 3600f * h) / 60);
        long s = seconds - (60L * m) - (3600L * h);

        String hStr = String.valueOf(h);
        if (1 == hStr.length()) hStr = "0" + hStr;
        String mStr = String.valueOf(m);
        if (1 == mStr.length()) mStr = "0" + mStr;
        String sStr = String.valueOf(s);
        if (1 == sStr.length()) sStr = "0" + sStr;

        if (h > 0)
            return hStr + ":" + mStr + ":" + sStr;
        else
            return mStr + ":" + sStr;
    }

    /**
     * Build an 64-bit SIP hash from the given data
     *
     * @param data Data to hash
     * @return Hash built from the given data
     */
    public static long hash64(@NonNull final byte[] data) {
        return SipHasher.hash(SIP_KEY, data);
    }

    /**
     * Compute the weighted average of the given operands
     * - Left part is the value
     * - Right part is the coefficient
     *
     * @param operands List of (value, coefficient) pairs
     * @return Weigthed average of the given operands; 0 if uncomputable
     */
    public static float weightedAverage(List<Pair<Float, Float>> operands) {
        if (operands.isEmpty()) return 0;

        float numerator = 0;
        float denominator = 0;
        for (Pair<Float, Float> operand : operands) {
            numerator += (operand.getFirst() * operand.getSecond());
            denominator += operand.getSecond();
        }
        return (denominator > 0) ? numerator / denominator : 0;
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
        byte[] buf = new byte[FILE_IO_BUFFER_SIZE];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    /**
     * Generate an ID for a RecyclerView ViewHolder without any ID to assign to
     *
     * @return Generated ID
     */
    public static long generateIdForPlaceholder() {
        // Make sure nothing collides with an actual ID; nobody has 1M books; it should be fine
        return (long) 1e6 + rand.nextLong();
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
    public static void tryShowMenuIcons(@NonNull Context context, @NonNull Menu menu) {
        try {
            if (menu instanceof MenuBuilder) {
                MenuBuilder builder = (MenuBuilder) menu;
                builder.setOptionalIconsVisible(true);
                int iconMarginPx = (int) context.getResources().getDimension(R.dimen.icon_margin);
                for (MenuItem item : builder.getVisibleItems()) {
                    if (item.getIcon() != null)
                        item.setIcon(new InsetDrawable(item.getIcon(), iconMarginPx, 0, iconMarginPx, 0));
                }
            }
        } catch (Exception e) {
            Timber.i(e);
        }
        MenuCompat.setGroupDividerEnabled(menu, true);
    }

    /**
     * Pauses the calling thread for the given number of milliseconds
     * For safety reasons, this method crahses if called from the main thread
     *
     * @param millis Number of milliseconds to pause the calling thread for
     */
    public static void pause(int millis) {
        assertNonUiThread();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Timber.d(e);
            Thread.currentThread().interrupt();
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
    public static int getRandomInt(int maxExclude) {
        return rand.nextInt(maxExclude);
    }

    // TODO doc
    public static long parseDatetimeToEpoch(@NonNull String date, @NonNull String pattern) {
        final String dateClean = date.trim().replaceAll("(?<=\\d)(st|nd|rd|th)", "");
        final DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern(pattern)
                .withResolverStyle(ResolverStyle.LENIENT)
                .withLocale(Locale.ENGLISH) // To parse english expressions (e.g. month name)
                .withZone(ZoneId.systemDefault());

        try {
            return Instant.from(formatter.parse(dateClean)).toEpochMilli();
        } catch (DateTimeParseException e) {
            Timber.w(e);
        }
        return 0;
    }

    // https://www.threeten.org/threetenbp/apidocs/org/threeten/bp/format/DateTimeFormatter.html#ofPattern(java.lang.String)
    public static long parseDateToEpoch(@NonNull String date, @NonNull String pattern) {
        final String dateClean = date.trim().replaceAll("(?<=\\d)(st|nd|rd|th)", "");
        final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .parseDefaulting(ChronoField.NANO_OF_DAY, 0) // To allow passing dates without time
                .toFormatter()
                .withResolverStyle(ResolverStyle.LENIENT)
                .withLocale(Locale.ENGLISH) // To parse english expressions (e.g. month name)
                .withZone(ZoneId.systemDefault());

        try {
            return Instant.from(formatter.parse(dateClean)).toEpochMilli();
        } catch (DateTimeParseException e) {
            Timber.w(e);
        }
        return 0;
    }

    public static String formatEpochToDate(long epoch, String pattern) {
        return formatEpochToDate(epoch, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
    }

    public static String formatEpochToDate(long epoch, DateTimeFormatter formatter) {
        if (0 == epoch) return "";
        Instant i = Instant.ofEpochMilli(epoch);
        return i.atZone(ZoneId.systemDefault()).format(formatter);
    }

    /**
     * Update the JSON file that stores bookmarks with the current bookmarks
     *
     * @param context Context to be used
     * @param dao     DAO to be used
     * @return True if the bookmarks JSON file has been updated properly; false instead
     */
    public static boolean updateBookmarksJson(@NonNull Context context, @NonNull CollectionDAO dao) {
        Helper.assertNonUiThread();
        List<SiteBookmark> bookmarks = dao.selectAllBookmarks();

        JsonContentCollection contentCollection = new JsonContentCollection();
        contentCollection.setBookmarks(bookmarks);

        DocumentFile rootFolder = getDocumentFromTreeUriString(context, Preferences.getStorageUri(StorageLocation.PRIMARY_1));
        if (null == rootFolder) return false;

        try {
            jsonToFile(context, contentCollection, JsonContentCollection.class, rootFolder, Consts.BOOKMARKS_JSON_FILE_NAME);
        } catch (IOException | IllegalArgumentException e) {
            // NB : IllegalArgumentException might happen for an unknown reason on certain devices
            // even though all the file existence checks are in place
            // ("Failed to determine if primary:.Hentoid/queue.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/queue.json at /storage/emulated/0/.Hentoid/queue.json")
            Timber.e(e);
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.recordException(e);
            return false;
        }
        return true;
    }

    /**
     * Update the JSON file that stores renaming rules with the current rules
     *
     * @param context Context to be used
     * @param dao     DAO to be used
     * @return True if the rules JSON file has been updated properly; false instead
     */
    public static boolean updateRenamingRulesJson(@NonNull Context context, @NonNull CollectionDAO dao) {
        Helper.assertNonUiThread();
        List<RenamingRule> rules = dao.selectRenamingRules(AttributeType.UNDEFINED, null);

        JsonContentCollection contentCollection = new JsonContentCollection();
        contentCollection.setRenamingRules(rules);

        DocumentFile rootFolder = getDocumentFromTreeUriString(context, Preferences.getStorageUri(StorageLocation.PRIMARY_1));
        if (null == rootFolder) return false;

        try {
            jsonToFile(context, contentCollection, JsonContentCollection.class, rootFolder, Consts.RENAMING_RULES_JSON_FILE_NAME);
        } catch (IOException | IllegalArgumentException e) {
            // NB : IllegalArgumentException might happen for an unknown reason on certain devices
            // even though all the file existence checks are in place
            // ("Failed to determine if primary:.Hentoid/queue.json is child of primary:.Hentoid: java.io.FileNotFoundException: Missing file for primary:.Hentoid/queue.json at /storage/emulated/0/.Hentoid/queue.json")
            Timber.e(e);
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.recordException(e);
            return false;
        }
        return true;
    }

    public static void logException(Throwable t) {
        List<LogEntry> log = new ArrayList<>();
        log.add(new LogEntry(StringHelper.protect(t.getMessage())));
        log.add(new LogEntry(Helper.getStackTraceString(t)));

        LogInfo logInfo = new LogInfo("latest-crash");
        logInfo.setEntries(log);
        logInfo.setHeaderName("latest-crash");
        LogHelperKt.writeLog(HentoidApp.Companion.getInstance(), logInfo);
    }

    public static String getStackTraceString(Throwable t) {
        // Don't replace this with Log.getStackTraceString() - it hides
        // UnknownHostException, which is not what we want.
        StringWriter sw = new StringWriter(256);
        PrintWriter pw = new PrintWriter(sw, false);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    // Hack waiting for https://github.com/material-components/material-components-android/issues/2726
    public static void removeLabels(@NonNull Slider slider) {
        slider.setLabelBehavior(LabelFormatter.LABEL_GONE);
        try {
            @SuppressWarnings("ConstantConditions")
            Method ensureLabelsRemoved = slider.getClass().getSuperclass().getDeclaredMethod("ensureLabelsRemoved");
            ensureLabelsRemoved.setAccessible(true);
            ensureLabelsRemoved.invoke(slider);
        } catch (Exception e) {
            Timber.w(e);
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
    public static void setMargins(@NonNull View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            p.setMargins(left, top, right, bottom);
        }
    }

    @Nullable
    public static Point getCenter(@NonNull View view) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            return new Point(p.leftMargin + view.getWidth() / 2, p.topMargin + view.getHeight() / 2);
        }
        return null;
    }

    public static int getPrefsIndex(@NonNull Resources res, int valuesRes, String value) {
        String[] values = res.getStringArray(valuesRes);
        int index = 0;
        for (String val : values) {
            if (val.equals(value)) return index;
            index++;
        }
        return -1;
    }

    public static Pair<Long, Long> getAppHeapBytes() {
        long nativeHeapSize = Debug.getNativeHeapSize();
        long nativeHeapFreeSize = Debug.getNativeHeapFreeSize();
        long usedMemInBytes = nativeHeapSize - nativeHeapFreeSize;
        return new Pair<>(usedMemInBytes, nativeHeapFreeSize);
    }

    public static Pair<Long, Long> getSystemHeapBytes(Context context) {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
        long nativeHeapSize = memoryInfo.totalMem;
        long nativeHeapFreeSize = memoryInfo.availMem;
        long usedMemInBytes = nativeHeapSize - nativeHeapFreeSize;
        return new Pair<>(usedMemInBytes, nativeHeapFreeSize);
    }

    public static long getAppTotalRamBytes() {
        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);

        long javaMem = Long.parseLong(memInfo.getMemoryStat("summary.java-heap")) * 1024;
        long natiMem = Long.parseLong(memInfo.getMemoryStat("summary.native-heap")) * 1024;
        long totalMem = Long.parseLong(memInfo.getMemoryStat("summary.total-pss")) * 1024;

        return javaMem + natiMem;
    }
}
