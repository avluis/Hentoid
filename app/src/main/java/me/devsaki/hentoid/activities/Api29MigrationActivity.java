package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel;
import me.devsaki.hentoid.services.API29MigrationService;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.ImportHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ToastHelper;
import me.devsaki.hentoid.workers.PrimaryImportWorker;
import timber.log.Timber;

public class Api29MigrationActivity extends AppCompatActivity {

    // UI
    private View step1button;
    private TextView step1folderTxt;
    private View step1check;
    private View step2block;
    private ProgressBar step2progress;
    private View step2check;
    private View step3block;
    private TextView step3Txt;
    private ProgressBar step3progress;
    private View step3check;
    private final ActivityResultLauncher<Intent> requestStoragePermLauncher =
            registerForActivityResult(new StartActivityForResult(), this::requestStoragePermResult);

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_api29_migration);

        String locationStr = Preferences.getStorageUri();
        if (locationStr.isEmpty())
            locationStr = Preferences.getSettingsFolder();
        else
            locationStr = FileHelper.getFullPathFromTreeUri(this, Uri.parse(locationStr));

        TextView location = findViewById(R.id.api29_location_txt);
        location.setText(getResources().getString(R.string.api29_migration_location, locationStr));

        // UI
        step1button = findViewById(R.id.import_step1_button);
        step1button.setOnClickListener(v -> selectHentoidFolder());
        step1folderTxt = findViewById(R.id.import_step1_folder);
        step1check = findViewById(R.id.import_step1_check);
        step2block = findViewById(R.id.import_step2);
        step2progress = findViewById(R.id.import_step2_bar);
        step2check = findViewById(R.id.import_step2_check);
        step3block = findViewById(R.id.import_step3);
        step3Txt = findViewById(R.id.import_step3_text);
        step3progress = findViewById(R.id.import_step3_bar);
        step3check = findViewById(R.id.import_step3_check);

        EventBus.getDefault().register(this);
        doMigrate();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    private void doMigrate() {
        Timber.d("API 29 migration / Initiated");
        DocumentFile storageDoc = FileHelper.getFolderFromTreeUriString(this, Preferences.getStorageUri());

        // If the root folder is already set to a content:// URI (previous use of SAF picker), start scanning at once
        if (storageDoc != null) {
            Timber.d("Detected dir : %s", storageDoc.getUri().toString());
            // Make certain we have the actual Hentoid/.Hentoid folder (root URI can be set to its parent on certain devices)
            storageDoc = ImportHelper.getExistingHentoidDirFrom(this, storageDoc);
            if (storageDoc != null) {
                Timber.d("Suggested dir : %s", storageDoc.getUri().toString());
                Preferences.setStorageUri(storageDoc.getUri().toString());
                scanLibrary(storageDoc);
            }
        }
        // else ask for the Hentoid folder, as PersistableUriPermission might not have been granted at all
        // (case of v11- app running on Android 10 with API28- target)
        if (null == storageDoc) step1button.setVisibility(View.VISIBLE);
    }

    private void selectHentoidFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.putExtra(DocumentsContract.EXTRA_PROMPT, getResources().getString(R.string.api29_migration_allow_write));
        }
        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);

        requestStoragePermLauncher.launch(intent);
    }

    private void requestStoragePermResult(final ActivityResult result) {
        // Return from the SAF picker
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            // Get Uri from Storage Access Framework
            Uri treeUri = result.getData().getData();
            if (treeUri != null) onSelectSAFRootFolder(treeUri);
        }
    }

    // Return from SAF picker
    public void onSelectSAFRootFolder(@NonNull final Uri treeUri) {

        FileHelper.persistNewUriPermission(this, treeUri, null); // We're migrating from v1.11- so there's no external library set

        DocumentFile selectedFolder = DocumentFile.fromTreeUri(this, treeUri);
        if (selectedFolder != null) {
            String folderName = selectedFolder.getName();
            if (null == folderName) folderName = "";

            // Make sure we detect the Hentoid folder if it's a child of the selected folder
            if (!ImportHelper.isHentoidFolderName(folderName))
                selectedFolder = ImportHelper.getExistingHentoidDirFrom(this, selectedFolder);
        }

        // If no existing hentoid folder is detected, tell the user to select it again
        if (null == selectedFolder || null == selectedFolder.getName() || !ImportHelper.isHentoidFolderName(selectedFolder.getName())) {
            ToastHelper.toast(R.string.api29_migration_select_folder);
            return;
        }
        scanLibrary(selectedFolder);
    }

    private void scanLibrary(@NonNull final DocumentFile root) {
        // Check if the selected folder is valid (user error msgs are displayed inside this call)
        int result = FileHelper.checkAndSetRootFolder(this, root);
        if (result < 0) {
            step1button.setVisibility(View.VISIBLE);
            if (-1 == result) ToastHelper.toast(this, R.string.error_creating_folder);
            else if (-2 == result || -3 == result)
                ToastHelper.toast(this, R.string.error_write_permission);
            return;
        }

        // Hentoid folder is finally selected at this point -> Update UI
        step1folderTxt.setText(FileHelper.getFullPathFromTreeUri(this, Uri.parse(Preferences.getStorageUri())));
        step1button.setVisibility(View.GONE);
        step1check.setVisibility(View.VISIBLE);
        step2block.setVisibility(View.VISIBLE);

        ImportNotificationChannel.init(this);
        Intent intent = API29MigrationService.makeIntent(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMigrationEvent(ProcessEvent event) {
        if (event.processId != R.id.migrate_api29) return;

        ProgressBar progressBar = (PrimaryImportWorker.STEP_2_BOOK_FOLDERS == event.step) ? step2progress : step3progress;
        if (ProcessEvent.EventType.PROGRESS == event.eventType) {
            progressBar.setMax(event.elementsTotal);
            progressBar.setProgress(event.elementsOK + event.elementsKO);
            if (PrimaryImportWorker.STEP_3_BOOKS == event.step) {
                step2check.setVisibility(View.VISIBLE);
                step3block.setVisibility(View.VISIBLE);
                step3Txt.setText(getResources().getString(R.string.api29_migration_step3, event.elementsKO + event.elementsOK, event.elementsTotal));
            }
        } else if (ProcessEvent.EventType.COMPLETE == event.eventType && PrimaryImportWorker.STEP_3_BOOKS == event.step) {
            step3Txt.setText(getResources().getString(R.string.api29_migration_step3, event.elementsTotal, event.elementsTotal));
            step3check.setVisibility(View.VISIBLE);
            goToLibraryActivity();
        }
    }

    private void goToLibraryActivity() {
        Timber.d("API29 migration / Complete : Launch library");
        Intent intent = new Intent(this, LibraryActivity.class);
        intent = UnlockActivity.wrapIntent(this, intent);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
