package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by avluis on 7/6/15.
 * General wrapper for network status query.
 */
public final class NetworkStatus {
    private static final String TAG = LogHelper.makeLogTag(NetworkStatus.class);

    private static NetworkInfo init(Context cxt) {
        Context context = cxt.getApplicationContext();
        ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        return connMgr.getActiveNetworkInfo();
    }

    public static boolean isOnline(Context cxt) {
        boolean connected;
        try {
            NetworkInfo netInfo = init(cxt);
            connected = netInfo != null && netInfo.isAvailable() &&
                    netInfo.isConnected();

            return connected;
        } catch (Exception e) {
            LogHelper.v(TAG, "Connectivity: ", e);
        }

        return false;
    }

    public static boolean isWifi(Context cxt) {
        boolean wifi;
        try {
            NetworkInfo netInfo = init(cxt);
            wifi = netInfo != null && netInfo.isConnected() && netInfo.getType() ==
                    ConnectivityManager.TYPE_WIFI;

            return wifi;
        } catch (Exception e) {
            LogHelper.v(TAG, "Connectivity: ", e);
        }

        return false;
    }

    public static boolean isMobile(Context cxt) {
        boolean mobile;
        try {
            NetworkInfo netInfo = init(cxt);
            mobile = netInfo != null && netInfo.isConnected() && netInfo.getType() ==
                    ConnectivityManager.TYPE_MOBILE;

            return mobile;
        } catch (Exception e) {
            LogHelper.v(TAG, "Connectivity: ", e);
        }

        return false;
    }

    // Must be run on a background thread!!
    public static boolean hasInternetAccess(Context cxt) {
        if (isOnline(cxt)) {
            try {
                HttpURLConnection url = (HttpURLConnection)
                        (new URL("http://clients3.google.com/generate_204").openConnection());
                url.setRequestProperty("User-Agent", "Android");
                url.setRequestProperty("Connection", "close");
                url.setConnectTimeout(100);
                url.setReadTimeout(100);
                url.connect();

                return (url.getResponseCode() == 204 && url.getContentLength() == 0);
            } catch (IOException e) {
                LogHelper.e(TAG, "Error checking internet connection: ", e);
            }
        } else {
            LogHelper.d(TAG, "No network available!");
        }

        return false;
    }
}
