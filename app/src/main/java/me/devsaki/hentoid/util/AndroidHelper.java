package me.devsaki.hentoid.util;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AbsListView;
import android.widget.EdgeEffect;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Field;
import java.util.Arrays;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.AppLockActivity;
import me.devsaki.hentoid.activities.DownloadsActivity;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;

import static android.content.pm.PackageManager.NameNotFoundException;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION;
import static android.os.Build.VERSION_CODES;

/**
 * Created by DevSaki on 20/05/2015.
 * Android focused utility class
 */
public class AndroidHelper {
    private static final String TAG = LogHelper.makeLogTag(AndroidHelper.class);

    private static Toast sToast;

    public static void openContent(Content content, final Context context) {
        SharedPreferences sharedPreferences = HentoidApplication.getAppPreferences();
        File dir = AndroidHelper.getContentDownloadDir(content, context);

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
            String message = context.getString(R.string.image_file_not_found).replace("@dir",
                    dir.getAbsolutePath());
            toast(context, message);
        } else {
            int readContentPreference = Integer.parseInt(sharedPreferences.getString(
                    ConstantsPreferences.PREF_READ_CONTENT_LISTS,
                    ConstantsPreferences.PREF_READ_CONTENT_DEFAULT + ""));
            if (readContentPreference == ConstantsPreferences.PREF_READ_CONTENT_ASK) {
                final File file = imageFile;
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setMessage(R.string.select_the_action)
                        .setPositiveButton(R.string.open_default_image_viewer,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openFile(file, context);
                                    }
                                })
                        .setNegativeButton(R.string.open_perfect_viewer,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        openPerfectViewer(file, context);
                                    }
                                }).create().show();
            } else if (readContentPreference == ConstantsPreferences
                    .PREF_READ_CONTENT_PERFECT_VIEWER) {
                openPerfectViewer(imageFile, context);
            }
        }
    }

    public static File getThumb(Content content, Context context) {
        File dir = AndroidHelper.getContentDownloadDir(content, context);

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

    public static File getContentDownloadDir(Content content, Context context) {
        File file;
        SharedPreferences prefs = HentoidApplication.getAppPreferences();
        String settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
        String folderDir = content.getSite().getFolder() + content.getUniqueSiteId();
        if (settingDir.isEmpty()) {
            return getDefaultDir(folderDir, context);
        }
        file = new File(settingDir, folderDir);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                file = new File(settingDir + folderDir);
                if (!file.exists()) {
                    boolean mkdirs = file.mkdirs();
                    LogHelper.d(TAG, mkdirs);
                }
            }
        }

        return file;
    }

    public static File getSiteDownloadDir(Site site, Context context) {
        File file;
        SharedPreferences prefs = HentoidApplication.getAppPreferences();
        String settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
        String folderDir = site.getFolder();
        if (settingDir.isEmpty()) {
            return getDefaultDir(folderDir, context);
        }
        file = new File(settingDir, folderDir);
        if (!file.exists()) {
            if (!file.mkdirs()) {
                file = new File(settingDir + folderDir);
                if (!file.exists()) {
                    boolean mkdirs = file.mkdirs();
                    LogHelper.d(TAG, mkdirs);
                }
            }
        }

        return file;
    }

    public static File getDefaultDir(String dir, Context context) {
        File file;
        try {
            file = new File(Environment.getExternalStorageDirectory()
                    + "/" + Constants.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
        } catch (Exception e) {
            file = context.getDir("", Context.MODE_PRIVATE);
            file = new File(file, "/" + Constants.DEFAULT_LOCAL_DIRECTORY);
        }

        if (!file.exists()) {
            if (!file.mkdirs()) {
                file = context.getDir("", Context.MODE_PRIVATE);
                file = new File(file, "/" + Constants.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
                if (!file.exists()) {
                    boolean mkdirs = file.mkdirs();
                    LogHelper.d(TAG, mkdirs);
                }
            }
        }

        return file;
    }

    public static boolean isDirEmpty(File directory) {
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

    public static String getSessionCookie() {
        return HentoidApplication.getAppPreferences()
                .getString(ConstantsPreferences.WEB_SESSION_COOKIE, "");
    }

    public static void setSessionCookie(String sessionCookie) {
        HentoidApplication.getAppPreferences()
                .edit()
                .putString(ConstantsPreferences.WEB_SESSION_COOKIE, sessionCookie)
                .apply();
    }

    private static void clearSharedPreferences(Context cxt) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(cxt);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.clear();
        editor.apply();
    }

    private static void clearSharedPreferences(String prefsName, Context cxt) {
        SharedPreferences sharedPrefs = cxt.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.clear();
        editor.apply();
    }

    private static void saveSharedPrefsKey(Context cxt) {
        SharedPreferences sharedPrefs = cxt.getSharedPreferences(
                ConstantsPreferences.PREFS_VERSION_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(ConstantsPreferences.PREFS_VERSION_KEY, ConstantsPreferences.PREFS_VERSION);
        editor.apply();
    }

    public static void queryPrefsKey(Context cxt) {
        final int prefsVersion = cxt.getSharedPreferences(
                ConstantsPreferences.PREFS_VERSION_KEY, Context.MODE_PRIVATE).getInt(
                ConstantsPreferences.PREFS_VERSION_KEY, 0);

        LogHelper.d(TAG, "Current Prefs Key value: " + prefsVersion);

        // Use this whenever any incompatible changes are made to Prefs.
        if (prefsVersion != ConstantsPreferences.PREFS_VERSION) {
            LogHelper.d(TAG, "Shared Prefs Key Mismatch! Clearing Prefs!");

            // Clear All
            clearSharedPreferences(cxt.getApplicationContext());

            // Save current Pref version key
            saveSharedPrefsKey(cxt.getApplicationContext());
        } else {
            LogHelper.d(TAG, "Prefs Key Match. Carry on.");
        }
    }

    private static void openFile(File aFile, Context context) {
        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        File file = new File(aFile.getAbsolutePath());
        String extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        myIntent.setDataAndType(Uri.fromFile(file), mimeType);
        context.startActivity(myIntent);
    }

    public static boolean getWebViewOverviewPrefs() {
        return HentoidApplication.getAppPreferences().getBoolean(
                ConstantsPreferences.PREF_WEBVIEW_OVERRIDE_OVERVIEW_LISTS,
                ConstantsPreferences.PREF_WEBVIEW_OVERRIDE_OVERVIEW_DEFAULT);
    }

    public static int getWebViewInitialZoomPrefs() {
        return Integer.parseInt(
                HentoidApplication.getAppPreferences().getString(
                        ConstantsPreferences.PREF_WEBVIEW_INITIAL_ZOOM_LISTS,
                        ConstantsPreferences.PREF_WEBVIEW_INITIAL_ZOOM_DEFAULT + ""));
    }

    public static boolean getMobileUpdatePrefs() {
        return HentoidApplication.getAppPreferences().getBoolean(
                ConstantsPreferences.PREF_CHECK_UPDATES_LISTS,
                ConstantsPreferences.PREF_CHECK_UPDATES_DEFAULT);
    }

    private static void openPerfectViewer(File firstImage, Context cxt) {
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
        if (sToast != null) {
            sToast.cancel();
        }
    }

    // For use whenever Toast messages could stack (e.g., repeated calls to Toast.makeText())
    public static void toast(String text) {
        Context cxt = HentoidApplication.getAppContext();
        if (cxt != null) {
            toast(cxt, text);
        }
    }

    public static void toast(Context cxt, String text) {
        toast(cxt, text, 0);
    }

    public static void toast(Context cxt, int resource) {
        toast(cxt, resource, 0);
    }

    public static void toast(Context cxt, String text, int duration) {
        toast(cxt, text, -1, duration);
    }

    public static void toast(Context cxt, int resource, int duration) {
        toast(cxt, null, resource, duration);
    }

    private static void toast(Context cxt, String text, int resource, int duration) {
        String message = null;
        if (text != null) {
            message = text;
        } else if (resource != -1) {
            message = cxt.getString(resource);
        } else {
            Throwable noResource = new Throwable("You must provide a String or Resource ID");
            try {
                throw noResource;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        switch (duration) {
            case 1:
                duration = Toast.LENGTH_LONG;
                break;
            default:
                duration = Toast.LENGTH_SHORT;
                break;
        }

        try {
            sToast.getView().isShown();
            sToast.setText(message);
        } catch (Exception e) {
            sToast = Toast.makeText(cxt, message, duration);
        }
        sToast.show();
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

        final String appLock = HentoidApplication.getAppPreferences()
                .getString(ConstantsPreferences.PREF_APP_LOCK, "");

        if (appLock.isEmpty()) {
            Intent intent = new Intent(cxt, DownloadsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ((Activity) cxt).overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            cxt.startActivity(intent);
        } else {
            Intent intent = new Intent(cxt, AppLockActivity.class);
            cxt.startActivity(intent);
        }
    }

    public static boolean isFirstRun() {
        return HentoidApplication.getAppPreferences()
                .getBoolean(ConstantsPreferences.PREF_FIRST_RUN,
                        ConstantsPreferences.PREF_FIRST_RUN_DEFAULT);
    }

    public static void commitFirstRun(boolean commit) {
        SharedPreferences.Editor editor = HentoidApplication
                .getAppPreferences().edit();
        editor.putBoolean(ConstantsPreferences.PREF_FIRST_RUN, commit);
        editor.apply();
    }

    @TargetApi(VERSION_CODES.JELLY_BEAN)
    public static boolean permissionsCheck(Activity activity, int permissionRequestCode) {
        if (ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {

            return true;
        } else {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE}, permissionRequestCode);

            return false;
        }
    }

    // TODO: Research/check for possible NullPointerException on older devices
    public static void setNavBarColor(Activity activity, int color) {
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            Context context = activity.getApplicationContext();
            int navColor = ContextCompat.getColor(context, color);
            activity.getWindow().setNavigationBarColor(navColor);
        }
    }

    // Mainly for use with Android < 5.0 - sets OverScroll Glow and Edge Line
    // TODO: Needs testing
    public static void changeEdgeEffect(Context cxt, View list, int glowColor, int lineColor) {
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
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
        } catch (PackageManager.NameNotFoundException e) {
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
            throw new RuntimeException("No resource ID found for: "
                    + resourceName + " / " + c, e);
        }
    }

    public static int getAppVersionCode(Context context) throws NameNotFoundException {
        if (context != null) {
            return context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionCode;
        } else {
            throw new NullPointerException("context is null");
        }
    }
}