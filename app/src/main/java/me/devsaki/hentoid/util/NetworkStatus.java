package me.devsaki.hentoid.util;

import android.content.Context;
import android.net.ConnectivityManager;

import com.annimon.stream.Optional;

import me.devsaki.hentoid.HentoidApp;

/**
 * General wrapper for network status query.
 */
public final class NetworkStatus {

    public static boolean isOnline() {
        return Optional.of(HentoidApp.getAppContext())
                .map(context -> (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE))
                .map(ConnectivityManager::getActiveNetworkInfo)
                .mapToBoolean(netInfo -> netInfo.isAvailable() && netInfo.isConnected())
                .orElse(false);
    }
}
