package me.devsaki.hentoid.util;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.IntentCompat;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceResponse;
import android.widget.AbsListView;
import android.widget.EdgeEffect;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AppLockActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.activities.IntroActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

/**
 * Created by avluis on 06/05/2016.
 * Utility class
 * <p/>
 * TODO: Add additional image viewers.
 */
public final class Helper {
    private static final String TAG = LogHelper.makeLogTag(Helper.class);
    private static Toast toast;

    public static void openContent(final Context context, Content content) {
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        File dir = getContentDownloadDir(context, content);
        File imageFile = null;
        File[] files = dir.listFiles();
        Arrays.sort(files);
        for (File file : files) {
            String filename = file.getName();
            if (filename.endsWith(".jpg") ||
                    filename.endsWith(".png") ||
                    filename.endsWith(".gif")) {
                imageFile = file;
                break;
            }
        }
        if (imageFile == null) {
            String message = context.getString(
                    R.string.image_file_not_found).replace("@dir", dir.getAbsolutePath());
            toast(context, message);
        } else {
            int readContentPreference = Integer.parseInt(
                    sp.getString(
                            ConstsPrefs.PREF_READ_CONTENT_LISTS,
                            ConstsPrefs.PREF_READ_CONTENT_DEFAULT + ""));
            if (readContentPreference == ConstsPrefs.PREF_READ_CONTENT_ASK) {
                final File file = imageFile;
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setMessage(R.string.select_the_action)
                        .setPositiveButton(R.string.open_default_image_viewer,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openFile(context, file);
                                    }
                                })
                        .setNegativeButton(R.string.open_perfect_viewer,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openPerfectViewer(context, file);
                                    }
                                }).create().show();
            } else if (readContentPreference == ConstsPrefs.PREF_READ_CONTENT_PERFECT_VIEWER) {
                openPerfectViewer(context, imageFile);
            }
        }
    }

    public static void viewContent(final Context context, Content content) {
        Intent intent = new Intent(context, content.getWebActivityClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Consts.INTENT_URL, content.getGalleryUrl());
        context.startActivity(intent);
    }

    public static File getThumb(Context context, Content content) {
        File dir = getContentDownloadDir(context, content);
        File[] fileList = dir.listFiles(
                new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.getName().contains("thumb");
                    }
                }
        );

        return fileList.length > 0 ? fileList[0] : null;
    }

    public static File getContentDownloadDir(Context context, Content content) {
        File file;
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        String settingDir = sp.getString(Consts.SETTINGS_FOLDER, "");
        String folderDir = content.getSite().getFolder() + content.getUniqueSiteId();
        if (settingDir.isEmpty()) {
            return getDefaultDir(context, folderDir);
        }

        file = new File(settingDir, folderDir);
        if (!file.exists() && !file.mkdirs()) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                boolean mkdirs = file.mkdirs();
                LogHelper.d(TAG, mkdirs);
            }
        }

        return file;
    }

    public static File getSiteDownloadDir(Context context, Site site) {
        File file;
        SharedPreferences sp = HentoidApp.getSharedPrefs();
        String settingDir = sp.getString(Consts.SETTINGS_FOLDER, "");
        String folderDir = site.getFolder();
        if (settingDir.isEmpty()) {
            return getDefaultDir(context, folderDir);
        }
        file = new File(settingDir, folderDir);
        if (!file.exists() && !file.mkdirs()) {
            file = new File(settingDir + folderDir);
            if (!file.exists()) {
                boolean mkdirs = file.mkdirs();
                LogHelper.d(TAG, mkdirs);
            }
        }

        return file;
    }

    public static File getDefaultDir(Context context, String dir) {
        File file;
        try {
            file = new File(Environment.getExternalStorageDirectory() + "/"
                    + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
        } catch (Exception e) {
            file = context.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY);
        }

        if (!file.exists() && !file.mkdirs()) {
            file = context.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Consts.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
            if (!file.exists()) {
                boolean mkdirs = file.mkdirs();
                LogHelper.d(TAG, mkdirs);
            }
        }

        return file;
    }

    // Is the target directory empty or not
    private static boolean isDirEmpty(File directory) {
        if (directory.isDirectory()) {
            String[] files = directory.list();
            if (files.length == 0) {
                LogHelper.d(TAG, "Directory is empty!");
                return true;
            } else {
                LogHelper.d(TAG, "Directory is NOT empty!");
                return false;
            }
        } else {
            LogHelper.d(TAG, "This is not a directory!");
        }
        return false;
    }

    // Gathers list of files in a directory and deletes them
    // but only if the directory is NOT empty - it does NOT delete the target directory
    public static void cleanDir(File directory) {
        boolean isDirEmpty = isDirEmpty(directory);

        if (!isDirEmpty) {
            boolean delete = false;
            String[] children = directory.list();
            for (String child : children) {
                delete = new File(directory, child).delete();
            }
            LogHelper.d(TAG, "Directory cleaned: " + delete);
        }
    }

    // As long as there are files in a directory it will recursively delete them -
    // finally, once there are no files, it deletes the target directory
    public static boolean deleteDir(File directory) {
        if (directory.isDirectory())
            for (File child : directory.listFiles()) {
                deleteDir(child);
            }

        boolean delete = directory.delete();
        LogHelper.d(TAG, "File/directory deleted: " + delete);
        return delete;
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

        LogHelper.d(TAG, "Current Prefs Key value: " + prefsVersion);

        // Use this whenever any incompatible changes are made to Prefs.
        if (prefsVersion != ConstsPrefs.PREFS_VERSION) {
            LogHelper.d(TAG, "Shared Prefs Key Mismatch! Clearing Prefs!");

            // Clear All
            clearSharedPreferences();

            // Save current Pref version key
            saveSharedPrefsKey(cxt.getApplicationContext());
        } else {
            LogHelper.d(TAG, "Prefs Key Match. Carry on.");
        }
    }

    private static void openFile(Context context, File aFile) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        File file = new File(aFile.getAbsolutePath());
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimeType);
        context.startActivity(myIntent);
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

    private static void openPerfectViewer(Context cxt, File firstImage) {
        try {
            Intent intent = cxt
                    .getPackageManager()
                    .getLaunchIntentForPackage("com.rookiestudio.perfectviewer");
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(firstImage), "image/*");
            cxt.startActivity(intent);
        } catch (Exception e) {
            toast(cxt, R.string.error_open_perfect_viewer);
        }
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

    private static void toast(int resource) {
        Context cxt = HentoidApp.getAppContext();
        if (cxt != null) {
            toast(cxt, cxt.getResources().getString(resource));
        }
    }

    public static void toast(Context cxt, String text) {
        toast(cxt, text, 0);
    }

    public static void toast(Context cxt, int resource) {
        toast(cxt, resource, 0);
    }

    private static void toast(Context cxt, String text, int duration) {
        toast(cxt, text, -1, duration);
    }

    private static void toast(Context cxt, int resource, int duration) {
        toast(cxt, null, resource, duration);
    }

    private static void toast(@NonNull Context cxt, @Nullable String text, int res, int duration) {
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

        int time = duration;
        switch (time) {
            case 1:
                time = Toast.LENGTH_LONG;
                break;
            default:
                time = Toast.LENGTH_SHORT;
                break;
        }

        try {
            toast.getView().isShown();
            toast.setText(message);
        } catch (Exception e) {
            LogHelper.d(TAG, "toast is null, creating one instead;");
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

    /**
     * Return true if the first-app-run-activities have already been executed.
     *
     * @param context Context to be used to lookup the {@link SharedPreferences}.
     */
    public static boolean isFirstRunProcessComplete(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                ConstsPrefs.PREF_WELCOME_DONE, false);
    }

    /**
     * Mark {@code newValue whether} this is the first time the first-app-run-processes have run.
     * Managed by {@link me.devsaki.hentoid.abstracts.DrawerActivity} the base activity.
     *
     * @param context  Context to be used to edit the {@link SharedPreferences}.
     * @param newValue New value that will be set.
     */
    public static void markFirstRunProcessesDone(final Context context, boolean newValue) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        sp.edit().putBoolean(ConstsPrefs.PREF_WELCOME_DONE, newValue).apply();
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

    // Note that this is a last resort method -- for use only when ALL else fails.
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
                    LogHelper.e(TAG, "Was not able to restart application, intent null");
                }
            } else {
                LogHelper.e(TAG, "Was not able to restart application, PM null");
            }
        } catch (Exception ex) {
            LogHelper.e(TAG, "Was not able to restart application", ex);
        }
    }

    // Sets navigation bar background color
    public static void setNavBarColor(Activity activity, int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Context context = activity.getApplicationContext();
            int navColor = ContextCompat.getColor(context, color);
            activity.getWindow().setNavigationBarColor(navColor);
        }
    }

    // Mainly for use with Android < 5.0 - sets OverScroll Glow and Edge Line
    public static void changeEdgeEffect(Context cxt, View list, int glowColor, int lineColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
                LogHelper.w(TAG, rEx);
            }
        }

        return R.drawable.ic_menu_unknown;
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

    public enum TYPE {JS, CSS, HTML, PLAIN}

    /**
     * Created by avluis on 06/12/2016.
     * Resource ID Exception
     */
    public static class ResourceException extends Exception {
        private String result;
        private Exception code;

        public ResourceException(String result, Exception code) {
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
