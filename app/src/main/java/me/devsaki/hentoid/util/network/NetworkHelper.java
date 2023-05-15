package me.devsaki.hentoid.util.network;

import static android.content.Context.ACTIVITY_SERVICE;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Helper for general newtworking
 */
public class NetworkHelper {

    private NetworkHelper() {
        throw new IllegalStateException("Utility class");
    }

    @IntDef({Connectivity.NO_INTERNET, Connectivity.WIFI, Connectivity.OTHER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Connectivity {
        int NO_INTERNET = -1;
        int WIFI = 0;
        int OTHER = 1;
    }


    /**
     * Return the device's current connectivity
     *
     * @param context Context to be used
     * @return Device's current connectivity
     */
    @SuppressWarnings({"squid:CallToDeprecatedMethod"})
    public static @Connectivity
    int getConnectivity(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (null == connectivityManager) return Connectivity.NO_INTERNET;

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (null == activeNetwork) return Connectivity.NO_INTERNET;
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (null == actNw) return Connectivity.NO_INTERNET;

        // Below code _does not_ detect wifi properly when there's a VPN on -> using WifiManager instead (!)
            /*
            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return Connectivity.WIFI;
            else return Connectivity.OTHER;
             */
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled() && wifiManager.getConnectionInfo().getBSSID() != null)
            return Connectivity.WIFI;
        else return Connectivity.OTHER;
    }

    /**
     * Get the number of bytes received by the app through networking since device boot.
     * Counts packets across all network interfaces.
     *
     * @param context Context to be used
     * @return Number of bytes received by the app through networking since device boot.
     */
    public static long getIncomingNetworkUsage(@NonNull final Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        if (null == manager) return -1;

        long totalReceived = 0;

        List<ActivityManager.RunningAppProcessInfo> runningApps = manager.getRunningAppProcesses();
        if (runningApps != null)
            for (ActivityManager.RunningAppProcessInfo runningApp : runningApps) {
                long received = TrafficStats.getUidRxBytes(runningApp.uid);
                totalReceived += received;
            }
        return totalReceived;
    }
}
