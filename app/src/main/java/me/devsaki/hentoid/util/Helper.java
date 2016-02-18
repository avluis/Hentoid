package me.devsaki.hentoid.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by DevSaki on 10/05/2015.
 * Generic utility class
 */
public final class Helper {

    private static final String TAG = Helper.class.getName();

    public static int ordinalIndexOf(String str, char delimiter, int n) {
        int pos = str.indexOf(delimiter, 0);
        while (n-- > 0 && pos != -1)
            pos = str.indexOf(delimiter, pos + 1);
        return pos;
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