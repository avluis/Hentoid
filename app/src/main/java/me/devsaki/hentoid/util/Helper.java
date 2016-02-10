package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import me.devsaki.hentoid.HentoidApplication;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.enums.Site;

/**
 * Created by DevSaki on 10/05/2015.
 */
public final class Helper {

    public static int ordinalIndexOf(String str, char delimiter, int n) {
        int pos = str.indexOf(delimiter, 0);
        while (n-- > 0 && pos != -1)
            pos = str.indexOf(delimiter, pos + 1);
        return pos;
    }

    public static String getSessionCookie() {
        return PreferenceManager
                .getDefaultSharedPreferences(HentoidApplication.getInstance())
                .getString(ConstantsPreferences.WEB_SESSION_COOKIE, "");
    }

    public static void setSessionCookie(String sessionCookie) {
        PreferenceManager
                .getDefaultSharedPreferences(HentoidApplication.getInstance())
                .edit()
                .putString(ConstantsPreferences.WEB_SESSION_COOKIE, sessionCookie)
                .apply();
    }

    public static File getDownloadDir(Content content, Context context) {
        File file;
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        String settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
        if (settingDir.isEmpty()) {
            return getDefaultDir(content.getSite().getFolder() + "/" +
                            content.getUniqueSiteId(),
                    context);
        }
        file = new File(settingDir, content.getSite().getFolder() + "/" +
                content.getUniqueSiteId());
        if (!file.exists()) {
            if (!file.mkdirs()) {
                file = new File(settingDir + content.getSite().getFolder() + "/" +
                        content.getUniqueSiteId());
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
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        String settingDir = prefs.getString(Constants.SETTINGS_FOLDER, "");
        if (settingDir.isEmpty()) {
            return getDefaultDir(site.getFolder() + "/", context);
        }
        file = new File(settingDir, site.getFolder() + "/");
        if (!file.exists()) {
            if (!file.mkdirs()) {
                file = new File(settingDir + site.getFolder() + "/");
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

    public static void saveInStorage(File dir, String filename, String imageUrl)
            throws Exception {

        final int BUFFER_SIZE = 23 * 1024;
        OutputStream output = null;
        InputStream input = null;
        File file = null;

        try {
            URL url = new URL(imageUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setConnectTimeout(10000);
            urlConnection.setRequestProperty("User-Agent", Constants.USER_AGENT);
            String sessionCookie = Helper.getSessionCookie();
            if (!sessionCookie.isEmpty()) {
                urlConnection.setRequestProperty("Cookie", sessionCookie);
            }

            switch (urlConnection.getHeaderField("Content-Type")) {
                case "image/png":
                    file = new File(dir, filename + ".png");
                    break;
                case "image/gif":
                    file = new File(dir, filename + ".gif");
                    break;
                default:
                    file = new File(dir, filename + ".jpg");
                    break;
            }

            if (file.exists()) {
                urlConnection.disconnect();
                return;
            }

            input = urlConnection.getInputStream();

            output = new FileOutputStream(file);

            byte data[] = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(data, 0, BUFFER_SIZE)) != -1) {
                output.write(data, 0, count);
            }
            output.flush();
            urlConnection.disconnect();

        } catch (Exception e) {
            if (file != null) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
            throw e;
        } finally {
            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
        }
    }
}