package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
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

    /**
     * Migrate HentoidDB books to ObjectBoxDB
     */
    private void startMigration() {
        int booksOK = 0;
        int booksKO = 0;
        String log;
        long newKey;

        HentoidDB oldDB = HentoidDB.getInstance(this);
        ObjectBoxDB newDB = ObjectBoxDB.getInstance(this);

        List<Integer> bookIds = oldDB.selectMigrableContentIds();
        List<String> importLog = new ArrayList<>();
        SparseArray<Long> keyMapping = new SparseArray<Long>();

        Timber.i("Import books starting : %s books total", bookIds.size());
        for (int i = 0; i < bookIds.size(); i++) {
            Content content = oldDB.selectContentById(bookIds.get(i));

            if (content != null) {
                newKey = newDB.insertContent(content);
                keyMapping.put(bookIds.get(i), newKey);
                booksOK++;
                log = "Import book OK : " + content.getTitle();
                Timber.d(log);
            } else {
                booksKO++;
                log = "Import book KO : ID" + bookIds.get(i);
                Timber.w(log);
            }
            importLog.add(log);

            eventProgress(content, bookIds.size(), booksOK, booksKO);
        }
        Timber.i("Import books complete : %s OK; %s KO", booksOK, booksKO);

        int queueOK = 0;
        int queueKO = 0;
        SparseIntArray queueIds = oldDB.selectQueueForMigration();
        Timber.i("Import queue starting : %s entries total", queueIds.size());
        for (int i = 0; i < queueIds.size(); i++) {
            Long targetKey = keyMapping.get(queueIds.keyAt(i));

            if (targetKey != null) {
                newDB.insertQueue(targetKey, queueIds.get(queueIds.keyAt(i)));
                queueOK++;
                log = "Import queue OK : " + targetKey;
                Timber.d(log);
            } else {
                queueKO++;
                log = "Import queue KO : ID" + queueIds.keyAt(i);
                Timber.w(log);
            }
            importLog.add(log);
        }
        Timber.i("Import queue complete : %s OK; %s KO", queueOK, queueKO);
        this.getApplicationContext().deleteDatabase(Consts.DATABASE_NAME);

        // Write log in root folder
        File importLogFile = writeImportLog(importLog);

        eventComplete(bookIds.size(), booksOK, booksKO, importLogFile);

        stopForeground(true);
        stopSelf();
    }

    private File writeImportLog(List<String> log) {
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
            File importLogFile = new File(root, "import_log.txt");
            FileHelper.saveBinaryInFile(importLogFile, logStr.toString().getBytes());
            return importLogFile;
        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }
}
