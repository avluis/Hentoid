package me.devsaki.hentoid.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.support.v4.content.IntentCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatDrawableManager;
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

    public static String getSessionCookie() {
        return HentoidApp.getSharedPrefs().getString(Consts.WEB_SESSION_COOKIE, "");
    }

    public static void setSessionCookie(String sessionCookie) {
        HentoidApp.getSharedPrefs()
                .edit()
                .putString(Consts.WEB_SESSION_COOKIE, sessionCookie)
                .apply();
    }

    private static void clearSharedPreferences() {
        SharedPreferences.Editor editor = HentoidApp.getSharedPrefs().edit();
        editor.clear();
        editor.apply();
    }

    private static void saveSharedPrefsKey(Context cxt) {
        SharedPreferences sp = cxt.getSharedPreferences(
                ConstsPrefs.PREFS_VERSION_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(ConstsPrefs.PREFS_VERSION_KEY, ConstsPrefs.PREFS_VERSION);
        editor.apply();
    }

    public static void queryPrefsKey(Context cxt) {
        final int prefsVersion = cxt.getSharedPreferences(
                ConstsPrefs.PREFS_VERSION_KEY, Context.MODE_PRIVATE).getInt(
                ConstsPrefs.PREFS_VERSION_KEY, 0);

        Timber.d("Current Prefs Key value: %s", prefsVersion);

        // Use this whenever any incompatible changes are made to Prefs.
        if (prefsVersion != ConstsPrefs.PREFS_VERSION) {
            Timber.d("Shared Prefs Key Mismatch! Clearing Prefs!");

            // Clear All
            clearSharedPreferences();

            // Save current Pref version key
            saveSharedPrefsKey(cxt.getApplicationContext());
        } else {
            Timber.d("Prefs Key Match. Carry on.");
        }
    }

    public static boolean getWebViewOverviewPrefs() {
        return HentoidApp.getSharedPrefs().getBoolean(
                ConstsPrefs.PREF_WEBVIEW_OVERRIDE_OVERVIEW_LISTS,
                ConstsPrefs.PREF_WEBVIEW_OVERRIDE_OVERVIEW_DEFAULT);
    }

    public static int getWebViewInitialZoomPrefs() {
        return Integer.parseInt(
                HentoidApp.getSharedPrefs().getString(
                        ConstsPrefs.PREF_WEBVIEW_INITIAL_ZOOM_LISTS,
                        ConstsPrefs.PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT + ""));
    }

    public static boolean getMobileUpdatePrefs() {
        return HentoidApp.getSharedPrefs().getBoolean(
                ConstsPrefs.PREF_CHECK_UPDATES_LISTS,
                ConstsPrefs.PREF_CHECK_UPDATES_DEFAULT);
    }

    public static void cancelToast() {
        if (toast != null) {
            toast.cancel();
            toast = null;
        }
    }

    // For use whenever Toast messages could stack (e.g., repeated calls to Toast.makeText())
    public static void toast(String text) {
        Context cxt = HentoidApp.getAppContext();
        if (cxt != null) {
            toast(cxt, text);
        }
    }

    public static void toast(int resource) {
        Context cxt = HentoidApp.getAppContext();
        if (cxt != null) {
            toast(cxt, cxt.getResources().getString(resource));
        }
    }

    public static void toast(Context cxt, String text) {
        toast(cxt, text, DURATION.SHORT);
    }

    public static void toast(Context cxt, int resource) {
        toast(cxt, resource, DURATION.SHORT);
    }

    public static void toast(Context cxt, String text, DURATION duration) {
        toast(cxt, text, -1, duration);
    }

    private static void toast(Context cxt, int resource, DURATION duration) {
        toast(cxt, null, resource, duration);
    }

    private static void toast(@NonNull Context cxt, @Nullable String text, int res,
                              DURATION duration) {
        String message = null;
        if (text != null) {
            message = text;
        } else if (res != -1) {
            message = cxt.getString(res);
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
            toast = Toast.makeText(cxt, message, time);
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

    public static void launchMainActivity(Context cxt) {
        final String appLock = HentoidApp.getSharedPrefs().getString(ConstsPrefs.PREF_APP_LOCK, "");

        if (appLock.isEmpty()) {
            Intent intent = new Intent(cxt, DownloadsActivity.class);
            cxt.startActivity(intent);
        } else {
            Intent intent = new Intent(cxt, AppLockActivity.class);
            cxt.startActivity(intent);
        }
    }

    public static boolean isFirstRun() {
        return HentoidApp.getSharedPrefs().getBoolean(
                ConstsPrefs.PREF_FIRST_RUN, ConstsPrefs.PREF_FIRST_RUN_DEFAULT);
    }

    public static void commitFirstRun(boolean commit) {
        SharedPreferences.Editor editor = HentoidApp.getSharedPrefs().edit();
        editor.putBoolean(ConstsPrefs.PREF_FIRST_RUN, commit);
        editor.apply();
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
    public static void reset(Context cxt, Activity activity) {
        Helper.toast(R.string.reset);
        Helper.commitFirstRun(true);
        Intent intent = new Intent(activity, IntroActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        cxt.startActivity(intent);
        activity.finish();
    }

    public static void doRestart(@NonNull Context cxt) {
        try {
            PackageManager pm = cxt.getPackageManager();
            if (pm != null) {
                Intent intent = pm.getLaunchIntentForPackage(cxt.getPackageName());
                if (intent != null) {
                    ComponentName componentName = intent.getComponent();
                    Intent mainIntent = IntentCompat.makeRestartActivityTask(componentName);
                    mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    cxt.startActivity(mainIntent);

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

    // Sets navigation bar background color
    @SuppressLint("NewApi")
    public static void setNavBarColor(Activity activity, int color) {
        if (Helper.isAtLeastAPI(Build.VERSION_CODES.LOLLIPOP)) {
            Context context = activity.getApplicationContext();
            int navColor = ContextCompat.getColor(context, color);
            activity.getWindow().setNavigationBarColor(navColor);
        }
    }

    // Mainly for use with Android < 5.0 - sets OverScroll Glow and Edge Line
    @SuppressLint("NewApi")
    public static void changeEdgeEffect(Context cxt, View list, int glowColor, int lineColor) {
        if (Helper.isAtLeastAPI(Build.VERSION_CODES.LOLLIPOP)) {
            EdgeEffect edgeEffectTop = new EdgeEffect(cxt);
            edgeEffectTop.setColor(glowColor);
            EdgeEffect edgeEffectBottom = new EdgeEffect(cxt);
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
            int glowDrawableId = cxt.getResources().getIdentifier("overscroll_glow", "drawable",
                    "android");
            Drawable androidGlow = ContextCompat.getDrawable(cxt, glowDrawableId);
            androidGlow.setColorFilter(ContextCompat.getColor(cxt, glowColor),
                    PorterDuff.Mode.SRC_ATOP);
            // Android < 5.0 - OverScroll Edge Line
            final int edgeDrawableId = cxt.getResources().getIdentifier("overscroll_edge",
                    "drawable", "android");
            final Drawable overScrollEdge = ContextCompat.getDrawable(cxt, edgeDrawableId);
            overScrollEdge.setColorFilter(ContextCompat.getColor(cxt, lineColor),
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
        Drawable d = AppCompatDrawableManager.get().getDrawable(context, drawableId);

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

    public static int getAppVersionCode(@NonNull Context cxt) throws NameNotFoundException {
        return cxt.getPackageManager().getPackageInfo(cxt.getPackageName(), 0).versionCode;
    }

    public static String getAppVersionInfo(@NonNull Context cxt) throws NameNotFoundException {
        return cxt.getPackageManager().getPackageInfo(cxt.getPackageName(), 0).versionName;
    }

    public static String getAppUserAgent(@NonNull Context cxt) throws NameNotFoundException {
        return Consts.USER_AGENT + " Hentoid/v" +
                cxt.getPackageManager().getPackageInfo(cxt.getPackageName(), 0).versionName;
    }

    public static WebResourceResponse getWebResourceResponseFromAsset(Site site, String filename,
                                                                      TYPE type) {
        Context cxt = HentoidApp.getAppContext();
        String pathPrefix = site.getDescription().toLowerCase(Locale.US) + "/";
        String file = pathPrefix + filename;
        try {
            File asset = new File(cxt.getExternalCacheDir() + "/" + file);
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

    @SuppressLint("NewApi")
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
