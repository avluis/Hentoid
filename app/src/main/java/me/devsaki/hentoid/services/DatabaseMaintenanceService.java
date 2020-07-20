package me.devsaki.hentoid.services;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
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
        if (checkNetwork()) {
            DatabaseMaintenance.performDatabaseHousekeeping(this);
        }
        else {
            NetworkStateReceiver.enable(getApplicationContext());
        }
    }

    boolean checkNetwork() {
        final ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = connManager.getActiveNetwork();
        if (activeNetwork != null) {
            return true;
        }
        return false;
    }

    public static class NetworkStateReceiver extends BroadcastReceiver {
        private static final String TAG = NetworkStateReceiver.class.getName();

        private static DatabaseMaintenanceService service;

        public static void setService(DatabaseMaintenanceService newService) {
            service = newService;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (service.checkNetwork()) {
                NetworkStateReceiver.disable(context);

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

        public static void enable(Context context) {
            final PackageManager packageManager = context.getPackageManager();
            final ComponentName receiver = new ComponentName(context, NetworkStateReceiver.class);
            packageManager.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }

        public static void disable(Context context) {
            final PackageManager packageManager = context.getPackageManager();
            final ComponentName receiver = new ComponentName(context, NetworkStateReceiver.class);
            packageManager.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }
}