package me.devsaki.hentoid.util;

import androidx.documentfile.provider.DocumentFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;

import javax.annotation.Nonnull;

import timber.log.Timber;

/**
 * Created by avluis on 06/05/2016.
 * JSON related utility class
 */
public class JsonHelper {

    public static String serializeToJson(Object o) {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        // convert java object to JSON format, and return as a JSON formatted string
        return gson.toJson(o);
    }

    /**
     * Serialize and save the object contents to a json file in the given directory.
     * The JSON file is created if it doesn't exist
     * @param object Object to be serialized and saved
     * @param dir Existing folder to save the JSON file to
     * @param <K> Type of the object to save
     * @throws IOException If anything happens during file I/O
     */
    public static <K> File createJson(K object, File dir) throws IOException {
        File file = new File(dir, Consts.JSON_FILE_NAME_V2);
        String json = serializeToJson(object);
        try (OutputStream output = FileHelper.getOutputStream(file)) {
            if (output != null) {
                // build
                byte[] bytes = json.getBytes();
                // write
                output.write(bytes);
                FileHelper.sync(output);
                output.flush();
            } else {
                Timber.w("JSON file creation failed for %s", file.getPath());
            }
        }
        return file;
    }

    /**
     * Serialize and save the object contents to an existing file using the JSON format
     * @param object Object to be serialized and saved
     * @param file Existing file to save to
     * @param <K> Type of the object to save
     * @throws IOException If anything happens during file I/O
     */
    static <K> void updateJson(K object, @Nonnull DocumentFile file) throws IOException {
        try (OutputStream output = FileHelper.getOutputStream(file)) {
            if (output != null) {
                String json = serializeToJson(object);
                // build
                byte[] bytes = json.getBytes();
                // write
                output.write(bytes);
                FileHelper.sync(output);
                output.flush();
            } else {
                Timber.w("JSON file creation failed for %s", file.getUri());
            }
        } catch (FileNotFoundException e) {
            Timber.e(e);
        }
    }

    public static <T> T jsonToObject(File f, Class<T> type) throws IOException {
        StringBuilder json = new StringBuilder();
        String sCurrentLine;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            while ((sCurrentLine = br.readLine()) != null) {
                json.append(sCurrentLine);
            }
        }
        // Ignore

        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        return gson.fromJson(json.toString(), type);
    }
}
