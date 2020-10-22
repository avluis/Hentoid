package me.devsaki.hentoid.util;

import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImportActivityBundle;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.database.domains.ImageFile;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.json.JsonContent;
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel;
import me.devsaki.hentoid.services.ExternalImportService;
import me.devsaki.hentoid.services.ImportService;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.O;
import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;

public class ImportHelper {

    private ImportHelper() {
        throw new IllegalStateException("Utility class");
    }


    private static final int RQST_STORAGE_PERMISSION_HENTOID = 3;
    private static final int RQST_STORAGE_PERMISSION_EXTERNAL = 4;

    @IntDef({Result.OK_EMPTY_FOLDER, Result.OK_LIBRARY_DETECTED, Result.OK_LIBRARY_DETECTED_ASK, Result.CANCELED, Result.INVALID_FOLDER, Result.CREATE_FAIL, Result.OTHER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Result {
        int OK_EMPTY_FOLDER = 0;
        int OK_LIBRARY_DETECTED = 1;
        int OK_LIBRARY_DETECTED_ASK = 2;
        int CANCELED = 3;
        int INVALID_FOLDER = 4;
        int CREATE_FAIL = 5;
        int OTHER = 6;
    }

    private static final FileHelper.NameFilter hentoidFolderNames = displayName -> displayName.equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY)
            || displayName.equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY_OLD);

    public static class ImportOptions {
        public boolean rename;
        public boolean cleanAbsent;
        public boolean cleanNoImages;
    }

    public static boolean isHentoidFolderName(@NonNull final String folderName) {
        return hentoidFolderNames.accept(folderName);
    }

    public static void openFolderPicker(@NonNull final Fragment caller, boolean isExternal) {
        Intent intent = getFolderPickerIntent(caller.requireContext());
        caller.startActivityForResult(intent, isExternal ? RQST_STORAGE_PERMISSION_EXTERNAL : RQST_STORAGE_PERMISSION_HENTOID);
    }

    public static void openFolderPicker(@NonNull final Activity caller, boolean isExternal) {
        Intent intent = getFolderPickerIntent(caller.getParent());
        caller.startActivityForResult(intent, isExternal ? RQST_STORAGE_PERMISSION_EXTERNAL : RQST_STORAGE_PERMISSION_HENTOID);
    }

    private static Intent getFolderPickerIntent(@NonNull final Context context) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.putExtra(DocumentsContract.EXTRA_PROMPT, "Allow Write Permission");
        }
        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        // Start the SAF at the specified location
        if (Build.VERSION.SDK_INT >= O && !Preferences.getStorageUri().isEmpty()) {
            DocumentFile file = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
            if (file != null)
                intent.putExtra(EXTRA_INITIAL_URI, file.getUri());
        }

        HentoidApp.LifeCycleListener.disable(); // Prevents the app from displaying the PIN lock when returning from the SAF dialog
        return intent;
    }

    // Return from SAF picker
    public static @Result
    int processPickerResult(
            @NonNull final Context context,
            int requestCode,
            int resultCode,
            final Intent data) {
        HentoidApp.LifeCycleListener.enable(); // Restores autolock on app going to background

        // Return from the SAF picker
        if ((requestCode == RQST_STORAGE_PERMISSION_HENTOID || requestCode == RQST_STORAGE_PERMISSION_EXTERNAL) && resultCode == Activity.RESULT_OK) {
            // Get Uri from Storage Access Framework
            Uri treeUri = data.getData();
            if (treeUri != null) {
                if (requestCode == RQST_STORAGE_PERMISSION_EXTERNAL)
                    return setAndScanExternalFolder(context, treeUri);
                else
                    return setAndScanHentoidFolder(context, treeUri, true, null);
            } else return Result.INVALID_FOLDER;
        } else if (resultCode == Activity.RESULT_CANCELED) {
            return Result.CANCELED;
        } else return Result.OTHER;
    }

    public static @Result
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
            return Result.INVALID_FOLDER;
        }
        // Retrieve or create the Hentoid folder
        DocumentFile hentoidFolder = addHentoidFolder(context, docFile);
        if (null == hentoidFolder) {
            Timber.e("Could not create Hentoid folder in root %s", docFile.getUri().toString());
            return Result.CREATE_FAIL;
        }
        // Set the folder as the app's downloads folder
        if (!FileHelper.checkAndSetRootFolder(context, hentoidFolder, true)) {
            Timber.e("Could not set the selected root folder %s", hentoidFolder.getUri().toString());
            return Result.INVALID_FOLDER;
        }

        // Scan the folder for an existing library; start the import
        if (hasBooks(context)) {
            if (!askScanExisting) {
                runHentoidImport(context, options);
                return Result.OK_LIBRARY_DETECTED;
            } else return Result.OK_LIBRARY_DETECTED_ASK;
        } else {
            // New library created - drop and recreate db (in case user is re-importing)
            new ObjectBoxDAO(context).deleteAllInternalBooks(true);
            return Result.OK_EMPTY_FOLDER;
        }
    }

    public static @Result
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
            return Result.INVALID_FOLDER;
        }
        // Set the folder as the app's external library folder
        Preferences.setExternalLibraryUri(docFile.getUri().toString());

        // Start the import
        runExternalImport(context);
        return Result.OK_LIBRARY_DETECTED;
    }

    public static void showExistingLibraryDialog(
            @NonNull final Context context,
            @Nullable Runnable cancelCallback
    ) {
        new MaterialAlertDialogBuilder(context, ThemeHelper.getIdForCurrentTheme(context, R.style.Theme_Light_Dialog))
                .setIcon(R.drawable.ic_warning)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(R.string.contents_detected)
                .setPositiveButton(android.R.string.yes,
                        (dialog1, which) -> {
                            dialog1.dismiss();
                            runHentoidImport(context, null);
                        })
                .setNegativeButton(android.R.string.no,
                        (dialog2, which) -> {
                            dialog2.dismiss();
                            if (cancelCallback != null) cancelCallback.run();
                        })
                .create()
                .show();
    }

    // Count the elements inside each site's download folder (but not its subfolders)
    //
    // NB : this method works approximately because it doesn't try to count JSON files
    // However, findFilesRecursively -the method used by ImportService- is too slow on certain phones
    // and might cause freezes -> we stick to that approximate method for ImportActivity
    private static boolean hasBooks(@NonNull final Context context) {
        List<DocumentFile> downloadDirs = new ArrayList<>();

        ContentProviderClient client = context.getContentResolver().acquireContentProviderClient(Uri.parse(Preferences.getStorageUri()));
        if (null == client) return false;
        try {
            for (Site s : Site.values()) {
                DocumentFile downloadDir = ContentHelper.getOrCreateSiteDownloadDir(context, client, s);
                if (downloadDir != null) downloadDirs.add(downloadDir);
            }

            for (DocumentFile downloadDir : downloadDirs) {
                List<DocumentFile> contentFiles = FileHelper.listFolders(context, downloadDir, client);
                if (!contentFiles.isEmpty()) return true;
            }
        } finally {
            // ContentProviderClient.close only available on API level 24+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                client.close();
            else
                client.release();
        }

        return false;
    }

    @Nullable
    private static DocumentFile addHentoidFolder(@NonNull final Context context, @NonNull final DocumentFile baseFolder) {
        String folderName = baseFolder.getName();
        if (null == folderName) folderName = "";

        // Don't create a .Hentoid subfolder inside the .Hentoid (or Hentoid) folder the user just selected...
        if (!isHentoidFolderName(folderName)) {
            DocumentFile targetFolder = getExistingHentoidDirFrom(context, baseFolder);

            // If not, create one
            if (targetFolder.getUri().equals(baseFolder.getUri()))
                return targetFolder.createDirectory(Consts.DEFAULT_LOCAL_DIRECTORY);
            else return targetFolder;
        }
        return baseFolder;
    }

    // Try and detect any ".Hentoid" or "Hentoid" folder inside the selected folder
    public static DocumentFile getExistingHentoidDirFrom(@NonNull final Context context, @NonNull final DocumentFile root) {
        if (!root.exists() || !root.isDirectory() || null == root.getName()) return root;

        // Selected folder _is_ the Hentoid folder
        if (isHentoidFolderName(root.getName())) return root;

        // If not, look for it in its children
        List<DocumentFile> hentoidDirs = FileHelper.listFoldersFilter(context, root, hentoidFolderNames);
        if (!hentoidDirs.isEmpty()) return hentoidDirs.get(0);
        else return root;
    }

    private static void runHentoidImport(
            @NonNull final Context context,
            @Nullable final ImportOptions options
    ) {
        ImportNotificationChannel.init(context);
        Intent intent = ImportService.makeIntent(context);

        ImportActivityBundle.Builder builder = new ImportActivityBundle.Builder();
        builder.setRefreshRename(null != options && options.rename);
        builder.setRefreshCleanAbsent(null != options && options.cleanAbsent);
        builder.setRefreshCleanNoImages(null != options && options.cleanNoImages);
        intent.putExtras(builder.getBundle());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static void runExternalImport(
            @NonNull final Context context
    ) {
        ImportNotificationChannel.init(context);
        Intent intent = ExternalImportService.makeIntent(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static Content scanBookFolder(
            @NonNull final Context context,
            @NonNull final DocumentFile bookFolder,
            @NonNull final ContentProviderClient client,
            @NonNull final List<String> parentNames,
            @NonNull final StatusContent targetStatus,
            @Nullable final List<DocumentFile> imageFiles,
            @Nullable final DocumentFile jsonFile) {
        Timber.d(">>>> scan book folder %s", bookFolder.getUri());

        Content result = null;
        if (jsonFile != null) {
            try {
                JsonContent content = JsonHelper.jsonToObject(context, jsonFile, JsonContent.class);
                result = content.toEntity();
                result.setJsonUri(jsonFile.getUri().toString());
            } catch (IOException ioe) {
                Timber.w(ioe);
            }
        }
        if (null == result) {
            String title = bookFolder.getName();
            if (null == title) title = "";
            title = title.replace("_", " ");
            // Remove expressions between []'s
            title = title.replaceAll("\\[[^(\\[\\])]*\\]", "");
            title = title.trim();
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
            result.addAttributes(parentNamesAsTags(parentNames, targetStatus.equals(StatusContent.EXTERNAL)));
        }

        result.setStatus(targetStatus).setStorageUri(bookFolder.getUri().toString());
        List<ImageFile> images = new ArrayList<>();
        scanImages(context, bookFolder, client, targetStatus, false, images, imageFiles);
        boolean coverExists = Stream.of(images).anyMatch(ImageFile::isCover);
        if (!coverExists) createCover(images);
        result.setImageFiles(images);
        if (0 == result.getQtyPages())
            result.setQtyPages(images.size() - 1); // Minus the cover
        result.computeSize();
        return result;
    }

    public static Content scanChapterFolders(
            @NonNull final Context context,
            @NonNull final DocumentFile parent,
            @NonNull final List<DocumentFile> chapterFolders,
            @NonNull final ContentProviderClient client,
            @NonNull final List<String> parentNames,
            @Nullable final DocumentFile jsonFile) {
        Timber.d(">>>> scan chapter folder %s", parent.getUri());

        Content result = null;
        if (jsonFile != null) {
            try {
                JsonContent content = JsonHelper.jsonToObject(context, jsonFile, JsonContent.class);
                result = content.toEntity();
                result.setJsonUri(jsonFile.getUri().toString());
            } catch (IOException ioe) {
                Timber.w(ioe);
            }
        }
        if (null == result) {
            result = new Content().setSite(Site.NONE).setTitle((null == parent.getName()) ? "" : parent.getName()).setUrl("");
            result.setDownloadDate(parent.lastModified());
            result.addAttributes(parentNamesAsTags(parentNames, true));
        }

        result.setStatus(StatusContent.EXTERNAL).setStorageUri(parent.getUri().toString());
        List<ImageFile> images = new ArrayList<>();
        // Scan pages across all subfolders
        for (DocumentFile chapterFolder : chapterFolders)
            scanImages(context, chapterFolder, client, StatusContent.EXTERNAL, true, images, null);
        boolean coverExists = Stream.of(images).anyMatch(ImageFile::isCover);
        if (!coverExists) createCover(images);
        result.setImageFiles(images);
        if (0 == result.getQtyPages())
            result.setQtyPages(images.size() - 1); // Minus the cover
        result.computeSize();
        return result;
    }

    private static void scanImages(
            @NonNull final Context context,
            @NonNull final DocumentFile bookFolder,
            @NonNull final ContentProviderClient client,
            @NonNull final StatusContent targetStatus,
            boolean addFolderNametoImgName,
            @NonNull final List<ImageFile> images,
            @Nullable List<DocumentFile> imageFiles) {
        int order = (images.isEmpty()) ? 0 : Stream.of(images).map(ImageFile::getOrder).max(Integer::compareTo).get();
        String folderName = (null == bookFolder.getName()) ? "" : bookFolder.getName();
        if (null == imageFiles)
            imageFiles = FileHelper.listFiles(context, bookFolder, client, ImageHelper.getImageNamesFilter());

        String namePrefix = "";
        if (addFolderNametoImgName) namePrefix = folderName + "-";

        images.addAll(ContentHelper.createImageListFromFiles(imageFiles, targetStatus, order, namePrefix));
    }

    private static void createCover(@NonNull final List<ImageFile> images) {
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

    private static AttributeMap parentNamesAsTags(@NonNull final List<String> parentNames, boolean addExternalTag) {
        AttributeMap result = new AttributeMap();
        // Don't include the very first one, it's the name of the root folder of the library
        if (parentNames.size() > 1) {
            for (int i = 1; i < parentNames.size(); i++)
                result.add(new Attribute(AttributeType.TAG, parentNames.get(i), parentNames.get(i), Site.NONE));
        }
        // Add a generic tag to filter external library books
        if (addExternalTag)
            result.add(new Attribute(AttributeType.TAG, "external-library", "external-library", Site.NONE));
        return result;
    }
}
