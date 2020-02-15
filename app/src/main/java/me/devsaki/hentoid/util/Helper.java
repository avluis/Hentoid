package me.devsaki.hentoid.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.graphics.Bitmap.Config.ARGB_8888;

/**
 * Created by avluis on 06/05/2016.
 * Generic utility class
 */
public final class Helper {

    private Helper() {
        throw new IllegalStateException("Utility class");
    }

    private final static Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    private static int DENSITY_DPI = -1;

    //Currently only nhentai source uses this method
    static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable d = ContextCompat.getDrawable(context, drawableId);

        if (d != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            d = (DrawableCompat.wrap(d)).mutate();
        }

        if (d != null) {
            Bitmap b = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, c.getWidth(), c.getHeight());
            d.draw(c);

            return b;
        } else {
            return Bitmap.createBitmap(0, 0, Bitmap.Config.ARGB_8888);
        }
    }

    static Bitmap tintBitmap(Bitmap bitmap, int color) {
        Paint p = new Paint();
        p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        Bitmap b = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawBitmap(bitmap, 0, 0, p);

        return b;
    }

    public static String capitalizeString(String s) {
        if (s == null || s.isEmpty()) return s;
        else if (s.length() == 1) return s.toUpperCase();
        else return s.substring(0, 1).toUpperCase() + s.toLowerCase().substring(1);
    }

    /**
     * Transforms the given int to format with a given length
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

    public static String decode64(String encodedString) {
        // Pure Java
        //byte[] decodedBytes = org.apache.commons.codec.binary.Base64.decodeBase64(encodedString);
        // Android
        byte[] decodedBytes = android.util.Base64.decode(encodedString, android.util.Base64.DEFAULT);
        return new String(decodedBytes);
    }

    public static int dpToPixel(@NonNull final Context context, int dp) {
        if (-1 == DENSITY_DPI) DENSITY_DPI = context.getResources().getDisplayMetrics().densityDpi;
        float scaleFactor = (1.0f / DisplayMetrics.DENSITY_DEFAULT) * DENSITY_DPI;
        return (int) (dp * scaleFactor);
    }


    public static List<Long> getListFromPrimitiveArray(long[] input) {
        List<Long> list = new ArrayList<>(input.length);
        for (long n : input) list.add(n);
        return list;
    }

    public static long[] getPrimitiveLongArrayFromList(List<Long> integers) {
        long[] ret = new long[integers.size()];
        Iterator<Long> iterator = integers.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }
        return ret;
    }

    // Match a number with optional '-' and decimal.
    public static boolean isNumeric(String str) {
        Matcher m = NUMERIC_PATTERN.matcher(str);
        return m.matches();
    }

    public static float coerceIn(float value, float min, float max) {
        if (value < min) return min;
        else if (value > max) return max;
        else return value;
    }

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

    public static boolean copyPlainTextToClipboard(@NonNull Context context, @NonNull String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("book URL", text);
            clipboard.setPrimaryClip(clip);
            return true;
        } else return false;
    }

    public static boolean isImageExtensionSupported(String extension) {
        return extension.equalsIgnoreCase("jpg")
                || extension.equalsIgnoreCase("jpeg")
                || extension.equalsIgnoreCase("gif")
                || extension.equalsIgnoreCase("png")
                || extension.equalsIgnoreCase("webp");
    }

    // https://stackoverflow.com/a/18603020/8374722
    public static String removeNonPrintableChars(@NonNull final String s) {
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

    // Fix for a crash on 5.1.1
    // https://stackoverflow.com/questions/41025200/android-view-inflateexception-error-inflating-class-android-webkit-webview
    // As fallback solution _only_ since it breaks other stuff in the webview (choice in SELECT tags for instance)
    public static Context getFixedContext(Context context) {
        return context.createConfigurationContext(new Configuration());
    }

    public static int getChromeVersion(Context context) {
        String chromeString = "Chrome/";
        String defaultUserAgent = WebSettings.getDefaultUserAgent(context);
        if (defaultUserAgent.contains(chromeString)) {
            int chromeIndex = defaultUserAgent.indexOf(chromeString);
            int dotIndex = defaultUserAgent.indexOf('.', chromeIndex);
            String version = defaultUserAgent.substring(chromeIndex + chromeString.length(), dotIndex);
            return Integer.parseInt(version);
        } else return -1;
    }
}
