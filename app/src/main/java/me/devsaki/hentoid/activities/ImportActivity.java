package me.devsaki.hentoid.activities;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.abstracts.BaseActivity;
import me.devsaki.hentoid.activities.bundles.ImportActivityBundle;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.fragments.import_.KitkatRootFolderFragment;
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel;
import me.devsaki.hentoid.services.ImportService;
import me.devsaki.hentoid.util.Consts;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.PermissionUtil;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastUtil;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.O;
import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;

/**
 * Created by avluis on 04/02/2016.
 * Library Directory selection and Import Activity
 */
public class ImportActivity extends BaseActivity implements KitkatRootFolderFragment.Parent {

    // Instance state keys
    private static final String CURRENT_DIR = "currentDir";
    private static final String PREV_DIR = "prevDir";
    private static final String RESTART_ON_EXIT = "restartOnExit";
    private static final String CALLED_BY_PREFS = "calledByPrefs";
    private static final String USE_DEFAULT_FOLDER = "useDefaultFolder";
    private static final String REFRESH_OPTIONS = "refreshOptions";


    private File currentRootDir;
    private File prevRootDir;
    private boolean restartOnExit = false;              // True if app has to be restarted when exiting the activity
    private boolean calledByPrefs = false;              // True if activity has been called by PrefsActivity
    private boolean useDefaultFolder = false;           // True if activity has been called by IntroActivity and user has selected default storage
    private boolean isRefresh = false;                  // True if user has asked for a collection refresh
    private boolean isRename = false;                   // True if user has asked for a collection renaming
    private boolean isCleanAbsent = false;              // True if user has asked for the cleanup of folders with no JSONs
    private boolean isCleanNoImages = false;            // True if user has asked for the cleanup of folders with no images
    private boolean isCleanUnreadable = false;          // True if user has asked for the cleanup of folders with unreadable JSONs

    private ProgressDialog progressDialog; // TODO - to replace because it's deprecated


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View contentView = new View(this, null, R.style.ImportTheme);
        setContentView(contentView);

        Intent intent = getIntent();
        if (intent != null) {
            if (intent.getAction() != null) {
                switch (intent.getAction()) {
                    case Intent.ACTION_APPLICATION_PREFERENCES:
                        Timber.d("Running from prefs screen.");
                        calledByPrefs = true;
                        break;
                    case Intent.ACTION_GET_CONTENT:
                        Timber.d("Importing default directory.");
                        useDefaultFolder = true;
                        break;
                    default:
                        Timber.d("Intent: %s Action: %s", intent, intent.getAction());
                        break;
                }
            }

            if (intent.getExtras() != null) {
                ImportActivityBundle.Parser parser = new ImportActivityBundle.Parser(intent.getExtras());
                isRefresh = parser.getRefresh();
                isRename = parser.getRefreshRename();
                isCleanAbsent = parser.getRefreshCleanAbsent();
                isCleanNoImages = parser.getRefreshCleanNoImages();
                isCleanUnreadable = parser.getRefreshCleanUnreadable();
            }
        }

        EventBus.getDefault().register(this);

        prepImport(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        exit(RESULT_CANCELED, ConstsImport.RESULT_CANCELED);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    private void prepImport(Bundle savedState) {
        if (savedState != null) {
            currentRootDir = (File) savedState.getSerializable(CURRENT_DIR);
            prevRootDir = (File) savedState.getSerializable(PREV_DIR);
            restartOnExit = savedState.getBoolean(RESTART_ON_EXIT);
            calledByPrefs = savedState.getBoolean(CALLED_BY_PREFS);
            useDefaultFolder = savedState.getBoolean(USE_DEFAULT_FOLDER);

            Bundle bundle = savedState.getBundle(REFRESH_OPTIONS);
            if (bundle != null) {
                ImportActivityBundle.Parser parser = new ImportActivityBundle.Parser(bundle);
                isRefresh = parser.getRefresh();
                isRename = parser.getRefreshRename();
                isCleanAbsent = parser.getRefreshCleanAbsent();
                isCleanNoImages = parser.getRefreshCleanNoImages();
                isCleanUnreadable = parser.getRefreshCleanUnreadable();
            }
        }
        checkForDefaultDirectory();
    }

    private void checkForDefaultDirectory() {
        if (PermissionUtil.requestExternalStoragePermission(this, ConstsImport.RQST_STORAGE_PERMISSION)) {
            Timber.d("Storage permission allowed!");
            String settingDir = Preferences.getRootFolderName();
            Timber.d(settingDir);

            File file;
            if (!settingDir.isEmpty())
                file = new File(settingDir);
            else {
                file = getExistingHentoidDirFrom(Environment.getExternalStorageDirectory());
                if (file.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath()))
                    file = new File(Environment.getExternalStorageDirectory(), Consts.DEFAULT_LOCAL_DIRECTORY);
            }

            if (file.exists() && file.isDirectory()) {
                currentRootDir = file;
            } else {
                currentRootDir = FileHelper.getDefaultDir(this, "");
                Timber.d("Creating new storage directory.");
            }
            pickDownloadDirectory(currentRootDir);
        } else {
            Timber.d("Storage permission denied!");
        }
    }

    // Try and detect any ".Hentoid" or "Hentoid" folder inside the selected folder
    private static File getExistingHentoidDirFrom(@NonNull File root) {

        if (!root.exists() || !root.isDirectory()) return root;

        if (root.getName().equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY)
                || root.getName().equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY_OLD))
            return root;

        File[] hentoidDirs = root.listFiles(
                file -> (file.isDirectory() &&
                        (
                                file.getName().equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY)
                                        || file.getName().equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY_OLD)
                        )
                )
        );

        if (hentoidDirs.length > 0) return hentoidDirs[0];
        else return root;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(CURRENT_DIR, currentRootDir);
        outState.putSerializable(PREV_DIR, prevRootDir);
        outState.putBoolean(RESTART_ON_EXIT, restartOnExit);
        outState.putBoolean(CALLED_BY_PREFS, calledByPrefs);
        outState.putBoolean(USE_DEFAULT_FOLDER, useDefaultFolder);

        ImportActivityBundle.Builder builder = new ImportActivityBundle.Builder();

        builder.setRefresh(isRefresh);
        builder.setRefreshRename(isRename);
        builder.setRefreshCleanAbsent(isCleanAbsent);
        builder.setRefreshCleanNoImages(isCleanNoImages);
        builder.setRefreshCleanUnreadable(isCleanUnreadable);

        outState.putBundle(REFRESH_OPTIONS, builder.getBundle());

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length <= 0) return;

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            exit(RESULT_OK, ConstsImport.PERMISSION_GRANTED);
        } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            // Permission Denied
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                exit(RESULT_CANCELED, ConstsImport.PERMISSION_DENIED);
            } else {
                exit(RESULT_CANCELED, ConstsImport.PERMISSION_DENIED_FORCED);
            }
        }
    }

    // Present Directory Picker
    private void pickDownloadDirectory(@NonNull final File dir) {
        if (FileHelper.isOnExtSdCard(dir) && !FileHelper.isWritable(dir)) {
            Timber.d("Inaccessible: moving back to default directory.");
            currentRootDir = new File(Environment.getExternalStorageDirectory() +
                    File.separator + Consts.DEFAULT_LOCAL_DIRECTORY + File.separator);
        }
        if (useDefaultFolder) {
            prevRootDir = currentRootDir;
            initImport();
        } else {
            openFolderPicker();
        }
    }

    private void initImport() {
        Timber.d("Clearing SAF");
        FileHelper.clearUri();
        revokePermission();

        Timber.d("Storage Path: %s", currentRootDir);

        importFolder(getExistingHentoidDirFrom(currentRootDir));
    }

    private void revokePermission() {
        for (UriPermission p : getContentResolver().getPersistedUriPermissions()) {
            getContentResolver().releasePersistableUriPermission(p.getUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        if (getContentResolver().getPersistedUriPermissions().isEmpty()) {
            Timber.d("Permissions revoked successfully.");
        } else {
            Timber.d("Permissions failed to be revoked.");
        }
    }

    private void openFolderPicker() {
        // Run SAF directory picker for Lollipop and above
        if (Build.VERSION.SDK_INT >= LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.putExtra(DocumentsContract.EXTRA_PROMPT, "Allow Write Permission");
            }
            // http://stackoverflow.com/a/31334967/1615876
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

            // Start the SAF at the specified location
            if (Build.VERSION.SDK_INT >= O && !Preferences.getSdStorageUri().isEmpty()) {
                DocumentFile file = DocumentFile.fromTreeUri(this, Uri.parse(Preferences.getSdStorageUri()));
                if (file != null)
                    intent.putExtra(EXTRA_INITIAL_URI, file.getUri());
            }

            startActivityForResult(intent, ConstsImport.RQST_STORAGE_PERMISSION);
        } else { // Kitkat : display the specific dialog for kitkat
            KitkatRootFolderFragment.invoke(getSupportFragmentManager());
        }
    }

    // Return from SAF picker
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Return from the SAF picker
        if (requestCode == ConstsImport.RQST_STORAGE_PERMISSION && resultCode == RESULT_OK) { // TODO - what happens when resultCode is _not_ RESULT_OK ?
            // Get Uri from Storage Access Framework
            Uri treeUri = data.getData();
            if (treeUri != null) onSelectSAFRootFolder(treeUri);
        }
    }

    // Return from Kitkat picker
    public void onSelectKitKatRootFolder(@NonNull File targetFolder) {
        finalizeSelectRootFolder(targetFolder);
    }

    // Return from Kitkat picker
    public void onSelectSAFRootFolder(@NonNull Uri treeUri) {
        String treePath = treeUri.getPath();

        if (null == treePath) {
            Timber.w("treePath is null");
            return;
        }

        int treePathSeparator = treePath.indexOf(':');
        String folderName = treePath.substring(treePathSeparator + 1);

        // Persist access permissions
        getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

//                String folderPath = null;
        File selectedFolder = null;
        // Is the selected folder on a removable media ?
        String[] removableMediaFolderRoots = FileHelper.getExtSdCardPaths();
        for (String s : removableMediaFolderRoots) {
            String sRoot = s.substring(s.lastIndexOf(File.separatorChar));
            String treeRoot = treePath.substring(0, treePathSeparator);
            treeRoot = treeRoot.substring(treeRoot.lastIndexOf(File.separatorChar));
            if (sRoot.equalsIgnoreCase(treeRoot)) {
                // Persist selected folder URI in shared preferences
                // NB : calling saveUri populates the preference used by FileHelper.isSAF, which indicates the library storage is on an SD card / an external USB storage device
                FileHelper.saveUri(treeUri);
                selectedFolder = new File(s + File.separatorChar + folderName);
                break;
            }
        }

        // Try with phone memory
        if (null == selectedFolder) {
            FileHelper.clearUri();
            selectedFolder = new File(Environment.getExternalStorageDirectory(), folderName);
        }

        finalizeSelectRootFolder(selectedFolder);
    }

    private void finalizeSelectRootFolder(@NonNull final File targetFolder) {
        String message;
        boolean success = false;

        // Add the Hentoid folder at the end of the path, if not present
        File folder = addHentoidFolder(targetFolder);

        if (!folder.exists()) {
            // Try and create directory; test if writable
            if (FileHelper.createDirectory(folder)) {
                Timber.i("Target folder created");
                if (FileHelper.isWritable(folder)) {
                    message = getResources().getString(R.string.kitkat_dialog_return_0);
                    success = true;
                } else message = getResources().getString(R.string.kitkat_dialog_return_1);
            } else message = getResources().getString(R.string.kitkat_dialog_return_2);

            message = message.replace("$s", folder.getAbsolutePath());
            ToastUtil.toast(HentoidApp.getAppContext(), message, Toast.LENGTH_LONG);
        } else success = true;

        if (success) importFolder(folder);
    }

    private File addHentoidFolder(@NonNull final File baseFolder) {
        String folderName = baseFolder.getName();
        // Don't create a .Hentoid subfolder inside the .Hentoid (or Hentoid) folder the user just selected...
        if (!folderName.equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY) && !folderName.equalsIgnoreCase(Consts.DEFAULT_LOCAL_DIRECTORY_OLD)) {
            File targetFolder = getExistingHentoidDirFrom(baseFolder);

            // If not, create one
            if (targetFolder.getAbsolutePath().equals(baseFolder.getAbsolutePath()))
                return new File(targetFolder, Consts.DEFAULT_LOCAL_DIRECTORY);
            else return targetFolder;
        }
        return baseFolder;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onImportEventProgress(ImportEvent event) {
        if (ImportEvent.EV_PROGRESS == event.eventType) {
            progressDialog.setMax(event.booksTotal);
            progressDialog.setProgress(event.booksOK + event.booksKO);
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onImportEventComplete(ImportEvent event) {
        if (ImportEvent.EV_COMPLETE == event.eventType) {
            if (progressDialog != null) progressDialog.dismiss();
            exit(RESULT_OK, (event.booksOK > 0) ? ConstsImport.EXISTING_LIBRARY_IMPORTED : ConstsImport.NEW_LIBRARY_CREATED);
        }
    }

    // Count the elements inside each site's download folder (but not its subfolders)
    //
    // NB : this method works approximately because it doesn't try to count JSON files
    // However, findFilesRecursively -the method used by ImportService- is too slow on certain phones
    // and might cause freezes -> we stick to that approximate method for ImportActivity
    private boolean hasBooks() {
        List<File> downloadDirs = new ArrayList<>();
        for (Site s : Site.values()) {
            downloadDirs.add(ContentHelper.getOrCreateSiteDownloadDir(this, s));
        }

        for (File downloadDir : downloadDirs) {
            File[] contentFiles = downloadDir.listFiles();
            if (contentFiles != null && contentFiles.length > 0) return true;
        }

        return false;
    }

    private void importFolder(File folder) {
        if (!FileHelper.checkAndSetRootFolder(folder.getAbsolutePath(), true)) {
            prepImport(null);
            return;
        }

        if (hasBooks()) {
            if (isRefresh)
                runImport(); // Do not ask if the user wants to import if he has asked for a refresh
            else new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_dialog_warning)
                    .setCancelable(false)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.contents_detected)
                    .setPositiveButton(android.R.string.yes,
                            (dialog1, which) -> {
                                dialog1.dismiss();
                                runImport();
                            })
                    .setNegativeButton(android.R.string.no,
                            (dialog12, which) -> {
                                dialog12.dismiss();
                                // Prior Library found, but user chose to cancel
                                restartOnExit = false;
                                if (prevRootDir != null) {
                                    currentRootDir = prevRootDir;
                                }
                                if (currentRootDir != null) {
                                    FileHelper.checkAndSetRootFolder(currentRootDir.getAbsolutePath());
                                }
                                exit(RESULT_CANCELED, ConstsImport.EXISTING_LIBRARY_FOUND);
                            })
                    .create()
                    .show();
        } else {
            // New library created - drop and recreate db (in case user is re-importing)
            cleanUpDB();

            new Handler().postDelayed(() -> exit(RESULT_OK, ConstsImport.NEW_LIBRARY_CREATED), 100);
        }
    }

    private void runImport() {
        // Prior Library found, drop and recreate db
        cleanUpDB();
        // Send results to scan
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(R.string.import_dialog);
        progressDialog.setMessage(this.getText(R.string.please_wait));
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(0);
        progressDialog.show();

        ImportNotificationChannel.init(this);
        Intent intent = ImportService.makeIntent(this);

        ImportActivityBundle.Builder builder = new ImportActivityBundle.Builder();
        builder.setRefresh(isRefresh);
        builder.setRefreshRename(isRename);
        builder.setRefreshCleanAbsent(isCleanAbsent);
        builder.setRefreshCleanNoImages(isCleanNoImages);
        builder.setRefreshCleanUnreadable(isCleanUnreadable);
        intent.putExtras(builder.getBundle());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void cleanUpDB() {
        Timber.d("Cleaning up DB.");
        Context context = HentoidApp.getAppContext();
        ObjectBoxDB db = ObjectBoxDB.getInstance(context);
        db.deleteAllBooks();
    }

    private void exit(int resultCode, String data) {
        Timber.d("Import activity exit - Data : %s, Restart needed: %s", data, restartOnExit);

        Intent returnIntent = new Intent();
        returnIntent.putExtra(ConstsImport.RESULT_KEY, data);
        setResult(resultCode, returnIntent);
        finish();

        if (restartOnExit && calledByPrefs) {
            Helper.doRestart(this);
        }
    }
}