package me.devsaki.hentoid.util;

import android.text.TextUtils;

import me.devsaki.hentoid.dto.LastVersionDto;
import me.devsaki.hentoid.dto.UserRequest;
import me.devsaki.hentoid.exceptions.HttpClientException;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by DevSaki on 14/05/2015.
 */
public class HttpClientHelper {

    public static String call(URL url) throws HttpClientException, IOException {
        CookieManager cookieManager = (CookieManager)CookieHandler.getDefault();

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setConnectTimeout(10000);
        if(cookieManager.getCookieStore().getCookies().size() > 0)
        {
            urlConnection.setRequestProperty("Cookie",
                    TextUtils.join("; ", cookieManager.getCookieStore().getCookies()));
        }
        urlConnection.connect();

        int code = urlConnection.getResponseCode();

        // Read the input stream into a String
        InputStream inputStream = urlConnection.getInputStream();
        StringBuffer buffer = new StringBuffer();
        if (inputStream == null) {
            // Nothing to do.
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = reader.readLine()) != null) {
            buffer.append(line);
        }

        if (buffer.length() == 0) {
            // Stream was empty.  No point in parsing.
            return null;
        }

        String result = buffer.toString();

        if(code!=200){
            throw new HttpClientException(result, code);
        }

        return result;
    }

    public static LastVersionDto checkLastVersion(UserRequest userRequest) throws HttpClientException, IOException {
        URL url = new URL("http://devsaki.me/versions/last");

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("POST");
        urlConnection.setConnectTimeout(10000);

        urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        OutputStreamWriter os= new OutputStreamWriter(urlConnection.getOutputStream());
        os.write(new Gson().toJson(userRequest));
        os.flush();
        os.close();
        urlConnection.connect();

        int code = urlConnection.getResponseCode();

        // Read the input stream into a String
        InputStream inputStream = urlConnection.getInputStream();
        StringBuffer buffer = new StringBuffer();
        if (inputStream == null) {
            // Nothing to do.
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = reader.readLine()) != null) {
            buffer.append(line);
        }

        if (buffer.length() == 0) {
            // Stream was empty.  No point in parsing.
            return null;
        }

        String result = buffer.toString();

        if(code!=200){
            throw new HttpClientException(result, code);
        }

        return new Gson().fromJson(result, LastVersionDto.class);
    }
}
