package me.devsaki.hentoid.util;

import android.content.pm.PackageManager;
import android.webkit.CookieManager;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import me.devsaki.hentoid.HentoidApp;
import timber.log.Timber;

/**
 * Created by DevSaki on 14/05/2015.
 * Http related utility class
 */
public class HttpClientHelper {

    private static final CookieManager cookieManager = CookieManager.getInstance();

    public static String call(String urlString) throws Exception {
        String cookie = cookieManager.getCookie(urlString);
        if (cookie == null || cookie.isEmpty()) {
            cookie = Preferences.getSessionCookie();
        }

        HttpURLConnection urlConnection = null;
        InputStream is = null;

        String userAgent;
        try {
            userAgent = Helper.getAppUserAgent(HentoidApp.getAppContext());
        } catch (PackageManager.NameNotFoundException e) {
            userAgent = Consts.USER_AGENT;
        }

        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.setConnectTimeout(10000);
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("User-Agent", userAgent);
            urlConnection.setRequestProperty("Cookie", cookie);

            is = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            if (sb.length() == 0) {
                // Stream was empty.  No point in parsing.
                return null;
            }

            String result = sb.toString();
            int code = urlConnection.getResponseCode();
            if (code != 200) {
                throw new HttpClientException(result, code);
            }

            return result;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Timber.e(e, "InputStream Error");
                }
            }
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
