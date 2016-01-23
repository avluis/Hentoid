package me.devsaki.hentoid.util;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import me.devsaki.hentoid.exceptions.HttpClientException;

/**
 * Created by DevSaki on 14/05/2015.
 */
public class HttpClientHelper {

    private String sessionCookies;

    public HttpClientHelper(String address) throws URISyntaxException {
        sessionCookies = TextUtils.join(
                "; ",
                Helper.getCookieManager()
                        .getCookieStore()
                        .get(new URI(address))
        );
    }

    public String callSession(String address) throws Exception {
        URL url = new URL(address);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setConnectTimeout(10000);
        urlConnection.setRequestProperty("User-Agent", Constants.USER_AGENT);
        urlConnection.setRequestProperty("Cookie", sessionCookies);

        return go(urlConnection);
    }

    public static String call(String address) throws Exception {
        String sessionCookies = TextUtils.join(
                "; ",
                Helper.getCookieManager()
                        .getCookieStore()
                        .get(new URI(address))
        );

        URL url = new URL(address);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.setConnectTimeout(10000);
        urlConnection.setRequestProperty("User-Agent", Constants.USER_AGENT);
        if(!sessionCookies.isEmpty()) {
            urlConnection.setRequestProperty("Cookie", sessionCookies);
        }

        return go(urlConnection);
    }

    private static String go(HttpURLConnection urlConnection) throws Exception {
        urlConnection.connect();

        int code = urlConnection.getResponseCode();

        // Read the input stream into a String
        InputStream inputStream = urlConnection.getInputStream();
        if (inputStream == null) {
            // Nothing to do.
            return null;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder builder = new StringBuilder();

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