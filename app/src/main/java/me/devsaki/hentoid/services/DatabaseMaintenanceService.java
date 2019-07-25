package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
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
        DatabaseMaintenance.performDatabaseHousekeeping(this);
    }
}