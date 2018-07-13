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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

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

    private static final int TIMEOUT_MS = 15000;

    public static class JSONResponse
    {
        public JSONObject object;
        public Date expiryDate;
    }

    public static <K> void saveJson(K object, File dir) throws IOException {
        File file = new File(dir, Consts.JSON_FILE_NAME_V2);
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        // convert java object to JSON format, and return as a JSON formatted string
        String json = gson.toJson(object);

        OutputStream output = null;
        try {
            output = FileHelper.getOutputStream(file);

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
        StringBuilder json = new StringBuilder();
        try {
            String sCurrentLine;
            br = new BufferedReader(new FileReader(f));
            while ((sCurrentLine = br.readLine()) != null) {
                json.append(sCurrentLine);
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

        return new Gson().fromJson(json.toString(), type);
    }

    @Nullable
    public synchronized static JSONResponse jsonReader(String jsonURL) throws IOException {
        try {
            Request request = new Request.Builder()
                    .url(jsonURL)
                    .addHeader("User-Agent", Helper.getAppUserAgent())
                    .addHeader("Data-type", "application/json")
                    .build();

            Call okHttpCall = OkHttpClientSingleton.getInstance(TIMEOUT_MS).newCall(request);

            Response okHttpResponse = okHttpCall.execute();

            int responseCode = okHttpResponse.code();
            Timber.d("HTTP Response: %s", responseCode);
            if (404 == responseCode) {
                return null;
            }

            JSONResponse response = new JSONResponse();
            String xExpire = okHttpResponse.header("x-expire");
            if (xExpire != null)
            {
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                response.expiryDate = dateFormat.parse(xExpire);
            } else {
                response.expiryDate = new Date();
            }

            ResponseBody body = okHttpResponse.body();
            if (body != null) {
                response.object = new JSONObject(readInputStream(body.byteStream()));
                return response;
            } else {
                Timber.e("JSON request body is null");
                return null;
            }
        } catch (JSONException e) {
            Timber.e(e, "JSON file not properly formatted");
        } catch (ParseException p) {
            Timber.e(p, "Expiry date not properly formatted");
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
