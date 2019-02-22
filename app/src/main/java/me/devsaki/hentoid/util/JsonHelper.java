package me.devsaki.hentoid.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;

import javax.annotation.Nullable;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Created by avluis on 06/05/2016.
 * JSON related utility class
 */
public class JsonHelper {

    public static String serializeToJson(Object o)
    {
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        // convert java object to JSON format, and return as a JSON formatted string
        return gson.toJson(o);
    }

    public static <K> void saveJson(K object, File dir) throws IOException {
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
        // finished
        // Ignore
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

    @Nullable
    synchronized static JSONObject jsonReader(String jsonURL) throws IOException {
        try {
            Request request = new Request.Builder()
                    .url(jsonURL)
                    .addHeader("User-Agent", Consts.USER_AGENT)
                    .addHeader("Data-type", "application/json")
                    .build();

            Call okHttpCall = OkHttpClientSingleton.getInstance().newCall(request);

            Response okHttpResponse = okHttpCall.execute();

            int responseCode = okHttpResponse.code();
            Timber.d("HTTP Response: %s", responseCode);
            if (404 == responseCode) {
                return null;
            }

            ResponseBody body = okHttpResponse.body();
            if (body != null) {
                return new JSONObject(readInputStream(body.byteStream()));
            } else {
                Timber.e("JSON request body is null");
                return null;
            }
        } catch (JSONException e) {
            Timber.e(e, "JSON file not properly formatted");
        }

        return null;
    }

    private static String readInputStream(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder(stream.available());
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
                Charset.forName("UTF-8")));
        String line = reader.readLine();

        while (line != null) {
            builder.append(line);
            line = reader.readLine();
        }
        reader.close();

        return builder.toString();
    }
}
