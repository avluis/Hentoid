package me.devsaki.hentoid.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import android.text.Html;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.webkit.WebResourceResponse;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.IntroActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.activities.UnlockActivity;
import me.devsaki.hentoid.activities.bundles.BaseWebActivityBundle;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import timber.log.Timber;

import static android.graphics.Bitmap.Config.ARGB_8888;

/**
 * Created by avluis on 06/05/2016.
 * Utility class
 */
public final class Helper {

    public static void viewContent(final Context context, Content content) {
        viewContent(context, content, false);
    }

    public static void viewContent(final Context context, Content content, boolean wrapPin) {
        Intent intent = new Intent(context, content.getWebActivityClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        BaseWebActivityBundle.Builder builder = new BaseWebActivityBundle.Builder();
        builder.setUrl(content.getGalleryUrl());
        intent.putExtras(builder.getBundle());
        if (wrapPin) intent = UnlockActivity.wrapIntent(context, intent);
        context.startActivity(intent);
    }

    public static void viewQueue(final Context context) {
        Intent intent = new Intent(context, QueueActivity.class);
        context.startActivity(intent);
    }

    // We have asked for permissions, but still denied.
    public static void reset(Context context, Activity activity) {
        ToastUtil.toast(R.string.reset);
        Preferences.setIsFirstRun(true);
        Intent intent = new Intent(activity, IntroActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
        activity.finish();
    }

    public static void doRestart(@NonNull Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                Intent intent = pm.getLaunchIntentForPackage(context.getPackageName());
                if (intent != null) {
                    ComponentName componentName = intent.getComponent();
                    Intent mainIntent = Intent.makeRestartActivityTask(componentName);
                    mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(mainIntent);

                    Runtime.getRuntime().exit(0);
                } else {
                    Timber.d("Was not able to restart application, intent null");
                }
            } else {
                Timber.d("Was not able to restart application, PM null");
            }
        } catch (Exception e) {
            Timber.e(e, "Was not able to restart application");
        }
    }

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

    public static WebResourceResponse getWebResourceResponseFromAsset(Site site, String filename,
                                                                      TYPE type) {
        Context context = HentoidApp.getAppContext();
        String pathPrefix = site.getDescription().toLowerCase(Locale.US) + "/";
        String file = pathPrefix + filename;
        try {
            File asset = new File(context.getExternalCacheDir() + File.separator + file);
            FileInputStream stream = new FileInputStream(asset);
            return Helper.getUtf8EncodedWebResourceResponse(stream, type);
        } catch (IOException e) {
            return null;
        }
    }

    private static WebResourceResponse getUtf8EncodedWebResourceResponse(InputStream open,
                                                                         TYPE type) {
        switch (type) {
            case JS:
                return new WebResourceResponse("text/js", "UTF-8", open);
            case CSS:
                return new WebResourceResponse("text/css", "UTF-8", open);
            case HTML:
                return new WebResourceResponse("text/html", "UTF-8", open);
            case PLAIN:
            default:
                return new WebResourceResponse("text/plain", "UTF-8", open);
        }
    }

    public static Spanned fromHtml(String source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY, null, null);
        } else {
            //noinspection deprecation
            return Html.fromHtml(source);
        }
    }

    public enum TYPE {JS, CSS, HTML, PLAIN}

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

    public static String buildListAsString(List<?> list) {
        return buildListAsString(list, "");
    }

    private static String buildListAsString(List<?> list, String valueDelimiter) {

        StringBuilder str = new StringBuilder();
        if (list != null) {
            boolean first = true;
            for (Object o : list) {
                if (!first) str.append(",");
                else first = false;
                str.append(valueDelimiter).append(o.toString().toLowerCase()).append(valueDelimiter);
            }
        }

        return str.toString();
    }

    public static List<Long> extractAttributeIdsByType(List<Attribute> attrs, AttributeType type) {
        return extractAttributeIdsByType(attrs, new AttributeType[]{type});
    }

    private static List<Long> extractAttributeIdsByType(List<Attribute> attrs, AttributeType[] types) {
        List<Long> result = new ArrayList<>();

        for (Attribute a : attrs) {
            for (AttributeType type : types) {
                if (a.getType().equals(type)) result.add(a.getId());
            }
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

    public static int dpToPixel(Context context, int dp) {
        float scaleFactor =
                (1.0f / DisplayMetrics.DENSITY_DEFAULT)
                        * context.getResources().getDisplayMetrics().densityDpi;

        return (int) (dp * scaleFactor);
    }


    public static List<Long> getListFromPrimitiveArray(long[] input) {
        List<Long> list = new ArrayList<>(input.length);
        for (long n : input) list.add(n);
        return list;
    }

    public static List<Integer> getListFromPrimitiveArray(int[] input) {
        List<Integer> list = new ArrayList<>(input.length);
        for (int n : input) list.add(n);
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

    public static boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
    }

    /**
     * Open the given url using the device's app(s) of choice
     *
     * @param context Context
     * @param url     Url to be opened
     */
    public static void openUrl(Context context, String url) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            context.startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Timber.e(e, "Activity not found to open %s", url);
            ToastUtil.toast(context, R.string.error_open, Toast.LENGTH_LONG);
        }
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
}
