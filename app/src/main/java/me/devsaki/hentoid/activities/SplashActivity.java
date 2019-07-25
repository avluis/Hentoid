package me.devsaki.hentoid.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.services.DatabaseMigrationService;
import me.devsaki.hentoid.util.Preferences;

/**
 * Displays a Splash while starting up.
 * <p>
 * Nothing but a splash/activity selection should be defined here.
 */
public class SplashActivity extends AppCompatActivity {

    private ProgressDialog progressDialog;
    private boolean busRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Preferences.isFirstRun()) {
            goToActivity(new Intent(this, IntroActivity.class));
        } else if (DatabaseMaintenance.hasToMigrate(this)) {
            handleDatabaseMigration();
        } else {
            goToDownloadsActivity();
        }
    }

    @Override
    protected void onDestroy() {
        if (busRegistered) EventBus.getDefault().unregister(this);

        super.onDestroy();
    }

    private void goToActivity(Intent intent) {
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private void goToDownloadsActivity() {
        Intent intent = new Intent(this, DownloadsActivity.class);
        intent = UnlockActivity.wrapIntent(this, intent);
        goToActivity(intent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onImportEvent(ImportEvent event) {
        if (ImportEvent.EV_PROGRESS == event.eventType) {
            progressDialog.setMax(event.booksTotal);
            progressDialog.setProgress(event.booksOK + event.booksKO);
        } else if (ImportEvent.EV_COMPLETE == event.eventType) {
            if (progressDialog != null) progressDialog.dismiss();
            goToDownloadsActivity();
        }
    }

    private void handleDatabaseMigration() {
        EventBus.getDefault().register(this);
        busRegistered = true;

        // Send results to scan
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(R.string.migrate_db);
        progressDialog.setMessage(this.getText(R.string.please_wait));
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(0);
        progressDialog.show();

        Intent intent = DatabaseMigrationService.makeIntent(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
