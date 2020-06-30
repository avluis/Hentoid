package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification;
import me.devsaki.hentoid.notification.import_.ImportProgressNotification;
import me.devsaki.hentoid.notification.import_.ImportStartNotification;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.LogUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import timber.log.Timber;

/**
 * Service responsible for importing an external library.
 */
public class ExternalImportService extends IntentService {

    private static final int NOTIFICATION_ID = 1;
    private static final Pattern ENDS_WITH_NUMBER = Pattern.compile(".*\\d+(\\.\\d+)?$");

    private static boolean running;
    private ServiceNotificationManager notificationManager;

    final FileHelper.NameFilter imageNamesFilter = displayName -> Helper.isImageExtensionSupported(FileHelper.getExtension(displayName));


    public ExternalImportService() {
        super(ExternalImportService.class.getName());
    }

    public static Intent makeIntent(@NonNull Context context) {
        return new Intent(context, ExternalImportService.class);
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

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        startImport();
    }

    private void eventProgress(int step, int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, step, booksOK, booksKO, nbBooks));
    }

    private void eventProcessed(int step, String name) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, step, name));
    }

    private void eventComplete(int step, int nbBooks, int booksOK, int booksKO, DocumentFile cleanupLogFile) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, step, booksOK, booksKO, nbBooks, cleanupLogFile));
    }

    private void trace(int priority, List<LogUtil.LogEntry> memoryLog, String s, String... t) {
        s = String.format(s, (Object[]) t);
        Timber.log(priority, s);
        if (null != memoryLog) memoryLog.add(new LogUtil.LogEntry(s));
    }


    /**
     * Import books from external folder
     */
    private void startImport() {
        int booksOK = 0;                        // Number of books imported
        int booksKO = 0;                        // Number of folders found with no valid book inside
        List<LogUtil.LogEntry> log = new ArrayList<>();

        DocumentFile rootFolder = DocumentFile.fromTreeUri(this, Uri.parse(Preferences.getExternalLibraryUri()));
        if (null == rootFolder || !rootFolder.exists()) {
            Timber.e("External folder is not defined (%s)", Preferences.getExternalLibraryUri());
            return;
        }

        ContentProviderClient client = this.getContentResolver().acquireContentProviderClient(Uri.parse(Preferences.getExternalLibraryUri()));
        if (null == client) return;

        DocumentFile logFile = null;
        CollectionDAO dao = new ObjectBoxDAO(this);

        try {
            notificationManager.startForeground(new ImportProgressNotification(this.getResources().getString(R.string.starting_import), 0, 0));

            List<Content> library = new ArrayList<>();
            // Deep recursive search starting from the place the user has selected
            scanFolderRecursive(rootFolder, client, new ArrayList<>(), library);
            eventComplete(2, 0, 0, 0, null);

            // Write JSON file for every found book and persist it in the DB
            trace(Log.DEBUG, log, "Import books starting - initial detected count : %s", library.size() + "");
            dao.deleteAllExternalBooks();

            for (Content content : library) {
                if (content.getJsonUri().isEmpty()) {
                    Uri jsonUri = null;
                    try {
                        jsonUri = getJsonFor(content, client);
                    } catch (IOException ioe) {
                        Timber.w(ioe); // Not blocking
                        trace(Log.WARN, log, "Could not create JSON in %s", content.getStorageUri());
                    }
                    if (jsonUri != null) content.setJsonUri(jsonUri.toString());
                }
                content.computeSize();
                dao.insertContent(content);
                trace(Log.INFO, log, "Import book OK : %s", content.getStorageUri());
                booksOK++;
                notificationManager.notify(new ImportProgressNotification(content.getTitle(), booksOK + booksKO, library.size()));
                eventProgress(3, library.size(), booksOK, booksKO);
            }
            trace(Log.INFO, log, "Import books complete - %s OK; %s KO; %s final count", booksOK + "", booksKO + "", library.size() + "");
            eventComplete(3, library.size(), booksOK, booksKO, null);

            // Write log in root folder
            logFile = LogUtil.writeLog(this, buildLogInfo(log));
        } finally {
            // ContentProviderClient.close only available on API level 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();

            eventComplete(4, booksOK + booksKO, booksOK, booksKO, logFile); // Final event; should be step 4
            notificationManager.notify(new ImportCompleteNotification(booksOK, booksKO));
            dao.cleanup();
        }

        stopForeground(true);
        stopSelf();
    }

    private LogUtil.LogInfo buildLogInfo(@NonNull List<LogUtil.LogEntry> log) {
        LogUtil.LogInfo logInfo = new LogUtil.LogInfo();
        logInfo.setLogName("Import external");
        logInfo.setFileName("import_external_log");
        logInfo.setNoDataMessage("No content detected.");
        logInfo.setLog(log);
        return logInfo;
    }

    private void scanFolderRecursive(
            @NonNull final DocumentFile root,
            @NonNull final ContentProviderClient client,
            @NonNull final List<String> parentNames,
            @NonNull final List<Content> library) {
        if (parentNames.size() > 4) return; // We've descended too far

        String rootName = (null == root.getName()) ? "" : root.getName();
        eventProcessed(2, rootName);

        Timber.d(">>>> scan root %s", root.getUri());
        List<DocumentFile> files = FileHelper.listDocumentFiles(this, root, client);
        List<DocumentFile> subFolders = new ArrayList<>();
        List<DocumentFile> images = new ArrayList<>();
        DocumentFile json = null;
        for (DocumentFile file : files)
            if (file.getName() != null) {
                if (file.isDirectory()) subFolders.add(file);
                else if (imageNamesFilter.accept(file.getName())) images.add(file);
                else if (file.getName().equals(Consts.JSON_FILE_NAME_V2)) json = file;
            }

        // If at least 2 subfolders and everyone of them ends with a number, we've got a multi-chapter book
        if (subFolders.size() >= 2) {
            boolean allSubfoldersEndWithNumber = Stream.of(subFolders).map(DocumentFile::getName).withoutNulls().allMatch(n -> ENDS_WITH_NUMBER.matcher(n).matches());
            if (allSubfoldersEndWithNumber) {
                // Make certain folders contain actual books by peeking the 1st one (could be a false positive, i.e. folders per year '1990-2000')
                int nbPicturesInside = FileHelper.countFiles(subFolders.get(0), client, imageNamesFilter);
                if (nbPicturesInside > 1) {
                    library.add(scanChapterFolders(root, subFolders, client, parentNames, json));
                    return;
                }
            }
        } else if (images.size() > 2) { // We've got a book !
            library.add(scanBookFolder(root, client, parentNames, images, json));
            return;
        }

        // If nothing above works, go down one level
        List<String> newParentNames = new ArrayList<>(parentNames);
        newParentNames.add(rootName);
        for (DocumentFile subfolder : subFolders)
            scanFolderRecursive(subfolder, client, newParentNames, library);
    }

    @Nullable
    private Uri getJsonFor(@NonNull final Content c, @NonNull final ContentProviderClient client) throws IOException {
        if (null == c.getStorageUri() || c.getStorageUri().isEmpty()) return null;

        DocumentFile contentFolder = DocumentFile.fromTreeUri(this, Uri.parse(c.getStorageUri()));
        if (null == contentFolder || !contentFolder.exists()) return null;

        // If it exists, use it as is, don't overwrite it
        DocumentFile jsonFile = FileHelper.findFile(this, contentFolder, client, Consts.JSON_FILE_NAME_V2);
        if (jsonFile != null && jsonFile.exists()) return jsonFile.getUri();

        return JsonHelper.jsonToFile(this, JsonContent.fromEntity(c), JsonContent.class, contentFolder).getUri();
    }

    private Content scanBookFolder(
            @NonNull final DocumentFile bookFolder,
            @NonNull final ContentProviderClient client,
            @NonNull final List<String> parentNames,
            @Nullable final List<DocumentFile> imageFiles,
            @Nullable final DocumentFile jsonFile) {
        Timber.d(">>>> scan book folder %s", bookFolder.getUri());

        Content result = null;
        if (jsonFile != null) {
            try {
                JsonContent content = JsonHelper.jsonToObject(this, jsonFile, JsonContent.class);
                result = content.toEntity();
                result.setJsonUri(jsonFile.getUri().toString());
            } catch (IOException ioe) {
                Timber.w(ioe);
            }
        }
        if (null == result) {
            String title = bookFolder.getName();
            result = new Content().setSite(Site.NONE).setTitle((null == title) ? "" : title).setUrl("");
            result.setDownloadDate(bookFolder.lastModified());
            result.addAttributes(parentNamesAsTags(parentNames));
        }

        result.setStatus(StatusContent.EXTERNAL).setStorageUri(bookFolder.getUri().toString());
        List<ImageFile> images = new ArrayList<>();
        scanImages(bookFolder, client, false, images, imageFiles);
        boolean coverExists = Stream.of(images).anyMatch(ImageFile::isCover);
        if (!coverExists) createCover(images);
        result.setImageFiles(images);
        if (0 == result.getQtyPages())
            result.setQtyPages(images.size() - 1); // Minus the cover
        return result;
    }

    private Content scanChapterFolders(
            @NonNull final DocumentFile parent,
            @NonNull final List<DocumentFile> chapterFolders,
            @NonNull final ContentProviderClient client,
            @NonNull final List<String> parentNames,
            @Nullable final DocumentFile jsonFile) {
        Timber.d(">>>> scan chapter folder %s", parent.getUri());

        Content result = null;
        if (jsonFile != null) {
            try {
                JsonContent content = JsonHelper.jsonToObject(this, jsonFile, JsonContent.class);
                result = content.toEntity();
                result.setJsonUri(jsonFile.getUri().toString());
            } catch (IOException ioe) {
                Timber.w(ioe);
            }
        }
        if (null == result) {
            result = new Content().setSite(Site.NONE).setTitle((null == parent.getName()) ? "" : parent.getName()).setUrl("");
            result.setDownloadDate(parent.lastModified());
            result.addAttributes(parentNamesAsTags(parentNames));
        }

        result.setStatus(StatusContent.EXTERNAL).setStorageUri(parent.getUri().toString());
        List<ImageFile> images = new ArrayList<>();
        // Scan pages across all subfolders
        for (DocumentFile chapterFolder : chapterFolders)
            scanImages(chapterFolder, client, true, images, null);
        boolean coverExists = Stream.of(images).anyMatch(ImageFile::isCover);
        if (!coverExists) createCover(images);
        result.setImageFiles(images);
        if (0 == result.getQtyPages())
            result.setQtyPages(images.size() - 1); // Minus the cover
        return result;
    }

    private void scanImages(
            @NonNull final DocumentFile bookFolder,
            @NonNull final ContentProviderClient client,
            boolean addFolderNametoImgName,
            @NonNull final List<ImageFile> images,
            @Nullable List<DocumentFile> imageFiles) {
        int order = (images.isEmpty()) ? 0 : Stream.of(images).map(ImageFile::getOrder).max(Integer::compareTo).get();
        String folderName = (null == bookFolder.getName()) ? "" : bookFolder.getName();
        if (null == imageFiles)
            imageFiles = FileHelper.listFiles(this, bookFolder, client, imageNamesFilter);

        String namePrefix = "";
        if (addFolderNametoImgName) namePrefix = folderName + "-";

        images.addAll(ContentHelper.createImageListFromFiles(imageFiles, StatusContent.EXTERNAL, order, namePrefix));
    }

    private void createCover(@NonNull final List<ImageFile> images) {
        if (!images.isEmpty()) {
            ImageFile firstImg = images.get(0);
            ImageFile cover = new ImageFile(0, "", StatusContent.DOWNLOADED, images.size());
            cover.setName(Consts.THUMB_FILE_NAME);
            cover.setFileUri(firstImg.getFileUri());
            cover.setSize(firstImg.getSize());
            cover.setIsCover(true);
            images.add(0, cover);
        }
    }

    private AttributeMap parentNamesAsTags(@NonNull final List<String> parentNames) {
        AttributeMap result = new AttributeMap();
        // Don't include the very first one, it's the name of the root folder of the library
        if (parentNames.size() > 1) {
            for (int i = 1; i < parentNames.size(); i++)
                result.add(new Attribute(AttributeType.TAG, parentNames.get(i), parentNames.get(i), Site.NONE));
        }
        return result;
    }
}
