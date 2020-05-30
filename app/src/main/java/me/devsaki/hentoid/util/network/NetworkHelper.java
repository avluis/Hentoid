package me.devsaki.hentoid.util.network;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Build;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import static android.content.Context.ACTIVITY_SERVICE;

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


    @SuppressWarnings({"squid:CallToDeprecatedMethod"})
    public static @Connectivity
    int getConnectivity(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (null == connectivityManager) return Connectivity.NO_INTERNET;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network networkCapabilities = connectivityManager.getActiveNetwork();
            if (null == networkCapabilities) return Connectivity.NO_INTERNET;
            NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(networkCapabilities);
            if (null == actNw) return Connectivity.NO_INTERNET;

            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return Connectivity.WIFI;
            else if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                return Connectivity.OTHER;
            else if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                return Connectivity.OTHER;
            else return Connectivity.NO_INTERNET; // Bluetooth, LoWPAN
        } else {
            NetworkInfo info = connectivityManager.getActiveNetworkInfo();
            if (null == info) return Connectivity.NO_INTERNET;

            switch (info.getType()) {
                case (ConnectivityManager.TYPE_WIFI):
                    return Connectivity.WIFI;
                case (ConnectivityManager.TYPE_MOBILE):
                case (ConnectivityManager.TYPE_ETHERNET):
                    return Connectivity.OTHER;
                default:
                    return Connectivity.NO_INTERNET; // Others (e.g. TYPE_MOBILE_MMS)
            }
        }
    }

    public static long getIncomingNetworkUsage(@NonNull final Context context) {
        // Get running processes
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
