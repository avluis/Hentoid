package me.devsaki.hentoid.workers;

import static me.devsaki.hentoid.util.ImportHelper.scanArchive;
import static me.devsaki.hentoid.util.ImportHelper.scanBookFolder;
import static me.devsaki.hentoid.util.ImportHelper.scanChapterFolders;
import static me.devsaki.hentoid.util.ImportHelper.scanForArchives;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification;
import me.devsaki.hentoid.notification.import_.ImportProgressNotification;
import me.devsaki.hentoid.notification.import_.ImportStartNotification;
import me.devsaki.hentoid.util.ArchiveHelper;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileExplorer;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.ImageHelper;
import me.devsaki.hentoid.util.ImportHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.notification.Notification;
import timber.log.Timber;


/**
 * Service responsible for importing an external library.
 */
public class ExternalImportWorker extends BaseWorker {

    private static final Pattern ENDS_WITH_NUMBER = Pattern.compile(".*\\d+(\\.\\d+)?$");

    public ExternalImportWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.external_import_service, null);
    }

    public static boolean isRunning(@NonNull Context context) {
        return isRunning(context, R.id.external_import_service);
    }

    @Override
    Notification getStartNotification() {
        return new ImportStartNotification();
    }

    @Override
    void onInterrupt() {
        // Nothing
    }

    @Override
    void onClear() {
        // Nothing
    }

    @Override
    void getToWork(@NonNull Data input) {
        startImport(getApplicationContext());
    }

    private void eventProgress(int step, int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.import_external, step, booksOK, booksKO, nbBooks));
    }

    private void eventProcessed(int step, String name) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.import_external, step, name));
    }

    private void eventComplete(int step, int nbBooks, int booksOK, int booksKO, DocumentFile cleanupLogFile) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.import_external, step, booksOK, booksKO, nbBooks, cleanupLogFile));
    }

    private void trace(int priority, int chapter, List<LogHelper.LogEntry> memoryLog, String s, String... t) {
        s = String.format(s, (Object[]) t);
        Timber.log(priority, s);
        boolean isError = (priority > Log.INFO);
        if (null != memoryLog) memoryLog.add(new LogHelper.LogEntry(s, chapter, isError));
    }


    /**
     * Import books from external folder
     */
    private void startImport(
            @NonNull final Context context
    ) {
        int booksOK = 0;                        // Number of books imported
        int booksKO = 0;                        // Number of folders found with no valid book inside
        List<LogHelper.LogEntry> log = new ArrayList<>();

        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(context, Preferences.getExternalLibraryUri());
        if (null == rootFolder) {
            Timber.e("External folder is not defined (%s)", Preferences.getExternalLibraryUri());
            return;
        }

        DocumentFile logFile = null;
        CollectionDAO dao = new ObjectBoxDAO(context);

        try (FileExplorer explorer = new FileExplorer(context, Uri.parse(Preferences.getExternalLibraryUri()))) {
            List<Content> library = new ArrayList<>();
            // Deep recursive search starting from the place the user has selected
            scanFolderRecursive(context, rootFolder, explorer, new ArrayList<>(), library, dao);
            eventComplete(ImportWorker.STEP_2_BOOK_FOLDERS, 0, 0, 0, null);

            // Write JSON file for every found book and persist it in the DB
            trace(Log.DEBUG, 0, log, "Import books starting - initial detected count : %s", library.size() + "");
            dao.deleteAllExternalBooks();

            for (Content content : library) {
                // If the same book folder is already in the DB, that means the user is trying to import
                // a subfolder of the Hentoid main folder (yes, it has happened) => ignore these books
                String duplicateOrigin = "folder";
                Content existingDuplicate = dao.selectContentByStorageUri(content.getStorageUri(), false);

                // The very same book may also exist in the DB under a different folder,
                if (null == existingDuplicate) {
                    existingDuplicate = dao.selectContentBySourceAndUrl(content.getSite(), content.getUrl(), "");
                    // Ignore the duplicate if it is queued; we do prefer to import a full book
                    if (existingDuplicate != null) {
                        if (ContentHelper.isInQueue(existingDuplicate.getStatus()))
                            existingDuplicate = null;
                        else duplicateOrigin = "book";
                    }
                }

                if (existingDuplicate != null && !existingDuplicate.isFlaggedForDeletion()) {
                    booksKO++;
                    trace(Log.INFO, 1, log, "Import book KO! (" + duplicateOrigin + " already in collection) : %s", content.getStorageUri());
                    continue;
                }

                if (content.getJsonUri().isEmpty()) {
                    Uri jsonUri = null;
                    try {
                        jsonUri = createJsonFileFor(context, content, explorer);
                    } catch (IOException ioe) {
                        Timber.w(ioe); // Not blocking
                        trace(Log.WARN, 1, log, "Could not create JSON in %s", content.getStorageUri());
                    }
                    if (jsonUri != null) content.setJsonUri(jsonUri.toString());
                }
                ContentHelper.addContent(context, dao, content);
                trace(Log.INFO, 1, log, "Import book OK : %s", content.getStorageUri());
                booksOK++;
                notificationManager.notify(new ImportProgressNotification(content.getTitle(), booksOK + booksKO, library.size()));
                eventProgress(ImportWorker.STEP_3_BOOKS, library.size(), booksOK, booksKO);
            }
            trace(Log.INFO, 2, log, "Import books complete - %s OK; %s KO; %s final count", booksOK + "", booksKO + "", library.size() + "");
            eventComplete(ImportWorker.STEP_3_BOOKS, library.size(), booksOK, booksKO, null);

            // Write log in root folder
            logFile = LogHelper.writeLog(context, buildLogInfo(log));
        } catch (IOException e) {
            Timber.w(e);
        } finally {
            eventComplete(ImportWorker.STEP_4_QUEUE_FINAL, booksOK + booksKO, booksOK, booksKO, logFile); // Final event; should be step 4
            notificationManager.notify(new ImportCompleteNotification(booksOK, booksKO));
            dao.cleanup();
        }
    }

    private LogHelper.LogInfo buildLogInfo(@NonNull List<LogHelper.LogEntry> log) {
        LogHelper.LogInfo logInfo = new LogHelper.LogInfo();
        logInfo.setHeaderName("Import external");
        logInfo.setFileName("import_external_log");
        logInfo.setNoDataMessage("No content detected.");
        logInfo.setEntries(log);
        return logInfo;
    }

    private void scanFolderRecursive(
            @NonNull final Context context,
            @NonNull final DocumentFile root,
            @NonNull final FileExplorer explorer,
            @NonNull final List<String> parentNames,
            @NonNull final List<Content> library,
            @NonNull final CollectionDAO dao) {
        if (parentNames.size() > 4) return; // We've descended too far

        String rootName = (null == root.getName()) ? "" : root.getName();
        eventProcessed(2, rootName);

        Timber.d(">>>> scan root %s", root.getUri());
        List<DocumentFile> files = explorer.listDocumentFiles(context, root);
        List<DocumentFile> subFolders = new ArrayList<>();
        List<DocumentFile> images = new ArrayList<>();
        List<DocumentFile> archives = new ArrayList<>();
        List<DocumentFile> jsons = new ArrayList<>();

        // Look for the interesting stuff
        for (DocumentFile file : files)
            if (file.getName() != null) {
                if (file.isDirectory()) subFolders.add(file);
                else if (ImageHelper.getImageNamesFilter().accept(file.getName())) images.add(file);
                else if (ArchiveHelper.getArchiveNamesFilter().accept(file.getName()))
                    archives.add(file);
                else if (JsonHelper.getJsonNamesFilter().accept(file.getName())) jsons.add(file);
            }

        // If at least 2 subfolders and everyone of them ends with a number, we've got a multi-chapter book
        if (subFolders.size() >= 2) {
            boolean allSubfoldersEndWithNumber = Stream.of(subFolders).map(DocumentFile::getName).withoutNulls().allMatch(n -> ENDS_WITH_NUMBER.matcher(n).matches());
            if (allSubfoldersEndWithNumber) {
                // Make certain folders contain actual books by peeking the 1st one (could be a false positive, i.e. folders per year '1990-2000')
                int nbPicturesInside = explorer.countFiles(subFolders.get(0), ImageHelper.getImageNamesFilter());
                if (nbPicturesInside > 1) {
                    DocumentFile json = ImportHelper.getFileWithName(jsons, Consts.JSON_FILE_NAME_V2);
                    library.add(scanChapterFolders(context, root, subFolders, explorer, parentNames, dao, json));
                }
                // Look for archives inside
                int nbArchivesInside = explorer.countFiles(subFolders.get(0), ArchiveHelper.getArchiveNamesFilter());
                if (nbArchivesInside > 0) {
                    List<Content> c = scanForArchives(context, subFolders, explorer, parentNames, dao);
                    library.addAll(c);
                }
            }
        }
        if (!archives.isEmpty()) { // We've got an archived book
            for (DocumentFile archive : archives) {
                DocumentFile json = ImportHelper.getFileWithName(jsons, archive.getName());
                Content c = scanArchive(context, root, archive, parentNames, StatusContent.EXTERNAL, dao, json);
                if (!c.getStatus().equals(StatusContent.IGNORED)) library.add(c);
            }
        }
        if (images.size() > 2) { // We've got a book
            DocumentFile json = ImportHelper.getFileWithName(jsons, Consts.JSON_FILE_NAME_V2);
            library.add(scanBookFolder(context, root, explorer, parentNames, StatusContent.EXTERNAL, dao, images, json));
        }

        // Go down one level
        List<String> newParentNames = new ArrayList<>(parentNames);
        newParentNames.add(rootName);
        for (DocumentFile subfolder : subFolders)
            scanFolderRecursive(context, subfolder, explorer, newParentNames, library, dao);
    }

    @Nullable
    private Uri createJsonFileFor(
            @NonNull final Context context,
            @NonNull final Content content,
            @NonNull final FileExplorer explorer) throws IOException {
        if (null == content.getStorageUri() || content.getStorageUri().isEmpty()) return null;

        // Check if the storage URI is valid
        DocumentFile contentFolder;
        if (content.isArchive()) {
            contentFolder = FileHelper.getFolderFromTreeUriString(context, content.getArchiveLocationUri());
        } else {
            contentFolder = FileHelper.getFolderFromTreeUriString(context, content.getStorageUri());
        }
        if (null == contentFolder) return null;

        // If a JSON file already exists at that location, use it as is, don't overwrite it
        String jsonName;
        if (content.isArchive()) {
            DocumentFile archiveFile = FileHelper.getFileFromSingleUriString(context, content.getStorageUri());
            jsonName = FileHelper.getFileNameWithoutExtension(StringHelper.protect(archiveFile.getName())) + ".json";
        } else {
            jsonName = Consts.JSON_FILE_NAME_V2;
        }
        DocumentFile jsonFile = explorer.findFile(context, contentFolder, jsonName);
        if (jsonFile != null && jsonFile.exists()) return jsonFile.getUri();

        return JsonHelper.jsonToFile(context, JsonContent.fromEntity(content), JsonContent.class, contentFolder, jsonName).getUri();
    }
}
