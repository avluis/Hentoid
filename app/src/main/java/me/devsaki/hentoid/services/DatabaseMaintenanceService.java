package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import me.devsaki.hentoid.database.DatabaseMaintenance;
import timber.log.Timber;

/**
 * Service responsible for performing housekeeping tasks on the database
 */
public class DatabaseMaintenanceService extends IntentService {

    private static boolean running;

    public DatabaseMaintenanceService() {
        super(DatabaseMaintenanceService.class.getName());
    }

    public static Intent makeIntent(Context context) {
        return new Intent(context, DatabaseMaintenanceService.class);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        Timber.i("Service created");
    }

    @Override
    public void onDestroy() {
        running = false;
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