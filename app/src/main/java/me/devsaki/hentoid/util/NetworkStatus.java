package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

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
}
