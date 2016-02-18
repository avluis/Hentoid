package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.Site;

/**
 * Created by DevSaki on 10/05/2015.
 */
public final class Helper {

    private static final String TAG = Helper.class.getName();

    public static int ordinalIndexOf(String str, char delimiter, int n) {
        int pos = str.indexOf(delimiter, 0);
        while (n-- > 0 && pos != -1)
            pos = str.indexOf(delimiter, pos + 1);
        return pos;
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

    public static File getThumb(Content content, Context context) {
        File dir = Helper.getDownloadDir(content, context);

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

    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and
            // width
            final int heightRatio = Math.round((float) height
                    / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will
            // guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromFile(String file, int reqWidth,
                                                     int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth,
                reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file, options);
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

    private static String escapeURL(String link) {
        try {
            String path = link;
            path = URLEncoder.encode(path, "utf8");
            path = path.replace("%3A", ":");
            path = path.replace("%2F", "/");
            path = path.replace("+", "%20");
            path = path.replace("%23", "#");
            path = path.replace("%3D", "=");
            return path;
        } catch (Exception e) {
            link = link.replaceAll("\\[", "%5B");
            link = link.replaceAll("\\]", "%5D");
            link = link.replaceAll("\\s", "%20");
        }
        return link;
    }

    public static <K> void saveJson(K object, File dir)
            throws IOException {
        File file = new File(dir, Constants.JSON_FILE_NAME_V2);
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        // convert java object to JSON format,
        // and returned as JSON formatted string
        String json = gson.toJson(object);
        FileWriter writer = new FileWriter(file, false);
        writer.write(json);
        writer.close();
    }

    public static <T> T jsonToObject(File f, Class<T> type) throws IOException {
        BufferedReader br = null;
        String json = "";
        try {

            String sCurrentLine;
            br = new BufferedReader(new FileReader(f));

            while ((sCurrentLine = br.readLine()) != null) {
                json += sCurrentLine;
            }

        } finally {
            if (br != null) br.close();
        }
        return new Gson().fromJson(json, type);
    }
}