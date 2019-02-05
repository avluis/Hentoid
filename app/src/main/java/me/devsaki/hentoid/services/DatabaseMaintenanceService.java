package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.util.List;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
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
        performDatabaseHousekeeping();
    }

    /**
     * Clean up database
     * NB : Heavy operations; must be performed in the background to avoid ANR at startup
     */
    private void performDatabaseHousekeeping() {
        HentoidDB db = HentoidDB.getInstance(this);
        // Set items that were being downloaded in previous session as paused
        Timber.i("Updating queue status : start");
        db.updateContentStatus(StatusContent.DOWNLOADING, StatusContent.PAUSED);
        Timber.i("Updating queue status : done");

        // Clear temporary books created from browsing a book page without downloading it
        Timber.i("Clearing temporary books : start");
        List<Content> obsoleteTempContent = db.selectContentByStatus(StatusContent.SAVED);
        Timber.i("Clearing temporary books : %s books detected", obsoleteTempContent.size());
        for (Content c : obsoleteTempContent) db.deleteContent(c);
        Timber.i("Clearing temporary books : done");
    }
}