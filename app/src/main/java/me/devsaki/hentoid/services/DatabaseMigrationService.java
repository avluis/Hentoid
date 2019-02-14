package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.HentoidDB;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Service responsible for migrating the oldHentoidDB to the ObjectBoxDB
 *
 * @see UpdateCheckService
 */
public class DatabaseMigrationService extends IntentService {

    private static boolean running;

    public DatabaseMigrationService() {
        super(DatabaseMigrationService.class.getName());
    }

    public static Intent makeIntent(Context context) {
        return new Intent(context, DatabaseMigrationService.class);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        running = true;

        Timber.w("Service created");
    }

    @Override
    public void onDestroy() {
        running = false;
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
        startMigration();
    }

    private void eventProgress(Content content, int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ImportEvent(ImportEvent.EV_PROGRESS, content, booksOK, booksKO, nbBooks));
    }

    private void eventComplete(int nbBooks, int booksOK, int booksKO, File importLogFile) {
        EventBus.getDefault().postSticky(new ImportEvent(ImportEvent.EV_COMPLETE, booksOK, booksKO, nbBooks, importLogFile));
    }

    private void trace(int priority, List<String> memoryLog, String s, String... t) {
        s = String.format(s, (Object[]) t);
        Timber.log(priority, s);
        if (null != memoryLog) memoryLog.add(s);
    }

    /**
     * Migrate HentoidDB books to ObjectBoxDB
     */
    private void startMigration() {
        int booksOK = 0;
        int booksKO = 0;
        long newKey;

        HentoidDB oldDB = HentoidDB.getInstance(this);
        ObjectBoxDB newDB = ObjectBoxDB.getInstance(this);

        List<Integer> bookIds = oldDB.selectMigrableContentIds();
        List<String> importLog = new ArrayList<>();
        SparseArray<Long> keyMapping = new SparseArray<>();

        trace(Log.INFO, importLog, "Import books starting : %s books total", bookIds.size() + "");
        for (int i = 0; i < bookIds.size(); i++) {
            Content content = oldDB.selectContentById(bookIds.get(i));

            if (content != null) {
                newKey = newDB.insertContent(content);
                keyMapping.put(bookIds.get(i), newKey);
                booksOK++;
                trace(Log.DEBUG, importLog, "Import book OK : " + content.getTitle());
            } else {
                booksKO++;
                trace(Log.WARN, importLog, "Import book KO : ID" + bookIds.get(i));
            }

            eventProgress(content, bookIds.size(), booksOK, booksKO);
        }
        trace(Log.INFO, importLog, "Import books complete : %s OK; %s KO", booksOK + "", booksKO + "");

        int queueOK = 0;
        int queueKO = 0;
        SparseIntArray queueIds = oldDB.selectQueueForMigration();
        trace(Log.INFO, importLog, "Import queue starting : %s entries total", queueIds.size() + "");
        for (int i = 0; i < queueIds.size(); i++) {
            Long targetKey = keyMapping.get(queueIds.keyAt(i));

            if (targetKey != null) {
                newDB.insertQueue(targetKey, queueIds.get(queueIds.keyAt(i)));
                queueOK++;
                trace(Log.INFO, importLog, "Import queue OK : target ID" + targetKey);
            } else {
                queueKO++;
                trace(Log.WARN, importLog, "Import queue KO : source ID" + queueIds.keyAt(i));
            }
        }
        trace(Log.INFO, importLog, "Import queue complete : %s OK; %s KO", queueOK + "", queueKO + "");
        this.getApplicationContext().deleteDatabase(Consts.DATABASE_NAME);

        // Write log in root folder
        File importLogFile = writeMigrationLog(importLog);

        eventComplete(bookIds.size(), booksOK, booksKO, importLogFile);

        stopForeground(true);
        stopSelf();
    }

    private File writeMigrationLog(List<String> log) {
        // Create the log
        StringBuilder logStr = new StringBuilder();
        logStr.append("Import log : begin").append(System.getProperty("line.separator"));
        if (log.isEmpty())
            logStr.append("No activity to report - No migrable content detected on existing database");
        else for (String line : log)
            logStr.append(line).append(System.getProperty("line.separator"));
        logStr.append("Import log : end");

        // Save it
        File root;
        try {
            String settingDir = Preferences.getRootFolderName();
            if (settingDir.isEmpty()) {
                root = FileHelper.getDefaultDir(this, "");
            } else {
                root = new File(settingDir);
            }
            File importLogFile = new File(root, "migration_log.txt");
            FileHelper.saveBinaryInFile(importLogFile, logStr.toString().getBytes());
            return importLogFile;
        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }
}
