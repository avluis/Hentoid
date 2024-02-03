package me.devsaki.hentoid.util.network

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.net.wifi.WifiManager

object NetworkHelper {

    enum class Connectivity { NO_INTERNET, WIFI, OTHER }
    /**
     * Return the device's current connectivity
     *
     * @param context Context to be used
     * @return Device's current connectivity
     */
    fun getConnectivity(context: Context): Connectivity {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
                ?: return Connectivity.NO_INTERNET
        val activeNetwork = connectivityManager.activeNetwork ?: return Connectivity.NO_INTERNET
        connectivityManager.getNetworkCapabilities(activeNetwork) ?: return Connectivity.NO_INTERNET

        // Below code _does not_ detect wifi properly when there's a VPN on -> using WifiManager instead (!)
        /*
            if (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return Connectivity.WIFI;
            else return Connectivity.OTHER;
             */
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        return if (wifiManager != null && wifiManager.isWifiEnabled && wifiManager.connectionInfo.bssid != null) Connectivity.WIFI else Connectivity.OTHER
    }

    /**
     * Get the number of bytes received by the app through networking since device boot.
     * Counts packets across all network interfaces.
     *
     * @param context Context to be used
     * @return Number of bytes received by the app through networking since device boot.
     */
    fun getIncomingNetworkUsage(context: Context): Long {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            ?: return -1
        var totalReceived: Long = 0
        val runningApps = manager.runningAppProcesses
        if (runningApps != null) for (runningApp in runningApps) {
            val received = TrafficStats.getUidRxBytes(runningApp.uid)
            totalReceived += received
        }
        return totalReceived
    }
}