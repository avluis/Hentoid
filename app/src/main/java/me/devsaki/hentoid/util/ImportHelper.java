package me.devsaki.hentoid.util;

import static android.os.Build.VERSION_CODES.O;
import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;
import static me.devsaki.hentoid.core.Consts.WORK_CLOSEABLE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.squareup.moshi.JsonDataException;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.threeten.bp.Instant;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.Consts;
import me.devsaki.hentoid.core.HentoidApp;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.AttributeMap;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.database.domains.SiteBookmark;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel;
import me.devsaki.hentoid.workers.ExternalImportWorker;
import me.devsaki.hentoid.workers.ImportWorker;
import me.devsaki.hentoid.workers.data.ImportData;
import timber.log.Timber;

public class ImportHelper {

    private ImportHelper() {
        throw new IllegalStateException("Utility class");
    }


    private static final String EXTERNAL_LIB_TAG = "external-library";

    @IntDef({PickerResult.OK, PickerResult.KO_NO_URI, PickerResult.KO_CANCELED, PickerResult.KO_OTHER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface PickerResult {
        int OK = 0; // OK - Returned a valid URI
        int KO_NO_URI = 1; // No URI selected
        int KO_CANCELED = 2; // Operation canceled
        int KO_OTHER = 3; // Any other issue
    }

    @IntDef({ProcessFolderResult.OK_EMPTY_FOLDER, ProcessFolderResult.OK_LIBRARY_DETECTED, ProcessFolderResult.OK_LIBRARY_DETECTED_ASK, ProcessFolderResult.KO_INVALID_FOLDER, ProcessFolderResult.KO_DOWNLOAD_FOLDER, ProcessFolderResult.KO_APP_FOLDER, ProcessFolderResult.KO_CREATE_FAIL, ProcessFolderResult.KO_ALREADY_RUNNING, ProcessFolderResult.KO_OTHER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ProcessFolderResult {
        int OK_EMPTY_FOLDER = 1; // OK - Existing, empty Hentoid folder
        int OK_LIBRARY_DETECTED = 2; // OK - En existing Hentoid folder with books
        int OK_LIBRARY_DETECTED_ASK = 3; // OK - Existing Hentoid folder with books + we need to ask the user if he wants to import them
        int KO_INVALID_FOLDER = 5; // File or folder is invalid, cannot be found
        int KO_APP_FOLDER = 6; // Selected folder is the app folder and can't be used as an external folder
        int KO_DOWNLOAD_FOLDER = 7; // Selected folder is the device's download folder and can't be used as a primary folder (downloads visibility + storage calculation issues)
        int KO_CREATE_FAIL = 8; // Hentoid folder could not be created
        int KO_ALREADY_RUNNING = 9; // Import is already running
        int KO_OTHER = 99; // Any other issue
    }

    private static final FileHelper.NameFilter hentoidFolderNames = displayName -> displayName.equalsIgnoreCase(Consts.DEFAULT_PRIMARY_FOLDER)
            || displayName.equalsIgnoreCase(Consts.DEFAULT_PRIMARY_FOLDER_OLD);

    /**
     * Import options for the Hentoid folder
     */
    public static class ImportOptions {
        public boolean rename; // If true, rename folders with current naming convention
        public boolean cleanNoJson; // If true, delete folders where no JSON file is found
        public boolean cleanNoImages; // If true, delete folders where no supported images are found
    }

    /**
     * Indicate whether the given folder name is a valid Hentoid folder name
     *
     * @param folderName Folder name to test
     * @return True if the given folder name is a valid Hentoid folder name; false if not
     */
    public static boolean isHentoidFolderName(@NonNull final String folderName) {
        return hentoidFolderNames.accept(folderName);
    }

    public static class PickFolderContract extends ActivityResultContract<Integer, ImmutablePair<Integer, Uri>> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Integer input) {
            HentoidApp.LifeCycleListener.disable(); // Prevents the app from displaying the PIN lock when returning from the SAF dialog
            return getFolderPickerIntent(context);
        }

        @Override
        public ImmutablePair<Integer, Uri> parseResult(int resultCode, @Nullable Intent intent) {
            HentoidApp.LifeCycleListener.enable(); // Restores autolock on app going to background
            return parsePickerResult(resultCode, intent);
        }
    }

    public static class PickFileContract extends ActivityResultContract<Integer, ImmutablePair<Integer, Uri>> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Integer input) {
            HentoidApp.LifeCycleListener.disable(); // Prevents the app from displaying the PIN lock when returning from the SAF dialog
            return getFilePickerIntent();
        }

        @Override
        public ImmutablePair<Integer, Uri> parseResult(int resultCode, @Nullable Intent intent) {
            HentoidApp.LifeCycleListener.enable(); // Restores autolock on app going to background
            return parsePickerResult(resultCode, intent);
        }
    }

    private static ImmutablePair<Integer, Uri> parsePickerResult(int resultCode, @Nullable Intent intent) {
        // Return from the SAF picker
        if (resultCode == Activity.RESULT_OK && intent != null) {
            // Get Uri from Storage Access Framework
            Uri uri = intent.getData();
            if (uri != null)
                return new ImmutablePair<>(PickerResult.OK, uri);
            else return new ImmutablePair<>(PickerResult.KO_NO_URI, null);
        } else if (resultCode == Activity.RESULT_CANCELED) {
            return new ImmutablePair<>(PickerResult.KO_CANCELED, null);
        }
        return new ImmutablePair<>(PickerResult.KO_OTHER, null);
    }

    /**
     * Get the intent for the SAF folder picker properly set up, positioned on the Hentoid primary folder
     *
     * @param context Context to be used
     * @return Intent for the SAF folder picker
     */
    private static Intent getFolderPickerIntent(@NonNull final Context context) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.putExtra(DocumentsContract.EXTRA_PROMPT, context.getString(R.string.dialog_prompt));
        }
        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        // Start the SAF at the specified location
        if (Build.VERSION.SDK_INT >= O && !Preferences.getStorageUri().isEmpty()) {
            DocumentFile file = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
            if (file != null)
                intent.putExtra(EXTRA_INITIAL_URI, file.getUri());
        }

        return intent;
    }

    /**
     * Get the intent for the SAF file picker properly set up
     *
     * @return Intent for the SAF folder picker
     */
    private static Intent getFilePickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        HentoidApp.LifeCycleListener.disable(); // Prevents the app from displaying the PIN lock when returning from the SAF dialog
        return intent;
    }

    /**
     * Scan the given tree URI for a Hentoid folder
     * If none is found there, try to create one
     *
     * @param context         Context to be used
     * @param treeUri         Tree URI of the folder where to find or create the Hentoid folder
     * @param askScanExisting If true and an existing non-empty Hentoid folder is found, the user will be asked if he wants to import its contents
     * @param options         Import options - See ImportHelper.ImportOptions
     * @return Standardized result - see ImportHelper.Result
     */
    public static @ProcessFolderResult
    int setAndScanHentoidFolder(
            @NonNull final Context context,
            @NonNull final Uri treeUri,
            boolean askScanExisting,
            @Nullable final ImportOptions options) {

        // Persist I/O permissions
        Uri externalUri = null;
        if (!Preferences.getExternalLibraryUri().isEmpty())
            externalUri = Uri.parse(Preferences.getExternalLibraryUri());
        FileHelper.persistNewUriPermission(context, treeUri, externalUri);

        // Check if the folder exists
        DocumentFile docFile = DocumentFile.fromTreeUri(context, treeUri);
        if (null == docFile || !docFile.exists()) {
            Timber.e("Could not find the selected file %s", treeUri.toString());
            return ProcessFolderResult.KO_INVALID_FOLDER;
        }
        // Check if the folder is not the device's Download folder
        List<String> pathSegments = treeUri.getPathSegments();
        if (pathSegments.size() > 1) {
            String firstSegment = pathSegments.get(1).toLowerCase();
            firstSegment = firstSegment.split(File.separator)[0];
            if (firstSegment.startsWith("download") || firstSegment.startsWith("primary:download")) {
                Timber.e("Device's download folder detected : %s", treeUri.toString());
                return ProcessFolderResult.KO_DOWNLOAD_FOLDER;
            }
        }
        // Retrieve or create the Hentoid folder
        DocumentFile hentoidFolder = getOrCreateHentoidFolder(context, docFile);
        if (null == hentoidFolder) {
            Timber.e("Could not create Hentoid folder in folder %s", docFile.getUri().toString());
            return ProcessFolderResult.KO_CREATE_FAIL;
        }
        // Set the folder as the app's downloads folder
        int result = FileHelper.checkAndSetRootFolder(context, hentoidFolder);
        if (result < 0) {
            Timber.e("Could not set the selected root folder (error = %d) %s", result, hentoidFolder.getUri().toString());
            return ProcessFolderResult.KO_INVALID_FOLDER;
        }

        // Scan the folder for an existing library; start the import
        if (hasBooks(context, hentoidFolder)) {
            if (!askScanExisting) {
                runPrimaryImport(context, options);
                return ProcessFolderResult.OK_LIBRARY_DETECTED;
            } else return ProcessFolderResult.OK_LIBRARY_DETECTED_ASK;
        } else {
            // New library created - drop and recreate db (in case user is re-importing)
            CollectionDAO dao = new ObjectBoxDAO(context);
            try {
                dao.deleteAllInternalBooks(true);
            } finally {
                dao.cleanup();
            }
            return ProcessFolderResult.OK_EMPTY_FOLDER;
        }
    }

    /**
     * Scan the given tree URI for external books or Hentoid books
     *
     * @param context Context to be used
     * @param treeUri Tree URI of the folder where to find external books or Hentoid books
     * @return Standardized result - see ImportHelper.Result
     */
    public static @ProcessFolderResult
    int setAndScanExternalFolder(
            @NonNull final Context context,
            @NonNull final Uri treeUri) {

        // Persist I/O permissions
        Uri hentoidUri = null;
        if (!Preferences.getStorageUri().isEmpty())
            hentoidUri = Uri.parse(Preferences.getStorageUri());
        FileHelper.persistNewUriPermission(context, treeUri, hentoidUri);

        // Check if the folder exists
        DocumentFile docFile = DocumentFile.fromTreeUri(context, treeUri);
        if (null == docFile || !docFile.exists()) {
            Timber.e("Could not find the selected file %s", treeUri.toString());
            return ProcessFolderResult.KO_INVALID_FOLDER;
        }
        String folderUri = docFile.getUri().toString();
        if (folderUri.equalsIgnoreCase(Preferences.getStorageUri())) {
            Timber.w("Trying to set the app folder as the external library %s", treeUri.toString());
            return ProcessFolderResult.KO_APP_FOLDER;
        }
        // Set the folder as the app's external library folder
        Preferences.setExternalLibraryUri(folderUri);

        // Start the import
        if (runExternalImport(context)) return ProcessFolderResult.OK_LIBRARY_DETECTED;
        else return ProcessFolderResult.KO_ALREADY_RUNNING;
    }

    /**
     * Show the dialog to ask the user if he wants to import existing books
     *
     * @param context        Context to be used
     * @param cancelCallback Callback to run when the dialog is canceled
     */
    public static void showExistingLibraryDialog(
            @NonNull final Context context,
            @Nullable Runnable cancelCallback
    ) {
        new MaterialAlertDialogBuilder(context, ThemeHelper.getIdForCurrentTheme(context, R.style.Theme_Light_Dialog))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(R.string.contents_detected)
                .setPositiveButton(R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            runPrimaryImport(context, null);
                        })
                .setNegativeButton(R.string.no,
                        (dialog2, which) -> {
                            dialog2.dismiss();
                            if (cancelCallback != null) cancelCallback.run();
                        })
                .create()
                .show();
    }

    /**
     * Detect whether the given folder contains books or not
     * by counting the elements inside each site's download folder (but not its subfolders)
     * <p>
     * NB : this method works approximately because it doesn't try to count JSON files
     * However, findFilesRecursively -the method used by ImportService- is too slow on certain phones
     * and might cause freezes -> we stick to that approximate method for ImportActivity
     *
     * @param context Context to be used
     * @param folder  Folder to examine
     * @return True if the current Hentoid folder contains at least one book; false if not
     */
    private static boolean hasBooks(@NonNull final Context context, @NonNull final DocumentFile folder) {
        try (FileExplorer explorer = new FileExplorer(context, folder.getUri())) {
            List<DocumentFile> folders = explorer.listFolders(context, folder);

            // Filter out download subfolders among listed subfolders
            for (DocumentFile subfolder : folders) {
                String subfolderName = subfolder.getName();
                if (subfolderName != null) {
                    for (Site s : Site.values())
                        if (subfolderName.equalsIgnoreCase(s.getFolder())) {
                            // Search subfolders within identified download folders
                            // NB : for performance issues, we assume the mere presence of a subfolder inside a download folder means there's an existing book
                            if (explorer.hasFolders(subfolder)) return true;
                            break;
                        }
                }
            }
        } catch (IOException e) {
            Timber.w(e);
        }

        return false;
    }

    /**
     * Detect or create the Hentoid app folder inside the given base folder
     *
     * @param context    Context to be used
     * @param baseFolder Root folder to search for or create the Hentoid folder
     * @return DocumentFile representing the found or newly created Hentoid folder
     */
    @Nullable
    private static DocumentFile getOrCreateHentoidFolder(@NonNull final Context context, @NonNull final DocumentFile baseFolder) {
        DocumentFile targetFolder = getExistingHentoidDirFrom(context, baseFolder);
        if (targetFolder != null) return targetFolder;
        else return baseFolder.createDirectory(Consts.DEFAULT_PRIMARY_FOLDER);
    }

    /**
     * Try and detect if the Hentoid primary folder is, or is inside the given folder
     *
     * @param context Context to use
     * @param root    Folder to search the Hentoid folder in
     * @return Detected Hentoid folder; null if nothing detected
     */
    @Nullable
    public static DocumentFile getExistingHentoidDirFrom(@NonNull final Context context, @NonNull final DocumentFile root) {
        if (!root.exists() || !root.isDirectory() || null == root.getName()) return null;

        // Selected folder _is_ the Hentoid folder
        if (isHentoidFolderName(root.getName())) return root;

        // If not, look for it in its children
        List<DocumentFile> hentoidDirs = FileHelper.listFoldersFilter(context, root, hentoidFolderNames);
        if (!hentoidDirs.isEmpty()) return hentoidDirs.get(0);
        else return null;
    }

    /**
     * Run the import of the Hentoid primary library
     *
     * @param context Context to use
     * @param options Import options to use
     */
    private static void runPrimaryImport(
            @NonNull final Context context,
            @Nullable final ImportOptions options
    ) {
        ImportNotificationChannel.init(context);

        ImportData.Builder builder = new ImportData.Builder();
        builder.setRefreshRename(null != options && options.rename);
        builder.setRefreshCleanNoJson(null != options && options.cleanNoJson);
        builder.setRefreshCleanNoImages(null != options && options.cleanNoImages);

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueueUniqueWork(
                Integer.toString(R.id.import_service),
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(ImportWorker.class).setInputData(builder.getData()).addTag(WORK_CLOSEABLE).build());
    }

    /**
     * Run the import of the Hentoid external library
     *
     * @param context Context to use
     */
    private static boolean runExternalImport(
            @NonNull final Context context
    ) {
        if (ExternalImportWorker.isRunning(context)) return false;

        ImportNotificationChannel.init(context);

        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueueUniqueWork(
                Integer.toString(R.id.external_import_service),
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(ExternalImportWorker.class).addTag(WORK_CLOSEABLE).build());

        return true;
    }

    /**
     * Create a Content from the given folder
     *
     * @param context      Context to use
     * @param bookFolder   Folder to analyze
     * @param explorer     FileExplorer to use
     * @param parentNames  Names of parent folders, for formatting purposes; last of the list is the immediate parent of bookFolder
     * @param targetStatus Target status of the Content to create
     * @param dao          CollectionDAO to use
     * @param imageFiles   List of images to match files with; null if they have to be recreated from the files
     * @param jsonFile     JSON file to use, if one has been detected upstream; null if it has to be detected
     * @return Content created from the folder information and files
     */
    public static Content scanBookFolder(
            @NonNull final Context context,
            @NonNull final DocumentFile bookFolder,
            @NonNull final FileExplorer explorer,
            @NonNull final List<String> parentNames,
            @NonNull final StatusContent targetStatus,
            @NonNull final CollectionDAO dao,
            @Nullable final List<DocumentFile> imageFiles,
            @Nullable final DocumentFile jsonFile) {
        Timber.d(">>>> scan book folder %s", bookFolder.getUri());

        Content result = null;
        if (jsonFile != null) {
            try {
                JsonContent content = JsonHelper.jsonToObject(context, jsonFile, JsonContent.class);
                result = content.toEntity(dao);
                result.setJsonUri(jsonFile.getUri().toString());
            } catch (IOException | JsonDataException e) {
                Timber.w(e);
            }
        }
        if (null == result) {
            String title = cleanTitle(bookFolder.getName());
            // Tachiyomi downloads - include parent folder name as title
            if (title.toLowerCase().startsWith("chapter") && !parentNames.isEmpty()) {
                // Single chapter
                if ("chapter".equalsIgnoreCase(title))
                    title = cleanTitle(parentNames.get(parentNames.size() - 1));
                else // Multiple chapters
                    title = cleanTitle(parentNames.get(parentNames.size() - 1)) + " " + title;
            }

            result = new Content().setTitle(title);

            Site site = Site.NONE;
            if (!parentNames.isEmpty()) {
                for (String parent : parentNames)
                    for (Site s : Site.values())
                        if (parent.equalsIgnoreCase(s.getFolder())) {
                            site = s;
                            break;
                        }
            }
            result.setSite(site);
            result.setDownloadDate(bookFolder.lastModified());
            result.addAttributes(parentNamesAsTags(parentNames));
        }
        if (targetStatus.equals(StatusContent.EXTERNAL))
            result.addAttributes(newExternalAttribute());

        result.setStatus(targetStatus).setStorageUri(bookFolder.getUri().toString());
        if (0 == result.getDownloadDate()) result.setDownloadDate(Instant.now().toEpochMilli());
        List<ImageFile> images = new ArrayList<>();
        scanImages(context, bookFolder, explorer, targetStatus, false, images, imageFiles);
        boolean coverExists = Stream.of(images).anyMatch(ImageFile::isCover);
        if (!coverExists) createCover(images);
        result.setImageFiles(images);
        if (0 == result.getQtyPages()) {
            int countUnreadable = (int) Stream.of(images).filterNot(ImageFile::isReadable).count();
            result.setQtyPages(images.size() - countUnreadable); // Minus unreadable pages (cover thumb)
        }
        result.computeSize();
        return result;
    }

    private static String cleanTitle(String s) {
        String result = StringHelper.protect(s);
        result = result.replace("_", " ");
        // Remove expressions between []'s
        result = result.replaceAll("\\[[^(\\[\\])]*\\]", "");
        return result.trim();
    }

    /**
     * Create a Content from the given parent folder and chapter subfolders, merging all "chapters" into one content
     *
     * @param context        Context to use
     * @param parent         Parent folder to take into account for title and download date
     * @param chapterFolders Folders containing chapters to scan for images
     * @param explorer       FileExplorer to use
     * @param parentNames    Names of parent folders, for formatting purposes; last of the list is the immediate parent of parent
     * @param dao            CollectionDAO to use
     * @param jsonFile       JSON file to use, if one has been detected upstream; null if it needs to be detected
     * @return Content created from the folder information, subfolders and files
     */
    public static Content scanChapterFolders(
            @NonNull final Context context,
            @NonNull final DocumentFile parent,
            @NonNull final List<DocumentFile> chapterFolders,
            @NonNull final FileExplorer explorer,
            @NonNull final List<String> parentNames,
            @NonNull final CollectionDAO dao,
            @Nullable final DocumentFile jsonFile) {
        Timber.d(">>>> scan chapter folder %s", parent.getUri());

        Content result = null;
        if (jsonFile != null) {
            try {
                JsonContent content = JsonHelper.jsonToObject(context, jsonFile, JsonContent.class);
                result = content.toEntity(dao);
                result.setJsonUri(jsonFile.getUri().toString());
            } catch (IOException | JsonDataException e) {
                Timber.w(e);
            }
        }
        if (null == result) {
            result = new Content().setSite(Site.NONE).setTitle((null == parent.getName()) ? "" : parent.getName()).setUrl("");
            result.setDownloadDate(parent.lastModified());
            result.addAttributes(parentNamesAsTags(parentNames));
        }
        result.addAttributes(newExternalAttribute());

        result.setStatus(StatusContent.EXTERNAL).setStorageUri(parent.getUri().toString());
        if (0 == result.getDownloadDate()) result.setDownloadDate(Instant.now().toEpochMilli());
        List<ImageFile> images = new ArrayList<>();
        // Scan pages across all subfolders
        for (DocumentFile chapterFolder : chapterFolders)
            scanImages(context, chapterFolder, explorer, StatusContent.EXTERNAL, true, images, null);
        boolean coverExists = Stream.of(images).anyMatch(ImageFile::isCover);
        if (!coverExists) createCover(images);
        result.setImageFiles(images);
        if (0 == result.getQtyPages()) {
            int countUnreadable = (int) Stream.of(images).filterNot(ImageFile::isReadable).count();
            result.setQtyPages(images.size() - countUnreadable); // Minus unreadable pages (cover thumb)
        }
        result.computeSize();
        return result;
    }

    /**
     * Populate on enrich the given image list according to the contents of the given folder
     *
     * @param context                Context to use
     * @param bookFolder             Folder to scan image files from
     * @param explorer               FileExplorer to use
     * @param targetStatus           Target status of the detected images
     * @param addFolderNametoImgName True if the parent folder name has to be added to detected images name
     * @param images                 Image list to populate or enrich
     * @param imageFiles             Image file list, if already listed upstream; null if it needs to be listed
     */
    private static void scanImages(
            @NonNull final Context context,
            @NonNull final DocumentFile bookFolder,
            @NonNull final FileExplorer explorer,
            @NonNull final StatusContent targetStatus,
            boolean addFolderNametoImgName,
            @NonNull final List<ImageFile> images,
            @Nullable List<DocumentFile> imageFiles) {
        int order = (images.isEmpty()) ? 0 : Stream.of(images).map(ImageFile::getOrder).max(Integer::compareTo).get();
        String folderName = (null == bookFolder.getName()) ? "" : bookFolder.getName();
        if (null == imageFiles)
            imageFiles = explorer.listFiles(context, bookFolder, ImageHelper.getImageNamesFilter());

        String namePrefix = "";
        if (addFolderNametoImgName) namePrefix = folderName + "-";

        images.addAll(ContentHelper.createImageListFromFiles(imageFiles, targetStatus, order, namePrefix));
    }

    /**
     * Create a cover and add it to the given image list
     *
     * @param images Image list to generate the cover from (and add it to)
     */
    public static void createCover(@NonNull final List<ImageFile> images) {
        if (!images.isEmpty()) {
            // Set the 1st element as cover
            images.get(0).setIsCover(true);
        }
    }

    /**
     * Return a list with the attribute flagging a book as external
     *
     * @return List with the attribute flagging a book as external
     */
    private static List<Attribute> newExternalAttribute() {
        return Stream.of(new Attribute(AttributeType.TAG, EXTERNAL_LIB_TAG, EXTERNAL_LIB_TAG, Site.NONE)).toList();
    }

    /**
     * Remove the attribute flagging the given book as external, if it exists
     *
     * @param content Content to remove the "external" attribute flag, if it has been set
     */
    public static void removeExternalAttributes(@NonNull final Content content) {
        content.putAttributes(Stream.of(content.getAttributes()).filterNot(a -> a.getName().equalsIgnoreCase(EXTERNAL_LIB_TAG)).toList());
        if (content.getStatus().equals(StatusContent.EXTERNAL))
            content.setStatus(StatusContent.DOWNLOADED);
    }

    /**
     * Convert the given list of parent folder names into a list of Attribute of type TAG
     *
     * @param parentNames List of parent folder names
     * @return Representation of parent folder names as Attributes of type TAG
     */
    private static AttributeMap parentNamesAsTags(@NonNull final List<String> parentNames) {
        AttributeMap result = new AttributeMap();
        // Don't include the very first one, it's the name of the root folder of the library
        if (parentNames.size() > 1) {
            for (int i = 1; i < parentNames.size(); i++)
                result.add(new Attribute(AttributeType.TAG, parentNames.get(i), parentNames.get(i), Site.NONE));
        }
        return result;
    }

    /**
     * Create Content from every archive inside the given subfolders
     *
     * @param context     Context to use
     * @param subFolders  Subfolders to scan for archives
     * @param explorer    FileExplorer to use
     * @param parentNames Names of parent folders, for formatting purposes; last of the list is the immediate parent of the scanned folders
     * @param dao         CollectionDAO to use
     * @return List of Content created from every archive inside the given subfolders
     */
    public static List<Content> scanForArchives(
            @NonNull final Context context,
            @NonNull final List<DocumentFile> subFolders,
            @NonNull final FileExplorer explorer,
            @NonNull final List<String> parentNames,
            @NonNull final CollectionDAO dao) {
        List<Content> result = new ArrayList<>();

        for (DocumentFile subfolder : subFolders) {
            List<DocumentFile> files = explorer.listFiles(context, subfolder, null);

            List<DocumentFile> archives = new ArrayList<>();
            List<DocumentFile> jsons = new ArrayList<>();

            // Look for the interesting stuff
            for (DocumentFile file : files)
                if (file.getName() != null) {
                    if (ArchiveHelper.getArchiveNamesFilter().accept(file.getName()))
                        archives.add(file);
                    else if (JsonHelper.getJsonNamesFilter().accept(file.getName()))
                        jsons.add(file);
                }

            for (DocumentFile archive : archives) {
                DocumentFile json = getFileWithName(jsons, archive.getName());
                Content c = scanArchive(context, subfolder, archive, parentNames, StatusContent.EXTERNAL, dao, json);
                if (!c.getStatus().equals(StatusContent.IGNORED))
                    result.add(c);
            }
        }

        return result;
    }

    /**
     * Create a content from the given archive
     * NB : any returned Content with the IGNORED status shouldn't be taken into account by the caller
     *
     * @param context      Context to use
     * @param parentFolder Parent folder where the archive is located
     * @param archive      Archive file to scan
     * @param parentNames  Names of parent folders, for formatting purposes; last of the list is the immediate parent of parentFolder
     * @param targetStatus Target status of the Content to create
     * @param dao          CollectionDAO to use
     * @param jsonFile     JSON file to use, if one has been detected upstream; null if it has to be detected
     * @return Content created from the given archive
     */
    public static Content scanArchive(
            @NonNull final Context context,
            @NonNull final DocumentFile parentFolder,
            @NonNull final DocumentFile archive,
            @NonNull final List<String> parentNames,
            @NonNull final StatusContent targetStatus,
            @NonNull final CollectionDAO dao,
            @Nullable final DocumentFile jsonFile) {

        Content result = null;
        if (jsonFile != null) {
            try {
                JsonContent content = JsonHelper.jsonToObject(context, jsonFile, JsonContent.class);
                result = content.toEntity(dao);
                result.setJsonUri(jsonFile.getUri().toString());
            } catch (IOException | JsonDataException e) {
                Timber.w(e);
            }
        }

        List<ArchiveHelper.ArchiveEntry> entries = Collections.emptyList();
        try {
            entries = ArchiveHelper.getArchiveEntries(context, archive);
        } catch (Exception e) {
            Timber.w(e);
        }

        List<ArchiveHelper.ArchiveEntry> imageEntries = Stream.of(entries)
                .filter(s -> ImageHelper.isImageExtensionSupported(FileHelper.getExtension(s.path)))
                .toList();

        if (imageEntries.isEmpty()) return new Content().setStatus(StatusContent.IGNORED);

        List<ImageFile> images = ContentHelper.createImageListFromArchiveEntries(archive.getUri(), imageEntries, targetStatus, 0, "");
        boolean coverExists = Stream.of(images).anyMatch(ImageFile::isCover);
        if (!coverExists) createCover(images);

        // Create content envelope
        if (null == result) {
            result = new Content().setSite(Site.NONE).setTitle((null == archive.getName()) ? "" : FileHelper.getFileNameWithoutExtension(archive.getName())).setUrl("");
            result.setDownloadDate(archive.lastModified());
            result.addAttributes(parentNamesAsTags(parentNames));
            result.addAttributes(newExternalAttribute());
        }
        result.setStatus(targetStatus).setStorageUri(archive.getUri().toString()); // Here storage URI is a file URI, not a folder
        if (0 == result.getDownloadDate()) result.setDownloadDate(Instant.now().toEpochMilli());
        result.setArchiveLocationUri(parentFolder.getUri().toString());

        result.setImageFiles(images);
        if (0 == result.getQtyPages()) {
            int countUnreadable = (int) Stream.of(images).filterNot(ImageFile::isReadable).count();
            result.setQtyPages(images.size() - countUnreadable); // Minus unreadable pages (cover thumb)
        }
        result.computeSize();
        // e.g. when the ZIP table doesn't contain any size entry
        if (result.getSize() <= 0) result.forceSize(archive.length());

        return result;
    }

    /**
     * Add the given list of bookmarks to the DB, handling duplicates
     * Bookmarks that have the same URL as existing ones won't be imported
     *
     * @param dao       CollectionDAO to use
     * @param bookmarks List of bookmarks to add to the existing bookmarks
     * @return Quantity of new integrated bookmarks
     */
    public static int importBookmarks(@NonNull final CollectionDAO dao, List<SiteBookmark> bookmarks) {
        // Don't import bookmarks that have the same URL as existing ones
        Set<SiteBookmark> existingBookmarkUrls = new HashSet<>(dao.selectAllBookmarks());
        List<SiteBookmark> bookmarksToImport = Stream.of(new HashSet<>(bookmarks)).filterNot(existingBookmarkUrls::contains).toList();
        dao.insertBookmarks(bookmarksToImport);
        return bookmarksToImport.size();
    }

    /**
     * Return the first file with the given name (without extension) among the given list of files
     *
     * @param files List of files to search into
     * @param name  File name to detect
     * @return First file with the given name among the given list, or null if none matches the given name
     */
    @Nullable
    public static DocumentFile getFileWithName(List<DocumentFile> files, @Nullable String name) {
        if (null == name) return null;

        String targetBareName = FileHelper.getFileNameWithoutExtension(name);
        Optional<DocumentFile> file = Stream.of(files).filter(f -> (f.getName() != null && FileHelper.getFileNameWithoutExtension(f.getName()).equalsIgnoreCase(targetBareName))).findFirst();
        return file.orElse(null);
    }
}
