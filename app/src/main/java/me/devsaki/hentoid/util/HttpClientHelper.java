package me.devsaki.hentoid.util;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import me.devsaki.hentoid.exceptions.HttpClientException;

/**
 * Created by DevSaki on 14/05/2015.
 */
public class HttpClientHelper {

    public static String call(String address) throws HttpClientException, IOException, URISyntaxException {
        URL url = new URL(address);
        URI uri = new URI(address);
        CookieManager cookieManager = (CookieManager) CookieHandler.getDefault();

        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setConnectTimeout(10000);
        urlConnection.setRequestProperty("User-Agent", Constants.USER_AGENT);
        if (cookieManager != null && cookieManager.getCookieStore().getCookies().size() > 0) {
            urlConnection.setRequestProperty("Cookie",
                    TextUtils.join("; ", cookieManager.getCookieStore().get(uri)));
        }
        urlConnection.connect();

        int code = urlConnection.getResponseCode();

        // Read the input stream into a String
        InputStream inputStream = urlConnection.getInputStream();
        StringBuilder builder = new StringBuilder();
        if (inputStream == null) {
            // Nothing to do.
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }

        if (builder.length() == 0) {
            // Stream was empty.  No point in parsing.
            return null;
        }

        String result = builder.toString();

        if (code != 200) {
            throw new HttpClientException(result, code);
        }

        return result;
    }
}