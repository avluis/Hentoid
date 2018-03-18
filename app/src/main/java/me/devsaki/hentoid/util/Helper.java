package me.devsaki.hentoid.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.webkit.WebResourceResponse;
import android.widget.AbsListView;
import android.widget.EdgeEffect;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Locale;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AppLockActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.activities.IntroActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import timber.log.Timber;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.graphics.Bitmap.Config.ARGB_8888;

/**
 * Created by avluis on 06/05/2016.
 * Utility class
 * <p/>
 * TODO: Add additional image viewers.
 */
public final class Helper {
    private static Toast toast;

    /**
     * @param apiLevel minimum API level version that has to support the device
     * @return true when the caller API version is at least apiLevel
     */
    public static boolean isAtLeastAPI(int apiLevel) {
        return Build.VERSION.SDK_INT >= apiLevel;
    }

    public static void viewContent(final Context context, Content content) {
        Intent intent = new Intent(context, content.getWebActivityClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Consts.INTENT_URL, content.getGalleryUrl());
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

    private static void toast(Context context, int resource, DURATION duration) {
        toast(context, null, resource, duration);
    }

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

    public static void ignoreSslErrors() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static boolean permissionsCheck(Activity activity, int permissionRequestCode,
                                           boolean request) {
        if (ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {

            return true;
        } else {
            if (request) {
                ActivityCompat.requestPermissions(activity, new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE}, permissionRequestCode);
            }

            return false;
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

    // Mainly for use with Android < 5.0 - sets OverScroll Glow and Edge Line
    @SuppressLint("NewApi")
    public static void changeEdgeEffect(Context context, View list, int glowColor, int lineColor) {
        if (Helper.isAtLeastAPI(Build.VERSION_CODES.LOLLIPOP)) {
            EdgeEffect edgeEffectTop = new EdgeEffect(context);
            edgeEffectTop.setColor(glowColor);
            EdgeEffect edgeEffectBottom = new EdgeEffect(context);
            edgeEffectBottom.setColor(glowColor);

            try {
                Field f1 = AbsListView.class.getDeclaredField("mEdgeGlowTop");
                f1.setAccessible(true);
                f1.set(list, edgeEffectTop);

                Field f2 = AbsListView.class.getDeclaredField("mEdgeGlowBottom");
                f2.setAccessible(true);
                f2.set(list, edgeEffectBottom);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Android < 5.0 - OverScroll Glow
            int glowDrawableId = context.getResources().getIdentifier("overscroll_glow", "drawable",
                    "android");
            Drawable androidGlow = ContextCompat.getDrawable(context, glowDrawableId);
            androidGlow.setColorFilter(ContextCompat.getColor(context, glowColor),
                    PorterDuff.Mode.SRC_ATOP);
            // Android < 5.0 - OverScroll Edge Line
            final int edgeDrawableId = context.getResources().getIdentifier("overscroll_edge",
                    "drawable", "android");
            final Drawable overScrollEdge = ContextCompat.getDrawable(context, edgeDrawableId);
            overScrollEdge.setColorFilter(ContextCompat.getColor(context, lineColor),
                    PorterDuff.Mode.SRC_ATOP);
        }
    }

    /**
     * Get a color value from a theme attribute.
     *
     * @param context      used for getting the color.
     * @param attribute    theme attribute.
     * @param defaultColor default to use.
     * @return color value
     */
    public static int getThemeColor(Context context, int attribute, int defaultColor) {
        int themeColor = 0;
        String packageName = context.getPackageName();
        try {
            Context packageContext = context.createPackageContext(packageName, 0);
            ApplicationInfo applicationInfo =
                    context.getPackageManager().getApplicationInfo(packageName, 0);
            packageContext.setTheme(applicationInfo.theme);
            Resources.Theme theme = packageContext.getTheme();
            TypedArray ta = theme.obtainStyledAttributes(new int[]{attribute});
            themeColor = ta.getColor(0, defaultColor);
            ta.recycle();
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return themeColor;
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

    public static int getId(String resourceName, Class<?> c) {
        try {
            Field idField = c.getDeclaredField(resourceName);
            return idField.getInt(idField);
        } catch (Exception e) {
            try {
                throw new ResourceException("No resource ID found for: " + resourceName +
                        " / " + c, e);
            } catch (ResourceException rEx) {
                Timber.w(rEx);
            }
        }

        return R.drawable.ic_menu_unknown;
    }

    public static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable d = ContextCompat.getDrawable(context, drawableId);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            d = (DrawableCompat.wrap(d)).mutate();
        }

        Bitmap b = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, c.getWidth(), c.getHeight());
        d.draw(c);

        return b;
    }

    public static Bitmap tintBitmap(Bitmap bitmap, int color) {
        Paint p = new Paint();
        p.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        Bitmap b = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), ARGB_8888);
        Canvas canvas = new Canvas(b);
        canvas.drawBitmap(bitmap, 0, 0, p);

        return b;
    }

    public static int getAppVersionCode(@NonNull Context context) throws NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
    }

    public static String getAppVersionInfo(@NonNull Context context) throws NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
    }

    public static String getAppUserAgent(@NonNull Context context) throws NameNotFoundException {
        return Consts.USER_AGENT + " Hentoid/v" +
                context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
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
        if (isAtLeastAPI(Build.VERSION_CODES.N)) {
            return Html.fromHtml(source, Html.FROM_HTML_MODE_LEGACY, null, null);
        } else {
            //noinspection deprecation
            return Html.fromHtml(source);
        }
    }

    public enum DURATION {SHORT, LONG}

    public enum TYPE {JS, CSS, HTML, PLAIN}

    /**
     * Created by avluis on 06/12/2016.
     * Resource ID Exception
     */
    private static class ResourceException extends Exception {
        private String result;
        private Exception code;

        ResourceException(String result, Exception code) {
            this.result = result;
            this.code = code;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public Exception getCode() {
            return code;
        }

        public void setCode(Exception code) {
            this.code = code;
        }
    }
}
