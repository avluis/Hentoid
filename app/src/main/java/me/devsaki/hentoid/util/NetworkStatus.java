package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Created by avluis on 7/6/15.
 */
public class NetworkStatus {

    private static final NetworkStatus instance = new NetworkStatus();
    static Context context;
    ConnectivityManager connMgr;
    NetworkInfo wifiInfo, mobileInfo;
    boolean connected = false;
    boolean wifi = false;
    boolean mobile = false;

    public static NetworkStatus getInstance(Context ctx) {
        context = ctx.getApplicationContext();
        return instance;
    }

    public boolean isOnline() {
        try {
            connMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo netInfo = connMgr.getActiveNetworkInfo();
            connected = netInfo != null && netInfo.isAvailable() &&
                    netInfo.isConnected();
            return connected;
        } catch (Exception e) {
            System.out.println("CheckConnectivity Exception: " + e.getMessage());
            Log.v("connectivity", e.toString());
        }
        return connected;
    }

    public boolean isWifi() {
        try {
            connMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            wifiInfo = connMgr.getActiveNetworkInfo();
            wifi = wifiInfo != null && wifiInfo.isConnected() && wifiInfo.getType() ==
                    ConnectivityManager.TYPE_WIFI;
            return wifi;
        } catch (Exception e) {
            System.out.println("CheckConnectivity Exception: " + e.getMessage());
            Log.v("connectivity", e.toString());
        }
        return wifi;
    }

    public boolean isMobile() {
        try {
            connMgr = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            mobileInfo = connMgr.getActiveNetworkInfo();
            mobile = mobileInfo != null && mobileInfo.isConnected() && mobileInfo.getType() ==
                    ConnectivityManager.TYPE_MOBILE;
            return mobile;
        } catch (Exception e) {
            System.out.println("CheckConnectivity Exception: " + e.getMessage());
            Log.v("connectivity", e.toString());
        }
        return mobile;
    }
}