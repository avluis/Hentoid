package me.devsaki.hentoid.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import com.annimon.stream.Optional;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import me.devsaki.hentoid.R;
import me.devsaki.hentoid.database.CollectionDAO;
import me.devsaki.hentoid.database.ObjectBoxDAO;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.events.ProcessEvent;
import me.devsaki.hentoid.fragments.tools.ImportDownloadsDialogFragment;
import me.devsaki.hentoid.notification.import_.ImportCompleteNotification;
import me.devsaki.hentoid.notification.import_.ImportProgressNotification;
import me.devsaki.hentoid.notification.import_.ImportStartNotification;
import me.devsaki.hentoid.util.ContentHelper;
import me.devsaki.hentoid.util.Preferences;
import me.devsaki.hentoid.util.StringHelper;
import me.devsaki.hentoid.util.download.ContentQueueManager;
import me.devsaki.hentoid.util.file.FileHelper;
import me.devsaki.hentoid.util.network.CloudflareHelper;
import me.devsaki.hentoid.util.notification.Notification;
import me.devsaki.hentoid.workers.data.DownloadsImportData;
import timber.log.Timber;


/**
 * Service responsible for importing downloads
 */
public class DownloadsImportWorker extends BaseWorker {

    // Variable used during the import process
    private CollectionDAO dao;
    private int totalItems = 0;
    private CloudflareHelper cfHelper = null;
    private int nbOK = 0;
    private int nbKO = 0;

    private final CompositeDisposable notificationDisposables = new CompositeDisposable();
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();


    public DownloadsImportWorker(
            @NonNull Context context,
            @NonNull WorkerParameters parameters) {
        super(context, parameters, R.id.downloads_import_service, "downloads-import");
    }

    public static boolean isRunning(@NonNull Context context) {
        return isRunning(context, R.id.downloads_import_service);
    }

    @Override
    Notification getStartNotification() {
        return new ImportStartNotification();
    }

    @Override
    void onInterrupt() {
        compositeDisposable.clear();
        notificationDisposables.clear();
    }

    @Override
    void onClear() {
        notificationDisposables.clear();
        compositeDisposable.clear();
        if (cfHelper != null) cfHelper.clear();
        if (dao != null) dao.cleanup();
    }

    @Override
    void getToWork(@NonNull Data input) {
        DownloadsImportData.Parser data = new DownloadsImportData.Parser(getInputData());
        if (data.getFileUri().isEmpty()) return;

        startImport(
                getApplicationContext(),
                data.getFileUri(),
                data.getQueuePosition(),
                data.getImportAsStreamed()
        );
    }

    /**
     * Import books from external folder
     */
    private void startImport(
            @NonNull final Context context,
            @NonNull final String fileUri,
            final int queuePosition,
            boolean importAsStreamed
    ) {
        DocumentFile file = FileHelper.getFileFromSingleUriString(context, fileUri);
        if (null == file) {
            trace(Log.ERROR, "Couldn't find downloads file at %s", fileUri);
            return;
        }
        List<String> downloads = ImportDownloadsDialogFragment.Companion.readFile(context, file);
        if (downloads.isEmpty()) {
            trace(Log.ERROR, "Downloads file %s is empty", fileUri);
            return;
        }
        totalItems = downloads.size();

        dao = new ObjectBoxDAO(context);

        try {
            for (String s : downloads) {
                String galleryUrl = s;
                if (StringHelper.isNumeric(galleryUrl))
                    galleryUrl = Content.getGalleryUrlFromId(Site.NHENTAI, galleryUrl);
                importGallery(galleryUrl, queuePosition, importAsStreamed, false);
            }
        } catch (InterruptedException ie) {
            Timber.e(ie);
            Thread.currentThread().interrupt();
        }

        if (Preferences.isQueueAutostart())
            ContentQueueManager.getInstance().resumeQueue(getApplicationContext());

        notifyProcessEnd();
    }

    private void importGallery(@NonNull String url, final int queuePosition, boolean importAsStreamed, boolean hasPassedCf) throws InterruptedException {
        Site site = Site.searchByUrl(url);
        if (null == site || Site.NONE == site) {
            trace(Log.WARN, "ERROR : Unsupported source @ %s", url);
            nextKO(getApplicationContext(), null);
            return;
        }

        Content existingContent = dao.selectContentBySourceAndUrl(site, Content.transformRawUrl(site, url), null);
        if (existingContent != null) {
            String location = (ContentHelper.isInQueue(existingContent.getStatus())) ? "queue" : "library";
            trace(Log.INFO, "ERROR : Content already in %s @ %s", location, url);
            nextKO(getApplicationContext(), null);
            return;
        }

        try {
            Optional<Content> content = ContentHelper.parseFromScratch(url);
            if (content.isEmpty()) {
                trace(Log.WARN, "ERROR : Unreachable content @ %s", url);
                nextKO(getApplicationContext(), null);
            } else {
                trace(Log.INFO, "Added content @ %s", url);
                Content c = content.get();
                c.setDownloadMode(importAsStreamed ? Content.DownloadMode.STREAM : Content.DownloadMode.DOWNLOAD);
                dao.addContentToQueue(
                        c,
                        null,
                        queuePosition,
                        -1,
                        ContentQueueManager.getInstance().isQueueActive(getApplicationContext())
                );
                nextOK(getApplicationContext());
            }
        } catch (IOException e) {
            trace(Log.WARN, "ERROR : While loading content @ %s", url);
            nextKO(getApplicationContext(), e);
        } catch (CloudflareHelper.CloudflareProtectedException cpe) {
            if (hasPassedCf) {
                trace(Log.WARN, "Cloudflare bypass ineffective for content @ %s", url);
                nextKO(getApplicationContext(), null);
                return;
            }
            trace(Log.INFO, "Trying to bypass Cloudflare for content @ %s", url);
            if (null == cfHelper) cfHelper = new CloudflareHelper();
            if (cfHelper.tryPassCloudflare(site, null)) {
                importGallery(url, queuePosition, importAsStreamed, true);
            } else {
                trace(Log.WARN, "Cloudflare bypass failed for content @ %s", url);
                nextKO(getApplicationContext(), null);
            }
        }
    }

    private void nextOK(@NonNull Context context) {
        nbOK++;
        notifyProcessProgress(context);
    }

    private void nextKO(@NonNull Context context, Throwable e) {
        nbKO++;
        if (e != null) Timber.w(e);
        notifyProcessProgress(context);
    }

    private void notifyProcessProgress(@NonNull Context context) {
        notificationDisposables.add(Completable.fromRunnable(() -> doNotifyProcessProgress(context))
                .subscribeOn(Schedulers.computation())
                .subscribe(
                        notificationDisposables::clear
                )
        );
    }

    private void doNotifyProcessProgress(@NonNull Context context) {
        notificationManager.notify(new ImportProgressNotification(context.getResources().getString(R.string.importing_downloads), nbOK + nbKO, totalItems));
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.PROGRESS, R.id.import_downloads, 0, nbOK, nbKO, totalItems));
    }

    private void notifyProcessEnd() {
        notificationManager.notify(new ImportCompleteNotification(nbOK, nbKO));
        EventBus.getDefault().post(new ProcessEvent(ProcessEvent.EventType.COMPLETE, R.id.import_downloads, 0, nbOK, nbKO, totalItems));
    }
}
