package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.views.NestedScrollWebView;
import timber.log.Timber;

/**
 * Displays a Splash while starting up.
 * <p>
 * Nothing but a splash/activity selection should be defined here.
 */
public class SplashActivity extends AppCompatActivity {

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //ThemeHelper.applyTheme(this); <-- this won't help; the starting activity is shown with the default theme, aka Light

        Timber.d("Splash / Init");

        // Pre-processing on app update
        if (Preferences.getLastKnownAppVersionCode() < BuildConfig.VERSION_CODE) {
            Timber.d("Splash / Update detected");
            onAppUpdated();
            Preferences.setLastKnownAppVersionCode(BuildConfig.VERSION_CODE);
        } else {
            followStartupFlow();
        }
    }

    private void followStartupFlow() {
        Timber.d("Splash / Startup flow initiated");
        if (Preferences.isFirstRun()) {
            goToActivity(new Intent(this, IntroActivity.class));
        } else if (hasToMigrateAPI29()) {
            goToAPI29MigrationActivity();
        } else {
            goToLibraryActivity();
        }
    }

    /**
     * Test if the ImageFiles stored in the DB have their URI's filled
     * If not, it indicates the collection has not been updated to fit the Android 10 I/O update
     *
     * @return True if a migration has to happen; false if not
     */
    private boolean hasToMigrateAPI29() {
        CollectionDAO dao = new ObjectBoxDAO(this);
        long imagesKO = dao.countOldStoredContent();
        Timber.d("Splash / API 29 migration detector : %s books KO", imagesKO);
        return imagesKO > 0;
    }

    @Override
    protected void onDestroy() {
        compositeDisposable.clear();

        super.onDestroy();
    }

    private void goToActivity(Intent intent) {
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    private void goToAPI29MigrationActivity() {
        Timber.d("Splash / Launch API 29 migration");
        Intent intent = new Intent(this, Api29MigrationActivity.class);
        intent = UnlockActivity.wrapIntent(this, intent);
        goToActivity(intent);
    }

    private void goToLibraryActivity() {
        Timber.d("Splash / Launch library");
        Intent intent = new Intent(this, LibraryActivity.class);
        intent = UnlockActivity.wrapIntent(this, intent);
        goToActivity(intent);
    }

    private void onAppUpdated() {
        compositeDisposable.add(
                Completable.fromRunnable(this::doOnAppUpdated)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                this::followStartupFlow,
                                Timber::e)
        );
    }

    private void doOnAppUpdated() {
        Timber.d("Splash / Start update pre-processing");
        // Clear webview cache (needs to execute inside the activity's Looper)
        Timber.d("Splash / Clearing webview cache");
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> {
            WebView webView;
            try {
                webView = new NestedScrollWebView(this);
            } catch (Resources.NotFoundException e) {
                // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
                // Creating with the application Context fixes this, but is not generally recommended for view creation
                webView = new NestedScrollWebView(Helper.getFixedContext(this));
            }
            webView.clearCache(true);
        });

        // Clear app cache
        Timber.d("Splash / Clearing app cache");
        try {
            File dir = this.getCacheDir();
            FileHelper.removeFile(dir);
        } catch (Exception e) {
            Timber.e(e, "Error when clearing app cache upon update");
        }

        EventBus.getDefault().postSticky(new AppUpdatedEvent());
        Timber.d("Splash / Update pre-processing complete");
    }
}
