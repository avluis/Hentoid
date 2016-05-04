package me.devsaki.hentoid.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by DevSaki on 14/05/2015.
 * Http related utility class
 */
public class HttpClientHelper {
    private static final String TAG = LogHelper.makeLogTag(HttpClientHelper.class);

    public static String call(String urlString) throws Exception {
        String sessionCookie = AndroidHelper.getSessionCookie();
        HttpURLConnection urlConnection = null;
        InputStream is = null;

        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.setConnectTimeout(10000);
            urlConnection.setRequestMethod("GET");
            urlConnection.setRequestProperty("User-Agent", Constants.USER_AGENT);

            if (!sessionCookie.isEmpty()) {
                urlConnection.setRequestProperty("Cookie", sessionCookie);
            }

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
                    LogHelper.e(TAG, "InputStream Error: ", e);
                }
            }
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}