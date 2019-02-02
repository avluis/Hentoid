package me.devsaki.hentoid.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.devsaki.hentoid.HentoidApp;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.events.ImportEvent;
import me.devsaki.hentoid.notification.import_.ImportNotificationChannel;
import me.devsaki.hentoid.services.DatabaseMigrationService;
import me.devsaki.hentoid.services.ImportService;
import me.devsaki.hentoid.util.AssetsCache;
import me.devsaki.hentoid.util.ConstsImport;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;

/**
 * Created by avluis on 1/9/16.
 * Displays a Splash while starting up.
 * Nothing but a splash/activity selection should be defined here.
 */
public class SplashActivity extends AppCompatActivity {

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AssetsCache.init(HentoidApp.getAppContext());

        if (Preferences.isFirstRun()) {
            Intent intent = new Intent(this, IntroActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        } else {
            if (DatabaseMaintenance.hasToMigrate(this)) handleDatabaseMigration();
            Helper.launchMainActivity(this);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }
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
        }
    }

    private void handleDatabaseMigration()
    {
        // Send results to scan
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(R.string.migrate_db);
        progressDialog.setMessage(this.getText(R.string.please_wait));
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(0);
        progressDialog.show();

        Intent intent = DatabaseMigrationService.makeIntent(this);
        startService(intent);
    }
}
