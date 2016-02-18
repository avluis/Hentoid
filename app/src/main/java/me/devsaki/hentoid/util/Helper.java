package me.devsaki.hentoid.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

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