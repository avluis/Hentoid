package me.devsaki.hentoid.services;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.IBinder;

import androidx.annotation.Nullable;

import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.notification.maintenance.MaintenanceNotification;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import timber.log.Timber;

/**
 * Service responsible for performing housekeeping tasks on the database
 */
public class DatabaseMaintenanceService extends IntentService {

    private ServiceNotificationManager notificationManager;


    public DatabaseMaintenanceService() {
        super(DatabaseMaintenanceService.class.getName());
    }

    public static Intent makeIntent(Context context) {
        return new Intent(context, DatabaseMaintenanceService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = new ServiceNotificationManager(this, 1);
        notificationManager.startForeground(new MaintenanceNotification("Performing maintenance"));

        Timber.i("Service created");
    }

    @Override
    public void onDestroy() {
        notificationManager.cancel();
        Timber.i("Service destroyed");

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (hasActiveNetwork()) {
            DatabaseMaintenance.performDatabaseHousekeeping(this);
        }
        else {
            NetworkStateReceiver networkStateReceiver = new NetworkStateReceiver();
            ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            networkStateReceiver.enable(getApplicationContext());
            networkStateReceiver.setService(this);
            connectivityManager.registerDefaultNetworkCallback(networkStateReceiver);
        }
    }

    //The method hasActiveNetwork() checks whether the network connection is active
    protected boolean hasActiveNetwork() {
        final ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = connManager.getActiveNetwork();
        return (activeNetwork != null);
    }

    public class NetworkStateReceiver extends ConnectivityManager.NetworkCallback {
        private DatabaseMaintenanceService service;

        public void setService(DatabaseMaintenanceService newService) {
            service = newService;
        }

        @Override
        public void onAvailable(Network network) {

            // If there is an active network connection, this method will "turn off" this class and arrange to process the request
            if (service.hasActiveNetwork()) {
                Context context = getApplicationContext();
                disable(context);
                final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                final Intent innerIntent = new Intent(context, DatabaseMaintenanceService.class);
                final PendingIntent pendingIntent = PendingIntent.getService(context, 0, innerIntent, 0);

                SharedPreferences preferences = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
                preferences.edit();
                boolean autoRefreshEnabled = preferences.getBoolean("pref_auto_refresh_enabled", false);

                final String hours = preferences.getString("pref_auto_refresh_enabled", "0");
                long hoursLong = Long.parseLong(hours) * 60 * 60 * 1000;

                if (autoRefreshEnabled && hoursLong != 0) {
                    final long alarmTime = preferences.getLong("last_auto_refresh_time", 0) + hoursLong;
                    alarmManager.set(AlarmManager.RTC, alarmTime, pendingIntent);
                } else {
                    alarmManager.cancel(pendingIntent);
                }
            }
        }

        // Method to  "turn on" this class
        public void enable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.registerDefaultNetworkCallback(this);
        }

        // Method to  "turn off" this class
        public void disable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(this);
        }

    }
}