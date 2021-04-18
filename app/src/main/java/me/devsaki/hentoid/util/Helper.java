package me.devsaki.hentoid.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.annimon.stream.Stream;

import org.apache.commons.text.StringEscapeUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import io.reactivex.disposables.Disposable;
import io.whitfin.siphash.SipHasher;

import static android.content.Context.CLIPBOARD_SERVICE;

/**
 * Created by avluis on 06/05/2016.
 * Generic utility class
 */
public final class Helper {

    private Helper() {
        throw new IllegalStateException("Utility class");
    }

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    private static final byte[] SIP_KEY = "0123456789ABCDEF".getBytes();


    /**
     * Return the given string formatted with a capital letter as its first letter
     *
     * @param s String to format
     * @return Given string formatted with a capital letter as its first letter
     */
    public static String capitalizeString(String s) {
        if (s == null || s.isEmpty()) return s;
        else if (s.length() == 1) return s.toUpperCase();
        else return s.substring(0, 1).toUpperCase() + s.toLowerCase().substring(1);
    }

    /**
     * Transform the given int to format with a given length
     * - If the given length is shorter than the actual length of the string, it will be truncated
     * - If the given length is longer than the actual length of the string, it will be left-padded with the character 0
     *
     * @param value  String to transform
     * @param length Target length of the final string
     * @return Reprocessed string of given length, according to rules documented in the method description
     */
    public static String formatIntAsStr(int value, int length) {
        String result = String.valueOf(value);

        if (result.length() > length) {
            result = result.substring(0, length);
        } else if (result.length() < length) {
            result = String.format("%1$" + length + "s", result).replace(' ', '0');
        }

        return result;
    }

    /**
     * Indicate of the given string is present as a word inside the given expression
     * "present as a word" means present as a substring separated from other substrings by separating characters
     *
     * @param toDetect   String whose presence to detect within the given expression
     * @param expression Expression where the given string will be searched for
     * @return True if the given string is present as a word inside the given expression; false if not
     */
    public static boolean isPresentAsWord(@NonNull final String toDetect, @NonNull final String expression) {
        String[] words = expression.split("\\W");
        return Stream.of(words).anyMatch(w -> w.equalsIgnoreCase(toDetect));
    }

    /**
     * Decode the given base-64-encoded string
     *
     * @param encodedString Base-64 encoded string to decode
     * @return Raw decoded data
     */
    public static byte[] decode64(String encodedString) {
        // Pure Java
        // return org.apache.commons.codec.binary.Base64.decodeBase64(encodedString);
        // Android
        return android.util.Base64.decode(encodedString, android.util.Base64.DEFAULT);
    }

    /**
     * Encode the given base-64-encoded string
     *
     * @param rawString Raw string to encode
     * @return Encoded string
     */
    public static String encode64(String rawString) {
        return android.util.Base64.encodeToString(rawString.getBytes(), android.util.Base64.DEFAULT);
    }

    /**
     * Retreives the given dimension value as DP, not pixels
     *
     * @param context Context to use to access resources
     * @param id      Dimension resource ID to get the value from
     * @return Given dimension value as DP
     */
    public static int dimensAsDp(@NonNull final Context context, @DimenRes int id) {
        return (int) (context.getResources().getDimension(id) / context.getResources().getDisplayMetrics().density);
    }

    /**
     * Create a Collections.List from the given array of primitive values
     *
     * @param input Array of primitive values to transform
     * @return Given values contained in a Collections.List
     */
    public static List<Long> getListFromPrimitiveArray(long[] input) {
        List<Long> list = new ArrayList<>(input.length);
        for (long n : input) list.add(n);
        return list;
    }

    /**
     * Create a Collections.List from the given array of primitive values
     *
     * @param input Array of primitive values to transform
     * @return Given values contained in a Collections.List
     */
    public static List<Integer> getListFromPrimitiveArray(int[] input) {
        List<Integer> list = new ArrayList<>(input.length);
        for (int n : input) list.add(n);
        return list;
    }

    /**
     * Create an array of primitive types from the given List of values
     *
     * @param input List of values to transform
     * @return Given values as an array of primitive types
     */
    public static long[] getPrimitiveLongArrayFromList(List<Long> input) {
        long[] ret = new long[input.size()];
        Iterator<Long> iterator = input.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    public static int[] getPrimitiveLongArrayFromInt(Set<Integer> input) {
        int[] ret = new int[input.size()];
        Iterator<Integer> iterator = input.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    /**
     * Determine whether the given string represents a numeric value or not
     *
     * @param str Value to test
     * @return True if the given value is numeric (including negative and decimal numbers); false if not
     */
    public static boolean isNumeric(@NonNull final String str) {
        Matcher m = NUMERIC_PATTERN.matcher(str);
        return m.matches();
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
    public static List<InputStream> duplicateInputStream(@Nonnull InputStream stream, int numberDuplicates) throws IOException {
        List<InputStream> result = new ArrayList<>();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len;
        while ((len = stream.read(buffer)) > -1) baos.write(buffer, 0, len);
        baos.flush();

        for (int i = 0; i < numberDuplicates; i++)
            result.add(new ByteArrayInputStream(baos.toByteArray()));

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
            ClipData clip = ClipData.newPlainText("book URL", text);
            clipboard.setPrimaryClip(clip);
            return true;
        } else return false;
    }

    /**
     * Remove all non-printable characters from the given string
     * https://stackoverflow.com/a/18603020/8374722
     *
     * @param s String to cleanup
     * @return Given string stripped from all its non-printable characters
     */
    public static String removeNonPrintableChars(@Nullable final String s) {
        if (null == s || s.isEmpty()) return "";

        StringBuilder newString = new StringBuilder(s.length());
        for (int offset = 0; offset < s.length(); ) {
            int codePoint = s.codePointAt(offset);
            offset += Character.charCount(codePoint);

            // Replace invisible control characters and unused code points
            switch (Character.getType(codePoint)) {
                case Character.CONTROL:     // \p{Cc}
                case Character.FORMAT:      // \p{Cf}
                case Character.PRIVATE_USE: // \p{Co}
                case Character.SURROGATE:   // \p{Cs}
                case Character.UNASSIGNED:  // \p{Cn}
                    // Don't do anything; these are characters we don't want in the new string
                    break;
                default:
                    newString.append(Character.toChars(codePoint));
                    break;
            }
        }
        return newString.toString();
    }

    /**
     * Unescape all escaped characters from the given string (Java convention)
     *
     * @param s String to be cleaned up
     * @return Given string where all escaped characters have been unescaped
     */
    public static String replaceEscapedChars(@NonNull final String s) {
        return StringEscapeUtils.unescapeJava(s);
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

    /// <summary>
    /// Format the given duration using the following format
    ///     DDdHH:MM:SS
    ///
    ///  Where
    ///     DD is the number of days, if applicable (i.e. durations of less than 1 day won't display the "DDd" part)
    ///     HH is the number of hours, if applicable (i.e. durations of less than 1 hour won't display the "HH:" part)
    ///     MM is the number of minutes
    ///     SS is the number of seconds
    /// </summary>
    /// <param name="seconds">Duration to format (in seconds)</param>
    /// <returns>Formatted duration according to the abovementioned convention</returns>

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
        long s = seconds - (60 * m) - (3600 * h);

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
     * Return the given value, or an empty string if it's null
     *
     * @param s String to protect if its value its null
     * @return The given value, or an empty string if it's null
     */
    public static String protect(@Nullable String s) {
        return (null == s) ? "" : s;
    }

    // TODO doc
    public static boolean isValidContextForGlide(final View view) {
        return isValidContextForGlide(view.getContext());
    }

    // TODO doc
    public static boolean isValidContextForGlide(final Context context) {
        if (context == null) {
            return false;
        }
        if (context instanceof Activity) {
            final Activity activity = (Activity) context;
            return !activity.isDestroyed() && !activity.isFinishing();
        }
        return true;
    }

    // TODO doc
    public static long hash64(@NonNull final byte[] data) {
        return SipHasher.hash(SIP_KEY, data);
    }

    // TODO doc
    public static double weigthedAverage(List<Pair<Double, Double>> operands) {
        if (operands.isEmpty()) return 0.0;

        double numerator = 0.0;
        double denominator = 0.0;
        for (Pair<Double, Double> operand : operands) {
            numerator += (operand.first * operand.second);
            denominator += operand.second;
        }
        return numerator / denominator;
    }

    // TODO doc
    public static class LifecycleRxCleaner implements LifecycleObserver {

        private final Disposable disposable;

        public LifecycleRxCleaner(Disposable disposable) {
            this.disposable = disposable;
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        private void onDestroy() {
            disposable.dispose();
        }

        public void publish() {
            Handler autoCleanHandler = new Handler(Looper.getMainLooper());
            autoCleanHandler.post(() -> ProcessLifecycleOwner.get().getLifecycle().addObserver(this));
        }
    }

    // TODO doc
    public static long generateIdForPlaceholder() {
        long result = new Random().nextLong();
        // Make sure nothing collides with an actual ID; nobody has 1M books; it should be fine
        while (result < 1e6) result = new Random().nextLong();
        return result;
    }
}
