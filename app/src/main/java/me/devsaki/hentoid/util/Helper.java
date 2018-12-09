package me.devsaki.hentoid.util;

import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.Html;
import android.text.Spanned;
import android.webkit.WebResourceResponse;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AppLockActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.activities.IntroActivity;
import me.devsaki.hentoid.activities.QueueActivity;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import timber.log.Timber;

import static android.graphics.Bitmap.Config.ARGB_8888;

/**
 * Created by avluis on 06/05/2016.
 * Utility class
 * <p/>
 * TODO: Add additional image viewers.
 */
public final class Helper {
    private static Toast toast;

    public static void viewContent(final Context context, Content content) {
        Intent intent = new Intent(context, content.getWebActivityClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Consts.INTENT_URL, content.getGalleryUrl());
        context.startActivity(intent);
    }

    public static void viewQueue(final Context context) {
        Intent intent = new Intent(context, QueueActivity.class);
        context.startActivity(intent);
    }

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

    @SafeVarargs
    public static <T> void executeAsyncTask(AsyncTask<T, ?, ?> task, T... params) {
        task.execute(params);
    }

    public static void launchMainActivity(Context context) {
        if (Preferences.getAppLockPin().isEmpty()) {
            Intent intent = new Intent(context, DownloadsActivity.class);
            context.startActivity(intent);
        } else {
            Intent intent = new Intent(context, AppLockActivity.class);
            context.startActivity(intent);
        }
    }

    // We have asked for permissions, but still denied.
    public static void reset(Context context, Activity activity) {
        Helper.toast(R.string.reset);
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

    public static String getActivityName(Context context, int attribute) {
        String activityName = context.getString(attribute);
        if (!activityName.isEmpty()) {
            return activityName;
        } else {
            activityName = context.getString(R.string.app_name);
        }
        return activityName;
    }

    public static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
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
            File asset = new File(context.getExternalCacheDir() + "/" + file);
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

    public enum DURATION {SHORT, LONG}

    public enum TYPE {JS, CSS, HTML, PLAIN}

    public static String capitalizeString(String s) {
        if (s == null || s.length() == 0) return s;
        else if (s.length() == 1) return s.toUpperCase();
        else return s.substring(0, 1).toUpperCase() + s.toLowerCase().substring(1);
    }

    /**
     * Transforms the given string to format with a given length
     * - If the given length is shorter than the actual length of the string, it will be truncated
     * - If the given length is longer than the actual length of the string, it will be right/left-padded with a given character
     *
     * @param value  String to transform
     * @param length Target length of the final string
     * @return Reprocessed string of given length, according to rules documented in the method description
     */
    public static String compensateStringLength(int value, int length) {
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

    public static String buildListAsString(List<?> list, String valueDelimiter) {

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

    public static List<Integer> extractAttributeIdsByType(List<Attribute> attrs, AttributeType type) {
        return extractAttributeIdsByType(attrs, new AttributeType[]{type});
    }

    private static List<Integer> extractAttributeIdsByType(List<Attribute> attrs, AttributeType[] types) {
        List<Integer> result = new ArrayList<>();

        for (Attribute a : attrs) {
            for (AttributeType type : types) {
                if (a.getType().equals(type)) result.add(a.getId());
            }
        }

        return result;
    }

    public static List<Integer> extractAttributesIds(List<Attribute> attrs) {
        List<Integer> result = new ArrayList<>();
        for (Attribute attr : attrs) result.add(attr.getId());
        return result;
    }

    public static Uri buildSearchUri(List<Attribute> attributes) {
        AttributeMap metadataMap = new AttributeMap();
        metadataMap.add(attributes);

        Uri.Builder searchUri = new Uri.Builder()
                .scheme("search")
                .authority("hentoid");

        for (AttributeType attrType : metadataMap.keySet()) {
            List<Attribute> attrs = metadataMap.get(attrType);
            for (Attribute attr : attrs)
                searchUri.appendQueryParameter(attrType.name(), attr.getId() + ";" + attr.getName());
        }
        return searchUri.build();
    }

    public static List<Attribute> parseSearchUri(Uri uri) {
        List<Attribute> result = new ArrayList<>();

        if (uri != null)
            for (String typeStr : uri.getQueryParameterNames()) {
                AttributeType type = AttributeType.searchByName(typeStr);
                if (type != null)
                    for (String attrStr : uri.getQueryParameters(typeStr)) {
                        String[] attrParams = attrStr.split(";");
                        if (2 == attrParams.length) {
                            result.add(new Attribute(type, attrParams[1], "").setExternalId(Integer.parseInt(attrParams[0])));
                        }
                    }
            }

        return result;
    }
}
