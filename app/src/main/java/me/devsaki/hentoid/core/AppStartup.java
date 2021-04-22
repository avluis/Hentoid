package me.devsaki.hentoid.core;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.thin.downloadmanager.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.BuildConfig;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.json.JsonSiteSettings;
import me.devsaki.hentoid.notification.download.DownloadNotificationChannel;
import me.devsaki.hentoid.notification.startup.StartupNotificationChannel;
import me.devsaki.hentoid.notification.update.UpdateNotificationChannel;
import me.devsaki.hentoid.services.UpdateCheckService;
import me.devsaki.hentoid.util.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.views.NestedScrollWebView;
import me.devsaki.hentoid.workers.StartupWorker;
import timber.log.Timber;

public class AppStartup {

    private List<Observable<Float>> launchTasks;
    private Disposable launchDisposable = null;

    private static boolean isInitialized = false;

    public void initApp(
            @NonNull final Context context,
            @NonNull Consumer<Float> onMainProgress,
            @NonNull Consumer<Float> onSecondaryProgress,
            @NonNull Runnable onComplete
    ) {
        if (isInitialized) onComplete.run();

        // Wait until pre-launch tasks are completed
        launchTasks = getPreLaunchTasks(context);
        launchTasks.addAll(DatabaseMaintenance.getPreLaunchCleanupTasks(context));

        // TODO switch from a recursive function to a full RxJava-powered chain
        doRunTask(0, onMainProgress, onSecondaryProgress, () -> {
            if (launchDisposable != null) launchDisposable.dispose();
            isInitialized = true;

            onComplete.run();
            // Run post-launch tasks on a worker
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.enqueueUniqueWork("startup", ExistingWorkPolicy.KEEP, new OneTimeWorkRequest.Builder(StartupWorker.class).build());
        });
    }

    private void doRunTask(
            int taskIndex,
            @NonNull Consumer<Float> onMainProgress,
            @NonNull Consumer<Float> onSecondaryProgress,
            @NonNull Runnable onComplete
    ) {
        if (launchDisposable != null) launchDisposable.dispose();
        try {
            onMainProgress.accept(taskIndex * 1f / launchTasks.size());
        } catch (Exception e) {
            Timber.w(e);
        }
        // Continue executing launch tasks
        if (taskIndex < launchTasks.size()) {
            Timber.i("Pre-launch task %s/%s", taskIndex + 1, launchTasks.size());
            launchDisposable = launchTasks.get(taskIndex)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            onSecondaryProgress,
                            Timber::e,
                            () -> doRunTask(taskIndex + 1, onMainProgress, onSecondaryProgress, onComplete)
                    );
        } else {
            onComplete.run();
        }
    }

    /**
     * Application initialization tasks
     * NB : Heavy operations; must be performed in the background to avoid ANR at startup
     */
    public static List<Observable<Float>> getPreLaunchTasks(@NonNull final Context context) {
        List<Observable<Float>> result = new ArrayList<>();
        result.add(createObservableFrom(context, AppStartup::stopWorkers));
        result.add(createObservableFrom(context, AppStartup::processAppUpdate));
        result.add(createObservableFrom(context, AppStartup::loadSiteProperties));
        result.add(createObservableFrom(context, AppStartup::initUtils));
        return result;
    }

    public static List<Observable<Float>> getPostLaunchTasks(@NonNull final Context context) {
        List<Observable<Float>> result = new ArrayList<>();
//        result.add(createObservableFrom(context, AppStartupDev::testImg));
        result.add(createObservableFrom(context, AppStartup::searchForUpdates));
        result.add(createObservableFrom(context, AppStartup::sendFirebaseStats));
        return result;
    }

    private static Observable<Float> createObservableFrom(@NonNull final Context context, BiConsumer<Context, ObservableEmitter<Float>> function) {
        return Observable.create(emitter -> function.accept(context, emitter));
    }

    private static void stopWorkers(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        try {
            Timber.i("Stop workers : start");
            WorkManager.getInstance(context).cancelAllWorkByTag(Consts.WORK_CLOSEABLE);
            Timber.i("Stop workers : done");
        } finally {
            emitter.onComplete();
        }
    }

    private static void loadSiteProperties(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        try (InputStream is = context.getResources().openRawResource(R.raw.sites)) {
            String siteSettingsStr = FileHelper.readStreamAsString(is);
            JsonSiteSettings siteSettings = JsonHelper.jsonToObject(siteSettingsStr, JsonSiteSettings.class);
            for (Map.Entry<String, JsonSiteSettings.JsonSite> entry : siteSettings.sites.entrySet()) {
                for (Site site : Site.values()) {
                    if (site.name().equalsIgnoreCase(entry.getKey())) {
                        site.updateFrom(entry.getValue());
                        break;
                    }
                }
            }
        } catch (IOException e) {
            Timber.e(e);
        } finally {
            emitter.onComplete();
        }
    }

    private static void initUtils(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        try {
            Timber.i("Init notifications : start");
            // Init notification channels
            StartupNotificationChannel.init(context);
            UpdateNotificationChannel.init(context);
            DownloadNotificationChannel.init(context);

            // Clears all previous notifications
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.cancelAll();
            Timber.i("Init notifications : done");
        } finally {
            emitter.onComplete();
        }
    }

    private static void processAppUpdate(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        Timber.i("Process app update : start");
        try {
            if (Preferences.getLastKnownAppVersionCode() < BuildConfig.VERSION_CODE) {
                Timber.d("Process app update : update detected from %s to %s", Preferences.getLastKnownAppVersionCode(), BuildConfig.VERSION_CODE);

                // Clear webview cache (needs to execute inside the activity's Looper)
                Timber.d("Process app update : Clearing webview cache");
                Handler h = new Handler(Looper.getMainLooper());
                h.post(() -> {
                    WebView webView;
                    try {
                        webView = new NestedScrollWebView(context);
                    } catch (Resources.NotFoundException e) {
                        // Some older devices can crash when instantiating a WebView, due to a Resources$NotFoundException
                        // Creating with the application Context fixes this, but is not generally recommended for view creation
                        webView = new NestedScrollWebView(Helper.getFixedContext(context));
                    }
                    webView.clearCache(true);
                });

                // Clear app cache
                Timber.d("Process app update : Clearing app cache");
                try {
                    File dir = context.getCacheDir();
                    FileHelper.removeFile(dir);
                } catch (Exception e) {
                    Timber.e(e, "Error when clearing app cache upon update");
                }

                EventBus.getDefault().postSticky(new AppUpdatedEvent());

                Preferences.setLastKnownAppVersionCode(BuildConfig.VERSION_CODE);
            }
        } finally {
            emitter.onComplete();
        }
        Timber.i("Process app update : done");
    }

    private static void searchForUpdates(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        Timber.i("Run app update : start");
        try {
            if (Preferences.isAutomaticUpdateEnabled()) {
                Timber.i("Run app update : auto-check is enabled");
                Intent intent = UpdateCheckService.makeIntent(context, false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
            }
        } finally {
            emitter.onComplete();
        }
        Timber.i("Run app update : done");
    }

    private static void sendFirebaseStats(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        Timber.i("Send Firebase stats : start");
        try {
            FirebaseAnalytics.getInstance(context).setUserProperty("color_theme", Integer.toString(Preferences.getColorTheme()));
            FirebaseAnalytics.getInstance(context).setUserProperty("endless", Boolean.toString(Preferences.getEndlessScroll()));
            FirebaseCrashlytics.getInstance().setCustomKey("Library display mode", Preferences.getEndlessScroll() ? "endless" : "paged");
        } catch (IllegalStateException e) { // Happens during unit tests
            Log.e("fail@init Crashlytics", e);
        } finally {
            emitter.onComplete();
        }
        Timber.i("Send Firebase stats : done");
    }
}
