package me.devsaki.hentoid.util;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImportActivityBundle;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel;
import me.devsaki.hentoid.services.ImportService;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.O;
import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;

public class ImportHelper {

    private ImportHelper() {
        throw new IllegalStateException("Utility class");
    }


    private static final int RQST_STORAGE_PERMISSION = 3;

    @IntDef({Result.OK_EMPTY_FOLDER, Result.OK_LIBRARY_DETECTED, Result.CANCELED, Result.INVALID_FOLDER, Result.OTHER})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Result {
        int OK_EMPTY_FOLDER = 0;
        int OK_LIBRARY_DETECTED = 1;
        int CANCELED = 2;
        int INVALID_FOLDER = 3;
        int OTHER = 4;
    }

    private static final FileHelper.NameFilter hentoidFolderNames = displayName -> displayName.equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY)
            || displayName.equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY_OLD);

    public static class ImportOptions {
        public boolean rename;
        public boolean cleanAbsent;
        public boolean cleanNoImages;
        public boolean cleanUnreadable;
    }


    public static void openFolderPicker(@NonNull final Fragment caller) {
        Intent intent = getFolderPickerIntent(caller.requireContext());
        caller.startActivityForResult(intent, RQST_STORAGE_PERMISSION);
    }

    public static void openFolderPicker(@NonNull final Activity caller) {
        Intent intent = getFolderPickerIntent(caller.getParent());
        caller.startActivityForResult(intent, RQST_STORAGE_PERMISSION);
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
            DocumentFile file = DocumentFile.fromTreeUri(context, Uri.parse(Preferences.getStorageUri()));
            if (file != null)
                intent.putExtra(EXTRA_INITIAL_URI, file.getUri());
        }

        HentoidApp.LifeCycleListener.disable(); // Prevents the app from displaying the PIN lock when returning from the SAF dialog
        return intent;
    }

    // Return from SAF picker
    // TODO - check if the processing can be done on a separate thread to avoid freezing while displaying the SAF dialog (especially during hasBooks())
    // TODO - just after a successful import, when the SAF dialog is reopened and another folder is chosen, that method is never called <-- fixed recently ?
    public static @Result
    int processPickerResult(
            @NonNull final Context context,
            int requestCode,
            int resultCode,
            final Intent data,
            @Nullable Runnable cancelCallback,
            @Nullable final ImportOptions options) {
        HentoidApp.LifeCycleListener.enable(); // Restores autolock on app going to background

        // Return from the SAF picker
        if (requestCode == RQST_STORAGE_PERMISSION && resultCode == Activity.RESULT_OK) {
            // Get Uri from Storage Access Framework
            Uri treeUri = data.getData();
            if (treeUri != null)
                return setAndScanFolder(context, treeUri, true, cancelCallback, options);
            else return Result.INVALID_FOLDER;
        } else if (resultCode == Activity.RESULT_CANCELED) {
            return Result.CANCELED;
        } else return Result.OTHER;
    }

    public static @Result
    int setAndScanFolder(
            @NonNull final Context context,
            @NonNull final Uri treeUri,
            boolean askScanExisting,
            @Nullable Runnable cancelCallback,
            @Nullable final ImportOptions options) {

        boolean isUriPermissionPeristed = false;
        ContentResolver contentResolver = context.getContentResolver();
        String treeUriId = DocumentsContract.getTreeDocumentId(treeUri);

        for (UriPermission p : contentResolver.getPersistedUriPermissions()) {
            if (DocumentsContract.getTreeDocumentId(p.getUri()).equals(treeUriId)) {
                isUriPermissionPeristed = true;
                break;
            }
        }

        if (!isUriPermissionPeristed) {
            // Release previous access permissions, if different than the new one
            FileHelper.revokePreviousPermissions(contentResolver, treeUri);
            // Persist new access permission
            contentResolver.takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        DocumentFile docFile = DocumentFile.fromTreeUri(context, treeUri);
        if (null == docFile || !docFile.exists()) {
            Timber.e("Could not find the selected file %s", treeUri.toString());
            return Result.INVALID_FOLDER;
        }

        DocumentFile hentoidFolder = addHentoidFolder(context, docFile);
        if (!FileHelper.checkAndSetRootFolder(context, hentoidFolder, true))
            return Result.INVALID_FOLDER;

        if (hasBooks(context)) {
            if (!askScanExisting) runImport(context, options);
            else
                new MaterialAlertDialogBuilder(context, ThemeHelper.getIdForCurrentTheme(context, R.style.Theme_Light_Dialog))
                        .setIcon(R.drawable.ic_warning)
                        .setCancelable(false)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.contents_detected)
                        .setPositiveButton(android.R.string.yes,
                                (dialog1, which) -> {
                                    dialog1.dismiss();
                                    runImport(context, options);
                                })
                        .setNegativeButton(android.R.string.no,
                                (dialog2, which) -> {
                                    dialog2.dismiss();
                                    if (cancelCallback != null) cancelCallback.run();
                                })
                        .create()
                        .show();

            return Result.OK_LIBRARY_DETECTED;
        } else {
            // New library created - drop and recreate db (in case user is re-importing)
            new ObjectBoxDAO(context).deleteAllBooks();
            return Result.OK_EMPTY_FOLDER;
        }
    }

    // Count the elements inside each site's download folder (but not its subfolders)
    //
    // NB : this method works approximately because it doesn't try to count JSON files
    // However, findFilesRecursively -the method used by ImportService- is too slow on certain phones
    // and might cause freezes -> we stick to that approximate method for ImportActivity
    private static boolean hasBooks(@NonNull final Context context) {
        List<DocumentFile> downloadDirs = new ArrayList<>();
        for (Site s : Site.values())
            downloadDirs.add(ContentHelper.getOrCreateSiteDownloadDir(context, s));

        for (DocumentFile downloadDir : downloadDirs) {
            List<DocumentFile> contentFiles = FileHelper.listFolders(context, downloadDir);
            if (!contentFiles.isEmpty()) return true;
        }

        return false;
    }

    private static DocumentFile addHentoidFolder(@NonNull final Context context, @NonNull final DocumentFile baseFolder) {
        String folderName = baseFolder.getName();
        if (null == folderName) folderName = "";

        // Don't create a .Hentoid subfolder inside the .Hentoid (or Hentoid) folder the user just selected...
        if (!hentoidFolderNames.accept(folderName)) {
            DocumentFile targetFolder = getExistingHentoidDirFrom(context, baseFolder);

            // If not, create one
            if (targetFolder.getUri().equals(baseFolder.getUri()))
                return targetFolder.createDirectory(Consts.DEFAULT_LOCAL_DIRECTORY);
            else return targetFolder;
        }
        return baseFolder;
    }

    // Try and detect any ".Hentoid" or "Hentoid" folder inside the selected folder
    private static DocumentFile getExistingHentoidDirFrom(@NonNull final Context context, @NonNull final DocumentFile root) {
        if (!root.exists() || !root.isDirectory() || null == root.getName()) return root;

        // Selected folder _is_ the Hentoid folder
        if (hentoidFolderNames.accept(root.getName())) return root;

        // If not, look for it in its children
        List<DocumentFile> hentoidDirs = FileHelper.listFolders(context, root, hentoidFolderNames);
        if (!hentoidDirs.isEmpty()) return hentoidDirs.get(0);
        else return root;
    }

    private static void cleanUpDB(@NonNull final Context context) {
        CollectionDAO dao = new ObjectBoxDAO(context);
        dao.deleteAllBooks();
    }

    private static void runImport(
            @NonNull final Context context,
            @Nullable final ImportOptions options
    ) {
        // Prior Library found, drop and recreate db
        cleanUpDB(context);

        ImportNotificationChannel.init(context);
        Intent intent = ImportService.makeIntent(context);

        ImportActivityBundle.Builder builder = new ImportActivityBundle.Builder();
        builder.setRefreshRename(null != options && options.rename);
        builder.setRefreshCleanAbsent(null != options && options.cleanAbsent);
        builder.setRefreshCleanNoImages(null != options && options.cleanNoImages);
        builder.setRefreshCleanUnreadable(null != options && options.cleanUnreadable);
        intent.putExtras(builder.getBundle());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
