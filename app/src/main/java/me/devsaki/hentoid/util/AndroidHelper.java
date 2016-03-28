package me.devsaki.hentoid.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.Site;

/**
 * Created by DevSaki on 20/05/2015.
 * Android focused utility class
 */
public class AndroidHelper {

    public static void openContent(Content content, final Context context) {
        SharedPreferences sharedPreferences = HentoidApplication.getAppPreferences();
        File dir = AndroidHelper.getDownloadDir(content, context);

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
            String message = context.getString(R.string.not_image_file_found).replace("@dir", dir.getAbsolutePath());
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
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
        File dir = AndroidHelper.getDownloadDir(content, context);

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

    public static File getDownloadDir(Content content, Context context) {
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
                    //noinspection ResultOfMethodCallIgnored
                    file.mkdirs();
                }
            }
        }
        return file;
    }

    public static File getDownloadDir(Site site, Context context) {
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
                    //noinspection ResultOfMethodCallIgnored
                    file.mkdirs();
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
            file = context.getDir("", Context.MODE_WORLD_WRITEABLE);
            file = new File(file, "/" + Constants.DEFAULT_LOCAL_DIRECTORY);
        }

        if (!file.exists()) {
            if (!file.mkdirs()) {
                file = context.getDir("", Context.MODE_WORLD_WRITEABLE);
                file = new File(file, "/" + Constants.DEFAULT_LOCAL_DIRECTORY + "/" + dir);
                if (!file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.mkdirs();
                }
            }
        }
        return file;
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

    private static void clearSharedPreferences(Context ctx) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.clear();
        editor.apply();
    }

    private static void clearSharedPreferences(String prefsName, Context ctx) {
        SharedPreferences sharedPrefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.clear();
        editor.apply();
    }

    private static void saveSharedPrefsKey(int prefsVersion, Context ctx) {
        SharedPreferences sharedPrefs = ctx.getSharedPreferences(
                ConstantsPreferences.PREFS_VERSION_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putInt(ConstantsPreferences.PREFS_VERSION_KEY, prefsVersion);
        editor.apply();
    }

    public static void queryPrefsKey(Context ctx) {
        final int prefsVersion = ctx.getSharedPreferences(
                ConstantsPreferences.PREFS_VERSION_KEY, Context.MODE_PRIVATE).getInt(
                ConstantsPreferences.PREFS_VERSION_KEY, 0);

        System.out.println("Current Prefs Key value: " + prefsVersion);

        // Use this whenever any incompatible changes are made to Prefs.
        if (prefsVersion != ConstantsPreferences.PREFS_VERSION) {
            System.out.println("Shared Prefs Key Mismatch! Clearing Prefs!");

            // Clear All
            clearSharedPreferences(ctx.getApplicationContext());

            // Save current Pref version key
            saveSharedPrefsKey(ConstantsPreferences.PREFS_VERSION,
                    ctx.getApplicationContext());
        } else {
            System.out.println("Prefs Key Match. Carry on.");
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

    private static void openPerfectViewer(File firstImage, Context context) {
        try {
            Intent intent = context
                    .getPackageManager()
                    .getLaunchIntentForPackage("com.rookiestudio.perfectviewer");
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(firstImage), "image/*");
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, R.string.error_open_perfect_viewer,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @SafeVarargs
    public static <T> void executeAsyncTask(AsyncTask<T, ?, ?> task, T... params) {
        task.execute(params);
    }

    public static void ignoreSslErrors() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }
}