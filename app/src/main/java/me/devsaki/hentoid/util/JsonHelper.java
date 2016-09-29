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
import java.net.URL;
import java.nio.charset.Charset;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by avluis on 06/05/2016.
 * JSON related utility class
 */
public class JsonHelper {
    private static final String TAG = LogHelper.makeLogTag(JsonHelper.class);

    public static <K> void saveJson(K object, File dir) throws IOException {
        File file = new File(dir, Consts.JSON_FILE_NAME_V2);
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        // convert java object to JSON format, and return as a JSON formatted string
        String json = gson.toJson(object);

        OutputStream output = null;
        try {
            output = FileHelper.getOutputStream(file);
            // build
            byte[] bytes = json.getBytes();
            // write
            output.write(bytes);
            FileHelper.sync(output);
            output.flush();
        } finally {
            // finished
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
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
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        return new Gson().fromJson(json, type);
    }

    public JSONObject jsonReader(String jsonURL) throws IOException {
        HttpsURLConnection https = null;
        InputStream stream = null;
        try {
            URL url = new URL(jsonURL);
            https = (HttpsURLConnection) url.openConnection();
            https.setReadTimeout(10000);
            https.setConnectTimeout(15000);
            https.setRequestMethod("GET");
            https.setDoInput(true);

            https.connect();
            int response = https.getResponseCode();

            LogHelper.d(TAG, "HTTP Response: " + response);
            if (response == 404) {
                return null;
            }

            stream = https.getInputStream();
            String s = readInputStream(stream);

            return new JSONObject(s);
        } catch (JSONException e) {
            LogHelper.e(TAG, e, "JSON file not properly formatted");
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            if (https != null) {
                https.disconnect();
            }
        }

        return null;
    }

    private String readInputStream(InputStream stream) throws IOException {
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
