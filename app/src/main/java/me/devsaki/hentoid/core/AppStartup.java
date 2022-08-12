package me.devsaki.hentoid.core;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.greenrobot.eventbus.EventBus;

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
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.DatabaseMaintenance;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.AppUpdatedEvent;
import me.devsaki.hentoid.json.core.JsonSiteSettings;
import me.devsaki.hentoid.notification.action.UserActionNotificationChannel;
import me.devsaki.hentoid.notification.delete.DeleteNotificationChannel;
import me.devsaki.hentoid.notification.download.DownloadNotificationChannel;
import me.devsaki.hentoid.notification.startup.StartupNotificationChannel;
import me.devsaki.hentoid.notification.update.UpdateNotificationChannel;
import me.devsaki.hentoid.receiver.PlugEventsReceiver;
import me.devsaki.hentoid.services.UpdateCheckService;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.Helper;
import me.devsaki.hentoid.util.JsonHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.workers.StartupWorker;
import timber.log.Timber;

public class AppStartup {

    private List<Observable<Float>> launchTasks;
    private Disposable launchDisposable = null;

    private static boolean isInitialized = false;

    private static synchronized void setInitialized() {
        isInitialized = true;
    }

    public void initApp(
            @NonNull final Context context,
            @NonNull Consumer<Float> onMainProgress,
            @NonNull Consumer<Float> onSecondaryProgress,
            @NonNull Runnable onComplete
    ) {
        if (isInitialized) {
            onComplete.run();
            return;
        }

        // Wait until pre-launch tasks are completed
        launchTasks = getPreLaunchTasks(context);
        launchTasks.addAll(DatabaseMaintenance.getPreLaunchCleanupTasks(context));

        // TODO switch from a recursive function to a full RxJava-powered chain
        doRunTask(0, onMainProgress, onSecondaryProgress, () -> {
            if (launchDisposable != null) launchDisposable.dispose();
            setInitialized();

            onComplete.run();
            // Run post-launch tasks on a worker
            WorkManager workManager = WorkManager.getInstance(context);
            workManager.enqueueUniqueWork(Integer.toString(R.id.startup_service),
                    ExistingWorkPolicy.KEEP,
                    new OneTimeWorkRequest.Builder(StartupWorker.class).build());
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
        result.add(createObservableFrom(context, AppStartup::clearPictureCache));
        result.add(createObservableFrom(context, AppStartup::createBookmarksJson));
        result.add(createObservableFrom(context, AppStartup::createPlugReceiver));
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
            UserActionNotificationChannel.init(context);
            DeleteNotificationChannel.init(context);
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

                Timber.d("Process app update : Clearing webview cache");
                ContextXKt.clearWebviewCache(context, null);

                Timber.d("Process app update : Clearing app cache");
                ContextXKt.clearAppCache(context);

                Timber.d("Process app update : Complete");
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
            Timber.e(e, "fail@init Crashlytics");
        } finally {
            emitter.onComplete();
        }
        Timber.i("Send Firebase stats : done");
    }

    // Clear archive picture cache (useful when user kills the app while in background with the viewer open)
    private static void clearPictureCache(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        Timber.i("Clear picture cache : start");
        try {
            FileHelper.emptyCacheFolder(context, Consts.PICTURE_CACHE_FOLDER);
        } finally {
            emitter.onComplete();
        }
        Timber.i("Clear picture cache : done");
    }

    // Creates the JSON file for bookmarks if it doesn't exist
    private static void createBookmarksJson(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        Timber.i("Create bookmarks JSON : start");
        try {
            DocumentFile appRoot = FileHelper.getFolderFromTreeUriString(context, Preferences.getStorageUri());
            if (appRoot != null) {
                DocumentFile bookmarksJson = FileHelper.findFile(context, appRoot, Consts.BOOKMARKS_JSON_FILE_NAME);
                if (null == bookmarksJson) {
                    Timber.i("Create bookmarks JSON : creating JSON");
                    CollectionDAO dao = new ObjectBoxDAO(context);
                    try {
                        Helper.updateBookmarksJson(context, dao);
                    } finally {
                        dao.cleanup();
                    }
                } else {
                    Timber.i("Create bookmarks JSON : already exists");
                }
            }
        } finally {
            emitter.onComplete();
        }
        Timber.i("Create bookmarks JSON : done");
    }

    private static void createPlugReceiver(@NonNull final Context context, ObservableEmitter<Float> emitter) {
        Timber.i("Create plug receiver : start");
        try {
            PlugEventsReceiver rcv = new PlugEventsReceiver();
            final IntentFilter filter = new IntentFilter();
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_HEADSET_PLUG);

            context.registerReceiver(rcv, filter);
        } finally {
            emitter.onComplete();
        }
        Timber.i("Create plug receiver : done");
    }
}
