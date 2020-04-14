package me.devsaki.hentoid.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.lmntrx.android.library.livin.missme.ProgressDialog;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import io.reactivex.disposables.CompositeDisposable;
import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.activities.bundles.ImportActivityBundle;
import me.devsaki.hentoid.database.ObjectBoxDB;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel;
import me.devsaki.hentoid.services.ImportService;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.ThemeHelper;
import timber.log.Timber;

public class Api29MigrationActivity extends AppCompatActivity {

    private ProgressDialog progressDialog;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private ObjectBoxDB db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_api29_migration);

        EventBus.getDefault().register(this);
        doMigrate();
    }

    private void doMigrate() {
        Timber.d("API 29 migration / Initiated");
        String storageUri = Preferences.getStorageUri();
        DocumentFile storageDoc = (storageUri.isEmpty()) ? null : DocumentFile.fromTreeUri(this, Uri.parse(storageUri));

        if (null == storageDoc || !storageDoc.exists()) {
            selectHentoidFolder();
        } else { // Folder already set to a content:// URI (previous use of SAF picker)
            scanLibrary(storageDoc);
        }
    }

    private void selectHentoidFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.putExtra(DocumentsContract.EXTRA_PROMPT, "Allow Write Permission");
        }
        // http://stackoverflow.com/a/31334967/1615876
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        startActivityForResult(intent, ConstsImport.RQST_STORAGE_PERMISSION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Return from the SAF picker
        if (requestCode == ConstsImport.RQST_STORAGE_PERMISSION && resultCode == RESULT_OK) {
            // Get Uri from Storage Access Framework
            Uri treeUri = data.getData();
            if (treeUri != null) onSelectSAFRootFolder(treeUri);
        } else if (resultCode == RESULT_CANCELED) {
            selectHentoidFolder(); // TODO explain to user and make him start again manually
        }
    }

    // Return from SAF picker
    public void onSelectSAFRootFolder(@NonNull final Uri treeUri) {

        // Release previous access permissions, if different than the new one
        FileHelper.revokePreviousPermissions(this, treeUri);

        // Persist new access permission
        getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        // Is this even possible, knowing that we just c
        DocumentFile docFile = DocumentFile.fromTreeUri(this, treeUri);
        if (docFile != null) {
            scanLibrary(docFile);
        }
    }

    private void scanLibrary(@NonNull final DocumentFile root) {
        if (!FileHelper.checkAndSetRootFolder(this, root, true)) {
            selectHentoidFolder(); // TODO explain to user and make him start again manually
            return;
        }

        // TODO integrate that into the main screen instead of a progress dialog
        // Send results to scan
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(0);
        progressDialog.setColor(ThemeHelper.getColor(this, R.color.secondary_light));
        progressDialog.setTextColor(R.color.white_opacity_87);
        progressDialog.setMessage(this.getString(R.string.updating_please_wait));
        progressDialog.show();

        ImportNotificationChannel.init(this);
        Intent intent = ImportService.makeIntent(this);

        ImportActivityBundle.Builder builder = new ImportActivityBundle.Builder();
        builder.setRefreshRename(false);
        builder.setRefreshCleanAbsent(false);
        builder.setRefreshCleanNoImages(false);
        builder.setRefreshCleanUnreadable(false);
        intent.putExtras(builder.getBundle());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onImportEvent(ImportEvent event) {
        if (ImportEvent.EV_PROGRESS == event.eventType) {
            progressDialog.setMax(event.booksTotal);
            progressDialog.setProgress(event.booksOK + event.booksKO);
        } else if (ImportEvent.EV_COMPLETE == event.eventType) {
            if (progressDialog != null) progressDialog.dismiss();
            goToLibraryActivity();
        }
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        compositeDisposable.clear();

        super.onDestroy();
    }

    private void goToActivity(Intent intent) {
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private void goToLibraryActivity() {
        Timber.d("Splash / Launch library");
        Intent intent = new Intent(this, LibraryActivity.class);
        intent = UnlockActivity.wrapIntent(this, intent);
        goToActivity(intent);
    }
}
