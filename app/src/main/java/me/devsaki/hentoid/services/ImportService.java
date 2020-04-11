package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import org.greenrobot.eventbus.EventBus;
import org.threeten.bp.Instant;

import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImportActivityBundle;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.json.ContentV1;
import me.devsaki.hentoid.json.DoujinBuilder;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.json.URLBuilder;
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification;
import me.devsaki.hentoid.notification.import_.ImportStartNotification;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LogUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.exception.ParseException;
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

    public static Intent makeIntent(@NonNull Context context) {
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
        notificationManager.startForeground(new ImportStartNotification());

        Timber.w("Service created");
    }

    @Override
    public void onDestroy() {
        running = false;
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
        // True if the user has asked for a cleanup when calling import from Preferences
        boolean doRename = false;
        boolean doCleanAbsent = false;
        boolean doCleanNoImages = false;
        boolean doCleanUnreadable = false;

        if (intent != null && intent.getExtras() != null) {
            ImportActivityBundle.Parser parser = new ImportActivityBundle.Parser(intent.getExtras());
            doRename = parser.getRefreshRename();
            doCleanAbsent = parser.getRefreshCleanAbsent();
            doCleanNoImages = parser.getRefreshCleanNoImages();
            doCleanUnreadable = parser.getRefreshCleanUnreadable();
        }
        startImport(doRename, doCleanAbsent, doCleanNoImages, doCleanUnreadable);
    }

    private void eventProgress(int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ImportEvent(ImportEvent.EV_PROGRESS, booksOK, booksKO, nbBooks));
    }

    private void eventComplete(int nbBooks, int booksOK, int booksKO, DocumentFile cleanupLogFile) {
        EventBus.getDefault().postSticky(new ImportEvent(ImportEvent.EV_COMPLETE, booksOK, booksKO, nbBooks, cleanupLogFile));
    }

    private void trace(int priority, List<LogUtil.LogEntry> memoryLog, String s, String... t) {
        s = String.format(s, (Object[]) t);
        Timber.log(priority, s);
        if (null != memoryLog) memoryLog.add(new LogUtil.LogEntry(s));
    }


    /**
     * Import books from known source folders
     *
     * @param rename              True if the user has asked for a folder renaming when calling import from Preferences
     * @param cleanNoJSON         True if the user has asked for a cleanup of folders with no JSONs when calling import from Preferences
     * @param cleanNoImages       True if the user has asked for a cleanup of folders with no images when calling import from Preferences
     * @param cleanUnreadableJSON True if the user has asked for a cleanup of folders with unreadable JSONs when calling import from Preferences
     */
    private void startImport(boolean rename, boolean cleanNoJSON, boolean cleanNoImages, boolean cleanUnreadableJSON) {
        int booksOK = 0;                        // Number of books imported
        int booksKO = 0;                        // Number of folders found with no valid book inside
        int nbFolders = 0;                      // Number of folders found with no content but subfolders
        Content content = null;
        List<LogUtil.LogEntry> log = new ArrayList<>();

        DocumentFile rootFolder = DocumentFile.fromTreeUri(this, Uri.parse(Preferences.getStorageUri()));
        if (null == rootFolder || !rootFolder.exists()) {
            Timber.e("rootFolder is not defined (%s)", Preferences.getStorageUri());
            return;
        }

        // 1st pass : count subfolders of every site folder
        List<DocumentFile> files = new ArrayList<>();
        List<DocumentFile> siteFolders = FileHelper.listFolders(this, rootFolder);
        for (DocumentFile f : siteFolders)
            files.addAll(FileHelper.listFolders(this, f));

        // 2nd pass : scan every folder for a JSON file or subdirectories
        String enabled = getApplication().getResources().getString(R.string.enabled);
        String disabled = getApplication().getResources().getString(R.string.disabled);
        trace(Log.DEBUG, log, "Import books starting - initial detected count : %s", files.size() + "");
        trace(Log.INFO, log, "Rename folders %s", (rename ? enabled : disabled));
        trace(Log.INFO, log, "Remove folders with no JSONs %s", (cleanNoJSON ? enabled : disabled));
        trace(Log.INFO, log, "Remove folders with no images %s", (cleanNoImages ? enabled : disabled));
        trace(Log.INFO, log, "Remove folders with unreadable JSONs %s", (cleanUnreadableJSON ? enabled : disabled));
        for (int i = 0; i < files.size(); i++) {
            DocumentFile folder = files.get(i);
            List<DocumentFile> imageFiles = null;

            // Detect the presence of images if the corresponding cleanup option has been enabled
            if (cleanNoImages) {
                imageFiles = FileHelper.listFiles(
                        folder,
                        file -> (file.isDirectory() || Helper.isImageExtensionSupported(FileHelper.getExtension(file.getName())))
                );

                if (imageFiles.isEmpty()) { // No images nor subfolders
                    booksKO++;
                    boolean success = folder.delete();
                    trace(Log.INFO, log, "[Remove no image %s] Folder %s", success ? "OK" : "KO", folder.getUri().toString());
                    continue;
                }
            }

            // Detect JSON and try to parse it
            try {
                content = importJson(this, folder);
                if (content != null) {

                    if (StatusContent.UNHANDLED_ERROR == content.getCover().getStatus()) // No cover
                    {
                        // Get the saved cover file
                        if (null == imageFiles) imageFiles = FileHelper.listFiles(
                                folder,
                                file -> (file.getName() != null && file.getName().startsWith("thumb"))
                        );

                        // Add it to the book's images
                        if (!imageFiles.isEmpty()) {
                            ImageFile cover = new ImageFile(0, content.getCoverImageUrl(), StatusContent.DOWNLOADED, content.getQtyPages());
                            cover.setFileUri(imageFiles.get(0).getUri().toString());
                            cover.setIsCover(true);
                            if (content.getImageFiles() != null) content.getImageFiles().add(cover);
                        }
                    }

                    if (rename) {
                        String canonicalBookDir = ContentHelper.formatBookFolderName(content);

                        List<String> currentPathParts = folder.getUri().getPathSegments();
                        String currentBookDir = currentPathParts.get(currentPathParts.size() - 1); // TODO check if that one's the folder name and not the file name

                        if (!canonicalBookDir.equalsIgnoreCase(currentBookDir)) {

                            //if (FileHelper.renameDirectory(folder, new File(settingDir, canonicalBookDir))) {
                            if (folder.renameTo(canonicalBookDir)) {
                                content.setStorageUri(canonicalBookDir);
                                trace(Log.INFO, log, "[Rename OK] Folder %s renamed to %s", currentBookDir, canonicalBookDir);
                            } else {
                                trace(Log.WARN, log, "[Rename KO] Could not rename file %s to %s", currentBookDir, canonicalBookDir);
                            }
                        }
                    }
                    // TODO : Populate images when data is loaded from old JSONs (DoujinBuilder object)
                    ObjectBoxDB.getInstance(this).insertContent(content);
                    trace(Log.INFO, log, "Import book OK : %s", folder.getUri().toString());
                } else { // JSON not found
                    List<DocumentFile> subdirs = FileHelper.listFolders(this, folder);
                    if (!subdirs.isEmpty()) // Folder doesn't contain books but contains subdirectories
                    {
                        files.addAll(subdirs);
                        trace(Log.INFO, log, "Subfolders found in : %s", folder.getUri().toString());
                        nbFolders++;
                        continue;
                    } else { // No JSON nor any subdirectory
                        trace(Log.WARN, log, "Import book KO! (no JSON found) : %s", folder.getUri().toString());
                        // Deletes the folder if cleanup is active
                        if (cleanNoJSON) {
                            boolean success = folder.delete();
                            trace(Log.INFO, log, "[Remove no JSON %s] Folder %s", success ? "OK" : "KO", folder.getUri().toString());
                        }
                    }
                }

                if (null == content) booksKO++;
                else booksOK++;
            } catch (ParseException jse) {
                if (null == content)
                    content = new Content().setTitle("none").setSite(Site.NONE).setUrl("");
                booksKO++;
                trace(Log.ERROR, log, "Import book ERROR : %s for Folder %s", jse.getMessage(), folder.getUri().toString());
                if (cleanUnreadableJSON) {
                    boolean success = folder.delete();
                    trace(Log.INFO, log, "[Remove unreadable JSON %s] Folder %s", success ? "OK" : "KO", folder.getUri().toString());
                }
            } catch (Exception e) {
                if (null == content)
                    content = new Content().setTitle("none").setSite(Site.NONE).setUrl("");
                booksKO++;
                trace(Log.ERROR, log, "Import book ERROR : %s for Folder %s", e.getMessage(), folder.getUri().toString());
            }

            eventProgress(files.size() - nbFolders, booksOK, booksKO);
        }
        trace(Log.INFO, log, "Import books complete - %s OK; %s KO; %s final count", booksOK + "", booksKO + "", files.size() - nbFolders + "");

        // Write cleanup log in root folder
        DocumentFile cleanupLogFile = LogUtil.writeLog(this, buildLogInfo(rename || cleanNoJSON || cleanNoImages || cleanUnreadableJSON, log));

        eventComplete(files.size(), booksOK, booksKO, cleanupLogFile);
        notificationManager.notify(new ImportCompleteNotification(booksOK, booksKO));

        stopForeground(true);
        stopSelf();
    }

    private LogUtil.LogInfo buildLogInfo(boolean cleanup, @NonNull List<LogUtil.LogEntry> log) {
        LogUtil.LogInfo logInfo = new LogUtil.LogInfo();
        logInfo.setLogName(cleanup ? "Cleanup" : "Import");
        logInfo.setFileName(cleanup ? "cleanup_log" : "import_log");
        logInfo.setNoDataMessage("No content detected.");
        logInfo.setLog(log);
        return logInfo;
    }


    @Nullable
    private static Content importJson(@NonNull Context context, @NonNull DocumentFile folder) throws ParseException {
        //DocumentFile json = folder.findFile(Consts.JSON_FILE_NAME_V2); // (v2) JSON file format
        DocumentFile json = FileHelper.findFile(context, folder, Consts.JSON_FILE_NAME_V2); // (v2) JSON file format
        if (json != null && json.exists()) return importJsonV2(json, folder);

        //json = folder.findFile(Consts.JSON_FILE_NAME); // (v1) JSON file format
        json = FileHelper.findFile(context, folder, Consts.JSON_FILE_NAME); // (v1) JSON file format
        if (json != null && json.exists()) return importJsonV1(json, folder);

        //json = folder.findFile(Consts.JSON_FILE_NAME_OLD); // (old) JSON file format (legacy and/or FAKKUDroid App)
        json = FileHelper.findFile(context, folder, Consts.JSON_FILE_NAME_OLD); // (old) JSON file format (legacy and/or FAKKUDroid App)
        if (json != null && json.exists()) return importJsonLegacy(json, folder);

        return null;
    }

    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    private static List<Attribute> from(List<URLBuilder> urlBuilders, Site site) {
        List<Attribute> attributes = null;
        if (urlBuilders == null) {
            return null;
        }
        if (!urlBuilders.isEmpty()) {
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

    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    private static Attribute from(URLBuilder urlBuilder, AttributeType type, Site site) {
        if (urlBuilder == null) {
            return null;
        }
        try {
            if (urlBuilder.getDescription() == null) {
                throw new ParseException("Problems loading attribute v2.");
            }

            return new Attribute(type, urlBuilder.getDescription(), urlBuilder.getId(), site);
        } catch (Exception e) {
            Timber.e(e, "Parsing URL to attribute");
            return null;
        }
    }

    @CheckResult
    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    private static Content importJsonLegacy(@NonNull final DocumentFile json, @NonNull final DocumentFile parentFolder) throws ParseException {
        try {
            DoujinBuilder doujinBuilder =
                    JsonHelper.jsonToObject(json, DoujinBuilder.class);
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
            content.setDownloadDate(Instant.now().toEpochMilli());
            Content contentV2 = content.toV2Content();

            contentV2.setStorageUri(parentFolder.getUri().toString());

            DocumentFile newJson = JsonHelper.createJson(JsonContent.fromEntity(contentV2), JsonContent.class, parentFolder);
            contentV2.setJsonUri(newJson.getUri().toString());

            return contentV2;
        } catch (Exception e) {
            Timber.e(e, "Error reading JSON (old) file");
            throw new ParseException("Error reading JSON (old) file : " + e.getMessage());
        }
    }

    @CheckResult
    @SuppressWarnings({"deprecation", "squid:CallToDeprecatedMethod"})
    private static Content importJsonV1(@NonNull final DocumentFile json, @NonNull final DocumentFile parentFolder) throws ParseException {
        try {
            ContentV1 content = JsonHelper.jsonToObject(json, ContentV1.class);
            if (content.getStatus() != StatusContent.DOWNLOADED
                    && content.getStatus() != StatusContent.ERROR) {
                content.setMigratedStatus();
            }
            Content contentV2 = content.toV2Content();

            contentV2.setStorageUri(parentFolder.getUri().toString());

            DocumentFile newJson = JsonHelper.createJson(JsonContent.fromEntity(contentV2), JsonContent.class, parentFolder);
            contentV2.setJsonUri(newJson.getUri().toString());

            return contentV2;
        } catch (Exception e) {
            Timber.e(e, "Error reading JSON (v1) file");
            throw new ParseException("Error reading JSON (v1) file : " + e.getMessage());
        }
    }

    @CheckResult
    private static Content importJsonV2(@NonNull final DocumentFile json, @NonNull final DocumentFile parentFolder) throws ParseException {
        try {
            JsonContent content = JsonHelper.jsonToObject(json, JsonContent.class);
            Content result = content.toEntity();
            result.setJsonUri(json.getUri().toString());

            result.setStorageUri(parentFolder.getUri().toString());

            if (result.getStatus() != StatusContent.DOWNLOADED
                    && result.getStatus() != StatusContent.ERROR) {
                result.setStatus(StatusContent.MIGRATED);
            }

            return result;
        } catch (Exception e) {
            Timber.e(e, "Error reading JSON (v2) file");
            throw new ParseException("Error reading JSON (v2) file : " + e.getMessage(), e);
        }
    }
}
