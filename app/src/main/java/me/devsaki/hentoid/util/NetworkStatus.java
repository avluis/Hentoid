package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import timber.log.Timber;

/**
 * Created by avluis on 7/6/15.
 * General wrapper for network status query.
 */
public final class NetworkStatus {

    private static NetworkInfo init(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connMgr.getActiveNetworkInfo();
    }

    public static boolean isOnline(Context context) {
        boolean connected;
        try {
            NetworkInfo netInfo = init(context);
            connected = netInfo != null && netInfo.isAvailable() &&
                    netInfo.isConnected();

            return connected;
        } catch (Exception e) {
            Timber.e(e);
        }

        return false;
    }

    public static boolean isWifi(Context context) {
        boolean wifi;
        try {
            NetworkInfo netInfo = init(context);
            wifi = netInfo != null && netInfo.isConnected() && netInfo.getType() ==
                    ConnectivityManager.TYPE_WIFI;

            return wifi;
        } catch (Exception e) {
            Timber.e(e);
        }

        return false;
    }

    // Must be run on a background thread!!
    public static boolean hasInternetAccess(Context context) {
        if (isOnline(context)) {
            try {
                HttpURLConnection url = (HttpURLConnection)
                        (new URL("http://clients3.google.com/generate_204").openConnection());
                url.setRequestProperty("User-Agent", "Android");
                url.setRequestProperty("Connection", "close");
                url.setConnectTimeout(1500);
                url.setReadTimeout(1500);
                url.connect();

                return (url.getResponseCode() == 204 && url.getContentLength() == 0);
            } catch (IOException e) {
                Timber.e(e, "Error while checking for internet connection");
            }
        } else {
            Timber.d("No network available!");
        }

        return false;
    }
}
