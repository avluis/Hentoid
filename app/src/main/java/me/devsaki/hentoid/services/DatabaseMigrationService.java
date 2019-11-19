package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.notification.maintenance.MaintenanceNotification;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.LogUtil;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import timber.log.Timber;

/**
 * Service responsible for migrating the old HentoidDB to ObjectBoxDB
 *
 * @see UpdateCheckService
 */
public class DatabaseMigrationService extends IntentService {

    private ServiceNotificationManager notificationManager;


    public DatabaseMigrationService() {
        super(DatabaseMigrationService.class.getName());
    }

    public static Intent makeIntent(Context context) {
        return new Intent(context, DatabaseMigrationService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = new ServiceNotificationManager(this, 1);
        notificationManager.startForeground(new MaintenanceNotification("Performing database migration"));

        Timber.w("Service created");
    }

    @Override
    public void onDestroy() {
        notificationManager.cancel();
        Timber.w("Service destroyed");

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        cleanUpDB();
        migrate();
    }

    private void eventProgress(Content content, int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ImportEvent(ImportEvent.EV_PROGRESS, content, booksOK, booksKO, nbBooks));
    }

    private void eventComplete(int nbBooks, int booksOK, int booksKO, File importLogFile) {
        EventBus.getDefault().post(new ImportEvent(ImportEvent.EV_COMPLETE, booksOK, booksKO, nbBooks, importLogFile));
    }

    private void trace(int priority, List<String> memoryLog, String s, String... t) {
        s = String.format(s, (Object[]) t);
        Timber.log(priority, s);
        if (null != memoryLog) memoryLog.add(s);
    }

    private void cleanUpDB() {
        Timber.d("Cleaning up DB.");
        Context context = HentoidApp.getAppContext();
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        db.deleteAllBooks();
        db.deleteAllQueue();
    }

    /**
     * Migrate HentoidDB books to ObjectBoxDB
     */
    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    private void migrate() {
        int booksOK = 0;
        int booksKO = 0;
        long newKey;
        Content content;

        HentoidDB oldDB = HentoidDB.getInstance(this);
        ObjectBoxDB newDB = ObjectBoxDB.getInstance(this);

        List<Integer> bookIds = oldDB.selectMigrableContentIds();
        List<String> log = new ArrayList<>();
        SparseArray<Long> keyMapping = new SparseArray<>();

        trace(Log.INFO, log, "Books migration starting : %s books total", bookIds.size() + "");
        for (int i = 0; i < bookIds.size(); i++) {
            content = oldDB.selectContentById(bookIds.get(i));

            try {
                if (content != null) {
                    newKey = newDB.insertContent(content);
                    keyMapping.put(bookIds.get(i), newKey);
                    booksOK++;
                    trace(Log.DEBUG, log, "Migrate book OK : %s", content.getTitle());
                } else {
                    booksKO++;
                    trace(Log.WARN, log, "Migrate book KO : ID %s", bookIds.get(i) + "");
                }
            } catch (Exception e) {
                Timber.e(e, "Migrate book ERROR");
                booksKO++;
                if (null == content)
                    content = new Content().setTitle("none").setUrl("").setSite(Site.NONE);
                trace(Log.ERROR, log, "Migrate book ERROR : %s %s %s", e.getMessage(), bookIds.get(i) + "", content.getTitle());
            }

            eventProgress(content, bookIds.size(), booksOK, booksKO);
        }
        trace(Log.INFO, log, "Books migration complete : %s OK; %s KO", booksOK + "", booksKO + "");

        int queueOK = 0;
        int queueKO = 0;
        SparseIntArray queueIds = oldDB.selectQueueForMigration();
        trace(Log.INFO, log, "Queue migration starting : %s entries total", queueIds.size() + "");
        for (int i = 0; i < queueIds.size(); i++) {
            Long targetKey = keyMapping.get(queueIds.keyAt(i));

            if (targetKey != null) {
                newDB.insertQueue(targetKey, queueIds.get(queueIds.keyAt(i)));
                queueOK++;
                trace(Log.INFO, log, "Migrate queue OK : target ID %s", targetKey + "");
            } else {
                queueKO++;
                trace(Log.WARN, log, "Migrate queue KO : source ID %s", queueIds.keyAt(i) + "");
            }
        }
        trace(Log.INFO, log, "Queue migration complete : %s OK; %s KO", queueOK + "", queueKO + "");
        this.getApplicationContext().deleteDatabase(Consts.DATABASE_NAME);

        // Write log in root folder
        File importLogFile = LogUtil.writeLog(this, log, buildLogInfo());

        eventComplete(bookIds.size(), booksOK, booksKO, importLogFile);

        stopForeground(true);
        stopSelf();
    }

    private LogUtil.LogInfo buildLogInfo() {
        LogUtil.LogInfo logInfo = new LogUtil.LogInfo();
        logInfo.logName = "Migration";
        logInfo.fileName = "migration_log";
        logInfo.noDataMessage = "No migrable content detected on existing database.";
        return logInfo;
    }
}
