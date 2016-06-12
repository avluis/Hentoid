package me.devsaki.hentoid.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by avluis on 06/05/2016.
 * JSON related utility class
 */
public class JsonHelper {
    public static <K> void saveJson(K object, File dir) throws IOException {
        File file = new File(dir, Consts.JSON_FILE_NAME_V2);
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        // convert java object to JSON format, and return as a JSON formatted string
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
