package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ContentV1;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.model.DoujinBuilder;
import me.devsaki.hentoid.model.URLBuilder;
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification;
import me.devsaki.hentoid.notification.import_.ImportStartNotification;
import me.devsaki.hentoid.util.AttributeException;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import timber.log.Timber;

/**
 * Service responsible for importing an existing library.
 *
 * @see UpdateCheckService
 */
public class ImportService extends IntentService {

    private static final int NOTIFICATION_ID = 1;

    private static boolean running;
    private ServiceNotificationManager notificationManager;


    public ImportService() {
        super(ImportService.class.getName());
    }

    public static Intent makeIntent(Context context) {
        return new Intent(context, ImportService.class);
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        running = true;
        notificationManager = new ServiceNotificationManager(this, NOTIFICATION_ID);
        notificationManager.cancel();

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
        // True if the user has asked for a cleanup when calling import from Preferences
        boolean doCleanup = false;

        if (intent != null) {
            doCleanup = intent.getBooleanExtra("cleanup", false);
        }
        startImport(doCleanup);
    }

    private void eventProgress(Content content, int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ImportEvent(ImportEvent.EV_PROGRESS, content, booksOK, booksKO, nbBooks));
    }

    private void eventComplete(int nbBooks, int booksOK, int booksKO, File cleanupLogFile) {
        EventBus.getDefault().postSticky(new ImportEvent(ImportEvent.EV_COMPLETE, booksOK, booksKO, nbBooks, cleanupLogFile));
    }

    private void trace(int priority, List<String> memoryLog, String s, String... t) {
        s = String.format(s, (Object[]) t);
        Timber.log(priority, s);
        if (null != memoryLog) memoryLog.add(s);
    }


    /**
     * Import books from known source folders
     *
     * @param cleanup True if the user has asked for a cleanup when calling import from Preferences
     */
    private void startImport(boolean cleanup) {
        int booksOK = 0;
        int booksKO = 0;
        Content content = null;
        List<String> log = new ArrayList<>();

        notificationManager.startForeground(new ImportStartNotification());

        List<File> files = FileHelper.findFilesRecursively(new File(Preferences.getRootFolderName()), "json");


        trace(Log.DEBUG, log, "Import books starting : %s books total", files.size() + "");
        trace(Log.INFO, log, "Cleanup %s", (cleanup ? "ENABLED" : "DISABLED"));
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);

            try {
                content = importJson(file);
                if (content != null) {
                    if (cleanup) {
                        String canonicalBookDir = FileHelper.formatDirPath(content);

                        String[] currentPathParts = file.getAbsolutePath().split("/");
                        String currentBookDir = "/" + currentPathParts[currentPathParts.length - 2] + "/" + currentPathParts[currentPathParts.length - 1];

                        if (!canonicalBookDir.equals(currentBookDir)) {
                            String settingDir = Preferences.getRootFolderName();
                            if (settingDir.isEmpty()) {
                                settingDir = FileHelper.getDefaultDir(this, canonicalBookDir).getAbsolutePath();
                            }

                            if (FileHelper.renameDirectory(file, new File(settingDir, canonicalBookDir))) {
                                content.setStorageFolder(canonicalBookDir);
                                trace(Log.INFO, log, "[Rename OK] Folder %s renamed to %s", currentBookDir, canonicalBookDir);
                            } else {
                                trace(Log.WARN, log, "[Rename KO] Could not rename file %s to %s", currentBookDir, canonicalBookDir);
                            }
                        }
                    }
                    ObjectBoxDB.getInstance(this).insertContent(content);
                    booksOK++;
                    trace(Log.INFO, log, "Import book OK : %s", file.getAbsolutePath());
                } else {
                    booksKO++;
                    trace(Log.WARN, log, "Import book KO : %s", file.getAbsolutePath());
                    // Deletes the folder if cleanup is active
                    if (cleanup) {
                        boolean success = FileHelper.removeFile(file);
                        trace(Log.INFO, log, "[Remove %s] Folder %s", success ? "OK" : "KO", file.getAbsolutePath());
                    }
                }
            } catch (Exception e) {
                Timber.e(e, "Import book ERROR");
                if (null == content)
                    content = new Content().setTitle("none").setUrl("").setSite(Site.NONE);
                booksKO++;
                trace(Log.ERROR, log, "Import book ERROR : %s %s", e.getMessage(), file.getAbsolutePath());
            }

            eventProgress(content, files.size(), booksOK, booksKO);
        }
        trace(Log.INFO, log, "Import books complete : %s OK; %s KO", booksOK + "", booksKO + "");

        // Write cleanup log in root folder
        File cleanupLogFile = writeLog(log, cleanup);

        eventComplete(files.size(), booksOK, booksKO, cleanupLogFile);
        notificationManager.notify(new ImportCompleteNotification(booksOK, booksKO));

        stopForeground(true);
        stopSelf();
    }

    private File writeLog(List<String> log, boolean isCleanup) {
        // Create the log
        StringBuilder logStr = new StringBuilder();
        logStr.append("Cleanup log : begin").append(System.getProperty("line.separator"));
        if (log.isEmpty())
            logStr.append("No activity to report - All folder names are formatted as expected.");
        else for (String line : log)
            logStr.append(line).append(System.getProperty("line.separator"));
        logStr.append("Cleanup log : end");

        // Save it
        File root;
        try {

            String settingDir = Preferences.getRootFolderName();
            if (settingDir.isEmpty()) {
                root = FileHelper.getDefaultDir(this, "");
            } else {
                root = new File(settingDir);
            }
            File cleanupLogFile = new File(root, isCleanup ? "cleanup_log.txt" : "import_log.txt");
            FileHelper.saveBinaryInFile(cleanupLogFile, logStr.toString().getBytes());
            return cleanupLogFile;
        } catch (Exception e) {
            Timber.e(e);
        }

        return null;
    }

    private static Content importJson(File file) {
        if (file.getName().equals(Consts.JSON_FILE_NAME_V2))
            return importJsonV2(file);  // (v2) JSON file format
        if (file.getName().equals(Consts.JSON_FILE_NAME))
            return importJsonV1(file, new File(file.getParent()));  // (v1) JSON file format
        if (file.getName().equals(Consts.OLD_JSON_FILE_NAME))
            return importJsonLegacy(file, new File(file.getParent()));  // (old) JSON file format (legacy and/or FAKKUDroid App)

        Timber.w("Book folder %s : no JSON file found !", new File(file.getParent()).getAbsolutePath());

        return null;
    }

    @SuppressWarnings("deprecation")
    private static List<Attribute> from(List<URLBuilder> urlBuilders, Site site) {
        List<Attribute> attributes = null;
        if (urlBuilders == null) {
            return null;
        }
        if (urlBuilders.size() > 0) {
            attributes = new ArrayList<>();
            for (URLBuilder urlBuilder : urlBuilders) {
                Attribute attribute = from(urlBuilder, AttributeType.TAG, site);
                if (attribute != null) {
                    attributes.add(attribute);
                }
            }
        }

        return attributes;
    }

    @SuppressWarnings("deprecation")
    private static Attribute from(URLBuilder urlBuilder, AttributeType type, Site site) {
        if (urlBuilder == null) {
            return null;
        }
        try {
            if (urlBuilder.getDescription() == null) {
                throw new AttributeException("Problems loading attribute v2.");
            }

            return new Attribute(type, urlBuilder.getDescription(), urlBuilder.getId(), site);
        } catch (Exception e) {
            Timber.e(e, "Parsing URL to attribute");
            return null;
        }
    }

    @Nullable
    @CheckResult
    @SuppressWarnings("deprecation")
    private static Content importJsonLegacy(File json, File file) {
        try {
            DoujinBuilder doujinBuilder =
                    JsonHelper.jsonToObject(json, DoujinBuilder.class);
            //noinspection deprecation
            ContentV1 content = new ContentV1();
            content.setUrl(doujinBuilder.getId());
            content.setHtmlDescription(doujinBuilder.getDescription());
            content.setTitle(doujinBuilder.getTitle());
            content.setSeries(from(doujinBuilder.getSeries(),
                    AttributeType.SERIE, content.getSite()));
            Attribute artist = from(doujinBuilder.getArtist(),
                    AttributeType.ARTIST, content.getSite());
            List<Attribute> artists = null;
            if (artist != null) {
                artists = new ArrayList<>(1);
                artists.add(artist);
            }

            content.setArtists(artists);
            content.setCoverImageUrl(doujinBuilder.getUrlImageTitle());
            content.setQtyPages(doujinBuilder.getQtyPages());
            Attribute translator = from(doujinBuilder.getTranslator(),
                    AttributeType.TRANSLATOR, content.getSite());
            List<Attribute> translators = null;
            if (translator != null) {
                translators = new ArrayList<>(1);
                translators.add(translator);
            }
            content.setTranslators(translators);
            content.setTags(from(doujinBuilder.getLstTags(), content.getSite()));
            content.setLanguage(from(doujinBuilder.getLanguage(), AttributeType.LANGUAGE, content.getSite()));

            content.setMigratedStatus();
            content.setDownloadDate(new Date().getTime());
            Content contentV2 = content.toV2Content();

            String fileRoot = Preferences.getRootFolderName();
            contentV2.setStorageFolder(json.getAbsoluteFile().getParent().substring(fileRoot.length()));
            try {
                JsonHelper.saveJson(contentV2.preJSONExport(), file);
            } catch (IOException e) {
                Timber.e(e,
                        "Error converting JSON (old) to JSON (v2): %s", content.getTitle());
            }

            return contentV2;
        } catch (Exception e) {
            Timber.e(e, "Error reading JSON (old) file");
        }
        return null;
    }

    @Nullable
    @CheckResult
    private static Content importJsonV1(File json, File file) {
        try {
            //noinspection deprecation
            ContentV1 content = JsonHelper.jsonToObject(json, ContentV1.class);
            if (content.getStatus() != StatusContent.DOWNLOADED
                    && content.getStatus() != StatusContent.ERROR) {
                content.setMigratedStatus();
            }
            Content contentV2 = content.toV2Content();

            String fileRoot = Preferences.getRootFolderName();
            contentV2.setStorageFolder(json.getAbsoluteFile().getParent().substring(fileRoot.length()));
            try {
                JsonHelper.saveJson(contentV2.preJSONExport(), file);
            } catch (IOException e) {
                Timber.e(e, "Error converting JSON (v1) to JSON (v2): %s", content.getTitle());
            }

            return contentV2;
        } catch (Exception e) {
            Timber.e(e, "Error reading JSON (v1) file");
        }
        return null;
    }

    @Nullable
    @CheckResult
    private static Content importJsonV2(File json) {
        try {
            Content content = JsonHelper.jsonToObject(json, Content.class);
            content.postJSONImport();

            String fileRoot = Preferences.getRootFolderName();
            content.setStorageFolder(json.getAbsoluteFile().getParent().substring(fileRoot.length()));

            if (content.getStatus() != StatusContent.DOWNLOADED
                    && content.getStatus() != StatusContent.ERROR) {
                content.setStatus(StatusContent.MIGRATED);
            }

            return content;
        } catch (Exception e) {
            Timber.e(e, "Error reading JSON (v2) file");
        }
        return null;
    }
}
