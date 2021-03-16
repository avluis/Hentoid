package me.devsaki.hentoid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.core.AppStartup;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.util.Preferences;
import timber.log.Timber;

/**
 * Displays a Splash while starting up.
 * <p>
 * Nothing but a splash/activity selection should be defined here.
 */
public class SplashActivity extends BaseActivity {

    private List<Observable<Float>> launchTasks;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private ProgressBar mainPb;
    private ProgressBar secondaryPb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        //ThemeHelper.applyTheme(this); <-- this won't help; the starting activity is shown with the default theme, aka Light

        mainPb = findViewById(R.id.progress_main);
        secondaryPb = findViewById(R.id.progress_secondary);
        TextView quote = findViewById(R.id.quote);

        String[] quotes = getResources().getStringArray(R.array.splash_quotes);
        int random = new Random().nextInt(quotes.length);
        quote.setText(quotes[random]);

        Timber.d("Splash / Init");

        // Wait until launch tasks are completed
        launchTasks = AppStartup.getPreLaunchTasks(this);
        launchTasks.addAll(DatabaseMaintenance.getPreLaunchCleanupTasks(this));
        // TODO execute post-launch tasks in another runner (background worker ?)
        launchTasks.addAll(AppStartup.getPostLaunchTasks(this));
        launchTasks.addAll(DatabaseMaintenance.getPostLaunchCleanupTasks(this));
        doLaunchTask(0);
    }

    private void doLaunchTask(int taskIndex) {
        mainPb.setProgress(Math.round(100 * (taskIndex * 1f / launchTasks.size())));
        // Continue executing launch tasks
        if (taskIndex < launchTasks.size()) {
            Timber.i("Splash / Launch task %s/%s", taskIndex + 1, launchTasks.size());
            compositeDisposable.add(
                    launchTasks.get(taskIndex)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    this::displaySecondaryProgress,
                                    Timber::e,
                                    () -> doLaunchTask(taskIndex + 1)
                            )
            );
        } else {
            mainPb.setVisibility(View.GONE);
            secondaryPb.setVisibility(View.GONE);
            followStartupFlow();
        }
    }

    private void displaySecondaryProgress(Float progress) {
        secondaryPb.setProgress(Math.round(progress * 100));
    }

    private void followStartupFlow() {
        Timber.d("Splash / Startup flow initiated");
        if (Preferences.isFirstRun()) { // Go to intro wizard if it's a first run
            goToActivity(new Intent(this, IntroActivity.class));
        } else if (hasToMigrateAPI29()) { // Go to API29 migration if the app has to migrate
            goToAPI29MigrationActivity();
        } else { // Go to the library screen
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
        try {
            long imagesKO = dao.countOldStoredContent();
            Timber.d("Splash / API 29 migration detector : %s books KO", imagesKO);
            return imagesKO > 0;
        } finally {
            dao.cleanup();
        }
    }

    @Override
    protected void onDestroy() {
        if (launchTasks != null) launchTasks.clear();
        compositeDisposable.clear();

        super.onDestroy();
    }

    /**
     * Close splash screen and go to the given activity
     *
     * @param intent Intent to launch through a new activity
     */
    private void goToActivity(Intent intent) {
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }

    /**
     * Go to the API29 migration screen (App v1.11- -> v1.12+)
     */
    private void goToAPI29MigrationActivity() {
        Timber.d("Splash / Launch API 29 migration");
        Intent intent = new Intent(this, Api29MigrationActivity.class);
        intent = UnlockActivity.wrapIntent(this, intent);
        goToActivity(intent);
    }

    /**
     * Go to the library screen
     */
    private void goToLibraryActivity() {
        Timber.d("Splash / Launch library");
        Intent intent = new Intent(this, LibraryActivity.class);
        intent = UnlockActivity.wrapIntent(this, intent);
        goToActivity(intent);
    }
}
