package me.devsaki.hentoid.services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification;
import me.devsaki.hentoid.notification.import_.ImportStartNotification;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.file.FileExplorer;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.image.ImageHelper;
import me.devsaki.hentoid.util.LogHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.util.notification.ServiceNotificationManager;
import timber.log.Timber;

/**
 * Service responsible for migrating an existing library to v1.12+ requirements
 * (persist folder and image URIs to the DB)
 *
 * @see UpdateCheckService
 */
public class API29MigrationService extends IntentService {

    private static final int NOTIFICATION_ID = API29MigrationService.class.getName().hashCode();

    private ServiceNotificationManager notificationManager;
    private Disposable searchDisposable = Disposables.empty();
    private final Map<String, Map<String, DocumentFile>> bookFoldersCache = new HashMap<>();
    private CollectionDAO dao;


    public API29MigrationService() {
        super(API29MigrationService.class.getName());
    }

    public static Intent makeIntent(@NonNull Context context) {
        return new Intent(context, API29MigrationService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = new ServiceNotificationManager(this, NOTIFICATION_ID);
        notificationManager.cancel();
        notificationManager.startForeground(new ImportStartNotification());

        Timber.w("Service created");
    }

    @Override
    public void onDestroy() {
        notificationManager.cancel();
        if (dao != null) dao.cleanup();
        Timber.w("Service destroyed");

        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        try {
            performMigration();
        } catch (InterruptedException ie) {
            Timber.e(ie);
            Thread.currentThread().interrupt();
        }
    }

    private void eventProgress(int step, int nbBooks, int booksOK, int booksKO) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.migrate_api29, step, booksOK, booksKO, nbBooks));
    }

    private void eventComplete(int step, int nbBooks, int booksOK, int booksKO, DocumentFile cleanupLogFile) {
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.migrate_api29, step, booksOK, booksKO, nbBooks, cleanupLogFile));
    }

    private void trace(int priority, int chapter, List<LogHelper.LogEntry> memoryLog, String s, String... t) {
        s = String.format(s, (Object[]) t);
        Timber.log(priority, s);
        boolean isError = (priority > Log.INFO);
        if (null != memoryLog) memoryLog.add(new LogHelper.LogEntry(s, chapter, isError));
    }


    /**
     * Import books from known source folders
     */
    private void performMigration() throws InterruptedException {
        List<LogHelper.LogEntry> log = new ArrayList<>();

        DocumentFile rootFolder = FileHelper.getDocumentFromTreeUriString(this, Preferences.getStorageUri());
        if (null == rootFolder) {
            Timber.e("rootFolder is not defined (%s)", Preferences.getStorageUri());
            return;
        }
        trace(Log.INFO, 0, log, "Using root folder %s", rootFolder.getUri().toString());

        // 1st pass : cache all book folders
        List<DocumentFile> siteFolders = FileHelper.listFolders(this, rootFolder);
        trace(Log.INFO, 0, log, "%s site folders detected", siteFolders.size() + "");

        List<DocumentFile> bookFolders;
        int foldersCount = 1;
        for (DocumentFile siteFolder : siteFolders) {
            bookFolders = FileHelper.listFolders(this, siteFolder);
            Map<String, DocumentFile> siteFoldersCache = new HashMap<>(bookFolders.size());
            for (DocumentFile bookFolder : bookFolders)
                siteFoldersCache.put(bookFolder.getName(), bookFolder);
            bookFoldersCache.put(siteFolder.getName(), siteFoldersCache);
            trace(Log.INFO, 0, log, "Site %s : %s book folders detected", siteFolder.getName(), siteFoldersCache.size() + "");
            eventProgress(2, siteFolders.size(), foldersCount++, 0);
        }

        // tasks are used to execute Rx's observeOn on current thread
        // See https://github.com/square/retrofit/issues/370#issuecomment-315868381
        LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

        // 2nd pass : scan every book in the library and match actual URIs to it
        dao = new ObjectBoxDAO(this);
        searchDisposable = dao.selectOldStoredBookIds()
                .observeOn(Schedulers.from(tasks::add))
                .subscribe(
                        list -> {
                            eventComplete(2, siteFolders.size(), siteFolders.size(), 0, null);
                            searchDisposable.dispose();
                            migrateLibrary(log, list);
                        },
                        throwable -> {
                            Timber.w(throwable);
                            ToastHelper.toast(R.string.book_list_loading_failed);
                        }
                );

        tasks.take().run();
    }

    private void migrateLibrary(@NonNull final List<LogHelper.LogEntry> log, @NonNull final List<Long> contentIds) {
        int booksOK = 0;                        // Number of books imported
        int booksKO = 0;                        // Number of folders found with no valid book inside

        trace(Log.DEBUG, 0, log, "Library migration starting - books to process : %s", contentIds.size() + "");

        try (FileExplorer fe = new FileExplorer(this, Uri.parse(Preferences.getStorageUri()))) {
            for (long contentId : contentIds) {
                Content content = dao.selectContent(contentId);
                if (content != null) {
                    try {
                        // Set the book's storage URI
                        Map<String, DocumentFile> siteFolder = bookFoldersCache.get(content.getSite().getDescription());
                        if (null == siteFolder) {
                            trace(Log.WARN, 1, log, "Migrate book KO : site folder %s not found for %s [%s]", content.getSite().getDescription(), content.getTitle(), contentId + "");
                            content.resetStorageFolder();
                            dao.insertContent(content);
                            booksKO++;
                            continue;
                        }
                        // It's normal to use the deprecated feature here since it's a migration job
                        //noinspection deprecation
                        String[] contentFolderParts = content.getStorageFolder().split(File.separator);
                        String bookFolderName = contentFolderParts[contentFolderParts.length - 1];
                        DocumentFile bookFolder = siteFolder.get(bookFolderName);
                        if (null == bookFolder) {
                            trace(Log.WARN, 1, log, "Migrate book KO : book folder %s not found in %s for %s [%s]", bookFolderName, content.getSite().getDescription(), content.getTitle(), contentId + "");
                            content.resetStorageFolder();
                            dao.insertContent(content);
                            booksKO++;
                            continue;
                        }
                        content.setStorageUri(bookFolder.getUri().toString());
                        content.resetStorageFolder();

                        // Delete the JSON URI if not in the correct format (file:// instead of content://)
                        // (might be the case when the migrated collection was stored on phone memory)
                        if (content.getJsonUri().isEmpty() || !content.getJsonUri().startsWith("content"))
                            content.setJsonUri("");

                        ContentHelper.addContent(this, dao, content);

                        List<ImageFile> contentImages;
                        if (content.getImageFiles() != null)
                            contentImages = content.getImageFiles();
                        else contentImages = new ArrayList<>();

                        // Attach file Uri's to the book's images
                        List<DocumentFile> imageFiles = fe.listFiles(this, bookFolder, ImageHelper.getImageNamesFilter());
                        if (!imageFiles.isEmpty()) {
                            if (contentImages.isEmpty()) { // No images described in the content (e.g. unread import from old JSON) -> recreate them
                                contentImages = ContentHelper.createImageListFromFiles(imageFiles);
                                content.setImageFiles(contentImages);
                                content.getCover().setUrl(content.getCoverImageUrl());
                            } else { // Existing images -> map them
                                contentImages = ContentHelper.matchFilesToImageList(imageFiles, contentImages);
                                // If images are set and no cover is defined, get it too
                                if (!contentImages.isEmpty() && StatusContent.UNHANDLED_ERROR == content.getCover().getStatus()) {
                                    Optional<DocumentFile> file = Stream.of(imageFiles).filter(f -> f.getName() != null && f.getName().startsWith(Consts.THUMB_FILE_NAME)).findFirst();
                                    if (file.isPresent()) {
                                        ImageFile cover = ImageFile.fromImageUrl(0, content.getCoverImageUrl(), StatusContent.DOWNLOADED, content.getQtyPages());
                                        cover.setName(Consts.THUMB_FILE_NAME);
                                        cover.setFileUri(file.get().getUri().toString());
                                        cover.setIsCover(true);
                                        contentImages.add(0, cover);
                                    }
                                }
                                content.setImageFiles(contentImages);
                                content.computeSize();
                                dao.insertContent(content);
                            }
                        }
                        dao.replaceImageList(contentId, contentImages);

                        booksOK++;
                        trace(Log.INFO, 1, log, "Migrate book OK : %s", bookFolder.getUri().toString());
                    } catch (Exception e) {
                        Timber.w(e);
                        booksKO++;
                        trace(Log.ERROR, 1, log, "Migrate book ERROR : %s for Content %s [%s]", e.getMessage(), content.getTitle(), contentId + "");
                    }
                } else booksKO++; // null books (content ID not found in DB)

                eventProgress(3, contentIds.size(), booksOK, booksKO);
            }
        } catch (IOException e) {
            Timber.w(e);
        }
        trace(Log.INFO, 2, log, "Migration complete - %s OK; %s KO; %s final count", booksOK + "", booksKO + "", contentIds.size() + "");

        // Write cleanup log in root folder
        DocumentFile migrationLogFile = LogHelper.writeLog(this, buildLogInfo(log));

        eventComplete(3, contentIds.size(), booksOK, booksKO, migrationLogFile);
        notificationManager.notify(new ImportCompleteNotification(booksOK, booksKO));

        stopForeground(true);
        stopSelf();
    }

    private LogHelper.LogInfo buildLogInfo(@NonNull List<LogHelper.LogEntry> log) {
        LogHelper.LogInfo logInfo = new LogHelper.LogInfo();
        logInfo.setHeaderName("API29Migration");
        logInfo.setFileName("API29_migration_log");
        logInfo.setNoDataMessage("No content detected.");
        logInfo.setEntries(log);
        return logInfo;
    }
}
