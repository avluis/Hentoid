package me.devsaki.hentoid.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.threeten.bp.Instant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ErrorRecord;
import me.devsaki.hentoid.database.domains.Group;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.QueueRecord;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.enums.ErrorType;
import me.devsaki.hentoid.enums.Grouping;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.fragments.tools.MetaImportDialogFragment;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.json.JsonContentCollection;
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification;
import me.devsaki.hentoid.notification.import_.ImportProgressNotification;
import me.devsaki.hentoid.notification.import_.ImportStartNotification;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.GroupHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.ImportHelper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.notification.Notification;
import me.devsaki.hentoid.workers.data.MetadataImportData;
import timber.log.Timber;


/**
 * Service responsible for importing metadata
 */
public class MetadataImportWorker extends BaseWorker {

    // Variable used during the import process
    private CollectionDAO dao;
    private int totalItems = 0;
    private int nbOK = 0;
    private int nbKO = 0;
    private int queueSize;
    private Map<Site, DocumentFile> siteFoldersCache = null;
    private final Map<Site, List<DocumentFile>> bookFoldersCache = new EnumMap<>(Site.class);


    public MetadataImportWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.metadata_import_service, "metadata-import");
    }

    public static boolean isRunning(@NonNull Context context) {
        return isRunning(context, R.id.metadata_import_service);
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
        MetadataImportData.Parser data = new MetadataImportData.Parser(getInputData());

        startImport(
                getApplicationContext(),
                data.getJsonUri(),
                data.isAdd(),
                data.isImportLibrary(),
                data.getEmptyBooksOption(),
                data.isImportQueue(),
                data.isImportCustomGroups(),
                data.isImportBookmarks()
        );
    }

    /**
     * Import books from external folder
     */
    private void startImport(
            @NonNull final Context context,
            @NonNull final String jsonUri,
            boolean add,
            boolean importLibrary,
            int emptyBooksOption,
            boolean importQueue,
            boolean importCustomGroups,
            boolean importBookmarks
    ) {
        DocumentFile jsonFile = FileHelper.getFileFromSingleUriString(context, jsonUri);
        if (null == jsonFile) {
            trace(Log.ERROR, "Couldn't find metadata JSON file at %s", jsonUri);
            return;
        }
        Optional<JsonContentCollection> collectionOpt = deserialiseJson(context, jsonFile);
        if (collectionOpt.isEmpty()) {
            trace(Log.ERROR, "Couldn't deserialize JSON file");
            return;
        }
        JsonContentCollection collection = collectionOpt.get();

        dao = new ObjectBoxDAO(context);
        if (!add) {
            if (importLibrary) dao.deleteAllInternalBooks(false);
            if (importQueue) dao.deleteAllQueuedBooks();
            if (importCustomGroups) dao.deleteAllGroups(Grouping.CUSTOM);
            if (importBookmarks) dao.deleteAllBookmarks();
        }

        // Done in one shot
        if (importBookmarks) {
            List<SiteBookmark> bookmarks = collection.getBookmarks();
            totalItems += bookmarks.size();
            ImportHelper.importBookmarks(dao, bookmarks);
            nbOK += bookmarks.size();
        }

        List<JsonContent> contentToImport = new ArrayList<>();
        if (importLibrary) contentToImport.addAll(collection.getJsonLibrary());
        if (importQueue) contentToImport.addAll(collection.getJsonQueue());
        queueSize = (int) dao.countAllQueueBooks();

        totalItems += contentToImport.size() + queueSize;

        if (importCustomGroups) {
            totalItems += collection.getCustomGroups().size();
            // Chain group import followed by content import
            runImportItems(
                    context,
                    collection.getCustomGroups(),
                    dao,
                    true,
                    emptyBooksOption,
                    () -> runImportItems(context, contentToImport, dao, false, emptyBooksOption, this::finish)
            );
        } else // Run content import alone
            runImportItems(context, contentToImport, dao, false, emptyBooksOption, this::finish);
    }

    private void runImportItems(@NonNull Context context,
                                @NonNull final List<?> items,
                                @NonNull final CollectionDAO dao,
                                boolean isGroup,
                                Integer emptyBooksOption,
                                @NonNull final Runnable onFinish) {
        for (Object c : items) {
            if (isStopped()) break;
            try {
                importItem(context, c, emptyBooksOption, dao);
                if (isGroup) GroupHelper.updateGroupsJson(context, dao);
                nextOK(context);
            } catch (Exception e) {
                nextKO(context, e);
            }
        }
        onFinish.run();
    }

    private void importItem(@NonNull Context context, @NonNull final Object o, int emptyBooksOption, @NonNull final CollectionDAO dao) {
        if (o instanceof JsonContent)
            importContent(context, (JsonContent) o, emptyBooksOption, dao);
        else if (o instanceof Group) importGroup((Group) o, dao);
    }

    private void importContent(@NonNull Context context, @NonNull final JsonContent jsonContent, int emptyBooksOption, @NonNull final CollectionDAO dao) {
        // Try to map the imported content to an existing book in the downloads folder
        // Folder names can be formatted in many ways _but_ they always contain the book unique ID !
        if (null == siteFoldersCache) siteFoldersCache = getSiteFolders(context);
        Content c = jsonContent.toEntity(dao);

        Content duplicate = dao.selectContentBySourceAndUrl(c.getSite(), c.getUrl(), "");
        if (duplicate != null) return;

        DocumentFile siteFolder = siteFoldersCache.get(c.getSite());
        if (null == siteFolder) {
            siteFolder = ContentHelper.getOrCreateSiteDownloadDir(context, null, c.getSite());
            if (siteFolder != null) siteFoldersCache.put(c.getSite(), siteFolder);
        }
        if (siteFolder != null) {
            boolean mappedToFiles = mapFilesToContent(context, c, siteFolder);
            // If no local storage found for the book, it goes in the errors queue (except if it already was in progress)
            if (!mappedToFiles) {
                switch (emptyBooksOption) {
                    case MetaImportDialogFragment.IMPORT_AS_STREAMED:
                        // Greenlighted if images exist and are available online
                        if (c.getImageFiles() != null && c.getImageFiles().size() > 0 && ContentHelper.isDownloadable(c)) {
                            c.setDownloadMode(Content.DownloadMode.STREAM);
                            List<ImageFile> imgs = c.getImageFiles();
                            if (imgs != null) {
                                List<ImageFile> newImages = Stream.of(imgs).map(i -> ImageFile.fromImageUrl(i.getOrder(), i.getUrl(), StatusContent.ONLINE, imgs.size())).toList();
                                c.setImageFiles(newImages);
                            }
                            c.forceSize(0);
                            break;
                        }
                        // no break here - import as empty if content unavailable online
                    case MetaImportDialogFragment.IMPORT_AS_EMPTY:
                        c.setImageFiles(Collections.emptyList());
                        c.clearChapters();
                        c.setStatus(StatusContent.PLACEHOLDER);
                        /*
                        DocumentFile bookFolder = ContentHelper.getOrCreateContentDownloadDir(requireContext(), c, siteFolder);
                        if (bookFolder != null) {
                            c.setStorageUri(bookFolder.getUri().toString());
                            ContentHelper.persistJson(requireContext(), c);
                        }
                        break;

                         */
                    case MetaImportDialogFragment.IMPORT_AS_ERROR:
                        if (!ContentHelper.isInQueue(c.getStatus()))
                            c.setStatus(StatusContent.ERROR);
                        List<ErrorRecord> errors = new ArrayList<>();
                        errors.add(new ErrorRecord(ErrorType.IMPORT, "", context.getResources().getQuantityString(R.plurals.book, 1), "No local images found when importing - Please redownload", Instant.now()));
                        c.setErrorLog(errors);
                        break;
                    default:
                    case MetaImportDialogFragment.DONT_IMPORT:
                        return;
                }
            }
        }

        // All checks successful => create the content
        long newContentId = ContentHelper.addContent(context, dao, c);
        // Insert queued content into the queue
        if (c.getStatus().equals(StatusContent.DOWNLOADING) || c.getStatus().equals(StatusContent.PAUSED)) {
            List<QueueRecord> lst = new ArrayList<>();
            lst.add(new QueueRecord(newContentId, queueSize++));
            dao.updateQueue(lst);
        }
    }

    private boolean mapFilesToContent(@NonNull Context context, @NonNull final Content c, @NonNull final DocumentFile siteFolder) {
        List<DocumentFile> bookfolders;
        if (bookFoldersCache.containsKey(c.getSite()))
            bookfolders = bookFoldersCache.get(c.getSite());
        else {
            bookfolders = FileHelper.listFolders(context, siteFolder);
            bookFoldersCache.put(c.getSite(), bookfolders);
        }
        boolean filesFound = false;
        if (bookfolders != null) {
            // Look for the book ID
            c.populateUniqueSiteId();
            for (DocumentFile f : bookfolders)
                if (f.getName() != null && f.getName().contains(ContentHelper.formatBookId(c))) {
                    // Cache folder Uri
                    c.setStorageUri(f.getUri().toString());
                    // Cache JSON Uri
                    DocumentFile json = FileHelper.findFile(context, f, Consts.JSON_FILE_NAME_V2);
                    if (json != null) c.setJsonUri(json.getUri().toString());
                    // Create the images from detected files
                    c.setImageFiles(ContentHelper.createImageListFromFolder(context, f));
                    filesFound = true;
                    break;
                }
        }
        return filesFound;
    }

    private Map<Site, DocumentFile> getSiteFolders(@NonNull Context context) {
        Helper.assertNonUiThread();
        Map<Site, DocumentFile> result = new EnumMap<>(Site.class);

        DocumentFile rootFolder = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
        if (null != rootFolder) {
            List<DocumentFile> subfolders = FileHelper.listFolders(context, rootFolder);
            String folderName;
            for (DocumentFile f : subfolders)
                if (f.getName() != null) {
                    folderName = f.getName().toLowerCase();
                    for (Site s : Site.values()) {
                        if (folderName.equalsIgnoreCase(s.getFolder())) {
                            result.put(s, f);
                            break;
                        }
                    }
                }
        }
        return result;
    }

    private void importGroup(@NonNull final Group group, @NonNull final CollectionDAO dao) {
        if (null == dao.selectGroupByName(Grouping.CUSTOM.getId(), group.name))
            dao.insertGroup(group);
    }

    private Optional<JsonContentCollection> deserialiseJson(@NonNull final Context context, @NonNull DocumentFile jsonFile) {
        JsonContentCollection result;
        try {
            result = JsonHelper.jsonToObject(context, jsonFile, JsonContentCollection.class);
        } catch (IOException e) {
            Timber.w(e);
            return Optional.empty();
        }
        return Optional.of(result);
    }


    private void nextOK(@NonNull Context context) {
        nbOK++;
        updateProgress(context);
    }

    private void nextKO(@NonNull Context context, Throwable e) {
        nbKO++;
        Timber.w(e);
        updateProgress(context);
    }

    private void updateProgress(@NonNull Context context) {
        notificationManager.notify(new ImportProgressNotification(context.getResources().getString(R.string.importing_metadata), nbOK + nbKO, totalItems));
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.import_metadata, 0, nbOK, nbKO, totalItems));
    }

    private void finish() {
        if (dao != null) dao.cleanup();
        notificationManager.notify(new ImportCompleteNotification(nbOK, nbKO));
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.import_metadata, 0, nbOK, nbKO, totalItems));
    }
}
